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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKBWriter;

import eu.esdihumboldt.hale.io.geoparquet.internal.meta.GeoColumnStats;

/**
 * Writes geometries as ISO WKB, as required by GeoParquet. The JTS
 * {@link WKBWriter} uses the EWKB flag for the Z dimension, so it is only used
 * for 2D geometries, 3D geometries are written by this class (type codes offset
 * by 1000).
 */
public class IsoWkbWriter {

	private final WKBWriter writer2d = new WKBWriter(2, false);

	/**
	 * Write a geometry as WKB.
	 *
	 * @param geometry the geometry
	 * @return the WKB bytes, big endian
	 */
	public byte[] write(Geometry geometry) {
		if (!GeoColumnStats.hasZ(geometry)) {
			return writer2d.write(geometry);
		}
		ByteBuffer buffer = ByteBuffer.allocate(size(geometry));
		buffer.order(ByteOrder.BIG_ENDIAN);
		put(buffer, geometry);
		return buffer.array();
	}

	private static int size(Geometry g) {
		int header = 1 + 4;
		if (g instanceof Point) {
			return header + 3 * 8;
		}
		if (g instanceof LineString) {
			return header + 4 + ((LineString) g).getNumPoints() * 3 * 8;
		}
		if (g instanceof Polygon) {
			Polygon p = (Polygon) g;
			int size = header + 4 + 4 + p.getExteriorRing().getNumPoints() * 3 * 8;
			for (int i = 0; i < p.getNumInteriorRing(); i++) {
				size += 4 + p.getInteriorRingN(i).getNumPoints() * 3 * 8;
			}
			return size;
		}
		int size = header + 4;
		for (int i = 0; i < g.getNumGeometries(); i++) {
			size += size(g.getGeometryN(i));
		}
		return size;
	}

	private static void put(ByteBuffer buffer, Geometry g) {
		buffer.put((byte) 0); // big endian
		if (g instanceof Point) {
			buffer.putInt(1001);
			Point p = (Point) g;
			if (p.isEmpty()) {
				buffer.putDouble(Double.NaN).putDouble(Double.NaN).putDouble(Double.NaN);
			}
			else {
				putCoordinate(buffer, p.getCoordinateSequence(), 0);
			}
		}
		else if (g instanceof LineString) {
			buffer.putInt(1002);
			putSequence(buffer, ((LineString) g).getCoordinateSequence());
		}
		else if (g instanceof Polygon) {
			buffer.putInt(1003);
			Polygon p = (Polygon) g;
			buffer.putInt(1 + p.getNumInteriorRing());
			putSequence(buffer, p.getExteriorRing().getCoordinateSequence());
			for (int i = 0; i < p.getNumInteriorRing(); i++) {
				putSequence(buffer, p.getInteriorRingN(i).getCoordinateSequence());
			}
		}
		else {
			int type;
			switch (g.getGeometryType()) {
			case Geometry.TYPENAME_MULTIPOINT:
				type = 1004;
				break;
			case Geometry.TYPENAME_MULTILINESTRING:
				type = 1005;
				break;
			case Geometry.TYPENAME_MULTIPOLYGON:
				type = 1006;
				break;
			default:
				type = 1007;
			}
			buffer.putInt(type);
			GeometryCollection c = (GeometryCollection) g;
			buffer.putInt(c.getNumGeometries());
			for (int i = 0; i < c.getNumGeometries(); i++) {
				put(buffer, c.getGeometryN(i));
			}
		}
	}

	private static void putSequence(ByteBuffer buffer, CoordinateSequence seq) {
		buffer.putInt(seq.size());
		for (int i = 0; i < seq.size(); i++) {
			putCoordinate(buffer, seq, i);
		}
	}

	private static void putCoordinate(ByteBuffer buffer, CoordinateSequence seq, int i) {
		buffer.putDouble(seq.getX(i)).putDouble(seq.getY(i)).putDouble(seq.getZ(i));
	}

}
