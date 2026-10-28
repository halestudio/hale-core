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
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.junit.Test

import com.fasterxml.jackson.databind.ObjectMapper

import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.DefinitionUtil
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultPropertyDefinition
import eu.esdihumboldt.hale.io.xsd.constraint.XmlAttributeFlag
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * End-to-end tests for writing complex (nested, INSPIRE-like) data.
 */
class GeoParquetComplexDataTest extends AbstractPlatformTest {

	private static final int COUNT = 500

	/**
	 * Schema similar to the INSPIRE Addresses Address feature type.
	 */
	private static Schema addressSchema() {
		Schema schema = new SchemaBuilder().schema {
			Address {
				inspireId(String)
				position {
					GeographicPosition {
						geometry(GeometryProperty)
						specification(String)
						method(String)
						'default'(Boolean)
					}
				}
				locator(cardinality: '0..n') {
					designator(String, cardinality: '0..n')
					level(String)
				}
				alternativeIdentifier(String, cardinality: '0..n')
				_(choice: true) {
					addressArea(String)
					postName(String)
				}
				postCode(String) {
					nilReason(String, [XmlAttributeFlag.ENABLED])
				}
			}
		}
		// recursive reference to the parent address
		TypeDefinition address = schema.mappingRelevantTypes.find { it.name.localPart == 'Address' }
		new DefaultPropertyDefinition(new QName('parent'), DefinitionUtil.getDefinitionGroup(address), address)
		schema
	}

	/** Longitude of the address with the given index. */
	private static double lon(int i) {
		5 + (i % 100) * 0.1
	}

	/** Latitude of the address with the given index. */
	private static double lat(int i) {
		47 + (i % 80) * 0.1
	}

	private static InstanceCollection addresses(Schema schema) {
		new InstanceBuilder(types: schema).createCollection {
			(0..<COUNT).each { int i ->
				Address {
					inspireId "ID-$i".toString()
					position {
						GeographicPosition {
							// lat/lon axis order of EPSG:4258
							geometry(GeoParquetInstanceWriterTest.createGeometry("POINT(${lat(i)} ${lon(i)})".toString(), new CodeDefinition('EPSG:4258')))
							specification 'entrance'
							method 'byAdministrator'
							'default'(i % 2 == 0)
						}
					}
					(0..(i % 3)).each { int l ->
						locator {
							(0..(l % 2)).each { int d -> designator "D$i-$l-$d".toString() }
							if (l != 1) {
								level 'floor'
							}
						}
					}
					if (i % 4 != 0) {
						alternativeIdentifier "ALT-$i-a".toString()
						alternativeIdentifier "ALT-$i-b".toString()
					}
					if (i % 2 == 0) {
						addressArea "Area $i".toString()
					}
					else {
						postName "Post $i".toString()
					}
					postCode(i % 5 == 0 ? null : "${10000 + i}".toString()) {
						if (i % 5 == 0) {
							nilReason 'unknown'
						}
					}
					if (i % 10 == 0) {
						parent {
							inspireId "PARENT-$i".toString()
							parent {
								inspireId "GRANDPARENT-$i".toString()
							}
						}
					}
				}
			}
		}
	}

