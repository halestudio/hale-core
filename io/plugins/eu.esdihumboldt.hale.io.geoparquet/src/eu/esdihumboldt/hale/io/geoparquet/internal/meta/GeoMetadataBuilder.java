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
package eu.esdihumboldt.hale.io.geoparquet.internal.meta;

import java.util.LinkedHashMap;
import java.util.Map;

import org.locationtech.jts.geom.Envelope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.esdihumboldt.hale.io.geoparquet.GeoParquetVersion;
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.TypeMapping;

/**
 * Builds the GeoParquet <code>geo</code> file metadata.
 */
public final class GeoMetadataBuilder {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private GeoMetadataBuilder() {
	}

	/**
	 * Build the <code>geo</code> metadata JSON.
	 *
	 * @param version the GeoParquet version
	 * @param primaryColumn the name of the primary geometry column
	 * @param stats the statistics per geometry column, primary column included
	 * @param crs the CRS per geometry column, columns without entry are CRS84
	 * @param primaryHasCovering if the primary geometry column has a bbox column
	 * @return the metadata as JSON string
	 */
	public static String build(GeoParquetVersion version, String primaryColumn,
			LinkedHashMap<String, GeoColumnStats> stats, Map<String, GeoParquetCrs> crs,
			boolean primaryHasCovering) {
		ObjectNode root = MAPPER.createObjectNode();
		root.put("version", version.getVersionString());
		root.put("primary_column", primaryColumn);

		ObjectNode columns = root.putObject("columns");
		for (Map.Entry<String, GeoColumnStats> entry : stats.entrySet()) {
			String name = entry.getKey();
			GeoColumnStats columnStats = entry.getValue();
			ObjectNode column = columns.putObject(name);
			column.put("encoding", "WKB");

			ArrayNode types = column.putArray("geometry_types");
			columnStats.getGeometryTypes().forEach(types::add);

			GeoParquetCrs columnCrs = crs.get(name);
			if (columnCrs != null) {
				if (columnCrs.isUnknown()) {
					column.putNull("crs");
				}
				else if (!columnCrs.isCrs84()) {
					try {
						column.set("crs", MAPPER.readTree(columnCrs.getProjJson()));
					} catch (JsonProcessingException e) {
						throw new IllegalStateException("Invalid PROJJSON for EPSG:"
								+ columnCrs.getEpsgCode() + ": " + e.getMessage(), e);
					}
				}
			}

			Envelope extent = columnStats.getExtent();
			if (extent != null) {
				ArrayNode bbox = column.putArray("bbox");
				bbox.add(extent.getMinX()).add(extent.getMinY()).add(extent.getMaxX())
						.add(extent.getMaxY());
			}

			if (primaryHasCovering && version.declaresCovering() && name.equals(primaryColumn)) {
				ObjectNode bbox = column.putObject("covering").putObject("bbox");
				for (String field : new String[] { "xmin", "ymin", "xmax", "ymax" }) {
					bbox.putArray(field).add(TypeMapping.BBOX_COLUMN).add(field);
				}
			}
		}
		return root.toString();
	}

}
