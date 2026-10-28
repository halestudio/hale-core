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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.parquet.schema.ColumnOrder;
import org.apache.parquet.schema.LogicalTypeAnnotation;
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
import eu.esdihumboldt.hale.io.xsd.constraint.XmlAttributeFlag;

/**
 * Maps hale type definitions to Parquet schemas.
 */
public class ParquetSchemaMapper {

	/**
	 * Safety limit for the search of the primary geometry (guards against recursive
	 * types).
	 */
	private static final int MAX_GEOMETRY_SEARCH_DEPTH = 32;

	private final MappingOptions options;

	private final ParquetFieldFactory fieldFactory;

	/**
	 * Create a schema mapper.
	 *
	 * @param options the mapping options
	 */
	public ParquetSchemaMapper(MappingOptions options) {
		this.options = options;
		this.fieldFactory = new ParquetFieldFactory(options);
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
		Set<String> reserved = new HashSet<>();
		if (primaryPath != null) {
			reserved.add(TypeMapping.BBOX_COLUMN);
			reserved.add(TypeMapping.PRIMARY_GEOMETRY_COLUMN);
		}

		Scope scope = mapScope(DefinitionUtil.getAllChildren(type), List.of(), 1, List.of(type),
				reserved, ctx);

		List<Type> fields = new ArrayList<>(scope.fields());
		List<ColumnMapping> columns = new ArrayList<>(scope.columns());
		List<String> geometryColumns = new ArrayList<>();
		if (primaryPath != null) {
			fields.add(createBbox());
			fields.add(createGeometryField(TypeMapping.PRIMARY_GEOMETRY_COLUMN, ctx));
			columns.add(new ColumnMapping(TypeMapping.PRIMARY_GEOMETRY_COLUMN, primaryPath, false,
					true, ValueKind.WKB, 0));
			geometryColumns.add(TypeMapping.PRIMARY_GEOMETRY_COLUMN);
		}
		geometryColumns.addAll(ctx.secondaryGeometryColumns);

		String messageName = type.getName().getLocalPart();
		MessageType schema = new MessageType(messageName, fields);
		return new TypeMapping(type, schema, columns, geometryColumns, primaryPath,
				primaryPath != null);
	}

	/**
	 * The result of mapping the properties of a type or struct.
	 */
	private record Scope(List<Type> fields, List<ColumnMapping> columns) {
	}

