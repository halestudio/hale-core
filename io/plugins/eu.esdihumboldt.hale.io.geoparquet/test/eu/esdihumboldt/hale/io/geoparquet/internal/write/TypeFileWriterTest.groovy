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

import static eu.esdihumboldt.hale.io.geoparquet.GeoParquetTestUtil.*
import static org.junit.Assert.*

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

import org.apache.parquet.example.data.Group
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.apache.parquet.hadoop.metadata.ParquetMetadata
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateXYM
import org.locationtech.jts.geom.CoordinateXYZM
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.impl.PackedCoordinateSequence
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.io.WKTWriter

import eu.esdihumboldt.cst.functions.geometric.GeometryHelperFunctions
import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.DefinitionUtil
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultPropertyDefinition
import eu.esdihumboldt.hale.io.geoparquet.BigNumberEncoding
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetCompliance
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetTestUtil.CollectingLog
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetVersion
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.MappingOptions
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ParquetSchemaMapper
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.TypeMapping
import eu.esdihumboldt.hale.io.xsd.constraint.XmlAttributeFlag
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for writing instances with {@link TypeFileWriter}.
 */
class TypeFileWriterTest extends AbstractPlatformTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder()

	/** Returns geometries unchanged. */
	static final GeometryConverter IDENTITY = { Object value, GeoParquetCrs target, SimpleLog log ->
		value instanceof GeometryProperty ? ((GeometryProperty) value).geometry : (Geometry) value
	} as GeometryConverter

	static final GeoParquetCrs UTM = GeoParquetCrs.resolve(new CodeDefinition('EPSG:25832', true), SimpleLog.NO_LOG)

	static GeometryProperty createGeom(String wkt, String crs = 'EPSG:25832') {
		GeometryHelperFunctions._with([geometry: wkt, crs: new CodeDefinition(crs, true)])
	}

	private TypeDefinition type(Schema schema, String name) {
		schema.mappingRelevantTypes.find { it.name.localPart == name }
	}

	private List<Instance> instances(InstanceCollection collection) {
		def result = []
		def it = collection.iterator()
		try {
			while (it.hasNext()) {
				result << it.next()
			}
		} finally {
			it.close()
		}
		result
	}

	/**
	 * Write instances of a type and return the file.
	 */
	private File write(Schema schema, String typeName, InstanceCollection collection,
			Map<String, GeoParquetCrs> crs = [:], Map args = [:]) {
		def log = args.log ?: SimpleLog.NO_LOG
		def options = args.options ?: MappingOptions.defaults()
		def version = args.version ?: GeoParquetVersion.V2_0
		options = new MappingOptions(version, options.nestingDepth(), options.bigNumberEncoding(), options.decimalScale())
		def mapping = new ParquetSchemaMapper(options).map(type(args.mappingSchema ?: schema, typeName), crs, log)
		def file = tmp.newFile()
		def writer = new TypeFileWriter(file.toPath(), mapping, version, crs, args.codec ?: CompressionCodecName.ZSTD,
				args.rowGroupLimit ?: 150000, args.bbox2d ?: false, args.converter ?: IDENTITY, log)
		try {
			instances(collection).each { writer.write(it) }
			assertEquals(instances(collection).size(), writer.rowCount)
		} finally {
			writer.close()
		}
		file
	}

	@Test
	void testWriteSimpleValues() {
		Schema schema = new SchemaBuilder().schema {
			t {
				a(String)
				b(Double)
				c(Long)
				d(Integer)
				e(Boolean)
				f(BigInteger)
				g(BigDecimal)
				h(LocalDate)
				i(Instant)
				j(byte[])
				k(LocalTime)
				l(Float)
			}
		}
		def big = new BigInteger(Long.MAX_VALUE.toString()) + 1
		def dec = new BigDecimal('1098491071975459529.6201509049614540479')
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				a('text')
				b(1.5d)
				c(Long.MAX_VALUE)
				d(42)
				e(true)
				f(big)
				g(dec)
				h(LocalDate.of(2024, 2, 29))
				i(Instant.parse('2024-02-29T12:34:56.789Z'))
				j([1, 2, 3] as byte[])
				k(LocalTime.of(13, 45, 30))
				l(2.5f)
			}
			t {
				a('only a')
			}
		}
		def file = write(schema, 't', collection)
		def rows = readRows(file)
		assertEquals(2, rows.size())

		def row = rows[0]
		assertEquals('text', row.getString('a', 0))
		assertEquals(1.5d, row.getDouble('b', 0), 0)
		assertEquals(Long.MAX_VALUE, row.getLong('c', 0))
		assertEquals(42, row.getInteger('d', 0))
		assertTrue(row.getBoolean('e', 0))
		assertEquals('9223372036854775808', row.getString('f', 0))
		assertEquals(dec.toPlainString(), row.getString('g', 0))
		assertEquals(LocalDate.of(2024, 2, 29).toEpochDay() as int, row.getInteger('h', 0))
		assertEquals(Instant.parse('2024-02-29T12:34:56.789Z').toEpochMilli(), row.getLong('i', 0))
		assertArrayEquals([1, 2, 3] as byte[], row.getBinary('j', 0).bytes)
		assertEquals(LocalTime.of(13, 45, 30).toSecondOfDay() * 1000, row.getInteger('k', 0))
		assertEquals(2.5f, row.getFloat('l', 0), 0)

		// nulls
		def second = rows[1]
		assertEquals('only a', second.getString('a', 0))
		[
			'b',
			'c',
			'd',
			'e',
			'f',
			'g',
			'h',
			'i',
			'j',
			'k',
			'l'
		].each {
			assertEquals(it, 0, second.getFieldRepetitionCount(it))
		}
	}

	@Test
	void testTimeIsNotUtcAdjusted() {
		Schema schema = new SchemaBuilder().schema {
			t { k(LocalTime) }
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t { k(LocalTime.of(14, 0, 1, 234_000_000)) }
		}
		def file = write(schema, 't', collection)
		def annotation = readFooter(file).fileMetaData.schema.getType('k').logicalTypeAnnotation
		assertEquals(LogicalTypeAnnotation.timeType(false,
				LogicalTypeAnnotation.TimeUnit.MILLIS), annotation)
		assertFalse(annotation.isAdjustedToUTC())
		assertEquals(50401234, readRows(file)[0].getInteger('k', 0))
	}

	@Test
	void testSqlTimeKeepsMillis() {
		Schema schema = new SchemaBuilder().schema {
			t { k(java.sql.Time) }
		}
		def times = [
			new java.sql.Time(java.sql.Time.valueOf(LocalTime.of(10, 20, 30)).time + 456),
			// negative epoch millis
			new java.sql.Time(-1500L)
		]
		def collection = new InstanceBuilder(types: schema).createCollection {
			times.each { tm ->
				t {
					k(tm)
				}
			}
		}
		def file = write(schema, 't', collection)
		def rows = readRows(file)
		times.eachWithIndex { tm, i ->
			def expected = tm.toLocalTime().plusNanos(Math.floorMod(tm.time, 1000L) * 1_000_000L)
			assertEquals(expected.toNanoOfDay().intdiv(1_000_000L) as int, rows[i].getInteger('k', 0))
		}
		assertEquals(LocalTime.of(10, 20, 30, 456_000_000).toNanoOfDay().intdiv(1_000_000L) as int,
				rows[0].getInteger('k', 0))
	}

	@Test
	void testTimestampAdjustment() {
		Schema schema = new SchemaBuilder().schema {
			t {
				a(LocalDateTime)
				b(Instant)
				c(Date)
			}
		}
		def ldt = LocalDateTime.of(2024, 3, 1, 12, 30, 15, 123_000_000)
		def instant = Instant.parse('2024-03-01T12:30:15.123Z')
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				a(ldt)
				b(instant)
				c(new Date(instant.toEpochMilli()))
			}
		}
		def file = write(schema, 't', collection)
		def msg = readFooter(file).fileMetaData.schema
		assertEquals(LogicalTypeAnnotation.timestampType(false, TimeUnit.MILLIS), msg.getType('a').logicalTypeAnnotation)
		assertEquals(LogicalTypeAnnotation.timestampType(true, TimeUnit.MILLIS), msg.getType('b').logicalTypeAnnotation)
		assertEquals(LogicalTypeAnnotation.timestampType(true, TimeUnit.MILLIS), msg.getType('c').logicalTypeAnnotation)
		def row = readRows(file)[0]
		assertEquals(ldt.toInstant(ZoneOffset.UTC).toEpochMilli(), row.getLong('a', 0))
		assertEquals(instant.toEpochMilli(), row.getLong('b', 0))
		assertEquals(instant.toEpochMilli(), row.getLong('c', 0))
	}

	@Test
	void testWriteNested() {
		Schema schema = new SchemaBuilder().schema {
			t {
				name(String)
				address {
					street(String)
					number(Integer)
				}
				tags(String, cardinality: '0..n')
				locators(cardinality: '0..n') {
					designator(String)
					type(String)
				}
				height(Double) { nilReason(String, [XmlAttributeFlag.ENABLED]) }
			}
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				name('full')
				address {
					street('Main')
					number(5)
				}
				tags('a')
				tags('b')
				locators {
					designator('1')
					type('x')
				}
				locators { designator('3') }
				height(1.5) { nilReason('none') }
			}
			t { name('empty') }
			t {
				name('partial')
				address { number(7) }
				locators { type('y') }
			}
		}
		def rows = readRows(write(schema, 't', collection))
		assertEquals(3, rows.size())

		def r = rows[0]
		assertEquals('Main', r.getGroup('address', 0).getString('street', 0))
		assertEquals(5, r.getGroup('address', 0).getInteger('number', 0))
		assertEquals(2, r.getGroup('tags', 0).getFieldRepetitionCount('list'))
		assertEquals('a', r.getGroup('tags', 0).getGroup('list', 0).getString('element', 0))
		assertEquals('b', r.getGroup('tags', 0).getGroup('list', 1).getString('element', 0))
		assertEquals(2, r.getGroup('locators', 0).getFieldRepetitionCount('list'))
		assertEquals('1', r.getGroup('locators', 0).getGroup('list', 0).getGroup('element', 0).getString('designator', 0))
		assertEquals('x', r.getGroup('locators', 0).getGroup('list', 0).getGroup('element', 0).getString('type', 0))
		assertEquals('3', r.getGroup('locators', 0).getGroup('list', 1).getGroup('element', 0).getString('designator', 0))
		assertEquals(0, r.getGroup('locators', 0).getGroup('list', 1).getGroup('element', 0).getFieldRepetitionCount('type'))
		// value of complex property
		assertEquals(1.5d, r.getGroup('height', 0).getDouble('value', 0), 0)
		assertEquals('none', r.getGroup('height', 0).getString('nilReason', 0))

		// everything missing
		def e = rows[1]
		[
			'address',
			'tags',
			'locators',
			'height'
		].each {
			assertEquals(it, 0, e.getFieldRepetitionCount(it))
		}

		// nulls inside structs
		def p = rows[2]
		assertEquals(0, p.getGroup('address', 0).getFieldRepetitionCount('street'))
		assertEquals(7, p.getGroup('address', 0).getInteger('number', 0))
		assertEquals(0, p.getFieldRepetitionCount('tags'))
		def loc = p.getGroup('locators', 0).getGroup('list', 0).getGroup('element', 0)
		assertEquals(0, loc.getFieldRepetitionCount('designator'))
		assertEquals('y', loc.getString('type', 0))
	}

	@Test
	void testGroupsAreTransparent() {
		Schema schema = new SchemaBuilder().schema {
			t {
				_ {
					x(String)
					_(choice: true) {
						p(String)
						q(Integer)
					}
				}
			}
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				x('vx')
				q(3)
			}
		}
		def row = readRows(write(schema, 't', collection))[0]
		assertEquals('vx', row.getString('x', 0))
		assertEquals(3, row.getInteger('q', 0))
		assertEquals(0, row.getFieldRepetitionCount('p'))
	}

	@Test
	void testJsonAndWktColumns() {
		Schema schema = new SchemaBuilder().schema {
			Node { name(String) }
		}
		TypeDefinition node = type(schema, 'Node')
		new DefaultPropertyDefinition(new javax.xml.namespace.QName('child'), DefinitionUtil.getDefinitionGroup(node), node)

		def collection = new InstanceBuilder(types: schema).createCollection {
			Node {
				name('parent')
				child { name('nested child') }
			}
		}
		def file = write(schema, 'Node', collection)
		def row = readRows(file)[0]
		assertEquals('parent', row.getString('name', 0))
		def json = new groovy.json.JsonSlurper().parseText(row.getString('child', 0))
		assertEquals('nested child', json.name)

		// nested geometry
		Schema schema2 = new SchemaBuilder().schema {
			t {
				geom(GeometryProperty)
				info {
					label(String)
					outline(GeometryProperty)
				}
			}
		}
		collection = new InstanceBuilder(types: schema2).createCollection {
			t {
				geom(createGeom('POINT (5 6)'))
				info {
					label('l')
					outline(createGeom('POINT (1 2)'))
				}
			}
		}
		row = readRows(write(schema2, 't', collection))[0]
		assertEquals('POINT (1 2)', row.getGroup('info', 0).getString('outline', 0))
		assertEquals('l', row.getGroup('info', 0).getString('label', 0))
	}

	@Test
	void testJsonShapeFollowsCardinality() {
		Schema schema = new SchemaBuilder().schema {
			Node {
				name(String)
				tags(String, cardinality: '0..n')
			}
		}
		TypeDefinition node = type(schema, 'Node')
		new DefaultPropertyDefinition(new javax.xml.namespace.QName('child'), DefinitionUtil.getDefinitionGroup(node), node)
		def moreDef = new DefaultPropertyDefinition(new javax.xml.namespace.QName('more'), DefinitionUtil.getDefinitionGroup(node), node)
		moreDef.setConstraint(eu.esdihumboldt.hale.common.schema.model.constraint.property.Cardinality.get(0, eu.esdihumboldt.hale.common.schema.model.constraint.property.Cardinality.UNBOUNDED))

		def collection = new InstanceBuilder(types: schema).createCollection {
			Node {
				name('p')
				child {
					name('c')
					tags('t')
					child { name('single') }
					more { name('m') }
				}
				more { name('m1') }
			}
		}
		def row = readRows(write(schema, 'Node', collection))[0]
		def slurper = new groovy.json.JsonSlurper()
		// single occurrence: scalar/object, repeated property: array even with one value
		assertEquals([name: 'c', tags: ['t'], child: [name: 'single'], more: [[name: 'm']]],
		slurper.parseText(row.getString('child', 0)))
		def more1 = row.getGroup('more', 0)
		assertEquals(1, more1.getFieldRepetitionCount('list'))
		assertEquals([name: 'm1'], slurper.parseText(more1.getGroup('list', 0).getString('element', 0)))
	}

	@Test
	void testGeometryFailuresAreReportedAndExportContinues() {
		Schema schema = new SchemaBuilder().schema {
			t {
				name(String)
				geom(GeometryProperty)
			}
		}
		def xs = [1, 99, 98, 4]
		def collection = new InstanceBuilder(types: schema).createCollection {
			xs.each { x ->
				t {
					name("n$x")
					geom(createGeom("POINT ($x 0)"))
				}
			}
		}
		def factory = new org.locationtech.jts.geom.GeometryFactory()
		GeometryConverter failing = { Object v, GeoParquetCrs target, SimpleLog l ->
			Geometry g = ((GeometryProperty) v).geometry
			if (g.coordinate.x == 99d) {
				// converter fails
				throw new IllegalStateException('conversion failed')
			}
			if (g.coordinate.x == 98d) {
				// geometry that fails when written
				def seq = [size: { 1 }, getDimension: { 2 }, getMeasures: { 0 }] as org.locationtech.jts.geom.CoordinateSequence
				return factory.createPoint(seq)
			}
			g
		} as GeometryConverter
		def log = new CollectingLog()
		def file = write(schema, 't', collection, [geometry: UTM], [log: log, converter: failing])
		def rows = readRows(file)
		assertEquals(4, rows.size())
		assertEquals(['n1', 'n99', 'n98', 'n4'], rows.collect { it.getString('name', 0) })
		[1, 2].each {
			assertEquals(0, rows[it].getFieldRepetitionCount('geometry'))
			assertEquals(0, rows[it].getFieldRepetitionCount('bbox'))
		}
		assertEquals('POINT (4 0)', wkb(rows[3], 'geometry').toText())
		assertEquals(log.errors.toString(), 2, log.errors.size())
		assertEquals([1d, 0d, 4d, 0d], geoMetadata(file).columns.geometry.bbox*.doubleValue())
	}

	/**
	 * Values written as JSON (beyond the nesting depth) use the same member names
	 * as the struct mapping: the value of an instance is <code>value</code>, a
	 * child with that name is suffixed, properties with the same local name in
	 * different namespaces (also XML attributes) get distinct names. No value is
	 * lost.
	 */
	@Test
	void testJsonNamesFollowStructMapping() {
		Schema schema = new SchemaBuilder().schema {
			t {
				inner {
					box(Double) {
						value(String)
						id(String, [XmlAttributeFlag.ENABLED], namespace: 'urn:a')
						id(Integer)
						name(String, namespace: 'urn:x')
						name(String, namespace: 'urn:y')
					}
				}
			}
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				inner {
					box(1.5) {
						value('child value')
						id('attribute', namespace: 'urn:a')
						id(7)
						name('x', namespace: 'urn:x')
						name('y', namespace: 'urn:y')
					}
				}
			}
		}
		// struct mapping
		def struct = readRows(write(schema, 't', collection))[0].getGroup('inner', 0).getGroup('box', 0)
		def structNames = struct.getType().getFields().collect { it.getName() }
		assertEquals([
			'value',
			'value_2',
			'attr_id',
			'id',
			'name',
			'name_2'
		], structNames)

		// JSON fallback
		def options = new MappingOptions(GeoParquetVersion.V2_0, 1, BigNumberEncoding.STRING, 9)
		def row = readRows(write(schema, 't', collection, [:], [options: options]))[0]
		def json = new groovy.json.JsonSlurper().parseText(row.getGroup('inner', 0).getString('box', 0))
		assertEquals(structNames as Set, json.keySet())
		assertEquals(1.5d, json.value as double, 0d)
		assertEquals('child value', json.value_2)
		assertEquals('attribute', json.attr_id)
		assertEquals(7, json.id)
		assertEquals('x', json.name)
		assertEquals('y', json.name_2)
	}

	@Test
	void testJsonOmitsPrimaryGeometry() {
		Schema schema = new SchemaBuilder().schema {
			t {
				l1 {
					l2 {
						geom(GeometryProperty)
						label(String)
						other(GeometryProperty)
					}
				}
			}
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				l1 {
					l2 {
						geom(createGeom('POINT (5 6)'))
						label('lbl')
						other(createGeom('POINT Z (1 2 3)'))
					}
				}
			}
		}
		def options = new MappingOptions(GeoParquetVersion.V2_0, 1, BigNumberEncoding.STRING, 9)
		def file = write(schema, 't', collection, [:], [options: options])
		def row = readRows(file)[0]
		assertEquals('POINT (5 6)', wkb(row, 'geometry').toText())
		def json = new groovy.json.JsonSlurper().parseText(row.getGroup('l1', 0).getString('l2', 0))
		assertEquals('lbl', json.label)
		assertFalse(json.containsKey('geom'))
		assertEquals('POINT Z(1 2 3)', json.other) // JTS WKT formatting
	}

	@Test
	void testGeometryAndBbox() {
		Schema schema = new SchemaBuilder().schema {
			t {
				name(String)
				geom(GeometryProperty)
			}
		}
		def wkts = [
			'POINT (400000 5500000)',
			'LINESTRING (400000 5500000, 400100 5500300)',
			'POLYGON ((400000 5500000, 400200 5500000, 400200 5500100, 400000 5500000))'
		]
		def collection = new InstanceBuilder(types: schema).createCollection {
			wkts.eachWithIndex { w, i ->
				t {
					name("f$i")
					geom(createGeom(w))
				}
			}
		}
		def crs = [geometry: UTM]
		def file = write(schema, 't', collection, crs)
		def rows = readRows(file)
		assertEquals(3, rows.size())
		def reader = new WKTReader()
		rows.eachWithIndex { row, i ->
			Geometry expected = reader.read(wkts[i])
			Geometry actual = wkb(row, 'geometry')
			assertTrue(expected.equalsExact(actual))
			def env = expected.envelopeInternal
			def bbox = row.getGroup('bbox', 0)
			assertEquals(env.minX, bbox.getDouble('xmin', 0), 0)
			assertEquals(env.minY, bbox.getDouble('ymin', 0), 0)
			assertEquals(env.maxX, bbox.getDouble('xmax', 0), 0)
			assertEquals(env.maxY, bbox.getDouble('ymax', 0), 0)
		}

		ParquetMetadata footer = readFooter(file)
		def columns = footer.blocks[0].columns
		def geomColumn = columns.find { it.path.toDotString() == 'geometry' }
		assertNotNull(geomColumn.geospatialStatistics)
		assertNotNull(geomColumn.geospatialStatistics.boundingBox)
		def xmin = columns.find { it.path.toDotString() == 'bbox.xmin' }
		assertTrue(xmin.statistics.hasNonNullValue())
		assertEquals(400000d, xmin.statistics.genericGetMin(), 0)

		// geo metadata
		def geo = geoMetadata(file)
		assertEquals('2.0.0', geo.version)
		assertEquals('geometry', geo.primary_column)
		assertEquals(25832, geo.columns.geometry.crs.id.code)
		assertEquals([
			'LineString',
			'Point',
			'Polygon'
		], geo.columns.geometry.geometry_types)
		assertEquals([
			400000d,
			5500000d,
			400200d,
			5500300d
		], geo.columns.geometry.bbox*.doubleValue())
		assertEquals(['bbox', 'xmin'], geo.columns.geometry.covering.bbox.xmin)
		assertEquals('EPSG:25832', footer.fileMetaData.schema.getType('geometry').logicalTypeAnnotation.crs)
	}

	@Test
	void testThreeDimensionalGeometryIsIsoWkb() {
		Schema schema = new SchemaBuilder().schema {
			t { geom(GeometryProperty) }
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t { geom(createGeom('POLYGON Z ((0 0 1, 4 0 2, 4 4 3, 0 0 1))')) }
		}
		def file = write(schema, 't', collection, [geometry: UTM])
		def row = readRows(file)[0]
		def bytes = row.getBinary('geometry', 0).bytes
		// ISO type code of Polygon Z
		assertEquals(1003, java.nio.ByteBuffer.wrap(bytes, 1, 4).getInt())
		def g = wkb(row, 'geometry')
		assertEquals(3d, g.coordinates[2].z, 0)
		assertEquals(['Polygon Z'], geoMetadata(file).columns.geometry.geometry_types)
		// 3D bbox: [xmin, ymin, zmin, xmax, ymax, zmax]
		assertEquals([0d, 0d, 1d, 4d, 4d, 3d], geoMetadata(file).columns.geometry.bbox*.doubleValue())
		GeoParquetCompliance.check(file)
	}

	/**
	 * Measure (M) values are not written, geometries with M are written as XY
	 * respectively XYZ; this is reported once per geometry column.
	 */
	@Test
	void testMeasuresAreNotWritten() {
		Schema schema = new SchemaBuilder().schema {
			t {
				a(GeometryProperty)
				b(GeometryProperty)
			}
		}
		def factory = new GeometryFactory()
		def crsDef = new CodeDefinition('EPSG:25832', true)
		def xym = { double x, double y, double m ->
			new DefaultGeometryProperty(crsDef, factory.createPoint(new CoordinateXYM(x, y, m)))
		}
		// XYZM via a packed sequence with dimension 4 and 1 measure
		def xyzm = new DefaultGeometryProperty(crsDef, factory.createLineString(
				new PackedCoordinateSequence.Double([0, 0, 1, 10, 4, 4, 2, 20] as double[], 4, 1)))
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				a(xym(1, 2, 7))
				b(xyzm)
			}
			t {
				a(xym(3, 4, 8))
				b(new DefaultGeometryProperty(crsDef, factory.createLineString(
						[
							new CoordinateXYZM(5, 5, 3, 30),
							new CoordinateXYZM(6, 6, 4, 40)
						] as Coordinate[])))
			}
		}
		def log = new CollectingLog()
		def file = write(schema, 't', collection, [geometry: UTM, b: UTM], [log: log])
		def rows = readRows(file)
		assertEquals('POINT (1 2)', wkb(rows[0], 'geometry').toText())
		assertEquals('POINT (3 4)', wkb(rows[1], 'geometry').toText())
		def wkt3d = new WKTWriter(3)
		assertEquals('LINESTRING Z(0 0 1, 4 4 2)', wkt3d.write(wkb(rows[0], 'b')))
		// ISO type code of LineString Z
		assertEquals(1002, java.nio.ByteBuffer.wrap(rows[0].getBinary('b', 0).bytes, 1, 4).getInt())
		assertEquals('LINESTRING Z(5 5 3, 6 6 4)', wkt3d.write(wkb(rows[1], 'b')))
		def geo = geoMetadata(file)
		assertEquals(['Point'], geo.columns.geometry.geometry_types)
		assertEquals(['LineString Z'], geo.columns.b.geometry_types)
		GeoParquetCompliance.check(file)

		// one warning per column
		assertEquals(log.warnings.toString(), 2, log.warnings.size())
		['geometry', 'b'].each { column ->
			def warnings = log.warnings.findAll { it.contains("column $column ") }
			assertEquals(log.warnings.toString(), 1, warnings.size())
			assertTrue(warnings[0], warnings[0].contains('(M)'))
		}
	}

	@Test
	void testNullAndEmptyGeometry() {
		Schema schema = new SchemaBuilder().schema {
			t {
				name(String)
				geom(GeometryProperty)
			}
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				name('null')
			}
			t {
				name('empty')
				geom(createGeom('POINT EMPTY'))
			}
			t {
				name('a')
				geom(createGeom('POINT (1 2)'))
			}
			t {
				name('b')
				geom(createGeom('POINT (3 4)'))
			}
		}
		def file = write(schema, 't', collection, [geometry: UTM])
		def rows = readRows(file)
		assertEquals(4, rows.size())
		rows[0..1].each {
			assertEquals(0, it.getFieldRepetitionCount('geometry'))
			assertEquals(0, it.getFieldRepetitionCount('bbox'))
		}
		assertEquals('POINT (1 2)', wkb(rows[2], 'geometry').toText())
		assertEquals([1d, 2d, 3d, 4d], geoMetadata(file).columns.geometry.bbox*.doubleValue())
	}

	@Test
	void testSecondaryGeometryColumnAndNoGeometry() {
		Schema schema = new SchemaBuilder().schema {
			t {
				a(GeometryProperty)
				b(GeometryProperty)
			}
			plain { name(String) }
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				a(createGeom('POINT (1 2)'))
				b(createGeom('LINESTRING (0 0, 1 1)'))
			}
			plain { name('x') }
		}
		def file = write(schema, 't', new eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstanceCollection(
				instances(collection).findAll { it.definition.name.localPart == 't' }), [geometry: UTM, b: UTM])
		def row = readRows(file)[0]
		assertEquals('POINT (1 2)', wkb(row, 'geometry').toText())
		assertEquals('LINESTRING (0 0, 1 1)', wkb(row, 'b').toText())
		def geo = geoMetadata(file)
		assertEquals(['geometry', 'b'], geo.columns.keySet() as List)
		assertFalse(geo.columns.b.containsKey('covering'))

		// type without geometry has no geo metadata
		def file2 = write(schema, 'plain', new eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstanceCollection(
				instances(collection).findAll { it.definition.name.localPart == 'plain' }))
		assertNull(geoMetadata(file2))
		assertEquals('x', readRows(file2)[0].getString('name', 0))
	}

	@Test
	void testMultipleGeometryValuesAreCombined() {
		Schema schema = new SchemaBuilder().schema {
			t { geom(GeometryProperty, cardinality: '0..n') }
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				geom(createGeom('POINT (1 2)'))
				geom(createGeom('POINT (3 4)'))
			}
		}
		def log = new CollectingLog()
		def file = write(schema, 't', collection, [geometry: UTM], [log: log])
		def g = wkb(readRows(file)[0], 'geometry')
		assertEquals('MULTIPOINT ((1 2), (3 4))', g.toText())
		assertEquals(1, log.warnings.size())
		assertEquals(['MultiPoint'], geoMetadata(file).columns.geometry.geometry_types)
	}

	@Test
	void testBigNumberValues() {
		Schema schema = new SchemaBuilder().schema {
			t {
				f(BigInteger)
				g(BigDecimal)
			}
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				f(new BigInteger('12345'))
				g(new BigDecimal('1.23'))
			}
			t {
				f(new BigInteger('9' * 39))
				g(new BigDecimal('1.23456'))
			}
			t {
				f(new BigInteger('-2'))
				g(new BigDecimal('-0.00005'))
			}
			t { g(new BigDecimal('7.77777')) }
		}

		// DOUBLE
		def file = write(schema, 't', collection, [:], [options: new MappingOptions(GeoParquetVersion.V2_0, 5, BigNumberEncoding.DOUBLE, 9)])
		def rows = readRows(file)
		assertEquals(1.23d, rows[0].getDouble('g', 0), 0)
		assertEquals(12345d, rows[0].getDouble('f', 0), 0)

		// DECIMAL with scale 4
		def log = new CollectingLog()
		file = write(schema, 't', collection, [:], [log: log, options: new MappingOptions(GeoParquetVersion.V2_0, 5, BigNumberEncoding.DECIMAL, 4)])
		rows = readRows(file)
		def unscaled = { Group row, String col -> new BigInteger(row.getBinary(col, 0).bytes) }
		assertEquals(12300G, unscaled(rows[0], 'g'))
		assertEquals(12345G, unscaled(rows[0], 'f'))
		assertEquals(12346G, unscaled(rows[1], 'g')) // HALF_EVEN
		assertEquals(0, rows[1].getFieldRepetitionCount('f')) // exceeds precision
		assertEquals(-2G, unscaled(rows[2], 'f'))
		assertEquals(0G, unscaled(rows[2], 'g')) // -0.00005 -> 0 (half even)
		assertEquals(77778G, unscaled(rows[3], 'g'))
		assertEquals(1, log.errors.size())
		assertEquals(1, log.warnings.size())
		assertTrue(log.warnings[0], log.warnings[0].contains('g'))
	}

	@Test
	void testConversionErrorIsReported() {
		Schema schema = new SchemaBuilder().schema {
			t {
				a(String)
				n(Integer)
			}
		}
		// instances with a value that does not fit to the mapped type
		Schema valueSchema = new SchemaBuilder().schema {
			t {
				a(String)
				n(String)
			}
		}
		def collection = new InstanceBuilder(types: valueSchema).createCollection {
			t {
				a('kept')
				n('abc')
			}
		}
		def log = new CollectingLog()
		def row = readRows(write(schema, 't', collection, [:], [log: log, mappingSchema: schema]))[0]
		assertEquals('kept', row.getString('a', 0))
		assertEquals(0, row.getFieldRepetitionCount('n'))
		assertEquals(log.errors.toString(), 1, log.errors.size())
	}

	@Test
	void testRowGroupLimit() {
		Schema schema = new SchemaBuilder().schema {
			t { a(Integer) }
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			(0..<25).each { i ->
				t {
					a(i)
				}
			}
		}
		def file = write(schema, 't', collection, [:], [rowGroupLimit: 10])
		assertEquals(3, readFooter(file).blocks.size())
		assertEquals(25, readRows(file).size())
	}

	@Test
	void testCodecs() {
		Schema schema = new SchemaBuilder().schema {
			t {
				a(String)
				geom(GeometryProperty)
			}
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			(0..<20).each { i ->
				t {
					a("value $i")
					geom(createGeom("POINT ($i $i)"))
				}
			}
		}
		[
			CompressionCodecName.ZSTD,
			CompressionCodecName.SNAPPY,
			CompressionCodecName.GZIP,
			CompressionCodecName.UNCOMPRESSED
		].each { codec ->
			def file = write(schema, 't', collection, [geometry: UTM], [codec: codec])
			readFooter(file).blocks.each { block ->
				block.columns.each { assertEquals(codec, it.codec) }
			}
			def rows = readRows(file)
			assertEquals(20, rows.size())
			assertEquals('value 7', rows[7].getString('a', 0))
			assertEquals('POINT (7 7)', wkb(rows[7], 'geometry').toText())
		}
	}

	@Test
	void testOverwritesExistingFile() {
		Schema schema = new SchemaBuilder().schema {
			t { a(Integer) }
		}
		def collection = new InstanceBuilder(types: schema).createCollection {
			t {
				a(1)
			}
		}
		def mapping = new ParquetSchemaMapper(MappingOptions.defaults()).map(type(schema, 't'), [:], SimpleLog.NO_LOG)
		def file = tmp.newFile()
		file.text = 'existing content'
		def writer = new TypeFileWriter(file.toPath(), mapping, GeoParquetVersion.V2_0, [:], CompressionCodecName.ZSTD, 100, false, IDENTITY, SimpleLog.NO_LOG)
		instances(collection).each { writer.write(it) }
		writer.close()
		assertEquals(1, readRows(file).size())
	}
}
