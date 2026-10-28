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

import static org.junit.Assert.*

import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.locationtech.jts.geom.Geometry

import eu.esdihumboldt.cst.functions.geometric.GeometryHelperFunctions
import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.MappingOptions
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ParquetSchemaMapper
import eu.esdihumboldt.hale.io.geoparquet.internal.write.GeometryConverter
import eu.esdihumboldt.hale.io.geoparquet.internal.write.TypeFileWriter
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Writes files through {@link TypeFileWriter} with Hadoop and native codec
 * libraries removed from the classpath (see the <code>hadoopFreeTest</code> task).
 */
class TypeFileWriterHadoopFreeTest extends AbstractPlatformTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder()

	@Test
	void testWriteAllCodecs() {
		try {
			Class.forName('org.apache.hadoop.conf.Configuration')
			fail('Hadoop must not be on the classpath')
		} catch (ClassNotFoundException e) {
			// expected
		}

		def schema = new SchemaBuilder().schema {
			t {
				name(String)
				tags(String, cardinality: '0..n')
				geom(GeometryProperty)
			}
		}
		def crs = [geometry: GeoParquetCrs.resolve(new CodeDefinition('EPSG:25832', true), SimpleLog.NO_LOG)]
		def mapping = new ParquetSchemaMapper(MappingOptions.defaults()).map(
				schema.mappingRelevantTypes.first(), crs, SimpleLog.NO_LOG)
		def instances = new InstanceBuilder(types: schema).createCollection {
			(0..<5).each { i ->
				t {
					name("n$i")
					tags('a')
					tags('b')
					geom(GeometryHelperFunctions._with([geometry: "POINT ($i $i)", crs: new CodeDefinition('EPSG:25832', true)]))
				}
			}
		}
		GeometryConverter converter = { Object v, GeoParquetCrs t, SimpleLog l ->
			((GeometryProperty) v).geometry
		} as GeometryConverter

		[
			CompressionCodecName.ZSTD,
			CompressionCodecName.SNAPPY,
			CompressionCodecName.GZIP,
			CompressionCodecName.UNCOMPRESSED
		].each { codec ->
			def file = tmp.newFile("${codec}.parquet")
			def writer = new TypeFileWriter(file.toPath(), mapping, GeoParquetVersion.V2_0, crs, codec, 1000,
					converter, SimpleLog.NO_LOG)
			def it = instances.iterator()
			try {
				while (it.hasNext()) {
					writer.write(it.next())
				}
			} finally {
				it.close()
				writer.close()
			}
			assertEquals(5, writer.rowCount)
			byte[] bytes = file.bytes
			assertEquals('PAR1', new String(bytes, 0, 4, 'US-ASCII'))
			assertEquals('PAR1', new String(bytes, bytes.length - 4, 4, 'US-ASCII'))
			assertTrue(new String(bytes, 'ISO-8859-1').contains('"primary_column":"geometry"'))
		}
	}
}
