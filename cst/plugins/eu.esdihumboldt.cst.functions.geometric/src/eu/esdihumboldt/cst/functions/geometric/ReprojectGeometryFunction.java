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

/**
 * Reproject geometry function constants.
 *
 * @author Sandro Salari
 * @author Stefano Costa, GeoSolutions
 */
public interface ReprojectGeometryFunction {

	/**
	 * Name of the parameter specifying the target reference system
	 */
	public static final String PARAMETER_REFERENCE_SYSTEM = "referenceSystem";

	/**
	 * the reproject geometry function Id
	 */
	public static final String ID = "eu.esdihumboldt.cst.functions.geometric.reproject_geometry";

}
