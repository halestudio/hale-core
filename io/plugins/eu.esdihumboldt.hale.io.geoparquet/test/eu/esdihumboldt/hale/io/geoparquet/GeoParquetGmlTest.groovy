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

import org.apache.parquet.example.data.Group
import org.apache.parquet.schema.GroupType
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.Type
import org.junit.Test
import org.locationtech.jts.geom.Geometry

import eu.esdihumboldt.hale.common.core.io.impl.LogProgressIndicator
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier
import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.instance.geometry.GeometryUtil
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.MappingOptions
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ParquetSchemaMapper
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.TypeMapping
import eu.esdihumboldt.hale.io.gml.reader.internal.GmlInstanceReader
import eu.esdihumboldt.hale.io.xsd.reader.XmlSchemaReader
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * End-to-end tests with GML application schemas and GML data read with hale's
 * XML schema and GML readers (test data of the GML and the instance model
 * plugins). In these schemas geometry properties have GML property types (e.g.
 * <code>gml:GeometryPropertyType</code>) that contain the actual geometry
 * elements, and features inherit <code>gml:boundedBy</code>.
 */
class GeoParquetGmlTest extends AbstractPlatformTest {

	private static File dataFile(String property, String path) {
		String dir = System.getProperty(property)
		assertNotNull("System property ${property} is not set", dir)
		File file = new File(dir, path)
		assertTrue("Missing test data ${file}", file.exists())
		file
	}

	private static Schema loadSchema(File xsd) {
		def reader = new XmlSchemaReader()
		reader.setSharedTypes(null)
		reader.setSource(new DefaultInputSupplier(xsd.toURI()))
		def report = reader.execute(new LogProgressIndicator())
		assertTrue(report.errors*.message.toString(), report.isSuccess())
		reader.schema
	}

	private static InstanceCollection loadGml(Schema schema, File gml) {
		def reader = new GmlInstanceReader()
		reader.setSource(new DefaultInputSupplier(gml.toURI()))
		reader.setSourceSchema(schema)
		def report = reader.execute(new LogProgressIndicator())
		assertTrue(report.errors*.message.toString(), report.isSuccess())
		reader.instances
	}

	private static List<Instance> toList(InstanceCollection instances) {
		List<Instance> result = []
		instances.iterator().withCloseable {
			it.each { Instance i ->
				result << i
			}
		}
		result
	}

	private static TypeDefinition type(Schema schema, String name) {
		def type = schema.mappingRelevantTypes.find { it.name.localPart == name }
		assertNotNull("Type ${name} not found", type)
		type
	}

	/**
	 * Assert that no field (at any nesting level) is named <code>boundedBy</code>
	 * and that the given fields are WKB geometry columns (not structs).
	 */
	private static void assertGeometryFields(MessageType schema, List<String> geometryColumns) {
		assertNoField(schema, 'boundedBy')
		geometryColumns.each { String name ->
			assertTrue("No field ${name} in ${schema}", schema.containsField(name))
			assertTrue("Field ${name} is no geometry column: ${schema.getType(name)}", schema.getType(name).isPrimitive())
		}
		// no suffixed duplicates of the geometry column
		assertFalse(schema.toString(), schema.containsField('geometry_2'))
	}

	private static void assertNoField(GroupType group, String name) {
		group.fields.each { Type field ->
			assertNotEquals("Unexpected field ${name} in ${group}", name, field.name)
			if (!field.isPrimitive()) {
				assertNoField(field.asGroupType(), name)
			}
		}
	}

	private static Geometry sourceGeometry(Instance instance, String property) {
		def geometries = GeometryUtil.getGeometries(instance, [
			new QName(instance.definition.name.namespaceURI, property)
		])
		assertEquals(1, geometries.size())
		geometries[0].geometry
	}

	@Test
	void testMappingGml32PropertyType() {
		Schema schema = loadSchema(dataFile('geoparquet.test.gmlDir', 'geom_schema/geom-gml32.xsd'))
		TypeMapping m = new ParquetSchemaMapper(MappingOptions.defaults()).map(type(schema, 'PrimitiveTestType'), [:], SimpleLog.NO_LOG)

		// the property, not an element inside its choice
		assertEquals(['geometry'], m.primaryGeometryPath*.localPart)
		assertGeometryFields(m.schema, ['geometry'])
		assertEquals(['geometry'], m.geometryColumns)
	}

