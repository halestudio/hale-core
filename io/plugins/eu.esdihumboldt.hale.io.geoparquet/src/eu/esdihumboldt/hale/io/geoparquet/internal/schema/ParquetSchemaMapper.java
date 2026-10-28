/*
 * Copyright (c) 2026 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.geoparquet.internal.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.parquet.schema.ColumnOrder;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.DefinitionUtil;
import eu.esdihumboldt.hale.common.schema.model.PropertyDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.AugmentedValueFlag;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.Binding;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.GeometryType;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.HasValueFlag;
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs;

/**
 * Maps hale type definitions to Parquet schemas.
 */
public class ParquetSchemaMapper {

	/**
	 * Safety limit for the search of the primary geometry (guards against recursive
	 * types).
	 */
	private static final int MAX_GEOMETRY_SEARCH_DEPTH = 32;

	private static final int DECIMAL_PRECISION = 38;

	private static final int DECIMAL_BYTES = 16;

	private final MappingOptions options;

	/**
	 * Create a schema mapper.
	 *
	 * @param options the mapping options
	 */
	public ParquetSchemaMapper(MappingOptions options) {
		this.options = options;
	}

	/**
	 * Determine the names of the top-level geometry columns that
	 * {@link #map(TypeDefinition, Map, SimpleLog)} will create for a type, primary
	 * geometry first. Allows to determine the CRS per column before mapping.
	 *
	 * @param type the type definition
	 * @return the geometry column names
	 */
	public List<String> geometryColumnNames(TypeDefinition type) {
		return map(type, Map.of(), SimpleLog.NO_LOG).getGeometryColumns();
	}

	/**
	 * Map a type to a Parquet schema.
	 *
	 * @param type the type definition
	 * @param geometryCrs the CRS per geometry column name, columns without entry
	 *            are assumed to be CRS84
	 * @param log the log for reporting problems
	 * @return the type mapping
	 */
	public TypeMapping map(TypeDefinition type, Map<String, GeoParquetCrs> geometryCrs,
			SimpleLog log) {
		List<QName> primaryPath = findPrimaryGeometryPath(type);

		MappingContext ctx = new MappingContext(geometryCrs, log, primaryPath);
		if (primaryPath != null) {
			// reserve names
			ctx.usedNames.add(TypeMapping.BBOX_COLUMN);
			ctx.usedNames.add(TypeMapping.PRIMARY_GEOMETRY_COLUMN);
		}

		mapChildren(DefinitionUtil.getAllChildren(type), List.of(), true, ctx);

		List<Type> fields = new ArrayList<>(ctx.fields);
		List<String> geometryColumns = new ArrayList<>();
		if (primaryPath != null) {
			fields.add(createBbox());
			fields.add(createGeometryField(TypeMapping.PRIMARY_GEOMETRY_COLUMN, ctx));
			ctx.columns.add(new ColumnMapping(TypeMapping.PRIMARY_GEOMETRY_COLUMN, primaryPath,
					false, true, ValueKind.WKB, 0));
			geometryColumns.add(TypeMapping.PRIMARY_GEOMETRY_COLUMN);
		}
		geometryColumns.addAll(ctx.secondaryGeometryColumns);

		String messageName = type.getName().getLocalPart();
		MessageType schema = new MessageType(messageName, fields);
		return new TypeMapping(type, schema, ctx.columns, geometryColumns, primaryPath,
				primaryPath != null);
	}

	/**
	 * Map a collection of child definitions. Groups and choices are flattened into
	 * the parent.
	 *
	 * @param children the children
	 * @param path the property path of the parent
	 * @param topLevel if the children are on top level (not inside a struct)
	 * @param ctx the mapping context
	 */
	private void mapChildren(Collection<? extends ChildDefinition<?>> children, List<QName> path,
			boolean topLevel, MappingContext ctx) {
		for (ChildDefinition<?> child : children) {
			if (child.asGroup() != null) {
				mapChildren(DefinitionUtil.getAllChildren(child.asGroup()),
						append(path, child.getName()), topLevel, ctx);
			}
			else {
				mapProperty(child.asProperty(), path, topLevel, ctx);
			}
		}
	}

