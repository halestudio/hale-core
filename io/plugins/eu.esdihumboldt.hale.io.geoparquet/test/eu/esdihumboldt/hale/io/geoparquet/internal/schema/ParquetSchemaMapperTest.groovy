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

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate

import javax.xml.namespace.QName

import org.apache.parquet.schema.ColumnOrder
import org.apache.parquet.schema.GroupType
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation
import org.apache.parquet.schema.LogicalTypeAnnotation.GeometryLogicalTypeAnnotation
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit
import org.apache.parquet.schema.PrimitiveType
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.Type
import org.junit.Test

import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.io.geoparquet.BigNumberEncoding
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetVersion
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for {@link ParquetSchemaMapper}.
 */
class ParquetSchemaMapperTest extends AbstractPlatformTest {

	private TypeDefinition type(Schema schema, String name) {
		schema.mappingRelevantTypes.find { it.name.localPart == name }
	}

	private TypeMapping map(Schema schema, String name, MappingOptions options = MappingOptions.defaults(),
			Map<String, GeoParquetCrs> crs = [:]) {
		new ParquetSchemaMapper(options).map(type(schema, name), crs, SimpleLog.NO_LOG)
	}

	private PrimitiveType prim(TypeMapping m, String field) {
		m.schema.getType(field).asPrimitiveType()
	}