	@Test
	void testHydroGml2() {
		Schema schema = loadSchema(dataFile('geoparquet.test.gmlDir', 'hydro/hydroEx.xsd'))
		InstanceCollection instances = loadGml(schema, dataFile('geoparquet.test.gmlDir', 'hydro/hydro.gml'))
		List<Instance> source = toList(instances)
		assertFalse(source.empty)

		withNewGeoParquet(schema, instances, { List<File> files ->
			assertEquals(1, files.size())
			File file = files[0]
			MessageType parquetSchema = readFooter(file).fileMetaData.schema
			assertGeometryFields(parquetSchema, ['geometry'])

			Map geo = geoMetadata(file)
			assertEquals('geometry', geo.primary_column)
			assertEquals(['LineString'], geo.columns.geometry.geometry_types)
			assertEquals(27700, geo.columns.geometry.crs.id.code)

			List<Group> rows = readRows(file)
			assertEquals(source.size(), rows.size())
			Map<String, Group> byId = rows.collectEntries { Group row -> [(row.getString('fid', 0)): row] }
			source.each { Instance instance ->
				String id = instance.getProperty(new QName('fid'))[0]
				Group row = byId[id]
				assertNotNull("No row for ${id}", row)
				Geometry expected = sourceGeometry(instance, 'geometry')
				Geometry actual = wkb(row, 'geometry')
				assertTrue("Geometry of ${id} differs: ${expected} vs. ${actual}", expected.equalsExact(actual, 1e-9))
			}
		})
	}

	@Test
	void testInspireHydroPhysicalWaters() {
		Schema schema = loadSchema(dataFile('geoparquet.test.instanceDataDir', 'erm/inspire3/HydroPhysicalWaters.xsd'))
		InstanceCollection instances = loadGml(schema, dataFile('geoparquet.test.instanceDataDir', 'out/transformWrite_ERM_HPW.gml'))
		List<Instance> source = toList(instances)
		assertFalse(source.empty)
		assertTrue(source.every { it.definition.name.localPart == 'WatercourseType' })

		withNewGeoParquet(schema, instances, { List<File> files ->
			assertEquals(1, files.size())
			File file = files[0]
			MessageType parquetSchema = readFooter(file).fileMetaData.schema
			assertGeometryFields(parquetSchema, ['geometry'])

			Map geo = geoMetadata(file)
			assertEquals('geometry', geo.primary_column)
			assertEquals(['Polygon'], geo.columns.geometry.geometry_types)

			List<Group> rows = readRows(file)
			assertEquals(source.size(), rows.size())
			Map<String, Group> byId = rows.collectEntries { Group row -> [(row.getString('id', 0)): row] }
			source.each { Instance instance ->
				String id = instance.getProperty(new QName(GML32, 'id'))[0]
				Group row = byId[id]
				assertNotNull("No row for ${id}", row)
				Geometry expected = sourceGeometry(instance, 'geometry')
				Geometry actual = wkb(row, 'geometry')
				// EPSG:4326 is written as lon/lat (OGC:CRS84), the source may be lat/lon
				assertEquals(expected.numPoints, actual.numPoints)
				assertEquals(expected.area, actual.area, 1e-12)
				assertTrue("Geometry of ${id} differs: ${expected} vs. ${actual}",
						expected.equalsExact(actual, 1e-9) || swapXY(expected).equalsExact(actual, 1e-9))
			}
		})
	}

	private static final String GML32 = 'http://www.opengis.net/gml/3.2'

	private static Geometry swapXY(Geometry geometry) {
		Geometry copy = geometry.copy()
		copy.apply({ org.locationtech.jts.geom.Coordinate c ->
			double x = c.x
			c.x = c.y
			c.y = x
		} as org.locationtech.jts.geom.CoordinateFilter)
		copy.geometryChanged()
		copy
	}
}