	@Test
	void testInspireAddressLike() {
		Schema schema = addressSchema()

		withNewGeoParquet(schema, addresses(schema), { List<File> files ->
			assertEquals(1, files.size())
			File file = files[0]
			def parquetSchema = readFooter(file).fileMetaData.schema

			// top-level geometry, position struct without geometry
			assertEquals(['geometry'], geoMetadata(file).columns.keySet() as List)
			assertEquals('geometry', geoMetadata(file).primary_column)
			assertEquals(4258, geoMetadata(file).columns.geometry.crs.id.code)
			def position = parquetSchema.getType('position').asGroupType().getType('GeographicPosition').asGroupType()
			assertEquals([
				'specification',
				'method',
				'default'
			], position.fields*.name)

			// locators: list of structs with a list of strings
			GroupType locator = parquetSchema.getType('locator').asGroupType()
			assertEquals(LogicalTypeAnnotation.listType(), locator.logicalTypeAnnotation)
			GroupType element = locator.getType(0).asGroupType().getType(0).asGroupType()
			assertEquals(['designator', 'level'], element.fields*.name)
			assertEquals(LogicalTypeAnnotation.listType(), element.getType('designator').logicalTypeAnnotation)
			assertEquals(LogicalTypeAnnotation.listType(), parquetSchema.getType('alternativeIdentifier').logicalTypeAnnotation)

			// choice is transparent, attribute becomes a field next to the value
			assertEquals(['addressArea', 'postName'], parquetSchema.fields*.name.findAll { it in ['addressArea', 'postName'] })
			assertEquals(['value', 'nilReason'], parquetSchema.getType('postCode').asGroupType().fields*.name)

			// recursion as JSON
			def parent = parquetSchema.getType('parent').asPrimitiveType()
			assertEquals(PrimitiveTypeName.BINARY, parent.primitiveTypeName)
			assertEquals(LogicalTypeAnnotation.stringType(), parent.logicalTypeAnnotation)

			List<Group> rows = readRows(file)
			assertEquals(COUNT, rows.size())
			def mapper = new ObjectMapper()
			Set<String> ids = [] as Set
			rows.each { Group row ->
				String id = row.getString('inspireId', 0)
				assertTrue(ids.add(id))
				int i = (id - 'ID-') as int

				// lon/lat order
				def g = wkb(row, 'geometry')
				assertEquals("lon of $id", lon(i), g.coordinate.x, 1e-9)
				assertEquals("lat of $id", lat(i), g.coordinate.y, 1e-9)

				def pos = row.getGroup('position', 0).getGroup('GeographicPosition', 0)
				assertEquals('entrance', pos.getString('specification', 0))
				assertEquals(i % 2 == 0, pos.getBoolean('default', 0))

				def locators = row.getGroup('locator', 0)
				int n = locators.getFieldRepetitionCount('list')
				assertEquals("locators of $id", 1 + (i % 3), n)
				(0..<n).each { int l ->
					def loc = locators.getGroup('list', l).getGroup('element', 0)
					assertEquals(l != 1, loc.getFieldRepetitionCount('level') > 0)
					assertEquals(1 + (l % 2), loc.getGroup('designator', 0).getFieldRepetitionCount('list'))
					assertEquals("D$i-$l-0".toString(), loc.getGroup('designator', 0).getGroup('list', 0).getString('element', 0))
				}

				def alt = row.getFieldRepetitionCount('alternativeIdentifier') == 0 ? 0 :
						row.getGroup('alternativeIdentifier', 0).getFieldRepetitionCount('list')
				assertEquals(i % 4 != 0 ? 2 : 0, alt)

				assertEquals(i % 2 == 0, row.getFieldRepetitionCount('addressArea') > 0)
				assertEquals(i % 2 != 0, row.getFieldRepetitionCount('postName') > 0)

				def postCode = row.getGroup('postCode', 0)
				if (i % 5 == 0) {
					assertEquals(0, postCode.getFieldRepetitionCount('value'))
					assertEquals('unknown', postCode.getString('nilReason', 0))
				}
				else {
					assertEquals("${10000 + i}".toString(), postCode.getString('value', 0))
					assertEquals(0, postCode.getFieldRepetitionCount('nilReason'))
				}

				if (i % 10 == 0) {
					def json = mapper.readTree(row.getString('parent', 0))
					assertTrue(json.toString().contains("PARENT-$i"))
					assertTrue(json.toString().contains("GRANDPARENT-$i"))
				}
				else {
					assertEquals(0, row.getFieldRepetitionCount('parent'))
				}
			}
		})
	}
}
