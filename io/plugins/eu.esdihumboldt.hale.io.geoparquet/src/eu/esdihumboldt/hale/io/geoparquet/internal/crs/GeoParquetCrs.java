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
package eu.esdihumboldt.hale.io.geoparquet.internal.crs;

import java.util.Optional;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.referencing.CRS;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.instance.geometry.CRSDefinitionUtil;
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;

/**
 * Describes the coordinate reference system of a GeoParquet geometry column.
 */
public final class GeoParquetCrs {

	private static final int WGS84 = 4326;

	/**
	 * Value for the Parquet geometry logical type to express an unknown CRS.
	 */
	private static final String PARQUET_UNKNOWN = "srid:0";

	private final Integer epsgCode;
	private final String projJson;
	private final boolean crs84;
	private final boolean unknown;
	private final CoordinateReferenceSystem targetCrs;
	private final boolean swapAxes;

	private GeoParquetCrs(Integer epsgCode, String projJson, boolean crs84, boolean unknown,
			CoordinateReferenceSystem targetCrs) {
		this.epsgCode = epsgCode;
		this.projJson = projJson;
		this.crs84 = crs84;
		this.unknown = unknown;
		this.targetCrs = targetCrs;
		this.swapAxes = targetCrs != null
				&& CRS.getAxisOrder(targetCrs) == CRS.AxisOrder.NORTH_EAST;
	}

	/**
	 * Resolve the GeoParquet CRS description for a CRS definition.
	 *
	 * @param crs the CRS definition, may be <code>null</code>
	 * @param log the log for reporting problems
	 * @return the CRS description, unknown if no EPSG code can be determined or the
	 *         code is not supported
	 */
	public static GeoParquetCrs resolve(CRSDefinition crs, SimpleLog log) {
		if (crs == null) {
			log.warn("No coordinate reference system available, writing unknown CRS");
			return unknown();
		}

		CoordinateReferenceSystem target = null;
		try {
			target = crs.getCRS();
		} catch (RuntimeException e) {
			log.warn("Could not decode coordinate reference system: " + e.getMessage(), e);
		}
		if (target == null) {
			log.warn("Coordinate reference system could not be decoded, writing unknown CRS");
			return unknown();
		}

		Integer code = determineEpsgCode(crs, target);
		if (code == null) {
			log.warn("No EPSG code found for the coordinate reference system, writing unknown CRS");
			return new GeoParquetCrs(null, null, false, true, target);
		}

		if (code == WGS84) {
			return new GeoParquetCrs(code, null, true, false, target);
		}

		Optional<String> projJson = ProjJsonRegistry.getInstance().getProjJson(code);
		if (projJson.isEmpty()) {
			log.warn("No PROJJSON definition available for EPSG:" + code + ", writing unknown CRS");
			return new GeoParquetCrs(null, null, false, true, target);
		}
		return new GeoParquetCrs(code, projJson.get(), false, false, target);
	}

	private static Integer determineEpsgCode(CRSDefinition crs, CoordinateReferenceSystem target) {
		String code = null;
		try {
			code = CRSDefinitionUtil.getCode(crs);
		} catch (RuntimeException e) {
			// fall through to lookup
		}
		if (code != null) {
			String epsg = CodeDefinition.extractEPSGCode(code);
			if (epsg != null) {
				try {
					return Integer.valueOf(epsg);
				} catch (NumberFormatException e) {
					// fall through to lookup
				}
			}
		}
		if (target != null) {
			try {
				return CRS.lookupEpsgCode(target, true);
			} catch (Exception e) {
				// no code
			}
		}
		return null;
	}

	/**
	 * @return the description for OGC:CRS84 (WGS 84, lon/lat)
	 */
	public static GeoParquetCrs crs84() {
		CoordinateReferenceSystem lonLat;
		try {
			lonLat = CRS.decode("EPSG:4326", true);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot decode EPSG:4326", e);
		}
		return new GeoParquetCrs(WGS84, null, true, false, lonLat);
	}

	/**
	 * @return the description for an unknown CRS
	 */
	public static GeoParquetCrs unknown() {
		return new GeoParquetCrs(null, null, false, true, null);
	}

	/**
	 * @return if the CRS is OGC:CRS84 (WGS 84 in lon/lat order)
	 */
	public boolean isCrs84() {
		return crs84;
	}

	/**
	 * @return if the CRS is unknown
	 */
	public boolean isUnknown() {
		return unknown;
	}

	/**
	 * @return the EPSG code or <code>null</code> if there is none
	 */
	public Integer getEpsgCode() {
		return epsgCode;
	}

	/**
	 * @return the PROJJSON for the <code>geo</code> metadata <code>crs</code>
	 *         property, <code>null</code> for CRS84 (property is omitted) and
	 *         unknown CRS (property is written as JSON <code>null</code>)
	 */
	public String getProjJson() {
		return projJson;
	}

	/**
	 * @return the CRS value for the Parquet geometry logical type,
	 *         <code>null</code> for CRS84
	 */
	public String getParquetCrs() {
		if (crs84) {
			return null;
		}
		if (unknown) {
			return PARQUET_UNKNOWN;
		}
		return "EPSG:" + epsgCode;
	}

	/**
	 * Get the CRS the geometries are converted to before writing, as decoded from
	 * the definition. Non-null for every result that is not {@link #isUnknown()
	 * unknown} (CRS84 and EPSG results). For unknown results it is non-null if the
	 * source definition could be decoded (e.g. a CRS without EPSG code or with a
	 * code that is not in the registry) and <code>null</code> only if no CRS was
	 * given or it could not be decoded.
	 *
	 * @return the target CRS, see above for when it may be <code>null</code>
	 */
	public CoordinateReferenceSystem getTargetCrs() {
		return targetCrs;
	}

	/**
	 * @return if x and y must be swapped after conversion to the target CRS so the
	 *         written coordinates are in x/y (lon/lat) order
	 */
	public boolean isSwapAxes() {
		return swapAxes;
	}

}
