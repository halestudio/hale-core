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

/**
 * The supported GeoParquet specification versions.
 */
public enum GeoParquetVersion {

	/** GeoParquet 1.0.0. */
	V1_0("1.0.0"),
	/** GeoParquet 1.1.0. */
	V1_1("1.1.0"),
	/** GeoParquet 2.0.0 (using the native Parquet GEOMETRY logical type). */
	V2_0("2.0.0");

	private final String versionString;

	private GeoParquetVersion(String versionString) {
		this.versionString = versionString;
	}

	/**
	 * @return the version string as written to the <code>geo</code> metadata
	 */
	public String getVersionString() {
		return versionString;
	}

	/**
	 * @return if geometry columns are annotated with the Parquet GEOMETRY logical
	 *         type
	 */
	public boolean usesNativeGeometryType() {
		return this == V2_0;
	}

	/**
	 * @return if the bbox covering is declared in the <code>geo</code> metadata
	 */
	public boolean declaresCovering() {
		return this == V1_1 || this == V2_0;
	}

	/**
	 * Determine the version from a parameter value.
	 *
	 * @param value the parameter value, accepts short (e.g. "1.1") and full (e.g.
	 *            "1.1.0") version strings; <code>null</code> or empty yields the
	 *            default (2.0)
	 * @return the version
	 * @throws IllegalArgumentException if the value is not a supported version
	 */
	public static GeoParquetVersion fromParameter(String value) {
		if (value == null || value.isBlank()) {
			return V2_0;
		}
		String v = value.trim();
		for (GeoParquetVersion version : values()) {
			if (version.versionString.equals(v)
					|| version.versionString.substring(0, 3).equals(v)) {
				return version;
			}
		}
		throw new IllegalArgumentException("Unsupported GeoParquet version: " + value);
	}

}
