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
package eu.esdihumboldt.hale.io.geoparquet.internal.schema

import static org.junit.Assert.*

import javax.xml.namespace.QName

import org.apache.parquet.schema.GroupType
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.Type
import org.junit.Test

import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.DefinitionUtil
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultPropertyDefinition
import eu.esdihumboldt.hale.io.geoparquet.BigNumberEncoding
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetVersion
import eu.esdihumboldt.hale.io.xsd.constraint.XmlAttributeFlag
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for nested structures in {@link ParquetSchemaMapper}.
 */
class ParquetSchemaMapperNestedTest extends AbstractPlatformTest {

	private static final String GML = 'http://www.opengis.net/gml/3.2'

	private TypeDefinition type(Schema schema, String name) {
		schema.mappingRelevantTypes.find { it.name.localPart == name }
	}

	private TypeMapping map(TypeDefinition type, MappingOptions options = MappingOptions.defaults()) {
		new ParquetSchemaMapper(options).map(type, [:], SimpleLog.NO_LOG)
	}

	private TypeMapping map(Schema schema, String name, MappingOptions options = MappingOptions.defaults()) {
		map(type(schema, name), options)
	}

	private ColumnMapping col(TypeMapping m, String name) {
		m.columns.find { it.columnName == name }
	}

	@Test
	void testStruct() {
		Schema schema = new SchemaBuilder().schema {
			t {
				address {
					street(String)
					number(Integer)
				}
			}
		}
		def m = map(schema, 't')
		GroupType address = m.schema.getType('address').asGroupType()
		assertEquals(Type.Repetition.OPTIONAL, address.repetition)
		assertNull(address.logicalTypeAnnotation)
		assertEquals(['street', 'number'], address.fields*.name)
		address.fields.each { assertEquals(Type.Repetition.OPTIONAL, it.repetition) }
		assertEquals(PrimitiveTypeName.INT32, address.getType('number').asPrimitiveType().primitiveTypeName)

		def c = col(m, 'address')
		assertTrue(c.struct)
		assertFalse(c.repeated)
		assertEquals(['street', 'number'], c.children*.columnName)
		assertEquals(ValueKind.INT32, c.children[1].valueKind)
		assertEquals(['number'], c.children[1].propertyPath*.localPart)
		assertEquals(['address'], c.propertyPath*.localPart)
	}

	@Test
	void testComplexWithValueAndAttribute() {
		Schema schema = new SchemaBuilder().schema {
			t {
				height(Double) {
					nilReason(String, [XmlAttributeFlag.ENABLED])
				}
			}
		}
		def m = map(schema, 't')
		GroupType height = m.schema.getType('height').asGroupType()
		assertEquals(['value', 'nilReason'], height.fields*.name)
		assertEquals(PrimitiveTypeName.DOUBLE, height.getType('value').asPrimitiveType().primitiveTypeName)
		def c = col(m, 'height')
		assertEquals(ColumnMapping.VALUE_FIELD, 'value')
		assertEquals(['value', 'nilReason'], c.children*.columnName)
		assertEquals(ValueKind.DOUBLE, c.children[0].valueKind)
		assertEquals(ValueKind.STRING, c.children[1].valueKind)
	}

	@Test
	void testAttributeCollidingWithElement() {
		Schema schema = new SchemaBuilder().schema {
			t {
				box {
					id(String, [XmlAttributeFlag.ENABLED], namespace: 'urn:a')
					id(Integer)
					href(String, [XmlAttributeFlag.ENABLED])
				}
			}
		}
		def m = map(schema, 't')
		GroupType box = m.schema.getType('box').asGroupType()
		assertEquals(['attr_id', 'id', 'href'], box.fields*.name)
	}

	@Test
	void testListOfStructs() {
		Schema schema = new SchemaBuilder().schema {
			t {
				locators(cardinality: '0..n') {
					designator(String)
					type(String)
				}
			}
		}
		def m = map(schema, 't')
		GroupType list = m.schema.getType('locators').asGroupType()
		assertEquals(LogicalTypeAnnotation.listType(), list.logicalTypeAnnotation)
		assertEquals(Type.Repetition.OPTIONAL, list.repetition)
		GroupType element = list.getType(0).asGroupType().getType(0).asGroupType()
		assertEquals('element', element.name)
		assertEquals(['designator', 'type'], element.fields*.name)
		def c = col(m, 'locators')
		assertTrue(c.struct)
		assertTrue(c.repeated)
		assertEquals(['designator', 'type'], c.children*.columnName)
	}

	@Test
	void testGroupAndChoiceAreTransparent() {
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
		def m = map(schema, 't')
		assertEquals(['x', 'p', 'q'], m.schema.fields*.name)
		m.schema.fields.each { assertEquals(Type.Repetition.OPTIONAL, it.repetition) }
		assertEquals(['x', 'p', 'q'], m.columns*.columnName)
		// group names are part of the path
		assertEquals(3, col(m, 'q').propertyPath.size())
		assertEquals('q', col(m, 'q').propertyPath.last().localPart)
	}

