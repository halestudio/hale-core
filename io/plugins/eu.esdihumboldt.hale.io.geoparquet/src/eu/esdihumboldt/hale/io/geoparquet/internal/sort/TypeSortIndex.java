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
package eu.esdihumboldt.hale.io.geoparquet.internal.sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.xml.namespace.QName;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.PropertyPaths;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.TypeMapping;
import eu.esdihumboldt.hale.io.geoparquet.internal.write.GeometryConverter;
import eu.esdihumboldt.hale.io.geoparquet.internal.write.GeometryValues;

/**
 * Sort index for the instances of one type, using the center of the primary
 * geometry as converted for writing (i.e. in the CRS of the geometry column).
 */
public class TypeSortIndex {

	private final SortIndex index = new SortIndex();
	private final GeometryConverter converter;
	private final List<QName> geometryPath;
	private final Map<String, GeoParquetCrs> crs;
	private final String geometryColumn;
	/**
	 * Instances with primary geometry added before the CRS of the primary geometry
	 * was known.
	 */
	private final List<InstanceReference> deferred = new ArrayList<>();

	/**
	 * Create the index.
	 *
	 * @param mapping the mapping of the type
	 * @param crs the CRS per geometry column; may be filled after the index has
	 *            been created, instances with primary geometry added while the CRS
	 *            of the primary geometry is not yet present in the map are only
	 *            added to the index by {@link #complete(Function)}
	 * @param converter the converter used for writing geometries
	 */
	public TypeSortIndex(TypeMapping mapping, Map<String, GeoParquetCrs> crs,
			GeometryConverter converter) {
		this.converter = converter;
		this.geometryPath = mapping.getPrimaryGeometryPath();
		this.crs = crs;
		this.geometryColumn = mapping.getPrimaryGeometryColumn();
	}

	/**
	 * Add an instance, instances without (primary) geometry are sorted last.
	 *
	 * @param instance the instance
	 * @param ref the reference to the instance
	 */
	public void add(Instance instance, InstanceReference ref) {
		if (geometryPath != null && crs.get(geometryColumn) == null && !GeometryValues
				.collect(PropertyPaths.findValues(instance, geometryPath)).isEmpty()) {
			// the CRS is not yet known
			deferred.add(ref);
			return;
		}
		addWithKey(instance, ref);
	}

	/**
	 * Add the instances that were deferred because the CRS of the primary geometry
	 * was not yet known. Must be called once the CRS is known, before
	 * {@link #hasGeometry()} or {@link #sorted()}.
	 *
	 * @param resolver resolves an instance reference, yields <code>null</code> if
	 *            the instance cannot be resolved (it is sorted last)
	 */
	public void complete(Function<InstanceReference, Instance> resolver) {
		for (InstanceReference ref : deferred) {
			Instance instance = resolver.apply(ref);
			if (instance == null) {
				index.addWithoutGeometry(ref);
			}
			else {
				addWithKey(instance, ref);
			}
		}
		deferred.clear();
	}

	private void addWithKey(Instance instance, InstanceReference ref) {
		Envelope envelope = new Envelope();
		GeoParquetCrs geometryCrs = geometryPath == null ? null : crs.get(geometryColumn);
		if (geometryCrs != null) {
			// all geometries of the primary geometry, as they are combined for writing
			for (Geometry geometry : GeometryValues.convert(
					PropertyPaths.findValues(instance, geometryPath), converter, geometryCrs,
					SimpleLog.NO_LOG)) {
				envelope.expandToInclude(geometry.getEnvelopeInternal());
			}
		}
		if (envelope.isNull()) {
			index.addWithoutGeometry(ref);
		}
		else {
			Coordinate center = envelope.centre();
			index.add(center.x, center.y, ref);
		}
	}

	/**
	 * @return if any of the instances added has a primary geometry
	 */
	public boolean hasGeometry() {
		return !index.getExtent().isNull();
	}

	/**
	 * @return the references in spatial order, see {@link SortIndex#sorted()}
	 */
	public List<InstanceReference> sorted() {
		return index.sorted();
	}
}
