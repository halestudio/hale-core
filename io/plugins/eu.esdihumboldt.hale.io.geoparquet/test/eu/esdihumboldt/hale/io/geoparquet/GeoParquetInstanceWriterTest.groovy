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

import javax.xml.namespace.QName

import org.apache.parquet.schema.LogicalTypeAnnotation
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader

import eu.esdihumboldt.cst.functions.geometric.GeometryHelperFunctions
import eu.esdihumboldt.hale.common.core.io.IOProviderConfigurationException
import eu.esdihumboldt.hale.common.core.io.impl.LogProgressIndicator
import eu.esdihumboldt.hale.common.core.io.report.IOReport
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier
import eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.instance.model.InstanceReference
import eu.esdihumboldt.hale.common.instance.model.MutableInstance
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchemaSpace
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
		})
	}

	@Test
	void testSingleTypeUsesTargetName() {
		Schema schema = citySchema()
		def target = new File(tmp.root, 'cities.parquet')
		def report = writeInstances(target, schema, cities(schema))
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
		})
	}

	@Test
	void testWriteFeaturesStreaming() {
		Schema schema = citySchema()
		withNewGeoParquet(schema, cities(schema), { List<File> files ->
			def rows = readRows(files[0])
			assertEquals(2, rows.size())
			// input order is kept
			assertEquals('Darmstadt', rows[0].getString('name', 0))
			assertEquals(8.651222, wkb(rows[0], 'geometry').x, 1e-9)
			assertEquals('München', rows[1].getString('name', 0))
			assertNotNull(geoMetadata(files[0]))
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
			it.setTargetCRS(new CodeDefinition('EPSG:25832', true))
		})
	}

	/**
	 * Writer that always returns the same target CRS object.
	 */
	static class FixedTargetCrsWriter extends GeoParquetInstanceWriter {

		CRSDefinition fixedTargetCrs

		@Override
		CRSDefinition getTargetCRS() {
			fixedTargetCrs
		}
	}

	/**
	 * The success of the conversion to the target CRS does not depend on the
	 * identity of the CRS definitions: if the geometries use the same CRS
	 * definition object as the target CRS, they are still written in lon/lat
	 * order.
	 */
	@Test
	void testTargetCrsSameObjectAsGeometryCrs() {
		Schema schema = citySchema()
		// lat/lon axis order
		def crs = new CodeDefinition('EPSG:4326')
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			city {
				name 'Darmstadt'
				location(createGeometry('POINT(49.872833 8.651222)', crs))
			}
		}
		File target = new File(tmp.newFolder(), 'cities.parquet')
		def writer = new FixedTargetCrsWriter(fixedTargetCrs: crs)
		writer.setTarget(new FileIOSupplier(target))
		def ss = new DefaultSchemaSpace()
		ss.addSchema(schema)
		writer.setTargetSchema(ss)
		writer.setInstances(instances)
		IOReport report = writer.execute(new LogProgressIndicator())

		assertTrue(report.errors*.message.toString(), report.success)
		assertTrue(report.errors*.message.toString(), report.errors.isEmpty())
		def point = wkb(readRows(target)[0], 'geometry')
		assertEquals(8.651222, point.x, 1e-9)
		assertEquals(49.872833, point.y, 1e-9)
	}

	@Test
	void testVersions() {
		Schema schema = citySchema()
		GeoParquetVersion.values().each { version ->
			withNewGeoParquet(schema, cities(schema), { List<File> files ->
				assertEquals(version.versionString, geoMetadata(files[0]).version)
			}, {
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
		})
		assertTrue(report.success)
		assertFalse(report.warnings.isEmpty())
		assertEquals(0, (tmp.root.list() as List).size())
	}

	/**
	 * If moving a file to its final name fails, the error names the files that
	 * were already replaced and those that were not.
	 */
	@Test
	void testFailedMoveNamesFiles() {
		Schema schema = twoTypeSchema()
		File dir = tmp.newFolder()
		def alpha = new File(dir, 'out_alpha.parquet')
		alpha.text = 'old'
		// a non-empty directory cannot be replaced by a file
		def beta = new File(dir, 'out_beta.parquet')
		beta.mkdir()
		new File(beta, 'content').text = 'old'

		def report = executeWriter(new File(dir, 'out.parquet'), schema, twoTypeInstances(schema), true)
		assertFalse(report.success)
		String message = report.errors*.message.join('\n')
		assertTrue(message, message.contains('out_alpha.parquet'))
		assertTrue(message, message.contains('out_beta.parquet'))
		// the already replaced file is named as such
		assertTrue(message, message.contains('replaced'))
		assertEquals('alpha', readRows(alpha)[0].getString('a', 0))
		assertTrue(beta.isDirectory())
		// no temporary files are left
		assertEquals([
			'out_alpha.parquet',
			'out_beta.parquet'
		], (dir.list() as List).sort())
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
		def report = writer.execute(new eu.esdihumboldt.hale.common.core.io.impl.LogProgressIndicator())
		assertFalse(report.success)
		assertFalse(report.errors.isEmpty())
		assertEquals('previous content', target.text)
		assertEquals(['existing.parquet'], tmp.root.list() as List)

		// overwrite -> replaced
		writeInstances(target, schema, cities(schema), {
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

	/**
	 * Instance collection that counts the instances that are iterated or
	 * resolved by reference.
	 */
	static class CountingCollection implements InstanceCollection {

		@Delegate
		InstanceCollection delegate

		int iterated = 0

		int resolved = 0

		@Override
		ResourceIterator<Instance> iterator() {
			ResourceIterator<Instance> it = delegate.iterator()
			new ResourceIterator<Instance>() {

						@Override
						boolean hasNext() {
							it.hasNext()
						}

						@Override
						Instance next() {
							iterated++
							it.next()
						}

						@Override
						void close() {
							it.close()
						}
					}
		}

		@Override
		Instance getInstance(InstanceReference reference) {
			resolved++
			delegate.getInstance(reference)
		}
	}

	private InstanceCollection manyTwoTypeInstances(Schema schema) {
		new InstanceBuilder(types: schema).createCollection {
			alpha { a('alpha1') }
			beta { b('beta1') }
			alpha { a('alpha2') }
			alpha { a('alpha3') }
			alpha { a('alpha4') }
		}
	}

	/**
	 * An existing type file is detected before the data of the type is written:
	 * in streaming mode once the second type occurs, when sorting before the
	 * instances are written.
	 */
	@Test
	void testExistingTypeFileFailsEarly() {
		[true, false].each { boolean sort ->
			Schema schema = twoTypeSchema()
			File dir = tmp.newFolder()
			def existing = new File(dir, 'out_beta.parquet')
			existing.text = 'precious'
			def instances = new CountingCollection(delegate: manyTwoTypeInstances(schema))

			def writer = new GeoParquetInstanceWriter()
			writer.setTarget(new FileIOSupplier(new File(dir, 'out.parquet')))
			def ss = new DefaultSchemaSpace()
			ss.addSchema(schema)
			writer.setTargetSchema(ss)
			writer.setInstances(instances)
			writer.setSort(sort)
			IOReport report = writer.execute(new LogProgressIndicator())

			assertFalse(report.success)
			assertTrue(report.errors*.message.toString(), report.errors.any { it.message.contains('out_beta.parquet') })
			assertEquals('precious', existing.text)
			assertEquals(['out_beta.parquet'], dir.list() as List)
			if (sort) {
				// no instance written
				assertEquals(0, instances.resolved)
			}
			else {
				// stopped when the second type occurred
				assertEquals(2, instances.iterated)
			}
		}
	}

	/**
	 * An existing file with the target name does not prevent writing multiple
	 * types (it is not written in that case).
	 */
	@Test
	void testExistingTargetDoesNotBlockMultipleTypes() {
		[true, false].each { boolean sort ->
			Schema schema = twoTypeSchema()
			File dir = tmp.newFolder()
			def target = new File(dir, 'out.parquet')
			target.text = 'precious'

			def writer = new GeoParquetInstanceWriter()
			writer.setTarget(new FileIOSupplier(target))
			def ss = new DefaultSchemaSpace()
			ss.addSchema(schema)
			writer.setTargetSchema(ss)
			writer.setInstances(twoTypeInstances(schema))
			writer.setSort(sort)
			IOReport report = writer.execute(new LogProgressIndicator())

			assertTrue(report.errors*.message.toString(), report.success)
			assertEquals('precious', target.text)
			assertEquals([
				'out.parquet',
				'out_alpha.parquet',
				'out_beta.parquet'
			], (dir.list() as List).sort())
		}
	}

	@Test
	void testWindingOrder() {
		Schema schema = new SchemaBuilder().schema {
			area {
				geom(GeometryProperty)
			}
		}
		// clockwise exterior ring in geographic CRS (lon/lat order) in the area of use of EPSG:25833
		def wkt = 'POLYGON((13 52, 13 52.001, 13.001 52.001, 13.001 52, 13 52))'
		def collection = {
			new InstanceBuilder(types: schema).createCollection {
				area {
					geom(createGeometry(wkt, new CodeDefinition('EPSG:4326', true)))
				}
			}
		}

		// default (no change): ring is written as is
		withNewGeoParquet(schema, collection(), { List<File> files ->
			def polygon = wkb(readRows(files[0])[0], 'geometry')
			assertFalse(org.locationtech.jts.algorithm.Orientation.isCCW(polygon.exteriorRing.coordinates))
		})

		// requested winding order is applied
		withNewGeoParquet(schema, collection(), { List<File> files ->
			def polygon = wkb(readRows(files[0])[0], 'geometry')
			assertTrue(org.locationtech.jts.algorithm.Orientation.isCCW(polygon.exteriorRing.coordinates))
		}, {
			it.setWindingOrder(eu.esdihumboldt.hale.common.instance.io.util.EnumWindingOrderTypes.counterClockwise)
		})

		// same with conversion to a target CRS parameter
		withNewGeoParquet(schema, collection(), { List<File> files ->
			def polygon = wkb(readRows(files[0])[0], 'geometry')
			assertTrue(org.locationtech.jts.algorithm.Orientation.isCCW(polygon.exteriorRing.coordinates))
			// plausible result of the reprojection (13 E, 52 N is about 362700 E, 5764000 N in EPSG:25833)
			def center = polygon.envelopeInternal.centre()
			assertEquals(362700d, center.x, 1500d)
			assertEquals(5764000d, center.y, 3000d)
		}, {
			it.setTargetCRS(new CodeDefinition('EPSG:25833', true))
			it.setWindingOrder(eu.esdihumboldt.hale.common.instance.io.util.EnumWindingOrderTypes.counterClockwise)
		})
	}

	private Schema crsFromLaterFeatureSchema() {
		new SchemaBuilder().schema {
			feature {
				id(Integer)
				geom(GeometryProperty)
			}
		}
	}

	private InstanceCollection firstWithoutGeometry(Schema schema) {
		new InstanceBuilder(types: schema).createCollection {
			feature {
				id(0)
			}
			feature {
				id(1)
			}
			feature {
				id(2)
				geom(createGeometry('POINT(400000 5800000)', new CodeDefinition('EPSG:25832', true)))
			}
			feature {
				id(3)
				geom(createGeometry('POINT(410000 5810000)', new CodeDefinition('EPSG:25832', true)))
			}
		}
	}

	private void assertCrsFromLaterFeature(List<File> files, List<Integer> expectedIds) {
		def geo = geoMetadata(files[0])
		assertEquals(25832, geo.columns.geometry.crs.id.code)
		assertEquals('EPSG:25832', readFooter(files[0]).fileMetaData.schema.getType('geometry').logicalTypeAnnotation.crs)
		def rows = readRows(files[0])
		assertEquals(expectedIds, rows.collect { it.getInteger('id', 0) })
		// geometries are written as they are (already in the column CRS)
		def written = rows.findAll { it.getFieldRepetitionCount('geometry') > 0 }
		assertEquals(2, written.size())
		assertEquals(400000d, wkb(written.find { it.getInteger('id', 0) == 2 }, 'geometry').x, 0d)
	}

	@Test
	void testCrsFromFirstGeometrySorted() {
		Schema schema = crsFromLaterFeatureSchema()
		withNewGeoParquet(schema, firstWithoutGeometry(schema), { List<File> files ->
			// geometries first (along the curve), the others in input order
			assertCrsFromLaterFeature(files, [2, 3, 0, 1])
		})
	}

	@Test
	void testCrsFromFirstGeometryStreaming() {
		Schema schema = crsFromLaterFeatureSchema()
		withNewGeoParquet(schema, firstWithoutGeometry(schema), { List<File> files ->
			// input order is preserved, also for the buffered features
			assertCrsFromLaterFeature(files, [0, 1, 2, 3])
		}, {
			it.setSort(false)
		})
	}

	private InstanceCollection firstWithoutCrs(Schema schema, boolean laterWithCrs = true) {
		def crs = laterWithCrs ? new CodeDefinition('EPSG:25832', true) : null
		new InstanceBuilder(types: schema).createCollection {
			feature {
				id(0)
				geom(new DefaultGeometryProperty(null, new WKTReader().read('POINT(8 50)')))
			}
			feature {
				id(1)
				geom(new DefaultGeometryProperty(crs, new WKTReader().read('POINT(9 51)')))
			}
			feature {
				id(2)
				geom(new DefaultGeometryProperty(crs, new WKTReader().read('POINT(10 52)')))
			}
		}
	}

	/**
	 * The CRS of a column is determined from the first geometry that has a CRS,
	 * not from a preceding geometry without CRS.
	 */
	@Test
	void testCrsFromFirstGeometryWithCrs() {
		[true, false].each { boolean sort ->
			Schema schema = crsFromLaterFeatureSchema()
			File target = new File(tmp.newFolder(), 'features.parquet')
			// no meaningful coordinates in EPSG:25832, so only the spec is checked
			writeInstances(target, schema, firstWithoutCrs(schema), { it.setSort(sort) }, false)
			ExternalValidators.checkGpio(target)

			assertEquals("sort=$sort", 25832, geoMetadata(target).columns.geometry.crs.id.code)
			def rows = readRows(target)
			assertEquals([0, 1, 2], rows.collect { it.getInteger('id', 0) }.sort())
			// the geometry without CRS is written as is
			assertEquals('POINT (8 50)', wkb(rows.find { it.getInteger('id', 0) == 0 }, 'geometry').toText())
		}
	}

	/**
	 * If no geometry has a CRS, the CRS is unknown, but the geometries are
	 * written as GeoParquet (and sorted).
	 */
	@Test
	void testNoGeometryWithCrs() {
		[true, false].each { boolean sort ->
			Schema schema = crsFromLaterFeatureSchema()
			File target = new File(tmp.newFolder(), 'features.parquet')
			IOReport report = writeInstances(target, schema, firstWithoutCrs(schema, false), { it.setSort(sort) }, false)
			ExternalValidators.checkGpio(target)

			def geo = geoMetadata(target)
			assertNotNull("sort=$sort", geo)
			assertTrue(geo.columns.geometry.containsKey('crs'))
			assertNull(geo.columns.geometry.crs)
			assertTrue(report.warnings*.message.toString(), report.warnings.any { it.message.contains('Could not determine the CRS') })
			def rows = readRows(target)
			assertEquals(3, rows.count { it.getFieldRepetitionCount('geometry') > 0 })
			// sorted along the curve, input order in streaming mode
			assertEquals([0, 1, 2], rows.collect { it.getInteger('id', 0) })
		}
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

	/**
	 * Instance collection that cannot resolve some references, or provides no
	 * reference for some instances.
	 */
	static class UnresolvableCollection implements InstanceCollection {

		@Delegate
		InstanceCollection delegate

		/** Names of the cities for which getInstance yields null. */
		Set<String> unresolvable = [] as Set

		/** Names of the cities for which getReference yields null. */
		Set<String> noReference = [] as Set

		private static String name(Instance instance) {
			instance.getProperty(new QName('name'))[0]
		}

		@Override
		InstanceReference getReference(Instance instance) {
			name(instance) in noReference ? null : delegate.getReference(instance)
		}

		@Override
		Instance getInstance(InstanceReference reference) {
			Instance instance = delegate.getInstance(reference)
			name(instance) in unresolvable ? null : instance
		}
	}

	/**
	 * Problems converting geometries to the target CRS are reported once per
	 * geometry, also when sorting (the conversion for the sort index does not
	 * report anything).
	 */
	@Test
	void testConversionErrorsReportedOnce() {
		[true, false].each { boolean sort ->
			Schema schema = citySchema()
			InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
				city {
					name 'Darmstadt'
					location(createGeometry('POINT(49.872833 8.651222)', new CodeDefinition('EPSG:4326')))
				}
				city {
					name 'Nowhere'
					// no CRS
					location(new DefaultGeometryProperty(null, new WKTReader().read('POINT(8 50)')))
				}
			}
			File target = new File(tmp.newFolder(), 'cities.parquet')
			def writer = new GeoParquetInstanceWriter()
			writer.setTarget(new FileIOSupplier(target))
			def ss = new DefaultSchemaSpace()
			ss.addSchema(schema)
			writer.setTargetSchema(ss)
			writer.setInstances(instances)
			writer.setTargetCRS(new CodeDefinition('EPSG:25832', true))
			writer.setSort(sort)
			IOReport report = writer.execute(new LogProgressIndicator())

			assertTrue(report.success)
			assertEquals("sort=$sort: ${report.errors*.message}", 1, report.errors.size())
			assertTrue(report.errors[0].message, report.errors[0].message.contains('No source CRS'))
			assertEquals(2, readRows(target).size())
		}
	}

	/**
	 * A geometry whose CRS cannot be transformed to the column CRS is not written
	 * (its source coordinates would be wrong in the column CRS): the geometry of
	 * that row is null, the failure is reported (once) and the file is valid.
	 * Applies to a column CRS determined from the data as well as to an explicit
	 * target CRS.
	 */
	@Test
	void testUntransformableGeometryNotWritten() {
		def local = org.geotools.referencing.crs.DefaultEngineeringCRS.CARTESIAN_2D
		def localCrs = new eu.esdihumboldt.hale.common.instance.geometry.impl.WKTDefinition(local.toWKT(), local)
		[
			null,
			new CodeDefinition('EPSG:25832', true)
		].each { CRSDefinition targetCrs ->
			[true, false].each { boolean sort ->
				def label = "sort=$sort, target=${targetCrs?.code}"
				Schema schema = crsFromLaterFeatureSchema()
				InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
					feature {
						id(0)
						geom(createGeometry('POINT(8 50)', new CodeDefinition('EPSG:4326', true)))
					}
					feature {
						id(1)
						geom(new DefaultGeometryProperty(localCrs, new WKTReader().read('POINT(1000 2000)')))
					}
					feature {
						id(2)
						geom(createGeometry('POINT(9 51)', new CodeDefinition('EPSG:4326', true)))
					}
				}
				File target = new File(tmp.newFolder(), 'features.parquet')
				def writer = new GeoParquetInstanceWriter()
				writer.setTarget(new FileIOSupplier(target))
				def ss = new DefaultSchemaSpace()
				ss.addSchema(schema)
				writer.setTargetSchema(ss)
				writer.setInstances(instances)
				if (targetCrs != null) {
					writer.setTargetCRS(targetCrs)
				}
				writer.setSort(sort)
				IOReport report = writer.execute(new LogProgressIndicator())

				assertTrue(label, report.success)
				assertEquals("$label: ${report.errors*.message}", 1, report.errors.size())
				assertTrue(report.errors[0].message, report.errors[0].message.contains('transformation'))

				def rows = readRows(target)
				assertEquals(label, [0, 1, 2], rows.collect { it.getInteger('id', 0) }.sort())
				def failed = rows.find { it.getInteger('id', 0) == 1 }
				assertEquals(label, 0, failed.getFieldRepetitionCount('geometry'))
				assertEquals(label, 2, rows.count { it.getFieldRepetitionCount('geometry') > 0 })
				if (sort) {
					// sorted like an instance without geometry (last)
					assertEquals(label, 1, rows.last().getInteger('id', 0))
				}

				// the bbox does not include the source coordinates
				def bbox = geoMetadata(target).columns.geometry.bbox
				if (targetCrs == null) {
					assertEquals(label, [8d, 50d, 9d, 51d], bbox*.doubleValue())
				}
				else {
					assertTrue(label + ': ' + bbox, bbox[0] > 400000d)
				}
				GeoParquetCompliance.check(target, 150000, true)
				ExternalValidators.check(target)
			}
		}
	}

	/**
	 * A reference that cannot be resolved (or an instance without reference) is
	 * reported as error, the instance is skipped and the export continues.
	 */
	@Test
	void testUnresolvableReference() {
		Schema schema = citySchema()
		def instances = new UnresolvableCollection(delegate: cities(schema),
		unresolvable: ['München'] as Set)
		File target = new File(tmp.newFolder(), 'cities.parquet')
		def writer = new GeoParquetInstanceWriter()
		writer.setTarget(new FileIOSupplier(target))
		def ss = new DefaultSchemaSpace()
		ss.addSchema(schema)
		writer.setTargetSchema(ss)
		writer.setInstances(instances)
		IOReport report = writer.execute(new LogProgressIndicator())

		assertTrue(report.success)
		assertEquals(report.errors*.message.toString(), 1, report.errors.size())
		assertTrue(report.errors[0].message, report.errors[0].message.contains('city'))
		def rows = readRows(target)
		assertEquals(['Darmstadt'], rows*.getString('name', 0))
		GeoParquetCompliance.check(target, 150000, true)

		// no reference in the first pass
		instances = new UnresolvableCollection(delegate: cities(schema),
		noReference: ['Darmstadt'] as Set)
		target = new File(tmp.newFolder(), 'cities.parquet')
		writer = new GeoParquetInstanceWriter()
		writer.setTarget(new FileIOSupplier(target))
		writer.setTargetSchema(ss)
		writer.setInstances(instances)
		report = writer.execute(new LogProgressIndicator())

		assertTrue(report.success)
		assertEquals(report.errors*.message.toString(), 1, report.errors.size())
		assertTrue(report.errors[0].message, report.errors[0].message.contains('city'))
		assertEquals(['München'], readRows(target)*.getString('name', 0))
	}

	/** Custom transverse mercator projection without EPSG code. */
	private static final String CUSTOM_TM_WKT = 'PROJCS["Custom TM",GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]],PROJECTION["Transverse_Mercator"],PARAMETER["latitude_of_origin",0],PARAMETER["central_meridian",9.5],PARAMETER["scale_factor",0.9996],PARAMETER["false_easting",600000],PARAMETER["false_northing",0],UNIT["metre",1],AXIS["Easting",EAST],AXIS["Northing",NORTH]]'

	/**
	 * A target CRS without EPSG code takes precedence over the CRS of the
	 * geometries: geometries are converted to it and the CRS is unknown in the
	 * metadata (with a warning).
	 */
	@Test
	void testTargetCrsWithoutEpsgCode() {
		[true, false].each { boolean sort ->
			Schema schema = citySchema()
			File target = new File(tmp.newFolder(), 'cities.parquet')
			IOReport report = writeInstances(target, schema, cities(schema), {
				it.setTargetCRS(new eu.esdihumboldt.hale.common.instance.geometry.impl.WKTDefinition(CUSTOM_TM_WKT, null))
				it.setSort(sort)
			}, false)
			// Portolan rejects an unknown CRS with coordinates outside the lon/lat
			// range (PRTLN-CNV004, it assumes WGS 84), so only the spec is checked
			ExternalValidators.checkGpio(target)
			def darmstadt = readRows(target).find { it.getString('name', 0) == 'Darmstadt' }
			def point = wkb(darmstadt, 'geometry')
			// easting/northing in the custom projection (not lon/lat)
			assertEquals(540000d, point.x, 20000d)
			assertEquals(5.53e6d, point.y, 50000d)

			def geo = geoMetadata(target)
			assertTrue(geo.columns.geometry.containsKey('crs'))
			assertNull(geo.columns.geometry.crs)
			def logical = readFooter(target).fileMetaData.schema.getType('geometry').logicalTypeAnnotation
			assertEquals('srid:0', logical.crs)
			assertTrue(report.warnings*.message.toString(), report.warnings.any { it.message.contains('EPSG code') })
		}
	}

	/**
	 * Measure (M) values are reported once per column, also if the geometries
	 * are transformed to another CRS (which drops the measures).
	 */
	@Test
	void testMeasuresAreReported() {
		[true, false].each { boolean sort ->
			Schema schema = citySchema()
			def factory = new org.locationtech.jts.geom.GeometryFactory()
			def utm = new CodeDefinition('EPSG:25832', true)
			def instances = new InstanceBuilder(types: schema).createCollection {
				city {
					name 'a'
					location(new eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty(utm,
							factory.createPoint(new org.locationtech.jts.geom.CoordinateXYZM(400000, 5500000, 100, 7))))
				}
				city {
					name 'b'
					location(new eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty(utm,
							factory.createPoint(new org.locationtech.jts.geom.CoordinateXYM(410000, 5510000, 8))))
				}
			}
			File target = new File(tmp.newFolder(), 'cities.parquet')
			IOReport report = writeInstances(target, schema, instances, { it.setSort(sort) })
			assertEquals(2, readRows(target).size())
			def warnings = report.warnings*.message.findAll { it.contains('(M)') }
			assertEquals(report.warnings*.message.toString(), 1, warnings.size())
			assertTrue(warnings[0], warnings[0].contains('column geometry '))
		}
	}

	/**
	 * Geometries held as collection of geometry properties (as created by hale's
	 * GML reader for some geometry types) and below instances of GML-like geometry
	 * property types: all geometries are combined.
	 */
	@Test
	void testGeometryCollectionsAndPropertyTypes() {
		Schema schema = new SchemaBuilder().schema {
			t {
				id(Integer)
				geometry(GeometryProperty)
				outline {
					LineString(GeometryProperty, cardinality: '0..n')
				}
			}
		}
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			t {
				id 1
				outline {
					LineString(createGeometry('LINESTRING(0 0, 1 1)', new CodeDefinition('EPSG:3857')))
					LineString(createGeometry('LINESTRING(2 2, 3 3)', new CodeDefinition('EPSG:3857')))
				}
			}
		}
		// a collection as value (not converted, as by the instance builder)
		instances.iterator().withCloseable {
			((MutableInstance) it.next()).setProperty(new QName('geometry'), [
				[
					createGeometry('POINT(1 2)', new CodeDefinition('EPSG:3857')),
					createGeometry('POINT(3 4)', new CodeDefinition('EPSG:3857'))
				]
			] as Object[])
		}
		withNewGeoParquet(schema, instances, { List<File> files ->
			def rows = readRows(files[0])
			assertEquals('MULTIPOINT ((1 2), (3 4))', wkb(rows[0], 'geometry').toText())
			assertEquals('MULTILINESTRING ((0 0, 1 1), (2 2, 3 3))', wkb(rows[0], 'outline').toText())
		})
	}

	/**
	 * Geometries in properties written as JSON: only the geometry as WKT, not the
	 * structure of the GML-like geometry element; gml:boundedBy is omitted.
	 */
	@Test
	void testGeometryInJson() {
		String gml = 'http://www.opengis.net/gml/3.2'
		Schema schema = new SchemaBuilder().schema {
			t {
				geom(GeometryProperty)
				info {
					sub {
						boundedBy(namespace: gml) {
							Envelope(GeometryProperty, namespace: gml)
						}
						geometry {
							Point(GeometryProperty, namespace: gml) {
								srsName(String, [
									eu.esdihumboldt.hale.io.xsd.constraint.XmlAttributeFlag.ENABLED
								])
							}
						}
						label(String)
					}
				}
			}
		}
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			t {
				geom(createGeometry('POINT(1 1)', new CodeDefinition('EPSG:3857')))
				info {
					sub {
						boundedBy {
							Envelope(createGeometry('POLYGON((0 0, 0 1, 1 1, 0 0))', new CodeDefinition('EPSG:3857')))
						}
						geometry {
							Point(createGeometry('POINT(5 6)', new CodeDefinition('EPSG:3857'))) {
								srsName 'EPSG:3857'
							}
						}
						label 'x'
					}
				}
			}
		}
		withNewGeoParquet(schema, instances, { List<File> files ->
			def rows = readRows(files[0])
			String json = rows[0].getGroup('info', 0).getString('sub', 0)
			assertEquals('{"geometry":"POINT (5 6)","label":"x"}', json)
		}, { it.setNestingDepth(1) })
	}

	private InstanceCollection citiesWithoutGeometry(Schema schema) {
		new InstanceBuilder(types: schema).createCollection {
			city {
				name 'Darmstadt'
				population 158254
			}
			city {
				name 'München'
				population 1471508
			}
		}
	}

	/**
	 * A type with geometry property, but without any geometry values: Portolan
	 * does not accept a GeoParquet file without geometries (no column bbox), so
	 * when sorting (emptiness known after the first pass) a plain Parquet file is
	 * written, with a warning.
	 */
	@Test
	void testGeometryColumnWithoutValuesSorted() {
		Schema schema = citySchema()
		File target = new File(tmp.newFolder(), 'cities.parquet')
		// includes the external validators (skipped for plain Parquet)
		IOReport report = writeInstances(target, schema, citiesWithoutGeometry(schema))

		assertNull(geoMetadata(target))
		def parquetSchema = readFooter(target).fileMetaData.schema
		// data is kept, geometry column as plain binary, no bbox column
		assertEquals([
			'name',
			'population',
			'geometry'
		], parquetSchema.fields*.name)
		assertNull(parquetSchema.getType('geometry').logicalTypeAnnotation)
		assertEquals(['Darmstadt', 'München'], readRows(target)*.getString('name', 0))
		assertTrue(report.warnings*.message.toString(), report.warnings.any { it.message.contains('plain Parquet') })
	}

	/**
	 * In streaming mode a type for which no geometry was found until the end of
	 * the input (all instances buffered) is written as plain Parquet as well.
	 */
	@Test
	void testGeometryColumnWithoutValuesStreaming() {
		Schema schema = citySchema()
		File target = new File(tmp.newFolder(), 'cities.parquet')
		// includes the external validators (skipped for plain Parquet)
		IOReport report = writeInstances(target, schema, citiesWithoutGeometry(schema), { it.setSort(false) })

		assertNull(geoMetadata(target))
		def parquetSchema = readFooter(target).fileMetaData.schema
		assertEquals([
			'name',
			'population',
			'geometry'
		], parquetSchema.fields*.name)
		assertNull(parquetSchema.getType('geometry').logicalTypeAnnotation)
		// input order
		assertEquals(['Darmstadt', 'München'], readRows(target)*.getString('name', 0))
		assertTrue(report.warnings*.message.toString(), report.warnings.any { it.message.contains('plain Parquet') })
	}

	/**
	 * Streaming: more leading instances without geometry than can be buffered.
	 * The file is opened when the buffer is full, before the CRS is known: the
	 * CRS is unknown (with a warning) and later geometries are written as they
	 * are. Input order is preserved.
	 */
	@Test
	void testStreamingBufferFull() {
		Schema schema = crsFromLaterFeatureSchema()
		int leading = 1005
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			(0..<leading).each { int i ->
				feature { id(i) }
			}
			feature {
				id(leading)
				geom(createGeometry('POINT(8 50)', new CodeDefinition('EPSG:4326', true)))
			}
			feature {
				id(leading + 1)
				geom(createGeometry('POINT(9 51)', new CodeDefinition('EPSG:4326', true)))
			}
		}
		File target = new File(tmp.newFolder(), 'features.parquet')
		// spatial ordering cannot be evaluated by Portolan with mostly empty geometries (info)
		IOReport report = writeInstances(target, schema, instances, { it.setSort(false) }, true, ['PTL-DAT-006'])

		def rows = readRows(target)
		assertEquals((0..(leading + 1)).toList(), rows.collect { it.getInteger('id', 0) })
		def geo = geoMetadata(target)
		assertTrue(geo.columns.geometry.containsKey('crs'))
		assertNull(geo.columns.geometry.crs)
		assertTrue(report.warnings*.message.toString(), report.warnings.any { it.message.contains('Could not determine the CRS') })
		assertEquals('POINT (8 50)', wkb(rows[leading], 'geometry').toText())
		assertEquals('POINT (9 51)', wkb(rows[leading + 1], 'geometry').toText())
	}

	/**
	 * Streaming with interleaved types: each type is buffered until its first
	 * geometry independently of the others, a type still pending when the input
	 * ends is written at the end (plain Parquet, as no geometry was seen).
	 */
	@Test
	void testStreamingInterleavedTypes() {
		Schema schema = new SchemaBuilder().schema {
			a {
				id(Integer)
				geom(GeometryProperty)
			}
			b {
				id(Integer)
				geom(GeometryProperty)
			}
			c {
				id(Integer)
				geom(GeometryProperty)
			}
		}
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			a { id(1) }
			c { id(1) }
			b { id(1) }
			a {
				id(2)
				geom(createGeometry('POINT(400000 5800000)', new CodeDefinition('EPSG:25832', true)))
			}
			b { id(2) }
			c { id(2) }
			a { id(3) }
			b {
				id(3)
				geom(createGeometry('POINT(1000000 6600000)', new CodeDefinition('EPSG:3857', true)))
			}
			a {
				id(4)
				geom(createGeometry('POINT(410000 5810000)', new CodeDefinition('EPSG:25832', true)))
			}
			b { id(4) }
			c { id(3) }
		}
		File dir = tmp.newFolder()
		IOReport report = writeInstances(new File(dir, 'out.parquet'), schema, instances, { it.setSort(false) }, false)

		File a = new File(dir, 'out_a.parquet')
		File b = new File(dir, 'out_b.parquet')
		File c = new File(dir, 'out_c.parquet')
		// input order per type
		assertEquals([1, 2, 3, 4], readRows(a).collect { it.getInteger('id', 0) })
		assertEquals([1, 2, 3, 4], readRows(b).collect { it.getInteger('id', 0) })
		assertEquals([1, 2, 3], readRows(c).collect { it.getInteger('id', 0) })
		// CRS of the first geometry of each type
		assertEquals(25832, geoMetadata(a).columns.geometry.crs.id.code)
		assertEquals(3857, geoMetadata(b).columns.geometry.crs.id.code)
		assertEquals('POINT (400000 5800000)', wkb(readRows(a)[1], 'geometry').toText())
		assertEquals('POINT (1000000 6600000)', wkb(readRows(b)[2], 'geometry').toText())
		[a, b].each { ExternalValidators.check(it) }

		// pending until the end: no geometry, plain Parquet (see
		// testGeometryColumnWithoutValuesStreaming)
		assertNull(geoMetadata(c))
		assertTrue(report.warnings*.message.toString(), report.warnings.any { it.message.contains('plain Parquet') && it.message.contains(' c ') })
		ExternalValidators.check(c)
	}
}
