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
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import eu.esdihumboldt.hale.common.core.io.impl.LogProgressIndicator;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier;
import eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty;
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstanceCollection;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.Binding;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultPropertyDefinition;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchema;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchemaSpace;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultTypeDefinition;
import eu.esdihumboldt.hale.io.geoparquet.internal.PureJavaCodecFactory;
import eu.esdihumboldt.util.test.AbstractPlatformTest;

/**
 * Tests that Parquet files can be written without Hadoop and native codec
 * libraries on the classpath. Run by the <code>hadoopFreeTest</code> Gradle
 * task, which removes those libraries from the classpath.
 */
public class HadoopFreeTest extends AbstractPlatformTest {

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

	/**
	 * Runs the {@link GeoParquetInstanceWriter} with all codecs on a small
	 * in-memory collection.
	 *
	 * @throws Exception if writing fails
	 */
	@Test
	public void testWriterWithoutHadoop() throws Exception {
		assertHadoopMissing();

		DefaultTypeDefinition stringType = new DefaultTypeDefinition(new QName("string"));
		stringType.setConstraint(Binding.get(String.class));
		DefaultTypeDefinition geometryType = new DefaultTypeDefinition(new QName("geometry"));
		geometryType.setConstraint(Binding.get(GeometryProperty.class));
		DefaultTypeDefinition type = new DefaultTypeDefinition(new QName("city"));
		new DefaultPropertyDefinition(new QName("name"), type, stringType);
		new DefaultPropertyDefinition(new QName("location"), type, geometryType);

		DefaultSchema schema = new DefaultSchema("http://example.com", null);
		schema.addType(type);
		DefaultSchemaSpace schemaSpace = new DefaultSchemaSpace();
		schemaSpace.addSchema(schema);

		List<Instance> instances = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			DefaultInstance instance = new DefaultInstance(type, null);
			instance.addProperty(new QName("name"), "city " + i);
			instance.addProperty(new QName("location"),
					new DefaultGeometryProperty<>(new CodeDefinition("EPSG:4326", true),
							new GeometryFactory().createPoint(new Coordinate(8 + i, 49 + i))));
			instances.add(instance);
		}

		for (String codec : new String[] { "zstd", "snappy", "gzip", "none" }) {
			writeCities(schemaSpace, instances, codec, true);
			writeCities(schemaSpace, instances, codec, false);
		}

		assertHadoopMissing();
	}

	private void writeCities(DefaultSchemaSpace schemaSpace, List<Instance> instances, String codec,
			boolean sort) throws Exception {
		File file = new File(tmp.getRoot(), codec + (sort ? "-sorted" : "") + ".parquet");
		GeoParquetInstanceWriter writer = new GeoParquetInstanceWriter();
		writer.setTarget(new FileIOSupplier(file));
		writer.setTargetSchema(schemaSpace);
		writer.setInstances(new DefaultInstanceCollection(instances));
		writer.setCompression(codec);
		writer.setSort(sort);
		IOReport report = writer.execute(new LogProgressIndicator());
		assertTrue(codec + ": " + report.getErrors(), report.isSuccess());
		assertTrue(codec, report.getErrors().isEmpty());
		assertTrue(codec, file.length() > 0);
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
