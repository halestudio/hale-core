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

import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.conf.HadoopParquetConfiguration;
import org.apache.parquet.conf.ParquetConfiguration;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.io.api.RecordConsumer;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetVersion;
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs;
import eu.esdihumboldt.hale.io.geoparquet.internal.meta.GeoMetadataBuilder;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.TypeMapping;

/**
 * Parquet write support for hale instances. Adds the GeoParquet
 * <code>geo</code> metadata to the file footer.
 */
public class InstanceWriteSupport extends WriteSupport<Instance> {

	/** Key of the GeoParquet metadata in the file footer. */
	public static final String GEO_METADATA_KEY = "geo";

	private final TypeMapping mapping;
	private final GeoParquetVersion version;
	private final Map<String, GeoParquetCrs> crs;
	private final ValueWriter valueWriter;

	/**
	 * Create a write support.
	 *
	 * @param mapping the type mapping
	 * @param version the GeoParquet version
	 * @param crs the CRS description per geometry column name
	 * @param converter the geometry converter
	 * @param log the log for reporting problems
	 */
	public InstanceWriteSupport(TypeMapping mapping, GeoParquetVersion version,
			Map<String, GeoParquetCrs> crs, GeometryConverter converter, SimpleLog log) {
		this.mapping = mapping;
		this.version = version;
		this.crs = crs;
		this.valueWriter = new ValueWriter(mapping, crs, converter, log);
	}

	@Override
	public String getName() {
		return "hale";
	}

	@Override
	public WriteContext init(Configuration configuration) {
		// only called if a Hadoop configuration is used
		return init(new HadoopParquetConfiguration(configuration));
	}

	@Override
	public WriteContext init(ParquetConfiguration configuration) {
		return new WriteContext(mapping.getSchema(), Map.of());
	}

	@Override
	public void prepareForWrite(RecordConsumer recordConsumer) {
		valueWriter.setRecordConsumer(recordConsumer);
	}

	@Override
	public void write(Instance instance) {
		valueWriter.writeRecord(instance);
	}

	@Override
	public FinalizedWriteContext finalizeWrite() {
		String primary = mapping.getPrimaryGeometryColumn();
		if (primary == null || !mapping.isGeoParquet()) {
			return new FinalizedWriteContext(Map.of());
		}
		String geo = GeoMetadataBuilder.build(version, primary, valueWriter.getStats(), crs,
				mapping.hasBboxColumn());
		return new FinalizedWriteContext(Map.of(GEO_METADATA_KEY, geo));
	}

}
