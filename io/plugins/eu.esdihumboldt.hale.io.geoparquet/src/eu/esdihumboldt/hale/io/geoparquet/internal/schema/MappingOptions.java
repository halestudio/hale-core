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
package eu.esdihumboldt.hale.io.geoparquet.internal.schema;

import eu.esdihumboldt.hale.io.geoparquet.BigNumberEncoding;
import eu.esdihumboldt.hale.io.geoparquet.GeoParquetVersion;

/**
 * Options for mapping hale types to Parquet schemas.
 *
 * @param version the GeoParquet version
 * @param nestingDepth the maximum nesting depth of structures
 * @param bigNumberEncoding the encoding for BigInteger/BigDecimal values
 * @param decimalScale the scale used for BigDecimal values encoded as DECIMAL
 */
public record MappingOptions(GeoParquetVersion version, int nestingDepth,
		BigNumberEncoding bigNumberEncoding, int decimalScale) {

	/**
	 * @return the default options
	 */
	public static MappingOptions defaults() {
		return new MappingOptions(GeoParquetVersion.V2_0, 5, BigNumberEncoding.STRING, 9);
	}

}
