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
package eu.esdihumboldt.hale.io.geoparquet.internal.write

import static org.junit.Assert.*

import org.geotools.referencing.crs.DefaultEngineeringCRS
import org.junit.Test
import org.locationtech.jts.io.WKTReader

import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.common.instance.geometry.impl.WKTDefinition
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetTestUtil.CollectingLog
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for {@link GeometryReprojector}.
 */
class GeometryReprojectorTest extends AbstractPlatformTest {

	static final GeoParquetCrs UTM = GeoParquetCrs.resolve(new CodeDefinition('EPSG:25832', true), SimpleLog.NO_LOG)

	/**
	 * If no transformation between two CRS can be found, the failure is
	 * reported once per CRS pair (and log), not for every geometry. The
	 * geometries are not converted (<code>null</code>), as their coordinates are
	 * not in the target CRS.
	 */
	@Test
	void testTransformLookupFailureReportedOnce() {
		def local = DefaultEngineeringCRS.CARTESIAN_2D
		def source = new WKTDefinition(local.toWKT(), local)
		def reprojector = new GeometryReprojector()
		def log = new CollectingLog()

		def geometries = (1..3).collect { new WKTReader().read("POINT($it $it)") }
		geometries.each { geometry ->
			assertNull(reprojector.reproject(geometry, source, UTM, log, ''))
		}
		assertEquals(log.errors.toString(), 1, log.errors.size())

		// another log (e.g. of another pass) is informed as well, once
		def other = new CollectingLog()
		geometries.each { geometry ->
			assertNull(reprojector.reproject(geometry, source, UTM, other, ''))
		}
		assertEquals(other.errors.toString(), 1, other.errors.size())
		assertEquals(1, log.errors.size())
	}
}
