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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;
import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.DefinitionGroup;
import eu.esdihumboldt.hale.common.schema.model.DefinitionUtil;
import eu.esdihumboldt.hale.io.geoparquet.internal.meta.GeoColumnStats;

/**
 * Serializes instance values to JSON for columns that are written as JSON
 * strings. Properties are written as object members named by the local name of
 * the property, repeated properties as arrays, groups are flattened into their
 * parent and geometries are written as WKT strings.
 */
class JsonValueSerializer {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final List<QName> primaryGeometryPath;

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
	 */
	private static class Member {

		final List<JsonNode> nodes = new ArrayList<>();
		boolean repeated;
	}

	private JsonNode toNode(Object value, List<QName> path) {
		if (value instanceof Group) {
			Group group = (Group) value;
			Map<String, Member> members = new LinkedHashMap<>();
			collect(group, path, false, members);
			Object own = value instanceof Instance ? ((Instance) value).getValue() : null;
			if (own != null && members.isEmpty()) {
				return simple(own);
			}
			ObjectNode obj = JsonNodeFactory.instance.objectNode();
			if (own != null) {
				obj.set("value", simple(own));
			}
			members.forEach((name, member) -> {
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

	private void collect(Group group, List<QName> path, boolean repeatedGroup,
			Map<String, Member> members) {
		DefinitionGroup definition = group.getDefinition();
		for (QName name : group.getPropertyNames()) {
			List<QName> childPath = new ArrayList<>(path);
			childPath.add(name);
			if (childPath.equals(primaryGeometryPath)) {
				// stored in the geometry column
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
					Member member = members.computeIfAbsent(name.getLocalPart(), n -> new Member());
					member.repeated |= repeated;
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
	 * Convert a geometry value to WKT.
	 *
	 * @param value a {@link Geometry} or {@link GeometryProperty}
	 * @return the WKT or <code>null</code> if the value is no geometry
	 */
	static String toWkt(Object value) {
		Geometry geometry = null;
		if (value instanceof GeometryProperty<?>) {
			geometry = ((GeometryProperty<?>) value).getGeometry();
		}
		else if (value instanceof Geometry) {
			geometry = (Geometry) value;
		}
		if (geometry == null) {
			return null;
		}
		return new WKTWriter(GeoColumnStats.hasZ(geometry) ? 3 : 2).write(geometry);
	}

}
