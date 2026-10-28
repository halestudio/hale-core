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

import org.apache.parquet.schema.LogicalTypeAnnotation
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.locationtech.jts.geom.Geometry

import eu.esdihumboldt.cst.functions.geometric.GeometryHelperFunctions
import eu.esdihumboldt.hale.common.core.io.IOProviderConfigurationException
import eu.esdihumboldt.hale.common.core.io.report.IOReport
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for writing instances with {@link GeoParquetInstanceWriter}.
 */
class GeoParquetInstanceWriterTest extends AbstractPlatformTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder()

	static GeometryProperty<? extends Geometry> createGeometry(String wkt, Object crs) {
		return GeometryHelperFunctions._with([geometry: wkt, crs: crs])
	}

	private Schema citySchema() {
		new SchemaBuilder().schema {
			city {
				name(String)
				population(Integer)
				location(GeometryProperty)
			}
		}
	}

	private InstanceCollection cities(Schema schema) {
		new InstanceBuilder(types: schema).createCollection {
			city {
				name 'Darmstadt'
				population 158254
				// lat/lon axis order
				location(createGeometry('POINT(49.872833 8.651222)', new CodeDefinition('EPSG:4326')))
			}
			city {
				name 'München'
				population 1471508
				location(createGeometry('POLYGON((48.1 9.5, 48.1 9.6, 48.2 9.6, 48.1 9.5))', new CodeDefinition('EPSG:4326')))
			}
		}
	}

	@Test
	void testWriteAttributes() {
		Schema schema = new SchemaBuilder().schema {
			attr1 {
				col1()
				col2()
			}
			abc {
				a(String)
				b(Double)
				c(Long)
			}
		}
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			attr1 {
				col1('Value 1')
				col2('Value 2')
			}
			abc {
				a('Text')
				b(3.14)
				c(42)
			}
			abc {
				a('More text')
				b(1.52)
				c(23)
			}
		}

		withNewGeoParquet(schema, instances, { List<File> files ->
			assertEquals([
				'out_abc.parquet',
				'out_attr1.parquet'
			], files*.name)
			files.each {
				assertNull(geoMetadata(it))
			}

			def abc = readRows(files[0])
			assertEquals(2, abc.size())
			assertEquals('Text', abc[0].getString('a', 0))
			assertEquals(3.14d, abc[0].getDouble('b', 0), 0)
			assertEquals(42L, abc[0].getLong('c', 0))
			assertEquals('More text', abc[1].getString('a', 0))

			def attr1 = readRows(files[1])
			assertEquals(1, attr1.size())
			assertEquals('Value 1', attr1[0].getString('col1', 0))
			assertEquals('Value 2', attr1[0].getString('col2', 0))
		}, { it.setSort(false) })
	}

	@Test
	void testSingleTypeUsesTargetName() {
		Schema schema = citySchema()
		def target = new File(tmp.root, 'cities.parquet')
		def report = writeInstances(target, schema, cities(schema), { it.setSort(false) })
		assertTrue(report.success)
		assertEquals(['cities.parquet'], tmp.root.list() as List)
		assertEquals(2, readRows(target).size())
	}

	@Test
	void testWriteFeatures() {
		Schema schema = citySchema()
		withNewGeoParquet(schema, cities(schema), { List<File> files ->
			assertEquals(1, files.size())
			def file = files[0]
			def rows = readRows(file)
			assertEquals(2, rows.size())

			// lat/lon input is written as lon/lat
			def point = wkb(rows[0], 'geometry')
			assertEquals(8.651222, point.x, 1e-9)
			assertEquals(49.872833, point.y, 1e-9)
			assertTrue(point.x >= 5 && point.x <= 10)
			def polygon = wkb(rows[1], 'geometry')
			assertTrue(polygon.envelopeInternal.minX >= 5 && polygon.envelopeInternal.maxX <= 10)

			def geo = geoMetadata(file)
			assertNotNull(geo)
			assertEquals('geometry', geo.primary_column)
			assertFalse(geo.columns.geometry.containsKey('crs'))

			def logical = readFooter(file).fileMetaData.schema.getType('geometry').logicalTypeAnnotation
			assertTrue(logical instanceof LogicalTypeAnnotation.GeometryLogicalTypeAnnotation)
			assertNull(logical.crs)
		}, { it.setSort(false) })
	}

	@Test
	void testWriteFeaturesTargetCRS() {
		Schema schema = citySchema()
		withNewGeoParquet(schema, cities(schema), { List<File> files ->
			def file = files[0]
			def point = wkb(readRows(file)[0], 'geometry')
			// UTM 32N, easting/northing
			assertEquals(475000d, point.x, 10000d)
			assertEquals(5.5e6d, point.y, 100000d)

			def geo = geoMetadata(file)
			assertEquals(25832, geo.columns.geometry.crs.id.code)

			def logical = readFooter(file).fileMetaData.schema.getType('geometry').logicalTypeAnnotation
			assertEquals('EPSG:25832', logical.crs)
		}, {
			it.setSort(false)
			it.setTargetCRS(new CodeDefinition('EPSG:25832', true))
		})
	}

	@Test
	void testVersions() {
		Schema schema = citySchema()
		GeoParquetVersion.values().each { version ->
			withNewGeoParquet(schema, cities(schema), { List<File> files ->
				assertEquals(version.versionString, geoMetadata(files[0]).version)
			}, {
				it.setSort(false)
				it.setVersion(version)
			})
		}
	}

	@Test
	void testParametersReachMapping() {
		Schema schema = new SchemaBuilder().schema {
			t {
				address {
					street(String)
					detail {
						note(String)
					}
				}
				amount(BigDecimal)
			}
		}
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			t {
				address {
					street('Main Street')
					detail {
						note('Second floor')
					}
				}
				amount(new BigDecimal('12.345'))
			}
		}

		withNewGeoParquet(schema, instances, { List<File> files ->
			def parquetSchema = readFooter(files[0]).fileMetaData.schema
			// struct below the nesting depth limit is written as JSON text
			def address = parquetSchema.getType('address')
			assertFalse(address.isPrimitive())
			assertTrue(address.asGroupType().getType('detail').isPrimitive())
			def row = readRows(files[0])[0]
			assertTrue(row.getGroup('address', 0).getString('detail', 0).contains('Second floor'))
			def decimal = parquetSchema.getType('amount').logicalTypeAnnotation
			assertTrue(decimal instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation)
			assertEquals(38, decimal.precision)
			assertEquals(2, decimal.scale)
		}, {
			it.setSort(false)
			it.setNestingDepth(1)
			it.setBigNumberEncoding(BigNumberEncoding.DECIMAL)
			it.setDecimalScale(2)
		})
	}

	@Test
	void testNoInstances() {
		Schema schema = citySchema()
		def target = new File(tmp.root, 'empty.parquet')
		IOReport report = writeInstances(target, schema, new InstanceBuilder(types: schema).createCollection {
		}, { it.setSort(false) })
		assertTrue(report.success)
		assertFalse(report.warnings.isEmpty())
		assertEquals(0, (tmp.root.list() as List).size())
	}

	@Test
	void testOverwrite() {
		Schema schema = citySchema()
		def target = tmp.newFile('existing.parquet')
		target.text = 'previous content'

		// no overwrite -> error, nothing written
		def writer = new GeoParquetInstanceWriter()
		writer.setTarget(new eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier(target))
		def ss = new eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchemaSpace()
		ss.addSchema(schema)
		writer.setTargetSchema(ss)
		writer.setInstances(cities(schema))
		writer.setSort(false)
		def report = writer.execute(new eu.esdihumboldt.hale.common.core.io.impl.LogProgressIndicator())
		assertFalse(report.success)
		assertFalse(report.errors.isEmpty())
		assertEquals('previous content', target.text)
		assertEquals(['existing.parquet'], tmp.root.list() as List)

		// overwrite -> replaced
		writeInstances(target, schema, cities(schema), {
			it.setSort(false)
			it.setOverwriteTargetFile(true)
		})
		assertEquals(2, readRows(target).size())
		assertEquals(['existing.parquet'], tmp.root.list() as List)
	}

	private Schema twoTypeSchema() {
		new SchemaBuilder().schema {
			alpha {
				a(String)
			}
			beta {
				b(String)
			}
		}
	}

	private InstanceCollection twoTypeInstances(Schema schema, String suffix = '') {
		new InstanceBuilder(types: schema).createCollection {
			alpha {
				a('alpha' + suffix)
			}
			beta {
				b('beta' + suffix)
			}
		}
	}

	private IOReport executeWriter(File target, Schema schema, InstanceCollection instances, boolean overwrite) {
		def writer = new GeoParquetInstanceWriter()
		writer.setTarget(new eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier(target))
		def ss = new eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchemaSpace()
		ss.addSchema(schema)
		writer.setTargetSchema(ss)
		writer.setInstances(instances)
		writer.setSort(false)
		writer.setOverwriteTargetFile(overwrite)
		writer.execute(new eu.esdihumboldt.hale.common.core.io.impl.LogProgressIndicator())
	}

	@Test
	void testExistingTypeFileNotTouchedWithoutOverwrite() {
		Schema schema = twoTypeSchema()
		def existing = tmp.newFile('out_beta.parquet')
		existing.text = 'precious'
		byte[] before = existing.bytes

		def report = executeWriter(new File(tmp.root, 'out.parquet'), schema, twoTypeInstances(schema), false)
		assertFalse(report.success)
		assertFalse(report.errors.isEmpty())
		assertArrayEquals(before, existing.bytes)
		// no other files, in particular no temporary files
		assertEquals(['out_beta.parquet'], tmp.root.list() as List)
	}

	@Test
	void testStaleTypeFileDoesNotBlockSingleType() {
		Schema schema = citySchema()
		def stale = tmp.newFile('out_city.parquet')
		stale.text = 'stale'
		def target = new File(tmp.root, 'out.parquet')

		def report = executeWriter(target, schema, cities(schema), false)
		assertTrue(report.errors*.message.toString(), report.success)
		assertEquals(2, readRows(target).size())
		assertEquals('stale', stale.text)
		assertEquals([
			'out.parquet',
			'out_city.parquet'
		], (tmp.root.list() as List).sort())
	}

	@Test
	void testOverwriteReplacesTypeFiles() {
		Schema schema = twoTypeSchema()
		def alpha = tmp.newFile('out_alpha.parquet')
		alpha.text = 'old'
		def beta = tmp.newFile('out_beta.parquet')
		beta.text = 'old'

		def report = executeWriter(new File(tmp.root, 'out.parquet'), schema, twoTypeInstances(schema), true)
		assertTrue(report.errors*.message.toString(), report.success)
		assertEquals('alpha', readRows(alpha)[0].getString('a', 0))
		assertEquals('beta', readRows(beta)[0].getString('b', 0))
		assertEquals([
			'out_alpha.parquet',
			'out_beta.parquet'
		], (tmp.root.list() as List).sort())
	}

	@Test
	void testWindingOrder() {
		Schema schema = new SchemaBuilder().schema {
			area {
				geom(GeometryProperty)
			}
		}
		// clockwise exterior ring in projected CRS (x/y order)
		def wkt = 'POLYGON((0 0, 0 10, 10 10, 10 0, 0 0))'
		def collection = {
			new InstanceBuilder(types: schema).createCollection {
				area {
					geom(createGeometry(wkt, new CodeDefinition('EPSG:25832', true)))
				}
			}
		}

		// default (no change): ring is written as is
		withNewGeoParquet(schema, collection(), { List<File> files ->
			def polygon = wkb(readRows(files[0])[0], 'geometry')
			assertFalse(org.locationtech.jts.algorithm.Orientation.isCCW(polygon.exteriorRing.coordinates))
		}, { it.setSort(false) })

		// requested winding order is applied
		withNewGeoParquet(schema, collection(), { List<File> files ->
			def polygon = wkb(readRows(files[0])[0], 'geometry')
			assertTrue(org.locationtech.jts.algorithm.Orientation.isCCW(polygon.exteriorRing.coordinates))
		}, {
			it.setSort(false)
			it.setWindingOrder(eu.esdihumboldt.hale.common.instance.io.util.EnumWindingOrderTypes.counterClockwise)
		})

		// same with conversion to a target CRS parameter
		withNewGeoParquet(schema, collection(), { List<File> files ->
			def polygon = wkb(readRows(files[0])[0], 'geometry')
			assertTrue(org.locationtech.jts.algorithm.Orientation.isCCW(polygon.exteriorRing.coordinates))
		}, {
			it.setSort(false)
			it.setTargetCRS(new CodeDefinition('EPSG:25833', true))
			it.setWindingOrder(eu.esdihumboldt.hale.common.instance.io.util.EnumWindingOrderTypes.counterClockwise)
		})
	}

	/**
	 * Type names that differ only in case get distinct file names (case
	 * insensitive file systems).
	 */
	@Test
	void testFileNamesCaseInsensitive() {
		Schema schema = new SchemaBuilder().schema {
			Road { name(String) }
			road { name(String) }
		}
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			Road { name 'A' }
			road { name 'b' }
		}
		withNewGeoParquet(schema, instances, { List<File> files ->
			assertEquals(2, files.size())
			def names = files*.name
			assertEquals(names.toString(), 2, names.collect { it.toLowerCase(Locale.ROOT) }.unique().size())
			assertTrue(names.toString(), names.contains('out_Road.parquet'))
			assertTrue(names.toString(), names.contains('out_road_2.parquet'))
		})
	}

	/**
	 * Invalid parameter values are rejected with a configuration error, both by
	 * validate() and before writing.
	 */
	@Test
	void testInvalidParameters() {
		Schema schema = citySchema()
		Map<String, Closure> invalid = [
			'decimalScale -1'     : { GeoParquetInstanceWriter w -> w.setDecimalScale(-1) },
			'decimalScale 39'     : { GeoParquetInstanceWriter w -> w.setDecimalScale(39) },
			'nestingDepth 0'      : { GeoParquetInstanceWriter w -> w.setNestingDepth(0) },
			'rowGroupRowLimit 0'  : { GeoParquetInstanceWriter w ->
				w.setRowGroupRowLimit(0)
			}
		]
		invalid.each { String label, Closure configure ->
			File target = new File(tmp.newFolder(), 'cities.parquet')
			def writer = new GeoParquetInstanceWriter()
			writer.setTarget(new FileIOSupplier(target))
			def ss = new DefaultSchemaSpace()
			ss.addSchema(schema)
			writer.setTargetSchema(ss)
			writer.setInstances(cities(schema))
			configure(writer)

			String parameter = label.split(' ')[0]
			try {
				writer.validate()
				fail("validate() accepts $label")
			} catch (IOProviderConfigurationException e) {
				assertTrue(e.message, e.message.contains(parameter))
			}
			try {
				writer.execute(new LogProgressIndicator())
				fail("execute() accepts $label")
			} catch (IOProviderConfigurationException e) {
				assertTrue(e.message, e.message.contains(parameter))
			}
			assertFalse(target.exists())
		}

		// boundaries are accepted
		withNewGeoParquet(schema, cities(schema), { List<File> files ->
			assertEquals(1, files.size())
		}, {
			it.setDecimalScale(38)
			it.setNestingDepth(1)
			it.setRowGroupRowLimit(1)
			it.validate()
		}, false)
	}
}
