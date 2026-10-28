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
package eu.esdihumboldt.hale.io.geoparquet.internal.write;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.esdihumboldt.hale.common.convert.ConversionUtil;
import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;
import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.DefinitionGroup;
import eu.esdihumboldt.hale.common.schema.model.DefinitionUtil;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.AugmentedValueFlag;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.HasValueFlag;
import eu.esdihumboldt.hale.io.geoparquet.internal.meta.GeoColumnStats;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ColumnMapping;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ColumnNameAllocator;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.GeometryProperties;
import eu.esdihumboldt.hale.io.xsd.constraint.XmlAttributeFlag;

/**
 * Serializes instance values to JSON for columns that are written as JSON
 * strings. Properties are written as object members named like the fields of
 * the struct mapping (see {@link ColumnNameAllocator}: by the local name of the
 * property, suffixed or prefixed on collisions, the value of an instance as
 * {@value ColumnMapping#VALUE_FIELD}), repeated properties as arrays, groups
 * are flattened into their parent and geometries are written as WKT strings.
 * <code>gml:boundedBy</code> is omitted.
 */
class JsonValueSerializer {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final List<QName> primaryGeometryPath;

	/**
	 * The properties defined for a definition group (at a path), see
	 * {@link #definedProperties(DefinitionGroup, List)}.
	 */
	private final Map<DefinitionKey, Map<QName, Boolean>> defined = new HashMap<>();

	/**
	 * @param primaryGeometryPath the absolute property path of the primary geometry
	 *            that is omitted (it is stored in its own column), may be
	 *            <code>null</code>
	 */
	JsonValueSerializer(List<QName> primaryGeometryPath) {
		this.primaryGeometryPath = primaryGeometryPath;
	}

	/**
	 * Serialize a value.
	 *
	 * @param value the value, usually an instance
	 * @param path the absolute property path of the value
	 * @return the JSON string
	 */
	String toJson(Object value, List<QName> path) {
		try {
			return MAPPER.writeValueAsString(toNode(value, path));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not serialize value to JSON", e);
		}
	}

	/**
	 * A member of a JSON object.
	 *
	 * @param nodes the values
	 * @param repeated if the property may occur multiple times (always an array)
	 * @param attribute if the property is an XML attribute
	 */
	private static class Member {

		final List<JsonNode> nodes = new ArrayList<>();
		boolean repeated;
		boolean attribute;
	}

	/**
	 * Key for the cached defined properties.
	 */
	private record DefinitionKey(DefinitionGroup definition, List<QName> path) {
	}

	private JsonNode toNode(Object value, List<QName> path) {
		if (isGeometry(value)) {
			// the geometry only, not the structure describing it (e.g. GML)
			String wkt = toWkt(value);
			return wkt == null ? JsonNodeFactory.instance.nullNode()
					: JsonNodeFactory.instance.textNode(wkt);
		}
		if (value instanceof Group) {
			Group group = (Group) value;
			Map<QName, Member> members = new LinkedHashMap<>();
			collect(group, path, false, members);
			Object own = value instanceof Instance ? ((Instance) value).getValue() : null;
			if (own != null && members.isEmpty()) {
				return simple(own);
			}
			Map<QName, String> names = allocateNames(group, path, own != null, members);
			ObjectNode obj = JsonNodeFactory.instance.objectNode();
			if (own != null) {
				obj.set(ColumnMapping.VALUE_FIELD, simple(own));
			}
			members.forEach((property, member) -> {
				String name = names.get(property);
				// by cardinality; without definition by number of values
				if (!member.repeated && member.nodes.size() == 1) {
					obj.set(name, member.nodes.get(0));
				}
				else {
					ArrayNode array = obj.putArray(name);
					member.nodes.forEach(array::add);
				}
			});
			return obj;
		}
		return simple(value);
	}

	/**
	 * Allocate the member names like for the fields of a struct: for all properties
	 * of the definition (so the names do not depend on which properties an instance
	 * has) and the properties present without definition,
	 * {@value ColumnMapping#VALUE_FIELD} is reserved for the value.
	 *
	 * @return the member names by property name
	 */
	private Map<QName, String> allocateNames(Group group, List<QName> path, boolean hasValue,
			Map<QName, Member> members) {
		Map<QName, Boolean> candidates = new LinkedHashMap<>();
		DefinitionGroup definition = group.getDefinition();
		if (definition != null) {
			candidates.putAll(definedProperties(definition, path));
			if (definition instanceof TypeDefinition type
					&& (type.getConstraint(HasValueFlag.class).isEnabled()
							|| type.getConstraint(AugmentedValueFlag.class).isEnabled())) {
				hasValue = true;
			}
		}
		members.forEach((name, member) -> candidates.putIfAbsent(name, member.attribute));

		List<ColumnNameAllocator.Candidate> list = new ArrayList<>();
		candidates.forEach(
				(name, attribute) -> list.add(new ColumnNameAllocator.Candidate(name, attribute)));
		Set<String> reserved = hasValue ? Set.of(ColumnMapping.VALUE_FIELD) : Set.of();
		// renaming is reported for the struct mapping only
		List<String> allocated = ColumnNameAllocator.allocate(list, reserved, SimpleLog.NO_LOG);

		Map<QName, String> names = new HashMap<>();
		for (int i = 0; i < list.size(); i++) {
			names.put(list.get(i).name(), allocated.get(i));
		}
		return names;
	}

