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
package eu.esdihumboldt.hale.io.geoparquet;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import eu.esdihumboldt.hale.io.geoparquet.internal.PureJavaCodecFactory;

/**
 * Tests that Parquet files can be written without Hadoop and native codec
 * libraries on the classpath. Run by the <code>hadoopFreeTest</code> Gradle
 * task, which removes those libraries from the classpath.
 */
public class HadoopFreeTest {

	/**
	 * Temporary folder for output files.
	 */
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/**
	 * Writes files with all supported codecs.
	 *
	 * @throws Exception if writing fails
	 */
	@Test
	public void testWriteAllCodecs() throws Exception {
		assertHadoopMissing();

		MessageType schema = MessageTypeParser.parseMessageType(
				"message test { required int64 id; optional binary name (STRING); }");
		for (CompressionCodecName codec : new CompressionCodecName[] { CompressionCodecName.ZSTD,
				CompressionCodecName.SNAPPY, CompressionCodecName.GZIP,
				CompressionCodecName.UNCOMPRESSED }) {
			File file = new File(tmp.getRoot(), codec.name() + ".parquet");
			SimpleGroupFactory groups = new SimpleGroupFactory(schema);
			try (ParquetWriter<Group> writer = ExampleParquetWriter
					.builder(new LocalOutputFile(file.toPath())).withType(schema)
					.withConf(new PlainParquetConfiguration())
					.withCodecFactory(new PureJavaCodecFactory()).withCompressionCodec(codec)
					.build()) {
				for (int i = 0; i < 3; i++) {
					writer.write(
							groups.newGroup().append("id", (long) i).append("name", "row " + i));
				}
			}
			assertTrue(file.getName(), file.length() > 0);
		}

		assertHadoopMissing();
	}

	private void assertHadoopMissing() {
		try {
			Class.forName("org.apache.hadoop.conf.Configuration");
			fail("Hadoop must not be on the classpath");
		} catch (ClassNotFoundException e) {
			// expected
		}
	}

}
