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

import org.locationtech.jts.geom.Geometry;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs;

/**
 * Converts geometry values to geometries in the CRS used for writing.
 */
public interface GeometryConverter {

	/**
	 * Convert a geometry value to a geometry in the target CRS.
	 *
	 * @param value the geometry value, e.g. a <code>GeometryProperty</code> or
	 *            <code>Geometry</code>
	 * @param target the target CRS description
	 * @param log the log for reporting problems
	 * @return the JTS geometry in the target CRS, with x/y being lon/lat for
	 *         geographic CRS, or <code>null</code> if there is no geometry or it
	 *         cannot be converted
	 */
	Geometry convert(Object value, GeoParquetCrs target, SimpleLog log);

}
