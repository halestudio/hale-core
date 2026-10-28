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
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.Types
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBWriter
import org.locationtech.jts.io.WKTReader

import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.common.instance.geometry.impl.WKTDefinition
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrsTest
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for {@link ExternalValidators}. Writer output is validated by
 * {@link GeoParquetTestUtil#writeInstances} (with the external validators
 * enabled), the other tests call the validators directly.
 */
class ExternalValidatorsTest extends AbstractPlatformTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder()

	@Test
	void testAcceptsWriterOutput() {
		def schema = new SchemaBuilder().schema {
			parcel {
				name(String)
				address {
					street(String)
					number(Integer)
				}
				area(Double)
				shape(GeometryProperty)
			}
		}
		def instances = {
			new InstanceBuilder(types: schema).createCollection {
				[0, 1, 2].each { int i ->
					parcel {
						name "parcel $i"
						address {
							street "Street $i"
							number i
						}
						area 100.5d * i
						shape(GeoParquetInstanceWriterTest.createGeometry(
								"POLYGON((${2600000 + i * 100} 1200000, ${2600000 + i * 100} 1200050, ${2600050 + i * 100} 1200050, ${2600050 + i * 100} 1200000, ${2600000 + i * 100} 1200000))",
								new CodeDefinition('EPSG:2056', true)))
					}
				}
			}
		}
		GeoParquetVersion.values().findAll { it != GeoParquetVersion.V1_0 }.each { GeoParquetVersion v ->
			File target = tmp.newFolder().toPath().resolve('parcels.parquet').toFile()
			// validators run as part of writeInstances
			writeInstances(target, schema, instances(), { it.setVersion(v) })
			assertTrue(target.exists())
			// and directly
			ExternalValidators.check(target)
		}
	}

	@Test
	void testRejectsMissingGeoBbox() {
		ExternalValidators.assumeAvailable('portolan')

		MessageType type = Types.buildMessage()
				.required(PrimitiveTypeName.BINARY).named('geometry')
				.addField(Types.requiredGroup().addFields(*[
					'xmin',
					'ymin',
					'xmax',
					'ymax'
				].collect {
					Types.required(PrimitiveTypeName.DOUBLE).columnOrder(ColumnOrder.typeDefined()).named(it)
				}).named('bbox'))
				.named('raw')
		def geo = [
			version       : '1.1.0',
			primary_column: 'geometry',
			columns       : [
				geometry: [
					encoding      : 'WKB',
					geometry_types: ['Point'],
					// no column-level bbox
					covering      : [bbox: [xmin: ['bbox', 'xmin'], ymin: ['bbox', 'ymin'], xmax: ['bbox', 'xmax'], ymax: ['bbox', 'ymax']]]
				]
			]
		]
		def rows = ['POINT (1 2)', 'POINT (5 -3)'].collect { String wkt ->
			Geometry g = new WKTReader().read(wkt)
			[geometry: new WKBWriter(2, true).write(g), bbox: [xmin: g.coordinate.x, ymin: g.coordinate.y, xmax: g.coordinate.x, ymax: g.coordinate.y]] as Map<String, Object>
		}
		// separate directory, as writeInstances checks all files in a directory
		File f = new File(tmp.newFolder(), 'raw.parquet')
		writeRaw(f, type, [geo: JsonOutput.toJson(geo)], rows)

		try {
			ExternalValidators.check(f)
			fail('Expected validation failure')
		} catch (AssertionError e) {
			assertTrue("Unexpected message: ${e.message}", e.message.contains('no geometry'))
		}
	}

	@Test
	void testUnknownCrsAccepted() {
		def schema = new SchemaBuilder().schema {
			area {
				geom(GeometryProperty)
			}
		}
		def instances = new InstanceBuilder(types: schema).createCollection {
			area {
				geom(GeoParquetInstanceWriterTest.createGeometry('POLYGON((0 0, 0 10, 10 10, 10 0, 0 0))',
						new WKTDefinition(GeoParquetCrsTest.CUSTOM_WKT, null)))
			}
		}
		File target = new File(tmp.newFolder(), 'unknown.parquet')
		def report = writeInstances(target, schema, instances, { it.setVersion(GeoParquetVersion.V2_0) })
		assertTrue(report.warnings*.message.toString(), report.warnings.any { it.message.toLowerCase().contains('unknown') })
		assertNull(geoMetadata(target).columns.geometry.crs)
		assertTrue(geoMetadata(target).columns.geometry.containsKey('crs'))

		// explicitly (also checked by writeInstances)
		ExternalValidators.check(target)
	}

	/**
	 * Output of <code>portolan check --json</code> (0.8.0) for a catalog with
	 * one GeoParquet file and only the ignored catalog metadata findings.
	 */
	static Map portolanReport(Map dataOverrides = [:]) {
		[
			success: false,
			command: 'check',
			data   : [
				mode         : 'all',
				passed       : false,
				files_checked: 2,
				error_count  : 1,
				warning_count: 1,
				findings     : [
					[rule_id: 'PTL-FIL-005', severity: 'warning', message: 'README.md does not appear to mention the data\'s provenance'],
					[rule_id: 'PTL-PRV-001', severity: 'error', message: 'collection declares no providers']
				]
			] + dataOverrides,
			errors : [
				[type: 'PTL-PRV-001', message: 'collection declares no providers']
			]
		]
	}

	private static void assertPortolanFails(String output, String expected) {
		def result = new ExternalValidators.Result(exitCode: 1, output: output)
		try {
			ExternalValidators.evaluatePortolanCheck('test.parquet', result)
			fail("Expected portolan check to fail for: $output")
		} catch (AssertionError e) {
			assertTrue("Unexpected message: ${e.message}", e.message.contains(expected))
			// the output is included
			assertTrue(e.message, e.message.contains(output))
		}
	}

	/**
	 * The evaluation of the Portolan report fails closed: a report without
	 * findings payload or without checked files fails, as do relevant findings.
	 */
	@Test
	void testPortolanReportEvaluation() {
		// normal report, only ignored findings (exit code 1 is ignored)
		ExternalValidators.evaluatePortolanCheck('test.parquet',
				new ExternalValidators.Result(exitCode: 1, output: 'Checking...\n' + JsonOutput.toJson(portolanReport())))

		// error envelope with empty data
		assertPortolanFails(JsonOutput.toJson([success: false, command: 'check', data: [:],
			errors: [
				[type: 'Error', message: 'something went wrong']
			]]), 'no findings')
		// no data at all
		assertPortolanFails(JsonOutput.toJson([success: false, command: 'check',
			errors: [
				[type: 'Error', message: 'something went wrong']
			]]), 'no findings')
		// findings not a list
		assertPortolanFails(JsonOutput.toJson(portolanReport([findings: 'none'])), 'no findings')
		// no files checked
		assertPortolanFails(JsonOutput.toJson(portolanReport([files_checked: 0])), 'no files')
		assertPortolanFails(JsonOutput.toJson(portolanReport([files_checked: null])), 'no files')
		// relevant finding
		def report = portolanReport()
		report.data.findings << [rule_id: 'PTL-DAT-007', severity: 'warning', message: 'statistics not usable']
		assertPortolanFails(JsonOutput.toJson(report), 'PTL-DAT-007')
		// ignored for this file
		ExternalValidators.evaluatePortolanCheck('test.parquet',
				new ExternalValidators.Result(exitCode: 1, output: JsonOutput.toJson(report)), ['PTL-DAT-007'])
		// no JSON
		assertPortolanFails('Error: no catalog', 'did not produce JSON')
	}
}