	/**
	 * Map a single property. The generated field and column mapping are added to
	 * the context.
	 *
	 * @param property the property
	 * @param parentPath the property path of the parent
	 * @param topLevel if the property is on top level (not inside a struct)
	 * @param ctx the mapping context
	 */
	private void mapProperty(PropertyDefinition property, List<QName> parentPath, boolean topLevel,
			MappingContext ctx) {
		List<QName> path = append(parentPath, property.getName());
		TypeDefinition propType = property.getPropertyType();

		if (path.equals(ctx.primaryPath)) {
			// moved to the primary geometry column
			return;
		}

		String name = ctx.uniqueName(property.getName().getLocalPart());

		if (propType.getConstraint(GeometryType.class).isGeometry()) {
			if (topLevel) {
				ctx.fields.add(createGeometryField(name, ctx));
				ctx.columns.add(new ColumnMapping(name, path, false, true, ValueKind.WKB, 0));
				ctx.secondaryGeometryColumns.add(name);
			}
			else {
				// nested geometries are written as WKT (not yet supported)
				ctx.usedNames.remove(name);
				ctx.log.warn("Skipping nested geometry property {0}", path);
			}
			return;
		}

		if (!propType.getConstraint(HasValueFlag.class).isEnabled()
				&& !propType.getConstraint(AugmentedValueFlag.class).isEnabled()) {
			// complex property without value (structs are not yet supported)
			ctx.usedNames.remove(name);
			ctx.log.warn("Skipping complex property {0} without value", path);
			return;
		}

		Class<?> binding = propType.getConstraint(Binding.class).getBinding();
		ValueKind kind = determineValueKind(binding);
		int scale = decimalScale(kind, binding);
		boolean repeated = DefinitionUtil.getCardinality(property).mayOccurMultipleTimes();

		ctx.fields.add(createField(name, kind, scale, repeated));
		ctx.columns.add(new ColumnMapping(name, path, repeated, true, kind, scale));
	}

	private int decimalScale(ValueKind kind, Class<?> binding) {
		if (kind != ValueKind.DECIMAL) {
			return 0;
		}
		return BigInteger.class.equals(binding) ? 0 : options.decimalScale();
	}

	/**
	 * Determine the kind of value for a binding.
	 *
	 * @param binding the property binding, may be <code>null</code>
	 * @return the value kind
	 */
	private ValueKind determineValueKind(Class<?> binding) {
		if (binding == null || String.class.equals(binding) || URI.class.equals(binding)) {
			return ValueKind.STRING;
		}
		if (Boolean.class.equals(binding)) {
			return ValueKind.BOOLEAN;
		}
		if (Byte.class.equals(binding) || Short.class.equals(binding)
				|| Integer.class.equals(binding)) {
			return ValueKind.INT32;
		}
		if (Long.class.equals(binding)) {
			return ValueKind.INT64;
		}
		if (Float.class.equals(binding)) {
			return ValueKind.FLOAT;
		}
		if (Double.class.equals(binding)) {
			return ValueKind.DOUBLE;
		}
		if (BigInteger.class.equals(binding) || BigDecimal.class.equals(binding)) {
			return switch (options.bigNumberEncoding()) {
			case DOUBLE -> ValueKind.DOUBLE;
			case DECIMAL -> ValueKind.DECIMAL;
			case STRING -> ValueKind.DECIMAL_AS_STRING;
			};
		}
		if (LocalDate.class.equals(binding) || java.sql.Date.class.equals(binding)) {
			return ValueKind.DATE;
		}
		if (LocalTime.class.equals(binding) || java.sql.Time.class.equals(binding)) {
			return ValueKind.TIME_MILLIS;
		}
		if (Instant.class.equals(binding) || LocalDateTime.class.equals(binding)
				|| Date.class.isAssignableFrom(binding)
				|| Calendar.class.isAssignableFrom(binding)) {
			return ValueKind.TIMESTAMP_MILLIS;
		}
		if (byte[].class.equals(binding)) {
			return ValueKind.BINARY;
		}
		return ValueKind.STRING;
	}

	/**
	 * Create the Parquet field for a column with values.
	 *
	 * @param name the field name
	 * @param kind the value kind
	 * @param scale the decimal scale
	 * @param repeated if the field is a list
	 * @return the field
	 */
	private Type createField(String name, ValueKind kind, int scale, boolean repeated) {
		LogicalTypeAnnotation annotation = annotation(kind, scale);
		int length = kind == ValueKind.DECIMAL ? DECIMAL_BYTES : 0;
		if (repeated) {
			var element = Types.optionalList().optionalElement(primitiveName(kind));
			if (annotation != null) {
				element = element.as(annotation);
			}
			if (length > 0) {
				element = element.length(length);
			}
			return element.named(name);
		}
		var builder = Types.optional(primitiveName(kind));
		if (annotation != null) {
			builder = builder.as(annotation);
		}
		if (length > 0) {
			builder = builder.length(length);
		}
		return builder.named(name);
	}

	private LogicalTypeAnnotation annotation(ValueKind kind, int scale) {
		return switch (kind) {
		case STRING, DECIMAL_AS_STRING, JSON, WKT -> LogicalTypeAnnotation.stringType();
		case DATE -> LogicalTypeAnnotation.dateType();
		case TIMESTAMP_MILLIS -> LogicalTypeAnnotation.timestampType(true, TimeUnit.MILLIS);
		case TIME_MILLIS -> LogicalTypeAnnotation.timeType(true, TimeUnit.MILLIS);
		case DECIMAL -> LogicalTypeAnnotation.decimalType(scale, DECIMAL_PRECISION);
		default -> null;
		};
	}

