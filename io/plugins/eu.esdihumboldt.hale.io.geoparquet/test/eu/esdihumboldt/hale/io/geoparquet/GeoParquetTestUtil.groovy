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

import static org.junit.Assert.assertTrue

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

import org.apache.parquet.ParquetReadOptions
import org.apache.parquet.conf.PlainParquetConfiguration
import org.apache.parquet.example.data.Group
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.hadoop.ParquetReader
import org.apache.parquet.hadoop.api.ReadSupport
import org.apache.parquet.hadoop.example.GroupReadSupport
import org.apache.parquet.hadoop.metadata.ParquetMetadata
import org.apache.parquet.io.LocalInputFile
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBReader

import eu.esdihumboldt.hale.common.core.io.impl.LogProgressIndicator
import eu.esdihumboldt.hale.common.core.io.report.IOReport
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier
import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchemaSpace

/**
 * Helpers for tests of GeoParquet files. Reading requires the Hadoop and codec
 * libraries that are only available on the test runtime classpath.
 */
class GeoParquetTestUtil {

	/**
	 * Read all rows of a Parquet file.
	 */
	static List<Group> readRows(File file) {
		def reader = new ParquetReader.Builder<Group>(new LocalInputFile(file.toPath()), new PlainParquetConfiguration()) {

					@Override
					protected ReadSupport<Group> getReadSupport() {
						new GroupReadSupport()
					}
				}.build()
		try {
			List<Group> rows = []
			Group row
			while ((row = reader.read()) != null) {
				rows << row
			}
			return rows
		} finally {
			reader.close()
		}
	}

	/**
	 * Read the footer of a Parquet file.
	 */
	static ParquetMetadata readFooter(File file) {
		def reader = ParquetFileReader.open(new LocalInputFile(file.toPath()),
				ParquetReadOptions.builder(new PlainParquetConfiguration()).build())
		try {
			return reader.footer
		} finally {
			reader.close()
		}
	}

	/**
	 * Get the parsed <code>geo</code> metadata of a Parquet file, null if there is none.
	 */
	static Map geoMetadata(File file) {
		String geo = readFooter(file).fileMetaData.keyValueMetaData['geo']
		geo == null ? null : (Map) new JsonSlurper().parseText(geo)
	}

	/**
	 * Read a WKB geometry column of a row.
	 */
	static Geometry wkb(Group row, String column) {
		new WKBReader().read(row.getBinary(column, 0).bytes)
	}

	/**
	 * Write an instance collection to a GeoParquet file. Asserts that the writer
	 * reports success and no errors.
	 */
	@CompileStatic
	static IOReport writeInstances(File target, Schema schema, InstanceCollection instances,
			Consumer<GeoParquetInstanceWriter> configurator = null) {
		GeoParquetInstanceWriter writer = new GeoParquetInstanceWriter()
		writer.setTarget(new FileIOSupplier(target))
		def ss = new DefaultSchemaSpace()
		ss.addSchema(schema)
		writer.setTargetSchema(ss)
		writer.setInstances(instances)

		if (configurator != null) {
			configurator.accept(writer)
		}

		IOReport report = writer.execute(new LogProgressIndicator())

		assertTrue(report.errors*.message.toString(), report.isSuccess())
		assertTrue(report.errors*.message.toString(), report.getErrors().isEmpty())

		return report
	}

	/**
	 * Write an instance collection to a GeoParquet file in a new temporary
	 * directory and call the handler with the files that were written, sorted by
	 * name. The files are deleted afterwards.
	 */
	@CompileStatic
	static void withNewGeoParquet(Schema schema, InstanceCollection instances,
			Consumer<List<File>> handler, Consumer<GeoParquetInstanceWriter> configurator = null) {
		Path dir = Files.createTempDirectory('geoparquet')
		try {
			File target = new File(dir.toFile(), 'out.parquet')
			writeInstances(target, schema, instances, configurator)
			List<File> files = (dir.toFile().listFiles() as List<File>).sort { File f -> f.name }
			handler.accept(files)
		} finally {
			dir.toFile().deleteDir()
		}
	}

	/**
	 * Log that collects the messages.
	 */
	static class CollectingLog implements SimpleLog {

		final List<String> warnings = []
		final List<String> errors = []
		final List<String> infos = []

		@Override
		void warn(String message, Throwable e) {
			warnings << message
		}

		@Override
		void error(String message, Throwable e) {
			errors << message
		}

		@Override
		void info(String message, Throwable e) {
			infos << message
		}
	}
}
