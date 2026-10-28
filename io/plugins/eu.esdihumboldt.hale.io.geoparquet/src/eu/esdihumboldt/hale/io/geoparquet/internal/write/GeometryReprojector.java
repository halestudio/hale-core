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

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs;

/**
 * Transforms geometries to the CRS of a GeoParquet column and swaps the axes to
 * lon/lat order if required. Caches the transformations. Not thread-safe.
 */
public class GeometryReprojector {

	/**
	 * The transformations per source and target CRS, or the failure to find one.
	 */
	private final Map<LookupKey, Lookup> transforms = new HashMap<>();

	/**
	 * Transform a geometry to the target CRS.
	 *
	 * @param geometry the geometry, is not modified
	 * @param sourceCrs the CRS of the geometry, may be <code>null</code>
	 * @param target the target CRS description
	 * @param log the log for reporting problems; if no transformation between the
	 *            CRS can be found, this is reported only once per CRS pair and log
	 * @param context description of the geometry for error messages
	 * @return the geometry in the target CRS (x/y being lon/lat for geographic
	 *         CRS), the given geometry itself if it needs no change or if it has no
	 *         source CRS (it is assumed to be in the target CRS), <code>null</code>
	 *         if the transformation failed (reported to the log; the geometry must
	 *         not be written, its coordinates are not in the target CRS)
	 */
	public Geometry reproject(Geometry geometry, CRSDefinition sourceCrs, GeoParquetCrs target,
			SimpleLog log, String context) {
		CoordinateReferenceSystem targetCrs = target.getTargetCrs();

		boolean copied = false;
		if (targetCrs != null && sourceCrs != null && sourceCrs.getCRS() != null) {
			Geometry transformed = transform(geometry, sourceCrs.getCRS(), targetCrs, true, log,
					context);
			if (transformed == null) {
				// geometry is not in the target CRS and must not be written
				return null;
			}
			copied = transformed != geometry;
			geometry = transformed;
		}

		if (target.isSwapAxes()) {
			geometry = swapXY(geometry, copied);
		}
		return geometry;
	}

	/**
	 * Transform a geometry from one CRS to another, without changing the axis
	 * order.
	 *
	 * @param geometry the geometry, is not modified
	 * @param source the CRS of the geometry
	 * @param target the target CRS
	 * @param lenient if the transformation lookup should be lenient (e.g. if datum
	 *            shift parameters are missing)
	 * @param log the log for reporting problems; if no transformation between the
	 *            CRS can be found, this is reported only once per CRS pair and log
	 * @param context description of the geometry for error messages
	 * @return the transformed geometry, the given geometry itself if the
	 *         transformation is the identity, <code>null</code> if the
	 *         transformation failed (reported to the log)
	 */
	public Geometry transform(Geometry geometry, CoordinateReferenceSystem source,
			CoordinateReferenceSystem target, boolean lenient, SimpleLog log, String context) {
		MathTransform transform = findTransform(source, target, lenient, log);
		if (transform == null) {
			return null;
		}
		if (transform.isIdentity()) {
			return geometry;
		}
		try {
			return JTS.transform(geometry, transform);
		} catch (Exception e) {
			log.error("Could not convert geometry to target CRS" + context, e);
			return null;
		}
	}

	/**
	 * Find the transformation between two CRS (cached, also if it fails).
	 *
	 * @return the transformation or <code>null</code> if there is none (reported
	 *         once per log)
	 */
	private MathTransform findTransform(CoordinateReferenceSystem source,
			CoordinateReferenceSystem target, boolean lenient, SimpleLog log) {
		Lookup lookup = transforms.computeIfAbsent(new LookupKey(source, target, lenient), k -> {
			try {
				return new Lookup(CRS.findMathTransform(source, target, lenient), null);
			} catch (Exception e) {
				return new Lookup(null, e);
			}
		});
		if (lookup.failure() != null && lookup.reportedTo().add(log)) {
			log.error(
					"Could not find a transformation from " + source.getName() + " to "
							+ target.getName()
							+ ", geometries in this CRS are not written (reported only once)",
					lookup.failure());
		}
		return lookup.transform();
	}

	/**
	 * Key for the cached transformations.
	 */
	private record LookupKey(CoordinateReferenceSystem source, CoordinateReferenceSystem target,
			boolean lenient) {
	}

	/**
	 * Result of the lookup of a transformation.
	 *
	 * @param transform the transformation, <code>null</code> if the lookup failed
	 * @param failure the failure, <code>null</code> if the lookup succeeded
	 * @param reportedTo the logs the failure was reported to
	 */
	private record Lookup(MathTransform transform, Exception failure, Set<SimpleLog> reportedTo) {

		Lookup(MathTransform transform, Exception failure) {
			this(transform, failure, Collections.newSetFromMap(new IdentityHashMap<>()));
		}
	}

	/**
	 * Swap the x and y ordinates of a geometry.
	 *
	 * @param geometry the geometry
	 * @param inPlace if the given geometry may be modified, otherwise a copy is
	 *            changed
	 * @return the geometry with swapped ordinates
	 */
	public static Geometry swapXY(Geometry geometry, boolean inPlace) {
		Geometry result = inPlace ? geometry : geometry.copy();
		result.apply(new CoordinateSequenceFilter() {

			@Override
			public void filter(CoordinateSequence seq, int i) {
				double x = seq.getOrdinate(i, 0);
				seq.setOrdinate(i, 0, seq.getOrdinate(i, 1));
				seq.setOrdinate(i, 1, x);
			}

			@Override
			public boolean isDone() {
				return false;
			}

			@Override
			public boolean isGeometryChanged() {
				return true;
			}
		});
		return result;
	}

}
