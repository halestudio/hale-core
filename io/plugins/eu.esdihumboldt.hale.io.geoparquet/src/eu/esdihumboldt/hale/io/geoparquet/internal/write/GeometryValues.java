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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

import javax.xml.namespace.QName;

import org.locationtech.jts.geom.Geometry;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty;
import eu.esdihumboldt.hale.common.instance.geometry.GeometryUtil;
import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs;

/**
 * Collects the geometries held by property values, wherever the instance model
 * holds them: directly as {@link GeometryProperty} or {@link Geometry}, as
 * collection of those, as (augmented) value of an instance (e.g. of a GML
 * geometry element) or below an instance (e.g. of a GML geometry property type,
 * where the geometry is the value of the geometry element inside the property).
 * Follows the search in {@link GeometryUtil#getGeometries}: breadth-first,
 * children of an instance with a geometry value are not searched (they only
 * describe that geometry).
 */
public final class GeometryValues {

	private GeometryValues() {
		// utility class
	}

	/**
	 * Collect the geometries of property values.
	 *
	 * @param values the values (not unwrapped), <code>null</code> values are
	 *            ignored
	 * @return the geometries, in order
	 */
	public static List<GeometryProperty<?>> collect(Collection<?> values) {
		List<GeometryProperty<?>> result = new ArrayList<>();
		for (Object value : values) {
			collect(value, result);
		}
		return result;
	}

	/**
	 * Collect the geometries of a property value.
	 *
	 * @param value the value (not unwrapped), may be <code>null</code>
	 * @return the geometries, in order
	 */
	public static List<GeometryProperty<?>> collect(Object value) {
		List<GeometryProperty<?>> result = new ArrayList<>();
		collect(value, result);
		return result;
	}

	private static void collect(Object value, List<GeometryProperty<?>> result) {
		if (value == null) {
			return;
		}
		if (!(value instanceof Group)) {
			addDirect(value, result);
			return;
		}

		Deque<Group> queue = new ArrayDeque<>();
		queue.add((Group) value);
		while (!queue.isEmpty()) {
			Group group = queue.poll();
			int before = result.size();
			if (group instanceof Instance) {
				addDirect(((Instance) group).getValue(), result);
			}
			if (result.size() > before) {
				// children are only parts of the geometry
				continue;
			}
			for (QName name : group.getPropertyNames()) {
				Object[] children = group.getProperty(name);
				if (children == null) {
					continue;
				}
				for (Object child : children) {
					if (child instanceof Group) {
						queue.add((Group) child);
					}
					else if (child != null) {
						addDirect(child, result);
					}
				}
			}
		}
	}

	/**
	 * Add a value that is a geometry or a collection of geometries.
	 */
	private static void addDirect(Object value, List<GeometryProperty<?>> result) {
		if (value instanceof GeometryProperty<?>) {
			result.add((GeometryProperty<?>) value);
		}
		else if (value instanceof Geometry) {
			result.add(new DefaultGeometryProperty<>(null, (Geometry) value));
		}
		else if (value instanceof Collection<?>) {
			for (Object element : (Collection<?>) value) {
				if (element != null && !(element instanceof Group)) {
					addDirect(element, result);
				}
			}
		}
	}

	/**
	 * Convert the geometries of property values to the CRS of a geometry column and
	 * combine multiple geometries.
	 *
	 * @param values the property values
	 * @param converter the geometry converter
	 * @param target the CRS of the column
	 * @param log the log for conversion problems (geometries that cannot be
	 *            converted are reported and skipped)
	 * @return the converted non-empty geometries, empty if there are none
	 */
	public static List<Geometry> convert(Collection<?> values, GeometryConverter converter,
			GeoParquetCrs target, SimpleLog log) {
		return convert(values, converter, target, log, "geometry");
	}

	/**
	 * Convert the geometries of property values to the CRS of a geometry column.
	 *
	 * @param values the property values
	 * @param converter the geometry converter
	 * @param target the CRS of the column
	 * @param log the log for conversion problems (geometries that cannot be
	 *            converted are reported and skipped)
	 * @param description description of the geometries for error messages, e.g.
	 *            <code>geometry of column x</code>
	 * @return the converted non-empty geometries, empty if there are none
	 */
	public static List<Geometry> convert(Collection<?> values, GeometryConverter converter,
			GeoParquetCrs target, SimpleLog log, String description) {
		List<Geometry> geometries = new ArrayList<>();
		for (GeometryProperty<?> property : collect(values)) {
			try {
				Geometry g = converter.convert(property, target, log);
				if (g != null && !g.isEmpty()) {
					geometries.add(g);
				}
			} catch (RuntimeException e) {
				log.error("Could not convert " + description + ": " + e.getMessage(), e);
			}
		}
		return geometries;
	}

	/**
	 * Combine geometries into one, using the matching multi geometry or a geometry
	 * collection if there are multiple.
	 *
	 * @param geometries the geometries
	 * @return the combined geometry, <code>null</code> if there are none
	 */
	public static Geometry combine(List<Geometry> geometries) {
		if (geometries.isEmpty()) {
			return null;
		}
		if (geometries.size() == 1) {
			return geometries.get(0);
		}
		return geometries.get(0).getFactory().buildGeometry(geometries);
	}

}