	/**
	 * Determine the properties defined for a definition group that are written,
	 * groups are flattened.
	 *
	 * @param definition the definition group
	 * @param path the absolute property path of the group
	 * @return the property names in definition order, mapped to if the property is
	 *         an XML attribute
	 */
	private Map<QName, Boolean> definedProperties(DefinitionGroup definition, List<QName> path) {
		return defined.computeIfAbsent(new DefinitionKey(definition, List.copyOf(path)), k -> {
			Map<QName, Boolean> result = new LinkedHashMap<>();
			collectDefined(DefinitionUtil.getAllChildren(definition), path, result);
			return result;
		});
	}

	private void collectDefined(Collection<? extends ChildDefinition<?>> children, List<QName> path,
			Map<QName, Boolean> result) {
		for (ChildDefinition<?> child : children) {
			List<QName> childPath = new ArrayList<>(path);
			childPath.add(child.getName());
			if (child.asGroup() != null) {
				collectDefined(DefinitionUtil.getAllChildren(child.asGroup()), childPath, result);
			}
			else if (child.asProperty() != null && !childPath.equals(primaryGeometryPath)
					&& !GeometryProperties.isBoundedBy(child.getName())) {
				result.putIfAbsent(child.getName(),
						child.asProperty().getConstraint(XmlAttributeFlag.class).isEnabled());
			}
		}
	}

	private void collect(Group group, List<QName> path, boolean repeatedGroup,
			Map<QName, Member> members) {
		DefinitionGroup definition = group.getDefinition();
		for (QName name : group.getPropertyNames()) {
			List<QName> childPath = new ArrayList<>(path);
			childPath.add(name);
			if (childPath.equals(primaryGeometryPath)) {
				// stored in the geometry column
				continue;
			}
			if (GeometryProperties.isBoundedBy(name)) {
				// not written, see ParquetSchemaMapper
				continue;
			}
			Object[] values = group.getProperty(name);
			if (values == null) {
				continue;
			}
			ChildDefinition<?> child = definition == null ? null : definition.getChild(name);
			boolean repeated = repeatedGroup || (child != null
					&& DefinitionUtil.getCardinality(child).mayOccurMultipleTimes());
			for (Object v : values) {
				if (v instanceof Group && !(v instanceof Instance)) {
					// groups are transparent, a repeated group makes the members repeated
					collect((Group) v, childPath, repeated, members);
				}
				else {
					Member member = members.computeIfAbsent(name, n -> new Member());
					member.repeated |= repeated;
					member.attribute |= child != null && child.asProperty() != null
							&& child.asProperty().getConstraint(XmlAttributeFlag.class).isEnabled();
					member.nodes.add(toNode(v, childPath));
				}
			}
		}
	}

	private JsonNode simple(Object value) {
		if (value == null) {
			return JsonNodeFactory.instance.nullNode();
		}
		if (value instanceof Boolean || value instanceof Number || value instanceof String) {
			return MAPPER.valueToTree(value);
		}
		String wkt = toWkt(value);
		if (wkt != null) {
			return JsonNodeFactory.instance.textNode(wkt);
		}
		String text;
		try {
			text = ConversionUtil.getAs(value, String.class);
		} catch (RuntimeException e) {
			text = null;
		}
		return JsonNodeFactory.instance.textNode(text != null ? text : value.toString());
	}

	/**
	 * Convert the geometries of a value to WKT (in their source CRS). Multiple
	 * geometries are combined to a multi geometry or geometry collection.
	 *
	 * @param value a {@link Geometry}, {@link GeometryProperty}, a collection of
	 *            those or an instance holding geometries (see
	 *            {@link GeometryValues})
	 * @return the WKT or <code>null</code> if the value holds no geometry
	 */
	static String toWkt(Object value) {
		List<Geometry> geometries = new ArrayList<>();
		for (GeometryProperty<?> property : GeometryValues.collect(value)) {
			if (property.getGeometry() != null) {
				geometries.add(property.getGeometry());
			}
		}
		Geometry geometry = GeometryValues.combine(geometries);
		if (geometry == null) {
			return null;
		}
		return new WKTWriter(GeoColumnStats.hasZ(geometry) ? 3 : 2).write(geometry);
	}

	/**
	 * Determine if a value is written as geometry (WKT): a geometry, or an instance
	 * with a geometry value or of a geometry property type.
	 */
	private static boolean isGeometry(Object value) {
		Object own = value instanceof Instance ? ((Instance) value).getValue() : value;
		if (own instanceof Geometry || own instanceof GeometryProperty<?>) {
			return true;
		}
		if (own instanceof Collection<?> && !((Collection<?>) own).isEmpty()
				&& ((Collection<?>) own).stream()
						.allMatch(e -> e instanceof Geometry || e instanceof GeometryProperty<?>)) {
			return true;
		}
		return value instanceof Instance && ((Instance) value).getDefinition() != null
				&& GeometryProperties.isGeometry(((Instance) value).getDefinition());
	}

}
