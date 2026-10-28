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
package eu.esdihumboldt.hale.io.geoparquet.internal.crs

import static org.junit.Assert.*

import groovy.json.JsonSlurper

import org.junit.Test

import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.common.instance.geometry.impl.WKTDefinition
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for {@link GeoParquetCrs} and {@link ProjJsonRegistry}.
 */
class GeoParquetCrsTest extends AbstractPlatformTest {

	/**
	 * Custom Lambert Conformal Conic without authority and not matching any EPSG CRS.
	 */
	static final String CUSTOM_WKT = '''PROJCS["Custom LCC",
  GEOGCS["GCS_WGS_1984",
    DATUM["D_WGS_1984", SPHEROID["WGS_1984",6378137.0,298.257223563]],
    PRIMEM["Greenwich",0.0],
    UNIT["Degree",0.0174532925199433]],
  PROJECTION["Lambert_Conformal_Conic_2SP"],
  PARAMETER["False_Easting",123456.7],
  PARAMETER["False_Northing",-7654.3],
  PARAMETER["Central_Meridian",17.123],
  PARAMETER["Standard_Parallel_1",41.337],
  PARAMETER["Standard_Parallel_2",53.713],
  PARAMETER["Latitude_Of_Origin",12.345],
  UNIT["Meter",1.0]]'''

	private static class CountingLog implements SimpleLog {
		int warnings = 0
		List<String> messages = []

		@Override
		void warn(String message, Throwable e) {
			warnings++
			messages << message
		}

		@Override
		void info(String message, Throwable e) {
		}

		@Override
		void error(String message, Throwable e) {
		}
	}

	@Test
	void testEpsgProjJson() {
		def crs = GeoParquetCrs.resolve(new CodeDefinition('EPSG:2056'), new CountingLog())
		assertEquals(2056, crs.epsgCode)
		def json = new JsonSlurper().parseText(crs.projJson)
		assertEquals('EPSG', json.id.authority)
		assertEquals(2056, json.id.code)
		assertEquals('ProjectedCRS', json.type)
		assertEquals('EPSG:2056', crs.parquetCrs)
		assertFalse(crs.swapAxes)
		assertFalse(crs.crs84)
		assertFalse(crs.unknown)
	}

	@Test
	void testWgs84IsCrs84() {
		def crs = GeoParquetCrs.resolve(new CodeDefinition('EPSG:4326'), new CountingLog())
		assertTrue(crs.crs84)
		assertNull(crs.projJson)
		assertNull(crs.parquetCrs)
		assertTrue(crs.swapAxes)
	}

	@Test
	void testLatitudeFirstCrsIsSwapped() {
		def crs = GeoParquetCrs.resolve(new CodeDefinition('EPSG:4258'), new CountingLog())
		assertFalse(crs.crs84)
		assertEquals(4258, crs.epsgCode)
		assertTrue(crs.swapAxes)
	}

	@Test
	void testWktWithoutCodeIsUnknown() {
		def log = new CountingLog()
		def crs = GeoParquetCrs.resolve(new WKTDefinition(CUSTOM_WKT, null), log)
		assertTrue(crs.unknown)
		assertNull(crs.epsgCode)
		assertNull(crs.projJson)
		assertEquals(1, log.warnings)
	}

	@Test
	void testNullIsUnknown() {
		def log = new CountingLog()
		assertTrue(GeoParquetCrs.resolve(null, log).unknown)
		assertEquals(1, log.warnings)
	}

	@Test
	void testRegistryMissingCode() {
		assertTrue(ProjJsonRegistry.instance.getProjJson(999999).isEmpty())
		assertTrue(ProjJsonRegistry.instance.getProjJson(4326).isPresent())
	}

	@Test
	void testCodeNotInRegistryIsUnknown() {
		// deprecated EPSG code that GeoTools can decode, but that is not bundled
		assertTrue(ProjJsonRegistry.instance.getProjJson(2008).isEmpty())
		def log = new CountingLog()
		def crs = GeoParquetCrs.resolve(new CodeDefinition('EPSG:2008'), log)
		assertTrue(crs.unknown)
		assertNull(crs.epsgCode)
		assertNull(crs.projJson)
		assertNotNull(crs.targetCrs)
		assertEquals([
			'No PROJJSON definition available for EPSG:2008, writing unknown CRS'
		], log.messages)
	}

	@Test
	void testUndecodableCrsDegradesToUnknown() {
		def log = new CountingLog()
		def def_ = [getCRS: { null }] as eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition
		def crs = GeoParquetCrs.resolve(def_, log)
		assertTrue(crs.unknown)
		assertNull(crs.epsgCode)
		assertEquals([
			'Coordinate reference system could not be decoded, writing unknown CRS'
		], log.messages)

		log = new CountingLog()
		def failing = [getCRS: { throw new IllegalStateException('boom') }] as eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition
		crs = GeoParquetCrs.resolve(failing, log)
		assertTrue(crs.unknown)
		assertEquals(2, log.messages.size())
		assertEquals('Coordinate reference system could not be decoded, writing unknown CRS', log.messages[1])
	}

	@Test
	void testCrs84Factory() {
		def crs = GeoParquetCrs.crs84()
		assertTrue(crs.crs84)
		assertNotNull(crs.targetCrs)
		assertFalse(crs.swapAxes)
	}
}
