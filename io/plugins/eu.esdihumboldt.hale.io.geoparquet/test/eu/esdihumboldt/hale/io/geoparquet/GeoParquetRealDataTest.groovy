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

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import javax.xml.namespace.QName

import org.apache.parquet.example.data.Group
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.PrimitiveType
import org.junit.Test
import org.locationtech.jts.geom.Geometry

import eu.esdihumboldt.hale.common.core.io.impl.LogProgressIndicator
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.schema.SchemaSpaceID
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.io.geopackage.GeopackageInstanceReader
import eu.esdihumboldt.hale.io.geopackage.GeopackageSchemaReader
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * End-to-end tests writing real-world data from GeoPackage files (test data of
 * the GeoPackage plugin) to GeoParquet.
 */
class GeoParquetRealDataTest extends AbstractPlatformTest {

	private static final int SAMPLE_SIZE = 20

	private static File gpkg(String name) {
		String dir = System.getProperty('geoparquet.test.gpkgDir')
		assertNotNull('System property geoparquet.test.gpkgDir is not set', dir)
		File file = new File(dir, name)
		assertTrue("Missing test data ${file}", file.exists())
		file
	}

	// the GeoPackage test helpers are not available (test source set of the other plugin)

	private static Schema loadSchema(File file) {
		def reader = new GeopackageSchemaReader()
		reader.setSchemaSpace(SchemaSpaceID.SOURCE)
		reader.setSource(new FileIOSupplier(file))
		def report = reader.execute(new LogProgressIndicator())
		assertTrue(report.isSuccess())
		assertTrue(report.errors.isEmpty())
		reader.schema
	}

	private static InstanceCollection loadInstances(Schema schema, File file) {
		def reader = new GeopackageInstanceReader()
		reader.setSource(new FileIOSupplier(file))
		reader.setSourceSchema(schema)
		def report = reader.execute(new LogProgressIndicator())
		assertTrue(report.isSuccess())
		assertTrue(report.errors.isEmpty())
		reader.instances
	}

	/**
	 * Value of a primitive column in a form that can be compared to source values.
	 */
	private static Object parquetValue(Group row, String column) {
		if (row.getFieldRepetitionCount(column) == 0) {
			return null
		}
		PrimitiveType type = row.type.getType(column).asPrimitiveType()
		def logical = type.logicalTypeAnnotation
		switch (type.primitiveTypeName) {
			case PrimitiveType.PrimitiveTypeName.BOOLEAN: return row.getBoolean(column, 0)
			case PrimitiveType.PrimitiveTypeName.INT32:
				return logical instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation ?
				LocalDate.ofEpochDay(row.getInteger(column, 0)) : row.getInteger(column, 0)
			case PrimitiveType.PrimitiveTypeName.INT64:
				return logical instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation ?
				Instant.ofEpochMilli(row.getLong(column, 0)) : row.getLong(column, 0)
			case PrimitiveType.PrimitiveTypeName.FLOAT: return row.getFloat(column, 0)
			case PrimitiveType.PrimitiveTypeName.DOUBLE: return row.getDouble(column, 0)
			default: return row.getString(column, 0)
		}
	}

	private static void assertSameValue(String message, Object source, Object written) {
		if (source == null) {
			assertNull(message, written)
		}
		else if (source instanceof Number) {
			assertTrue("$message: expected $source but was $written", written instanceof Number)
			assertEquals(message, ((Number) source).doubleValue(), ((Number) written).doubleValue(), 1e-9)
		}
		else if (source instanceof Date) {
			assertEquals(message, ((Date) source).time, toMillis(written))
		}
		else if (source instanceof java.time.temporal.TemporalAccessor || source instanceof java.util.Calendar) {
			assertEquals(message, toMillis(source), toMillis(written))
		}
		else if (source instanceof Boolean) {
			assertEquals(message, source, written)
		}
		else {
			assertEquals(message, source.toString(), written)
		}
	}