	@Test
	void testRepeatedGroupMakesChildrenRepeated() {
		Schema schema = new SchemaBuilder().schema {
			t {
				_(cardinality: '0..n') {
					a(String)
					b(Integer)
				}
			}
		}
		def m = map(schema, 't')
		assertTrue(col(m, 'a').repeated)
		assertTrue(col(m, 'b').repeated)
		assertEquals(LogicalTypeAnnotation.listType(), m.schema.getType('a').logicalTypeAnnotation)
	}

	@Test
	void testRecursionBecomesJson() {
		Schema schema = new SchemaBuilder().schema {
			Node { name(String) }
		}
		TypeDefinition node = type(schema, 'Node')
		new DefaultPropertyDefinition(new QName('child'), DefinitionUtil.getDefinitionGroup(node), node)

		def m = map(node)
		assertEquals(['name', 'child'], m.schema.fields*.name)
		def child = m.schema.getType('child').asPrimitiveType()
		assertEquals(PrimitiveTypeName.BINARY, child.primitiveTypeName)
		assertEquals(LogicalTypeAnnotation.stringType(), child.logicalTypeAnnotation)
		assertEquals(ValueKind.JSON, col(m, 'child').valueKind)
	}

	@Test
	void testDepthLimit() {
		Schema schema = new SchemaBuilder().schema {
			t {
				l1 {
					l2 {
						l3 {
							l4 {
								l5 {
									l6 {
										l7(String)
									}
								}
							}
						}
					}
				}
			}
		}
		def m = map(schema, 't', new MappingOptions(GeoParquetVersion.V2_0, 5, BigNumberEncoding.STRING, 9))
		def c = col(m, 'l1')
		(2..5).each { c = c.children[0]; assertTrue(c.struct) ; assertEquals("l$it".toString(), c.columnName) }
		def l6 = c.children[0]
		assertEquals('l6', l6.columnName)
		assertEquals(ValueKind.JSON, l6.valueKind)

		GroupType l5 = m.schema.getType('l1').asGroupType().getType('l2').asGroupType()
				.getType('l3').asGroupType().getType('l4').asGroupType().getType('l5').asGroupType()
		assertEquals(LogicalTypeAnnotation.stringType(), l5.getType('l6').asPrimitiveType().logicalTypeAnnotation)

		// lower limit
		m = map(schema, 't', new MappingOptions(GeoParquetVersion.V2_0, 1, BigNumberEncoding.STRING, 9))
		assertTrue(col(m, 'l1').struct)
		assertEquals(ValueKind.JSON, col(m, 'l1').children[0].valueKind)
	}

	@Test
	void testNestedGeometryIsWkt() {
		Schema schema = new SchemaBuilder().schema {
			t {
				geom(GeometryProperty)
				info {
					label(String)
					outline(GeometryProperty)
				}
			}
		}
		def m = map(schema, 't')
		assertEquals(['info', 'bbox', 'geometry'], m.schema.fields*.name)
		def outline = m.schema.getType('info').asGroupType().getType('outline').asPrimitiveType()
		assertEquals(LogicalTypeAnnotation.stringType(), outline.logicalTypeAnnotation)
		assertEquals(ValueKind.WKT, col(m, 'info').children[1].valueKind)
		assertEquals(['geometry'], m.geometryColumns)
	}

	@Test
	void testNestedPrimaryGeometryRemovedFromStruct() {
		Schema schema = new SchemaBuilder().schema {
			Address {
				position {
					GeographicPosition {
						geometry(GeometryProperty)
						'default'(Boolean)
					}
				}
			}
		}
		def m = map(schema, 'Address')
		assertEquals([
			'position',
			'bbox',
			'geometry'
		], m.schema.fields*.name)
		GroupType pos = m.schema.getType('position').asGroupType().getType('GeographicPosition').asGroupType()
		assertEquals(['default'], pos.fields*.name)
	}

	@Test
	void testStructWithOnlyPrimaryGeometryIsOmitted() {
		Schema schema = new SchemaBuilder().schema {
			t {
				name(String)
				position { geometry(GeometryProperty) }
			}
		}
		def m = map(schema, 't')
		assertEquals(['name', 'bbox', 'geometry'], m.schema.fields*.name)
	}

	@Test
	void testNameCollisionsAreSuffixed() {
		Schema schema = new SchemaBuilder().schema {
			t {
				name(namespace: GML)
				name(namespace: 'urn:x')
				_(choice: true) {
					name(Integer, namespace: 'urn:y')
				}
				bbox(String)
				geom(GeometryProperty)
			}
		}
		def m = map(schema, 't')
		assertEquals([
			'name_2',
			'name',
			'name_3',
			'bbox_2',
			'bbox',
			'geometry'
		], m.schema.fields*.name)
		assertEquals('urn:x', col(m, 'name').propertyPath.last().namespaceURI)
		assertEquals(GML, col(m, 'name_2').propertyPath.last().namespaceURI)
		assertEquals('urn:y', col(m, 'name_3').propertyPath.last().namespaceURI)
		assertNotNull(col(m, 'bbox_2'))
	}
}
