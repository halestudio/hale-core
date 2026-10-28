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
package eu.esdihumboldt.hale.io.geoparquet

import static eu.esdihumboldt.hale.io.geoparquet.GeoParquetTestUtil.*
import static org.junit.Assert.*

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.apache.parquet.schema.ColumnOrder
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.Types
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBWriter
import org.locationtech.jts.io.WKTReader

import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for {@link GeoParquetCompliance}.
 */
class GeoParquetComplianceTest extends AbstractPlatformTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder()

	private static final List<String> POINTS = ['POINT (1 2)', 'POINT (5 -3)']

	private static byte[] wkbOf(String wkt) {
		new WKBWriter(2, true).write(new WKTReader().read(wkt))
	}

	/**
	 * Schema of a version 1.1.0 file with a plain binary geometry column and a
	 * bbox covering column.
	 */
	private static MessageType rawSchema(boolean columnOrder, LogicalTypeAnnotation geometryType = null) {
		def bbox = Types.requiredGroup()
		[
			'xmin',
			'ymin',
			'xmax',
			'ymax'
		].each { String n ->
			def b = Types.required(PrimitiveTypeName.DOUBLE)
			if (columnOrder) {
				b = b.columnOrder(ColumnOrder.typeDefined())
			}
			bbox.addField(b.named(n))
		}
		Types.buildMessage()
				.required(PrimitiveTypeName.BINARY).as(geometryType).named('geometry')
				.addField(bbox.named('bbox'))
				.named('raw')
	}

	private static String rawGeo(List<Double> bbox, List<String> types = ['Point'], String version = '1.1.0',
			Map extra = [:]) {
		JsonOutput.toJson([
			version       : version,
			primary_column: 'geometry',
			columns       : [
				geometry: [
					encoding      : 'WKB',
					geometry_types: types,
					bbox          : bbox,
					covering      : [bbox: [xmin: ['bbox', 'xmin'], ymin: ['bbox', 'ymin'], xmax: ['bbox', 'xmax'], ymax: ['bbox', 'ymax']]]
				] + extra
			]
		])
	}

	private static List<Map<String, Object>> rawRows() {
		POINTS.collect { String wkt ->
			Geometry g = new WKTReader().read(wkt)
			[geometry: wkbOf(wkt), bbox: [xmin: g.coordinate.x, ymin: g.coordinate.y, xmax: g.coordinate.x, ymax: g.coordinate.y]] as Map<String, Object>
		}
	}

	private File writeRawFile(boolean columnOrder, String geo, LogicalTypeAnnotation geometryType = null,
			Collection<String> noStatistics = [], List<Map<String, Object>> rows = rawRows()) {
		File f = tmp.newFile()
		writeRaw(f, rawSchema(columnOrder, geometryType), [geo: geo], rows, noStatistics)
		f
	}

	private static void assertFails(String expectedPart, Closure c) {
		try {
			c()
		} catch (AssertionError e) {
			assertTrue("Unexpected message: ${e.message}", e.message.toLowerCase().contains(expectedPart.toLowerCase()))
			return
		}
		fail("Expected compliance failure mentioning '$expectedPart'")
	}

	@Test
	void testAcceptsRawCompliantFile() {
		GeoParquetCompliance.check(writeRawFile(true, rawGeo([1d, -3d, 5d, 2d])))
	}

	@Test
	void testDetectsMissingColumnOrder() {
		File f = writeRawFile(false, rawGeo([1d, -3d, 5d, 2d]))
		assertFails('column order') { GeoParquetCompliance.check(f) }
	}

	@Test
	void testDetectsWrongBbox() {
		File f = writeRawFile(true, rawGeo([1d, -3d, 5d, 3d]))
		assertFails('column bbox') { GeoParquetCompliance.check(f) }
	}

	@Test
	void testDetectsWrongGeometryTypes() {
		File f = writeRawFile(true, rawGeo([1d, -3d, 5d, 2d], ['Polygon']))
		assertFails('geometry_types') { GeoParquetCompliance.check(f) }
	}

	@Test
	void testDetectsSchemaViolation() {
		File f = writeRawFile(true, rawGeo([1d, -3d, 5d, 2d]).replace('"WKB"', '"WKT"'))
		assertFails('schema') { GeoParquetCompliance.check(f) }
	}

	@Test
	void testDetectsRowGroupLimit() {
		File f = writeRawFile(true, rawGeo([1d, -3d, 5d, 2d]))
		assertFails('row group') { GeoParquetCompliance.check(f, 1) }
	}

	@Test
	void testDetectsMissingGeo() {
		File f = tmp.newFile()
		writeRaw(f, rawSchema(true), [:], rawRows())
		// fine if no geometry is expected, a violation if it is
		GeoParquetCompliance.check(f)
		assertTrue(GeoParquetCompliance.hasGeometryColumn(f))
		assertFails('geo metadata') { GeoParquetCompliance.check(f, 150000, true) }
	}

	@Test
	void testDetectsEmptyGeoColumns() {
		File f = tmp.newFile()
		writeRaw(f, rawSchema(true), [geo: '{"version":"1.1.0","primary_column":"geometry","columns":{}}'], rawRows())
		// the JSON schema already requires columns; the explicit rule guards versions/schemas that do not
		assertFails('columns') { GeoParquetCompliance.check(f) }
	}

	@Test
	void testDetectsNoRowGroups() {
		File f = writeRawFile(true, rawGeo([1d, -3d, 5d, 2d]), null, [], [])
		assertFails('row groups') { GeoParquetCompliance.check(f) }
	}

	@Test
	void testDetectsCrsMismatch() {
		def epsg25832 = GeoParquetCrs.resolve(new CodeDefinition('EPSG:25832', true), SimpleLog.NO_LOG)
		def geo = rawGeo([1d, -3d, 5d, 2d], ['Point'], '2.0.0', [crs: new JsonSlurper().parseText(epsg25832.projJson)])
		File f = writeRawFile(true, geo, LogicalTypeAnnotation.geometryType('EPSG:25831'))
		assertFails('crs consistency') { GeoParquetCompliance.check(f) }
	}

	@Test
	void testDetectsCrsInLogicalTypeButNotInGeo() {
		File f = writeRawFile(true, rawGeo([1d, -3d, 5d, 2d], ['Point'], '2.0.0'),
		LogicalTypeAnnotation.geometryType('EPSG:25832'))
		assertFails('crs consistency') { GeoParquetCompliance.check(f) }
	}

	@Test
	void testDetectsMissingBboxStatistics() {
		File f = writeRawFile(true, rawGeo([1d, -3d, 5d, 2d]), null, ['bbox.xmin'])
		assertFails('bbox statistics') { GeoParquetCompliance.check(f) }
	}

	@Test
	void testDetectsMissingGeospatialStatistics() {
		File f = writeRawFile(true, rawGeo([1d, -3d, 5d, 2d], ['Point'], '2.0.0'),
		LogicalTypeAnnotation.geometryType(null), ['geometry'])
		assertFails('geospatial statistics') { GeoParquetCompliance.check(f) }
	}

	@Test
	void testAcceptsRawCompliant20File() {
		File f = writeRawFile(true, rawGeo([1d, -3d, 5d, 2d], ['Point'], '2.0.0'),
		LogicalTypeAnnotation.geometryType(null))
		GeoParquetCompliance.check(f, 150000, true)
	}

	@Test
	void testAcceptsWriterOutput() {
		def schema = new SchemaBuilder().schema {
			city {
				name(String)
				location(GeometryProperty)
			}
		}
		def instances = new InstanceBuilder(types: schema).createCollection {
			city {
				name 'a'
				location(GeoParquetInstanceWriterTest.createGeometry('POINT Z (8.6 49.8 100)', new CodeDefinition('EPSG:4326')))
			}
			city {
				name 'b'
				location(GeoParquetInstanceWriterTest.createGeometry('POINT Z (9.1 48.1 200)', new CodeDefinition('EPSG:25832')))
			}
		}
		GeoParquetVersion.values().each { GeoParquetVersion v ->
			File target = tmp.newFolder().toPath().resolve('out.parquet').toFile()
			// writeInstances runs the compliance check on the output
			writeInstances(target, schema, instances, { it.setVersion(v) })
			assertTrue(target.exists())
			GeoParquetCompliance.check(target)
		}
	}
}