	private PrimitiveTypeName primitiveName(ValueKind kind) {
		return switch (kind) {
		case BOOLEAN -> PrimitiveTypeName.BOOLEAN;
		case INT32, DATE, TIME_MILLIS -> PrimitiveTypeName.INT32;
		case INT64, TIMESTAMP_MILLIS -> PrimitiveTypeName.INT64;
		case FLOAT -> PrimitiveTypeName.FLOAT;
		case DOUBLE -> PrimitiveTypeName.DOUBLE;
		case DECIMAL -> PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY;
		case STRING, BINARY, DECIMAL_AS_STRING, WKB, WKT, JSON -> PrimitiveTypeName.BINARY;
		};
	}

	/**
	 * Create the field for a geometry column (WKB).
	 */
	private Type createGeometryField(String name, MappingContext ctx) {
		var builder = Types.optional(PrimitiveTypeName.BINARY);
		if (options.version().usesNativeGeometryType()) {
			GeoParquetCrs crs = ctx.geometryCrs.getOrDefault(name, GeoParquetCrs.crs84());
			builder.as(LogicalTypeAnnotation.geometryType(crs.getParquetCrs()));
		}
		return builder.named(name);
	}

	/**
	 * Create the bbox covering column.
	 */
	private Type createBbox() {
		var group = Types.optionalGroup();
		for (String field : new String[] { "xmin", "ymin", "xmax", "ymax" }) {
			// column order must be set explicitly, otherwise readers ignore the
			// statistics
			group.addField(Types.required(PrimitiveTypeName.DOUBLE)
					.columnOrder(ColumnOrder.typeDefined()).named(field));
		}
		return group.named(TypeMapping.BBOX_COLUMN);
	}

	/**
	 * Find the shortest property path to a geometry property (breadth-first,
	 * children in definition order, groups and choices are transparent).
	 *
	 * @param type the type definition
	 * @return the property path, or <code>null</code> if there is no geometry
	 */
	private List<QName> findPrimaryGeometryPath(TypeDefinition type) {
		record Node(TypeDefinition type, List<QName> path) {
		}

		Set<TypeDefinition> visited = new HashSet<>();
		visited.add(type);
		List<Node> level = List.of(new Node(type, List.of()));

		for (int depth = 0; depth < MAX_GEOMETRY_SEARCH_DEPTH && !level.isEmpty(); depth++) {
			List<Node> next = new ArrayList<>();
			for (Node node : level) {
				// flatten groups, keep definition order
				List<PropertyEntry> props = new ArrayList<>();
				collectProperties(DefinitionUtil.getAllChildren(node.type()), node.path(), props);
				for (PropertyEntry entry : props) {
					TypeDefinition propType = entry.property().getPropertyType();
					if (propType.getConstraint(GeometryType.class).isGeometry()) {
						return entry.path();
					}
					if (visited.add(propType)) {
						next.add(new Node(propType, entry.path()));
					}
				}
			}
			level = next;
		}
		return null;
	}

	private record PropertyEntry(PropertyDefinition property, List<QName> path) {
	}

	private void collectProperties(Collection<? extends ChildDefinition<?>> children,
			List<QName> path, List<PropertyEntry> result) {
		for (ChildDefinition<?> child : children) {
			if (child.asGroup() != null) {
				collectProperties(DefinitionUtil.getAllChildren(child.asGroup()),
						append(path, child.getName()), result);
			}
			else {
				result.add(new PropertyEntry(child.asProperty(), append(path, child.getName())));
			}
		}
	}

	private static List<QName> append(List<QName> path, QName name) {
		List<QName> result = new ArrayList<>(path);
		result.add(name);
		return result;
	}

	/**
	 * State while mapping a type.
	 */
	private static class MappingContext {

		final Map<String, GeoParquetCrs> geometryCrs;
		final SimpleLog log;
		final List<QName> primaryPath;

		final Set<String> usedNames = new HashSet<>();
		final List<Type> fields = new ArrayList<>();
		final List<ColumnMapping> columns = new ArrayList<>();
		final List<String> secondaryGeometryColumns = new ArrayList<>();

		MappingContext(Map<String, GeoParquetCrs> geometryCrs, SimpleLog log,
				List<QName> primaryPath) {
			this.geometryCrs = geometryCrs;
			this.log = log;
			this.primaryPath = primaryPath;
		}

		/**
		 * Reserve a unique column name based on the given name.
		 */
		String uniqueName(String base) {
			String name = base;
			int i = 2;
			while (!usedNames.add(name)) {
				name = base + "_" + i++;
			}
			if (!name.equals(base)) {
				log.warn("Column name {0} is already used, using {1} instead", base, name);
			}
			return name;
		}
	}

}
