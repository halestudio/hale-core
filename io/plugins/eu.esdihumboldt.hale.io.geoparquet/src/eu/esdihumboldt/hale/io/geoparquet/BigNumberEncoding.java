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

import java.util.Locale;

/**
 * How big numbers (BigInteger, BigDecimal) are encoded in Parquet.
 */
public enum BigNumberEncoding {

	/** As string (lossless, default). */
	STRING,
	/** As double (lossy). */
	DOUBLE,
	/** As Parquet DECIMAL with precision 38. */
	DECIMAL;

	/**
	 * Determine the encoding from a parameter value.
	 *
	 * @param value the parameter value (case-insensitive), <code>null</code> or
	 *            empty yields {@link #STRING}
	 * @return the encoding
	 * @throws IllegalArgumentException if the value is unknown
	 */
	public static BigNumberEncoding fromParameter(String value) {
		if (value == null || value.isBlank()) {
			return STRING;
		}
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}

}
