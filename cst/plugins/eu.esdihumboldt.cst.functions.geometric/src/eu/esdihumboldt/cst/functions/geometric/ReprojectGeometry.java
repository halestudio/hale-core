/*
 * Copyright (c) 2024 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.cst.functions.geometric;

import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;

import com.google.common.collect.ListMultimap;

import eu.esdihumboldt.hale.common.align.model.impl.PropertyEntityDefinition;
import eu.esdihumboldt.hale.common.align.transformation.engine.TransformationEngine;
import eu.esdihumboldt.hale.common.align.transformation.function.PropertyValue;
import eu.esdihumboldt.hale.common.align.transformation.function.TransformationException;
import eu.esdihumboldt.hale.common.align.transformation.function.impl.AbstractSingleTargetPropertyTransformation;
import eu.esdihumboldt.hale.common.align.transformation.function.impl.NoResultException;
import eu.esdihumboldt.hale.common.align.transformation.report.TransformationLog;
import eu.esdihumboldt.hale.common.instance.geometry.CRSDefinitionManager;
import eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty;
import eu.esdihumboldt.hale.common.instance.geometry.GeometryFinder;
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition;
import eu.esdihumboldt.hale.common.instance.helper.DepthFirstInstanceTraverser;
import eu.esdihumboldt.hale.common.instance.helper.InstanceTraverser;
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;

/**
 * Reproject geometry function.
 *
 * @author Sandro Salari
 * @author Stefano Costa, GeoSolutions
 */
@Immutable
public class ReprojectGeometry
		extends AbstractSingleTargetPropertyTransformation<TransformationEngine>
		implements ReprojectGeometryFunction {

	@Override
	protected Object evaluate(String transformationIdentifier, TransformationEngine engine,
			ListMultimap<String, PropertyValue> variables, String resultName,
			PropertyEntityDefinition resultProperty, Map<String, String> executionParameters,
			TransformationLog log) throws TransformationException, NoResultException {

		// Get input geometry
		PropertyValue input = variables.get("source").get(0);
		Object inputValue = input.getValue();
		InstanceTraverser traverser = new DepthFirstInstanceTraverser(true);
		GeometryFinder geoFind = new GeometryFinder(null);
		traverser.traverse(inputValue, geoFind);
		List<GeometryProperty<?>> geoms = geoFind.getGeometries();
		Geometry sourceGeometry = geoms.get(0).getGeometry();

		CRSDefinition crsDef = geoms.get(0).getCRSDefinition();
		if (crsDef == null) {
			throw new TransformationException(
					"Geometry does not have an associated Coordinate Reference System");
		}
		CoordinateReferenceSystem sourceCRS = crsDef.getCRS();

		Geometry resultGeometry = sourceGeometry;
		CoordinateReferenceSystem targetCRS = sourceCRS;

		// Get input parameter
		String srs = getParameterChecked(PARAMETER_REFERENCE_SYSTEM).as(String.class);
		if (srs != null) {
			try {
				targetCRS = parseReferenceSystemParamter(srs);
			} catch (Exception e) {
				throw new TransformationException(
						"Error determining destination Cordinate Reference System.", e);
			}

			// Retrieve transformation from cell context, or create a new
			// instance
			Map<Object, Object> cellContext = getExecutionContext().getCellContext();
			MathTransform transform = getOrCreateMathTransform(sourceCRS, targetCRS, cellContext);

			// Apply transformation
			try {
				resultGeometry = JTS.transform(sourceGeometry, transform);
			} catch (MismatchedDimensionException | TransformException e) {
				throw new TransformationException(
						"Problem on execute transformation from: " + sourceCRS + " to " + targetCRS,
						e);
			}
		}

		return new DefaultGeometryProperty<Geometry>(
				new CodeDefinition(CRS.toSRS(targetCRS), targetCRS), resultGeometry);

	}

	/**
	 * Construct a {@link CoordinateReferenceSystem} instance by parsing the input
	 * string via a {@link CRSDefinitionManager} instance.
	 *
	 * @param crs string representation of the CRS
	 * @return the {@link CoordinateReferenceSystem} instance
	 */
	private CoordinateReferenceSystem parseReferenceSystemParamter(String crs) {
		CoordinateReferenceSystem parsedCrs = null;

		if (crs != null) {
			CRSDefinition crsDef = CRSDefinitionManager.getInstance().parse(crs);
			if (crsDef != null) {
				parsedCrs = crsDef.getCRS();
			}
			else {
				throw new IllegalArgumentException("Could not parse CRS definition.");
			}
		}

		return parsedCrs;
	}

	/**
	 * Attempt to find a math transform between the specified Coordinate Reference
	 * Systems.
	 * <p>
	 * The method first tries to look up a relevant MathTransform instance from the
	 * provided {@code context} object; then, if none was found, it creates a new
	 * one and stores it in the context, to allow its reuse by following reproject
	 * transformations.
	 * </p>
	 *
	 * @param sourceCRS The source CRS.
	 * @param targetCRS The target CRS.
	 * @param context The context.
	 * @return The math transform from {@code sourceCRS} to {@code targetCRS}.
	 * @throws TransformationException if no math transform could be found
	 */
	private MathTransform getOrCreateMathTransform(CoordinateReferenceSystem sourceCRS,
			CoordinateReferenceSystem targetCRS, Map<Object, Object> context)
			throws TransformationException {
		MathTransform transform = null;
		String key = sourceCRS.getName().hashCode() + " --> " + targetCRS.getName().hashCode();

		synchronized (context) {
			transform = (MathTransform) context.get(key);
			if (transform == null) {
				transform = createMathTransform(sourceCRS, targetCRS);

				context.put(key, transform);
			}
		}

		return transform;
	}

	/**
	 * Attempt to find a math transform between the specified Coordinate Reference
	 * Systems.
	 *
	 * @param sourceCRS The source CRS.
	 * @param targetCRS The target CRS.
	 * @return The math transform from {@code sourceCRS} to {@code targetCRS}.
	 * @throws TransformationException if no math transform could be found
	 */
	private MathTransform createMathTransform(CoordinateReferenceSystem sourceCRS,
			CoordinateReferenceSystem targetCRS) throws TransformationException {
		MathTransform transform = null;
		try {
			transform = CRS.findMathTransform(sourceCRS, targetCRS, false);
			// Transformation cannot be found because either the sourceCRS or
			// the targetCRS is missing bursa-wolf parameters
		} catch (FactoryException ex1) {
			try {
				Integer sourceEpsgCode = CRS.lookupEpsgCode(sourceCRS, true);
				Integer targetEpsgCode = CRS.lookupEpsgCode(targetCRS, true);
				if (sourceEpsgCode != null && targetEpsgCode != null) {
					transform = CRS.findMathTransform(CRS.decode("EPSG:" + sourceEpsgCode, true),
							CRS.decode("EPSG:" + targetEpsgCode, true));
				}
				else {
					throw new TransformationException(
							"Unable to find requested transformation from: " + sourceCRS + " to "
									+ targetCRS);
				}
			} catch (FactoryException ex2) {
				throw new TransformationException(
						"Problem on execute transformation from: " + sourceCRS + " to " + targetCRS,
						ex2);
			}
		}
		return transform;
	}
}