	@Test
	void testSimpleTypes() {
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
			}
		}
		def m = map(schema, 't')

		assertEquals([
			'a',
			'b',
			'c',
			'd',
			'e',
			'f',
			'g',
			'h',
			'i',
			'j'
		], m.schema.fields*.name)
		assertEquals(PrimitiveTypeName.BINARY, prim(m, 'a').primitiveTypeName)
		assertEquals(LogicalTypeAnnotation.stringType(), prim(m, 'a').logicalTypeAnnotation)
		assertEquals(PrimitiveTypeName.DOUBLE, prim(m, 'b').primitiveTypeName)
		assertEquals(PrimitiveTypeName.INT64, prim(m, 'c').primitiveTypeName)
		assertEquals(PrimitiveTypeName.INT32, prim(m, 'd').primitiveTypeName)
		assertEquals(PrimitiveTypeName.BOOLEAN, prim(m, 'e').primitiveTypeName)
		assertEquals(LogicalTypeAnnotation.stringType(), prim(m, 'f').logicalTypeAnnotation)
		assertEquals(LogicalTypeAnnotation.stringType(), prim(m, 'g').logicalTypeAnnotation)
		assertEquals(PrimitiveTypeName.INT32, prim(m, 'h').primitiveTypeName)
		assertEquals(LogicalTypeAnnotation.dateType(), prim(m, 'h').logicalTypeAnnotation)
		assertEquals(PrimitiveTypeName.INT64, prim(m, 'i').primitiveTypeName)
		assertEquals(LogicalTypeAnnotation.timestampType(true, TimeUnit.MILLIS), prim(m, 'i').logicalTypeAnnotation)
		assertEquals(PrimitiveTypeName.BINARY, prim(m, 'j').primitiveTypeName)
		assertNull(prim(m, 'j').logicalTypeAnnotation)

		// column mappings
		assertEquals(ValueKind.INT64, m.columns.find { it.columnName == 'c' }.valueKind)
		assertEquals(ValueKind.DECIMAL_AS_STRING, m.columns.find { it.columnName == 'f' }.valueKind)
		assertEquals([new QName('a')].collect { it.localPart }, m.columns.find { it.columnName == 'a' }.propertyPath*.localPart)
		assertTrue(m.columns.every { it.optional && !it.repeated })
	}

	@Test
	void testPrimaryGeometryV2() {
		Schema schema = new SchemaBuilder().schema {
			t {
				name(String)
				geom(GeometryProperty)
			}
		}
		def m = map(schema, 't', MappingOptions.defaults(), ['geometry': GeoParquetCrs.resolve(
			new eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition('EPSG:25832', true), SimpleLog.NO_LOG)])

		assertEquals(['name', 'bbox', 'geometry'], m.schema.fields*.name)
		assertEquals('geometry', m.primaryGeometryColumn)
		assertEquals(['geometry'], m.geometryColumns)
		assertEquals(['geom'], m.primaryGeometryPath*.localPart)
		assertTrue(m.hasBboxColumn())

		def geom = prim(m, 'geometry')
		assertEquals(PrimitiveTypeName.BINARY, geom.primitiveTypeName)
		assertTrue(geom.logicalTypeAnnotation instanceof GeometryLogicalTypeAnnotation)
		assertEquals('EPSG:25832', ((GeometryLogicalTypeAnnotation) geom.logicalTypeAnnotation).crs)
		assertEquals(Type.Repetition.OPTIONAL, geom.repetition)

		GroupType bbox = m.schema.getType('bbox').asGroupType()
		assertEquals([
			'xmin',
			'ymin',
			'xmax',
			'ymax'
		], bbox.fields*.name)
		bbox.fields.each {
			assertEquals(PrimitiveTypeName.DOUBLE, it.asPrimitiveType().primitiveTypeName)
			assertEquals(Type.Repetition.REQUIRED, it.repetition)
			assertEquals(ColumnOrder.typeDefined(), it.asPrimitiveType().columnOrder)
		}

		// bbox is not part of the columns
		assertEquals(['name', 'geometry'], m.columns*.columnName)
	}

	@Test
	void testPrimaryGeometryV1() {
		Schema schema = new SchemaBuilder().schema {
			t {
				name(String)
				geom(GeometryProperty)
			}
		}
		[
			GeoParquetVersion.V1_1,
			GeoParquetVersion.V1_0
		].each { v ->
			def options = new MappingOptions(v, 5, BigNumberEncoding.STRING, 9)
			def m = map(schema, 't', options)
			assertEquals(['name', 'bbox', 'geometry'], m.schema.fields*.name)
			def geom = prim(m, 'geometry')
			assertEquals(PrimitiveTypeName.BINARY, geom.primitiveTypeName)
			assertNull(geom.logicalTypeAnnotation)
			assertTrue(m.hasBboxColumn())
		}
	}

	@Test
	void testNestedPrimaryGeometryIsMovedUp() {
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
			'GeographicPosition',
			'geometry'
		], m.primaryGeometryPath*.localPart)
		assertEquals('geometry', m.primaryGeometryColumn)
		assertTrue(m.schema.containsField('geometry'))
		assertTrue(m.schema.containsField('bbox'))
		assertEquals(1, m.schema.fields.count { it.name == 'geometry' })
		// no other geometry column
		assertEquals(['geometry'], m.geometryColumns)
	}

	@Test
	void testSecondaryGeometryColumn() {
		Schema schema = new SchemaBuilder().schema {
			t {
				geom(GeometryProperty)
				outline(GeometryProperty)
			}
		}
		def mapper = new ParquetSchemaMapper(MappingOptions.defaults())
		def m = map(schema, 't')
		assertEquals(['geometry', 'outline'], m.geometryColumns)
		assertEquals(['geometry', 'outline'], mapper.geometryColumnNames(type(schema, 't')))
		assertEquals(PrimitiveTypeName.BINARY, prim(m, 'outline').primitiveTypeName)
		assertTrue(prim(m, 'outline').logicalTypeAnnotation instanceof GeometryLogicalTypeAnnotation)
	}

	@Test
	void testNoGeometry() {
		Schema schema = new SchemaBuilder().schema {
			t { name(String) }
		}
		def m = map(schema, 't')
		assertNull(m.primaryGeometryColumn)
		assertNull(m.primaryGeometryPath)
		assertFalse(m.hasBboxColumn())
		assertTrue(m.geometryColumns.empty)
		assertEquals(['name'], m.schema.fields*.name)
	}

	@Test
	void testRepeatedSimpleProperty() {
		Schema schema = new SchemaBuilder().schema {
			t {
				names(String, cardinality: '0..n')
			}
		}
		def m = map(schema, 't')
		GroupType list = m.schema.getType('names').asGroupType()
		assertEquals(LogicalTypeAnnotation.listType(), list.logicalTypeAnnotation)
		assertEquals(Type.Repetition.OPTIONAL, list.repetition)
		def element = list.getType(0).asGroupType().getType(0).asPrimitiveType()
		assertEquals('element', element.name)
		assertEquals(Type.Repetition.OPTIONAL, element.repetition)
		assertEquals(LogicalTypeAnnotation.stringType(), element.logicalTypeAnnotation)
		assertTrue(m.columns[0].repeated)
	}

	@Test
	void testBigNumberEncodings() {
		Schema schema = new SchemaBuilder().schema {
			t {
				f(BigInteger)
				g(BigDecimal)
			}
		}
		def opts = { BigNumberEncoding e -> new MappingOptions(GeoParquetVersion.V2_0, 5, e, 4) }

		def m = map(schema, 't', opts(BigNumberEncoding.STRING))
		assertEquals(LogicalTypeAnnotation.stringType(), prim(m, 'f').logicalTypeAnnotation)
		assertEquals(LogicalTypeAnnotation.stringType(), prim(m, 'g').logicalTypeAnnotation)

		m = map(schema, 't', opts(BigNumberEncoding.DOUBLE))
		assertEquals(PrimitiveTypeName.DOUBLE, prim(m, 'f').primitiveTypeName)
		assertEquals(PrimitiveTypeName.DOUBLE, prim(m, 'g').primitiveTypeName)

		m = map(schema, 't', opts(BigNumberEncoding.DECIMAL))
		def f = prim(m, 'f')
		def g = prim(m, 'g')
		assertEquals(PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY, f.primitiveTypeName)
		assertEquals(16, f.typeLength)
		assertEquals(38, ((DecimalLogicalTypeAnnotation) f.logicalTypeAnnotation).precision)
		assertEquals(0, ((DecimalLogicalTypeAnnotation) f.logicalTypeAnnotation).scale)
		assertEquals(16, g.typeLength)
		assertEquals(38, ((DecimalLogicalTypeAnnotation) g.logicalTypeAnnotation).precision)
		assertEquals(4, ((DecimalLogicalTypeAnnotation) g.logicalTypeAnnotation).scale)
		assertEquals(ValueKind.DECIMAL, m.columns.find { it.columnName == 'g' }.valueKind)
		assertEquals(4, m.columns.find { it.columnName == 'g' }.decimalScale)
		assertEquals(0, m.columns.find { it.columnName == 'f' }.decimalScale)
	}

	@Test
	void testVersionParameters() {
		assertEquals(GeoParquetVersion.V2_0, GeoParquetVersion.fromParameter(null))
		assertEquals(GeoParquetVersion.V1_0, GeoParquetVersion.fromParameter('1.0'))
		assertEquals(GeoParquetVersion.V1_1, GeoParquetVersion.fromParameter('1.1.0'))
		assertEquals(GeoParquetVersion.V2_0, GeoParquetVersion.fromParameter('2.0'))
		assertEquals('1.1.0', GeoParquetVersion.V1_1.versionString)
		assertTrue(GeoParquetVersion.V2_0.usesNativeGeometryType())
		assertFalse(GeoParquetVersion.V1_1.usesNativeGeometryType())
		assertTrue(GeoParquetVersion.V1_1.declaresCovering())
		assertFalse(GeoParquetVersion.V1_0.declaresCovering())
		assertEquals(BigNumberEncoding.STRING, BigNumberEncoding.fromParameter(null))
		assertEquals(BigNumberEncoding.DECIMAL, BigNumberEncoding.fromParameter('Decimal'))
	}
}
