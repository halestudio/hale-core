/*
 * Copyright (c) 2014 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.instance.io.impl;

import java.util.Collection;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;

import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.core.io.report.IOReporter;
import eu.esdihumboldt.hale.common.core.io.report.impl.IOMessageImpl;
import eu.esdihumboldt.hale.common.instance.geometry.CRSDefinitionManager;
import eu.esdihumboldt.hale.common.instance.geometry.CRSDefinitionUtil;
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition;
import eu.esdihumboldt.hale.common.instance.io.GeoInstanceWriter;
import eu.esdihumboldt.hale.common.instance.io.util.EnumWindingOrderTypes;
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;
import eu.esdihumboldt.util.Pair;
import eu.esdihumboldt.util.geometry.WindingOrder;

/**
 * Abstract {@link GeoInstanceWriter} base implementation
 *
 * @author Simon Templer
 * @since 2.9
 */
public abstract class AbstractGeoInstanceWriter extends AbstractInstanceWriter
		implements GeoInstanceWriter {

	/**
	 * Maximum number of characters of a WKT representation of a geometry to
	 * include in an error message.
	 */
	private static final int WKT_EXCERPT_MAX_LENGTH = 200;

	/**
	 * Identifying information about the source element (feature) currently
	 * being written, used to enrich geometry error messages so that the
	 * affected element can be located in the source data. Any of the fields
	 * may be <code>null</code> if the information is not available.
	 */
	public static class FeatureContext {

		private final String featureType;
		private final String featureId;

		/**
		 * Create a new feature context.
		 *
		 * @param featureType a display name for the feature type, may be
		 *            <code>null</code>
		 * @param featureId the feature's identifier (e.g. gml:id), may be
		 *            <code>null</code>
		 */
		public FeatureContext(String featureType, String featureId) {
			this.featureType = featureType;
			this.featureId = featureId;
		}

		/**
		 * @return a display name for the feature type, may be
		 *         <code>null</code>
		 */
		public String getFeatureType() {
			return featureType;
		}

		/**
		 * @return the feature's identifier (e.g. gml:id), may be
		 *         <code>null</code>
		 */
		public String getFeatureId() {
			return featureId;
		}

	}

	private FeatureContext currentFeatureContext;

	/**
	 * Set the context describing the feature currently being written, to be
	 * included in geometry error messages raised while writing it.
	 * Subclasses that have access to the source instance should call this
	 * before writing an instance's properties, and restore the previous
	 * context (as returned by this method) afterwards, e.g. in a
	 * <code>finally</code> block.
	 *
	 * @param context the current feature context, or <code>null</code> to
	 *            reset it
	 * @return the previous context, may be <code>null</code>
	 */
	protected FeatureContext setCurrentFeatureContext(FeatureContext context) {
		FeatureContext previous = this.currentFeatureContext;
		this.currentFeatureContext = context;
		return previous;
	}

	/**
	 * Build a diagnostic suffix identifying the feature/geometry associated
	 * with a geometry-related error, for inclusion in error messages.
	 * Information that is not available is simply omitted.
	 *
	 * @param geom the geometry associated with the error, may be
	 *            <code>null</code>
	 * @return a describing string, starting with a separator if not empty
	 */
	protected String describeGeometryContext(Geometry geom) {
		StringBuilder sb = new StringBuilder();

		FeatureContext context = currentFeatureContext;
		if (context != null) {
			if (context.getFeatureType() != null) {
				sb.append(" [feature type: ").append(context.getFeatureType()).append(']');
			}
			if (context.getFeatureId() != null) {
				sb.append(" [feature gml:id: ").append(context.getFeatureId()).append(']');
			}
		}

		String wkt = toWktExcerpt(geom);
		if (wkt != null) {
			sb.append(" [geometry: ").append(wkt).append(']');
		}

		return sb.toString();
	}

	/**
	 * Create a (possibly truncated) WKT representation of a geometry, for use
	 * in error messages.
	 *
	 * @param geom the geometry, may be <code>null</code>
	 * @return the WKT excerpt, or <code>null</code> if it is not available
	 */
	private static String toWktExcerpt(Geometry geom) {
		if (geom == null) {
			return null;
		}
		try {
			String wkt = geom.toText();
			if (wkt != null && wkt.length() > WKT_EXCERPT_MAX_LENGTH) {
				wkt = wkt.substring(0, WKT_EXCERPT_MAX_LENGTH) + "...";
			}
			return wkt;
		} catch (Exception e) {
			// WKT representation could not be created, omit it
			return null;
		}
	}

	@Override
	public void setTargetCRS(CRSDefinition crs) {
		setParameter(PARAM_TARGET_CRS, Value.of(CRSDefinitionManager.getInstance().asString(crs)));
	}

	@Override
	public CRSDefinition getTargetCRS() {
		return CRSDefinitionManager.getInstance()
				.parse(getParameter(PARAM_TARGET_CRS).as(String.class));
	}

	@Override
	public void setCustomEPSGPrefix(String epsgPrefix) {
		setParameter(PARAM_CRS_CODE_FORMAT, Value.of(epsgPrefix));
	}

	@Override
	public String getCustomEPSGPrefix() {
		return getParameter(PARAM_CRS_CODE_FORMAT).as(String.class);
	}

	@Override
	public void setWindingOrder(EnumWindingOrderTypes windingOrderType) {
		setParameter(PARAM_UNIFY_WINDING_ORDER, Value.of(windingOrderType.toString()));
	}

	@Override
	public EnumWindingOrderTypes getWindingOrder() {
		EnumWindingOrderTypes value = getParameter(PARAM_UNIFY_WINDING_ORDER)
				.as(EnumWindingOrderTypes.class);
		if (value == null)
			return getDefaultWindingOrder();
		else
			return value;
	}

	/**
	 * Get default Winding Order. Function is to give functionality to the subType
	 * to change the default Winding order.
	 *
	 * @return EnumWindingOrderTypes default Winding order
	 */
	protected EnumWindingOrderTypes getDefaultWindingOrder() {
		return EnumWindingOrderTypes.noChanges;
	}

	/**
	 * Convert the given geometry to the target CRS, if possible (and a target CRS
	 * is set).
	 *
	 * @param geom the geometry to convert
	 * @param sourceCrs the source CRS
	 * @param report the reporter
	 * @return a pair of geometry and CRS definition, either the converted geometry
	 *         and the target CRS or the given geometry and the source CRS
	 */
	protected Pair<Geometry, CRSDefinition> convertGeometry(Geometry geom, CRSDefinition sourceCrs,
			IOReporter report) {
		if (getTargetCRS() != null && getTargetCRS().getCRS() != null) {
			if (sourceCrs != null && sourceCrs.getCRS() != null) {
				try {
					// TODO cache mathtransforms?
					MathTransform transform = CRS.findMathTransform(sourceCrs.getCRS(),
							getTargetCRS().getCRS());
					Geometry targetGeometry = JTS.transform(geom, transform);
					return new Pair<>(targetGeometry, getTargetCRS());
				} catch (Exception e) {
					if (report != null) {
						report.error(new IOMessageImpl(
								"Could not convert geometry to target CRS"
										+ describeGeometryContext(geom),
								e));
					}
					// return original geometry
					return new Pair<>(geom, sourceCrs);
				}
			}
			else {
				// Report that the transformation could not performed b/c no
				// valid source CRS was passed
				if (report != null) {
					report.error(new IOMessageImpl(
							"Could not convert geometry to target CRS: No source CRS provided"
									+ describeGeometryContext(geom),
							null));
				}
				return new Pair<>(geom, sourceCrs);
			}
		}
		else {
			// return original geometry
			return new Pair<>(geom, sourceCrs);
		}
	}

	/**
	 * Returns a pair of geometry and associated CRS definition for the given value.
	 * The value has to be a Geometry or a GeometryProperty, otherwise
	 * <code>null</code> is returned.
	 *
	 * @param value the value to extract the information from
	 * @param allowConvert if conversion to the target CRS should be performed if
	 *            applicable
	 * @param report the reporter
	 * @return a pair of geometry and CRS definition (latter may be
	 *         <code>null</code>), or <code>null</code> if the argument doesn't
	 *         contain a geometry
	 */
	protected Pair<Geometry, CRSDefinition> extractGeometry(Object value, boolean allowConvert,
			IOReporter report) {
		Pair<Geometry, CRSDefinition> pair = getGeometryPair(value, allowConvert, report);

		if (pair == null)
			return null;

		return unifyGeometryPair(pair, report);
	}

	/**
	 * Returns a pair of geometry and associated CRS definition for the given value.
	 * The value has to be a Geometry or a GeometryProperty, otherwise
	 * <code>null</code> is returned.
	 *
	 * @param value the value to extract the information from
	 * @param allowConvert if conversion to the target CRS should be performed if
	 *            applicable
	 * @param report the reporter
	 * @return a pair of geometry and CRS definition (latter may be
	 *         <code>null</code>), or <code>null</code> if the argument doesn't
	 *         contain a geometry
	 */
	private Pair<Geometry, CRSDefinition> getGeometryPair(Object value, boolean allowConvert,
			IOReporter report) {
		// TODO collection handling (-> happens for example with target
		// CompositeSurface)
		if (value instanceof Collection) {
			if (!((Collection<?>) value).isEmpty()) {
				// TODO combine geometries?
				value = ((Collection<?>) value).iterator().next();
			}
		}
		if (value instanceof Geometry) {
			return new Pair<>((Geometry) value, null);
		}
		else if (value instanceof GeometryProperty<?>) {
			CRSDefinition def = ((GeometryProperty<?>) value).getCRSDefinition();
			Geometry geom = ((GeometryProperty<?>) value).getGeometry();
			if (allowConvert) {
				return convertGeometry(geom, def, report);
			}
			return new Pair<>(geom, def);
		}
		else
			return null;
	}

	/**
	 * Returns a pair of unified geometry of given geometry and associated CRS
	 * definition based on Winding order supplied.
	 *
	 * @param pair A pair of Geometry and CRSDefinition, on which winding process
	 *            will get done.
	 * @param report the reporter
	 * @return Unified Pair .
	 */
	protected Pair<Geometry, CRSDefinition> unifyGeometryPair(Pair<Geometry, CRSDefinition> pair,
			IOReporter report) {

		// get Geometry object
		Geometry geom = pair.getFirst();
		if (geom == null) {
			return pair;
		}

		// getting CRS
		CRSDefinition def = pair.getSecond();
		CoordinateReferenceSystem crs = null;
		if (def != null)
			crs = pair.getSecond().getCRS();

		// unify geometry
		geom = unifyGeometry(geom, report, crs);

		return new Pair<>(geom, pair.getSecond());
	}

	/**
	 * Returns a unified geometry of given geometry based on Winding order supplied.
	 *
	 * @param geom The Geometry object, on which winding process will get done.
	 * @param report the reporter
	 * @param crs Coordinate Reference System
	 * @return Unified geometry .
	 */
	protected Geometry unifyGeometry(Geometry geom, IOReporter report,
			CoordinateReferenceSystem crs) {
		if (geom == null) {
			return geom;
		}
		// getting winding order
		EnumWindingOrderTypes windingOrder = getWindingOrder();

		if (windingOrder == null || windingOrder == EnumWindingOrderTypes.noChanges) {
			return geom;
		}
		else {
			Geometry unifiedGeometry;
			// unify geometry using WindingOrder utility.

			switch (windingOrder) {

			case counterClockwise:
				unifiedGeometry = unifyWindingOrderSafely(geom, true, crs);
				break;
			case clockwise:
				unifiedGeometry = unifyWindingOrderSafely(geom, false, crs);
				break;
			default:
				if (report != null) {
					report.error(new IOMessageImpl(
							"Parameter encountered as winding order is not known: "
									+ windingOrder.toString() + describeGeometryContext(geom),
							null));
				}
				unifiedGeometry = geom;
				break;
			}
			return unifiedGeometry;
		}
	}

	/**
	 * Unify the winding order of a geometry, enriching the error message with
	 * information identifying the affected feature/geometry (where available)
	 * if the underlying geometry processing fails, e.g. because of an invalid
	 * source geometry. The exception is rethrown so that behavior on
	 * encountering an invalid geometry is unchanged; only the log output is
	 * improved.
	 *
	 * @param geom the geometry to unify
	 * @param counterClockWise whether to unify to counter-clockwise or
	 *            clockwise order
	 * @param crs the CRS of the geometry
	 * @return the unified geometry
	 */
	private Geometry unifyWindingOrderSafely(Geometry geom, boolean counterClockWise,
			CoordinateReferenceSystem crs) {
		try {
			return WindingOrder.unifyWindingOrder(geom, counterClockWise, crs);
		} catch (RuntimeException e) {
			String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			throw new GeometryProcessingException(
					"Could not unify winding order of geometry: " + cause
							+ describeGeometryContext(geom),
					e);
		}
	}

	/**
	 * Extract a CRS code from the given CRS definition.
	 *
	 * @param crsDef the CRS definition
	 * @return the CRS code, may be <code>null</code>
	 */
	protected String extractCode(CRSDefinition crsDef) {
		if (crsDef == null) {
			return null;
		}
		String orgCode = CRSDefinitionUtil.getCode(crsDef);
		String customPrefix = getCustomEPSGPrefix();
		if (orgCode != null && customPrefix != null) {
			// try to extract EPSG code
			String epsgCode = CodeDefinition.extractEPSGCode(orgCode);
			if (epsgCode != null) {
				return customPrefix + epsgCode;
			}
		}
		return orgCode;
	}

}
