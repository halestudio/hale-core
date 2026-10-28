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
package eu.esdihumboldt.hale.io.geoparquet.internal.meta;

import java.util.SortedSet;
import java.util.TreeSet;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * Collects the extent and geometry types of the geometries written to a column.
 */
public class GeoColumnStats {

	private final Envelope extent = new Envelope();

	private final SortedSet<String> geometryTypes = new TreeSet<>();

	private double minZ = Double.NaN;

	private double maxZ = Double.NaN;

	/**
	 * Add a geometry. Null and empty geometries are ignored.
	 *
	 * @param g the geometry
	 */
	public void add(Geometry g) {
		if (g == null || g.isEmpty()) {
			return;
		}
		extent.expandToInclude(g.getEnvelopeInternal());
		geometryTypes.add(typeName(g));
		g.apply(new CoordinateSequenceFilter() {

			@Override
			public void filter(CoordinateSequence seq, int i) {
				double z = seq.getZ(i);
				if (!Double.isNaN(z)) {
					minZ = Double.isNaN(minZ) ? z : Math.min(minZ, z);
					maxZ = Double.isNaN(maxZ) ? z : Math.max(maxZ, z);
				}
			}

			@Override
			public boolean isDone() {
				return false;
			}

			@Override
			public boolean isGeometryChanged() {
				return false;
			}
		});
	}

	/**
	 * @return if any coordinate of the added geometries has a Z value
	 */
	public boolean hasZ() {
		return !Double.isNaN(minZ);
	}

	/**
	 * @return the minimum Z value of all coordinates with Z, <code>NaN</code> if
	 *         there is none
	 */
	public double getMinZ() {
		return minZ;
	}

	/**
	 * @return the maximum Z value of all coordinates with Z, <code>NaN</code> if
	 *         there is none
	 */
	public double getMaxZ() {
		return maxZ;
	}

	/**
	 * @return the extent of all geometries, <code>null</code> if no geometry was
	 *         added
	 */
	public Envelope getExtent() {
		return extent.isNull() ? null : new Envelope(extent);
	}

	/**
	 * @return the GeoParquet geometry type names, with suffix " Z" for 3D
	 */
	public SortedSet<String> getGeometryTypes() {
		return geometryTypes;
	}

	private static String typeName(Geometry g) {
		String name = g.getGeometryType();
		if (Geometry.TYPENAME_LINEARRING.equals(name)) {
			name = Geometry.TYPENAME_LINESTRING;
		}
		return hasZ(g) ? name + " Z" : name;
	}

	/**
	 * Determine if a geometry has Z values.
	 *
	 * @param g the geometry
	 * @return if any coordinate of the geometry has a Z value
	 */
	public static boolean hasZ(Geometry g) {
		boolean[] found = new boolean[1];
		g.apply(new CoordinateSequenceFilter() {

			@Override
			public void filter(CoordinateSequence seq, int i) {
				if (!Double.isNaN(seq.getZ(i))) {
					found[0] = true;
				}
			}

			@Override
			public boolean isDone() {
				return found[0];
			}

			@Override
			public boolean isGeometryChanged() {
				return false;
			}
		});
		return found[0];
	}

	/**
	 * Determine if a geometry has measure (M) values.
	 *
	 * @param g the geometry
	 * @return if any coordinate of the geometry has a measure value
	 */
	public static boolean hasM(Geometry g) {
		boolean[] found = new boolean[1];
		g.apply(new CoordinateSequenceFilter() {

			@Override
			public void filter(CoordinateSequence seq, int i) {
				if (seq.hasM() && !Double.isNaN(seq.getM(i))) {
					found[0] = true;
				}
			}

			@Override
			public boolean isDone() {
				return found[0];
			}

			@Override
			public boolean isGeometryChanged() {
				return false;
			}
		});
		return found[0];
	}

}
