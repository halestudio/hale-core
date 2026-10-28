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
package eu.esdihumboldt.hale.io.geoparquet.internal.write;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.conf.ParquetConfiguration;
import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.OutputFile;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetVersion;
import eu.esdihumboldt.hale.io.geoparquet.internal.PureJavaCodecFactory;
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.TypeMapping;

/**
 * Writes the instances of one type to a Parquet file. An existing file is
 * overwritten.
 */
public class TypeFileWriter implements Closeable {

	private final ParquetWriter<Instance> writer;

	private long rowCount;

	/**
	 * Create the writer and the file.
	 *
	 * @param file the file to write
	 * @param mapping the type mapping
	 * @param version the GeoParquet version
	 * @param crs the CRS description per geometry column name
	 * @param codec the compression codec
	 * @param rowGroupRowLimit the maximum number of rows per row group
	 * @param bbox2d if the column bbox in the <code>geo</code> metadata is written
	 *            as 2D bbox also for geometries with Z values
	 * @param converter the geometry converter
	 * @param log the log for reporting problems
	 * @throws IOException if the file cannot be created
	 */
	public TypeFileWriter(Path file, TypeMapping mapping, GeoParquetVersion version,
			Map<String, GeoParquetCrs> crs, CompressionCodecName codec, int rowGroupRowLimit,
			boolean bbox2d, GeometryConverter converter, SimpleLog log) throws IOException {
		InstanceWriteSupport support = new InstanceWriteSupport(mapping, version, crs, bbox2d,
				converter, log);
		this.writer = new Builder(new LocalOutputFile(file), support)
				.withConf(new PlainParquetConfiguration())
				.withCodecFactory(new PureJavaCodecFactory()).withCompressionCodec(codec)
				.withRowGroupRowCountLimit(rowGroupRowLimit)
				.withWriteMode(ParquetFileWriter.Mode.OVERWRITE).build();
	}

	/**
	 * Write an instance as row.
	 *
	 * @param instance the instance
	 * @throws IOException if writing fails
	 */
	public void write(Instance instance) throws IOException {
		writer.write(instance);
		rowCount++;
	}

	/**
	 * @return the number of rows written
	 */
	public long getRowCount() {
		return rowCount;
	}

	@Override
	public void close() throws IOException {
		writer.close();
	}

	/**
	 * Builder providing the write support.
	 */
	private static class Builder extends ParquetWriter.Builder<Instance, Builder> {

		private final InstanceWriteSupport support;

		Builder(OutputFile file, InstanceWriteSupport support) {
			super(file);
			this.support = support;
		}

		@Override
		protected Builder self() {
			return this;
		}

		@Override
		protected WriteSupport<Instance> getWriteSupport(Configuration conf) {
			return support;
		}

		@Override
		protected WriteSupport<Instance> getWriteSupport(ParquetConfiguration conf) {
			return support;
		}
	}

}