	private static long toMillis(Object value) {
		switch (value) {
			case Date: return ((Date) value).time
			case java.util.Calendar: return ((Calendar) value).timeInMillis
			case Instant: return ((Instant) value).toEpochMilli()
			case LocalDate: return ((LocalDate) value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
			case java.time.LocalDateTime: return ((java.time.LocalDateTime) value).toInstant(ZoneOffset.UTC).toEpochMilli()
			case java.time.OffsetDateTime: return ((java.time.OffsetDateTime) value).toInstant().toEpochMilli()
			case java.time.ZonedDateTime: return ((java.time.ZonedDateTime) value).toInstant().toEpochMilli()
			default: fail("Unexpected date type ${value.getClass()}: $value")
		}
	}

	/**
	 * Compare the written file of a type with the source instances: row count,
	 * and for sampled rows (identified by the primary key column) all attribute
	 * values and the geometry.
	 *
	 * @return the number of rows compared
	 */
	private static int verifyType(File file, TypeDefinition type, List<Instance> source, String pk,
			String geometryProperty, int epsg) {
		// the source CRS is kept
		assertEquals(epsg, geoMetadata(file).columns.geometry.crs.id.code)
		List<Group> rows = readRows(file)
		assertEquals("Row count of ${type.name.localPart}", source.size(), rows.size())

		Map<Object, Group> byKey = [:]
		rows.each { Group row ->
			Object key = parquetValue(row, pk)
			assertNull("Duplicate key $key", byKey.put(key, row))
		}

		// deterministic sample of the source instances
		List<Instance> sample = source.size() <= SAMPLE_SIZE ? source :
				(0..<SAMPLE_SIZE).collect { int i -> source[(int) (i * source.size() / SAMPLE_SIZE)] }
		assertTrue(sample.size() >= Math.min(SAMPLE_SIZE, source.size()))

		sample.each { Instance instance ->
			Object key = instance.getProperty(new QName(pk))[0]
			Group row = byKey[key instanceof Number ? ((Number) key).longValue() : key] ?:
					byKey.find { k, v -> k == key || (k instanceof Number && key instanceof Number && k.doubleValue() == key.doubleValue()) }?.value
			assertNotNull("No row for key $key in ${type.name.localPart}", row)

			instance.propertyNames.each { QName name ->
				Object value = instance.getProperty(name)[0]
				if (name.localPart == geometryProperty) {
					Geometry expected = ((GeometryProperty) value).geometry
					Geometry actual = wkb(row, 'geometry')
					assertTrue("Geometry differs for key $key: $expected vs. $actual", expected.equalsExact(actual, 1e-9))
				}
				else {
					assertSameValue("Property ${name.localPart} of key $key", value, parquetValue(row, name.localPart))
				}
			}
		}
		sample.size()
	}

	@Test
	void testNoiseMaps() {
		File gpkg = gpkg('MajorAirports-StrategicNoiseMaps-AT-LOWW.gpkg')
		Schema schema = loadSchema(gpkg)
		InstanceCollection instances = loadInstances(schema, gpkg)

		Map<TypeDefinition, List<Instance>> byType = [:].withDefault { [] }
		instances.iterator().withCloseable {
			it.each { Instance i ->
				byType[i.definition] << i
			}
		}
		// the two feature tables
		def featureTypes = byType.keySet().findAll { it.name.localPart.startsWith('NoiseContours') }
		assertEquals(2, featureTypes.size())

		withNewGeoParquet(schema, instances, { List<File> files ->
			def parquet = files.findAll { it.name.endsWith('.parquet') }
			println "Written files: ${parquet*.name}"
			featureTypes.each { TypeDefinition type ->
				File file = parquet.find { it.name == "out_${type.name.localPart}.parquet" }
				assertNotNull("No file for ${type.name.localPart} in ${files*.name}", file)
				assertTrue(verifyType(file, type, byType[type], 'id', 'location', 3035) > 0)
			}
		})
	}

	@Test
	void testWastewater() {
		File gpkg = gpkg('util_wastewater_discharge.gpkg')
		Schema schema = loadSchema(gpkg)
		InstanceCollection instances = loadInstances(schema, gpkg)
		assertEquals(3193, instances.size())

		List<Instance> all = []
		instances.iterator().withCloseable {
			it.each { Instance i ->
				all << i
			}
		}
		TypeDefinition type = all[0].definition

		long zstd, none
		withNewGeoParquet(schema, instances, { List<File> files ->
			assertEquals(1, files.size())
			assertEquals(3193, verifyType(files[0], type, all, 'fid', 'geom', 26915) > 0 ? readRows(files[0]).size() : -1)
			zstd = files[0].length()
		})
		withNewGeoParquet(schema, instances, { List<File> files ->
			none = files[0].length()
			assertEquals('UNCOMPRESSED', readFooter(files[0]).blocks[0].columns[0].codec.name())
		}, { it.setCompression('none') }, false)
		println "wastewater: zstd=$zstd bytes, none=$none bytes"
		assertTrue("ZSTD ($zstd) must be smaller than uncompressed ($none)", zstd < none)
	}
}
