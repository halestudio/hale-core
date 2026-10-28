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
package eu.esdihumboldt.hale.io.geoparquet.internal.meta

import static org.junit.Assert.*

import groovy.json.JsonSlurper

import org.junit.Test
import org.locationtech.jts.io.WKTReader

import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetTestUtil
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetVersion
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for {@link GeoMetadataBuilder}.
 */
class GeoMetadataBuilderTest extends AbstractPlatformTest {

	private GeoColumnStats statsOf(String... wkts) {
		def s = new GeoColumnStats()
		def reader = new WKTReader()
		wkts.each { s.add(reader.read(it)) }
		s
	}

	@Test
	void testStats() {
		def s = statsOf('POINT (1 2)', 'POINT EMPTY', 'LINESTRING Z (0 0 1, 5 6 2)')
		s.add(null)
		assertEquals(['LineString Z', 'Point'], s.geometryTypes as List)
		def e = s.extent
		assertEquals([0d, 0d, 5d, 6d], [
			e.minX,
			e.minY,
			e.maxX,
			e.maxY
		])
		assertNull(new GeoColumnStats().extent)
	}

	@Test
	void testVersions() {
		def crs84 = GeoParquetCrs.crs84()
		def epsg = GeoParquetCrs.resolve(new CodeDefinition('EPSG:25832', true), SimpleLog.NO_LOG)
		def unknown = GeoParquetCrs.unknown()
		assertTrue(unknown.unknown)

		GeoParquetVersion.values().each { version ->
			def stats = new LinkedHashMap<String, GeoColumnStats>()
			stats.geometry = statsOf('POINT (1 2)', 'MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))')
			stats.other = statsOf('POINT Z (1 2 3)')
			stats.third = new GeoColumnStats()
			def crsMap = [geometry: crs84, other: epsg, third: unknown]

			String json = GeoMetadataBuilder.build(version, 'geometry', stats, crsMap, true)
			def geo = new JsonSlurper().parseText(json)

			assertEquals(version.versionString, geo.version)
			assertEquals('geometry', geo.primary_column)
			def cols = geo.columns
			assertEquals(['geometry', 'other', 'third'], cols.keySet() as List)
			cols.each { n, c -> assertEquals('WKB', c.encoding) }

			// covering only for the primary column of versions that declare it
			if (version == GeoParquetVersion.V1_0) {
				assertFalse(cols.geometry.containsKey('covering'))
			}
			else {
				assertEquals(['bbox', 'xmin'], cols.geometry.covering.bbox.xmin)
				assertEquals(['bbox', 'ymax'], cols.geometry.covering.bbox.ymax)
			}
			assertFalse(cols.other.containsKey('covering'))

			// crs
			assertFalse(cols.geometry.containsKey('crs'))
			assertEquals(25832, cols.other.crs.id.code)
			assertTrue(cols.third.containsKey('crs'))
			assertNull(cols.third.crs)

			// bbox and types
			assertEquals([0d, 0d, 1d, 2d], cols.geometry.bbox*.doubleValue())
			assertEquals(['MultiPolygon', 'Point'], cols.geometry.geometry_types)
			assertEquals(['Point Z'], cols.other.geometry_types)
			assertFalse(cols.third.containsKey('bbox'))

			assertEquals("$version: ${GeoParquetTestUtil.validateGeo(version, json)}".toString(), [], GeoParquetTestUtil.validateGeo(version, json))

			// without covering
			json = GeoMetadataBuilder.build(version, 'geometry', stats, crsMap, false)
			assertFalse(new JsonSlurper().parseText(json).columns.geometry.containsKey('covering'))
			assertEquals([], GeoParquetTestUtil.validateGeo(version, json))
		}
	}

	@Test
	void testValidationDetectsInvalidMetadata() {
		// guards against the validation being a no-op
		assertFalse(GeoParquetTestUtil.validateGeo(GeoParquetVersion.V2_0, '{"version":"1.0.0","primary_column":"g","columns":{}}').isEmpty())
	}
}