	/**
	 * Map the children of a type or struct. Groups and choices are flattened into
	 * the parent.
	 *
	 * @param children the children
	 * @param basePath the absolute property path of the struct, empty on top level
	 * @param depth the nesting depth of the properties (1 on top level)
	 * @param typePath the types of the enclosing structs, including the current
	 * @param reserved field names that must not be used
	 * @param ctx the mapping context
	 * @return the fields and column mappings
	 */
	private Scope mapScope(Collection<? extends ChildDefinition<?>> children, List<QName> basePath,
			int depth, List<TypeDefinition> typePath, Set<String> reserved, MappingContext ctx) {
		List<PropertyEntry> all = new ArrayList<>();
		collectProperties(children, List.of(), false, all);

		// names are only allocated for properties that become fields
		List<PropertyEntry> entries = all.stream()
				.filter(e -> producesField(e, basePath, depth, typePath, ctx)).toList();
		List<ColumnNameAllocator.Candidate> candidates = entries.stream()
				.map(e -> new ColumnNameAllocator.Candidate(e.property().getName(),
						e.property().getConstraint(XmlAttributeFlag.class).isEnabled()))
				.toList();
		List<String> names = ColumnNameAllocator.allocate(candidates, reserved, ctx.log);

		List<Type> fields = new ArrayList<>();
		List<ColumnMapping> columns = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			mapProperty(entries.get(i), names.get(i), basePath, depth, typePath, fields, columns,
					ctx);
		}
		return new Scope(fields, columns);
	}

	private boolean hasValue(TypeDefinition type) {
		return type.getConstraint(HasValueFlag.class).isEnabled()
				|| type.getConstraint(AugmentedValueFlag.class).isEnabled();
	}

	/**
	 * Determine if a complex property is written as JSON, because of the nesting
	 * depth limit or because its type is recursive.
	 */
	private boolean isJson(TypeDefinition propType, int depth, List<TypeDefinition> typePath) {
		return depth > options.nestingDepth() || typePath.contains(propType);
	}

	/**
	 * Determine if a property results in a field.
	 */
	private boolean producesField(PropertyEntry entry, List<QName> basePath, int depth,
			List<TypeDefinition> typePath, MappingContext ctx) {
		List<QName> path = append(basePath, entry.path());
		if (path.equals(ctx.primaryPath)) {
			// moved to the primary geometry column
			return false;
		}
		TypeDefinition propType = entry.property().getPropertyType();
		if (propType.getConstraint(GeometryType.class).isGeometry()) {
			return true;
		}
		if (DefinitionUtil.hasChildren(propType)) {
			if (hasValue(propType) || isJson(propType, depth, typePath)) {
				return true;
			}
			List<TypeDefinition> childTypePath = append(typePath, propType);
			List<PropertyEntry> childEntries = new ArrayList<>();
			collectProperties(DefinitionUtil.getAllChildren(propType), List.of(), false,
					childEntries);
			return childEntries.stream()
					.anyMatch(e -> producesField(e, path, depth + 1, childTypePath, ctx));
		}
		return hasValue(propType);
	}

	/**
	 * Map a single property. The generated fields and column mappings are added to
	 * the given lists.
	 */
	private void mapProperty(PropertyEntry entry, String name, List<QName> basePath, int depth,
			List<TypeDefinition> typePath, List<Type> fields, List<ColumnMapping> columns,
			MappingContext ctx) {
		PropertyDefinition property = entry.property();
		List<QName> path = entry.path();
		TypeDefinition propType = property.getPropertyType();
		boolean repeated = entry.repeatedGroup()
				|| DefinitionUtil.getCardinality(property).mayOccurMultipleTimes();

		if (propType.getConstraint(GeometryType.class).isGeometry()) {
			if (depth == 1) {
				fields.add(createGeometryField(name, ctx));
				columns.add(new ColumnMapping(name, path, false, true, ValueKind.WKB, 0));
				ctx.secondaryGeometryColumns.add(name);
			}
			else {
				addValue(name, path, repeated, ValueKind.WKT, 0, fields, columns);
			}
			return;
		}

		if (DefinitionUtil.hasChildren(propType)) {
			if (isJson(propType, depth, typePath)) {
				ctx.log.info("Property {0} is written as JSON string", append(basePath, path));
				addValue(name, path, repeated, ValueKind.JSON, 0, fields, columns);
				return;
			}
			mapStruct(property, name, path, repeated, basePath, depth, typePath, fields, columns,
					ctx);
			return;
		}

		Class<?> binding = propType.getConstraint(Binding.class).getBinding();
		ValueKind kind = fieldFactory.determineValueKind(binding);
		addValue(name, path, repeated, kind, fieldFactory.decimalScale(kind, binding), fields,
				columns);
	}

	private void addValue(String name, List<QName> path, boolean repeated, ValueKind kind,
			int scale, List<Type> fields, List<ColumnMapping> columns) {
		fields.add(fieldFactory.createField(name, kind, scale, repeated));
		columns.add(new ColumnMapping(name, path, repeated, true, kind, scale));
	}

	/**
	 * Map a complex property to a struct (or list of structs).
	 */
	private void mapStruct(PropertyDefinition property, String name, List<QName> path,
			boolean repeated, List<QName> basePath, int depth, List<TypeDefinition> typePath,
			List<Type> fields, List<ColumnMapping> columns, MappingContext ctx) {
		TypeDefinition propType = property.getPropertyType();
		List<Type> childFields = new ArrayList<>();
		List<ColumnMapping> childColumns = new ArrayList<>();
		Set<String> reserved = new HashSet<>();

		if (hasValue(propType)) {
			Class<?> binding = propType.getConstraint(Binding.class).getBinding();
			ValueKind kind = fieldFactory.determineValueKind(binding);
			int scale = fieldFactory.decimalScale(kind, binding);
			addValue(ColumnMapping.VALUE_FIELD, List.of(), false, kind, scale, childFields,
					childColumns);
			reserved.add(ColumnMapping.VALUE_FIELD);
		}

		Scope scope = mapScope(DefinitionUtil.getAllChildren(propType), append(basePath, path),
				depth + 1, append(typePath, propType), reserved, ctx);
		childFields.addAll(scope.fields());
		childColumns.addAll(scope.columns());

		if (repeated) {
			fields.add(Types.optionalList().groupElement(Type.Repetition.OPTIONAL)
					.addFields(childFields.toArray(new Type[0])).named(name));
		}
		else {
			fields.add(
					Types.optionalGroup().addFields(childFields.toArray(new Type[0])).named(name));
		}
		columns.add(new ColumnMapping(name, path, repeated, true, childColumns));
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
				collectProperties(DefinitionUtil.getAllChildren(node.type()), node.path(), false,
						props);
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

	/**
	 * A property found below a type or struct.
	 *
	 * @param property the property
	 * @param path the property path from the type or struct, including group names
	 * @param repeatedGroup if the property is inside a repeated group or choice
	 */
	private record PropertyEntry(PropertyDefinition property, List<QName> path,
			boolean repeatedGroup) {
	}

	private void collectProperties(Collection<? extends ChildDefinition<?>> children,
			List<QName> path, boolean repeatedGroup, List<PropertyEntry> result) {
		for (ChildDefinition<?> child : children) {
			if (child.asGroup() != null) {
				boolean repeated = repeatedGroup
						|| DefinitionUtil.getCardinality(child).mayOccurMultipleTimes();
				collectProperties(DefinitionUtil.getAllChildren(child.asGroup()),
						append(path, child.getName()), repeated, result);
			}
			else {
				result.add(new PropertyEntry(child.asProperty(), append(path, child.getName()),
						repeatedGroup));
			}
		}
	}

	private static <T> List<T> append(List<T> path, T element) {
		List<T> result = new ArrayList<>(path);
		result.add(element);
		return result;
	}

	private static List<QName> append(List<QName> path, List<QName> more) {
		List<QName> result = new ArrayList<>(path);
		result.addAll(more);
		return result;
	}

	/**
	 * State while mapping a type.
	 */
	private static class MappingContext {

		final Map<String, GeoParquetCrs> geometryCrs;
		final SimpleLog log;
		final List<QName> primaryPath;

		final List<String> secondaryGeometryColumns = new ArrayList<>();

		MappingContext(Map<String, GeoParquetCrs> geometryCrs, SimpleLog log,
				List<QName> primaryPath) {
			this.geometryCrs = geometryCrs;
			this.log = log;
			this.primaryPath = primaryPath;
		}
	}

}
