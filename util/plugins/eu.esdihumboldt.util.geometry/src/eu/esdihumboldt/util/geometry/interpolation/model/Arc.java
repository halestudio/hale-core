
/*
 * Copyright (c) 2017 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.util.geometry.interpolation.model;

/**
 * General interface for Arc representations.
 *
 * @author Simon Templer
 */
public interface Arc extends ComplexGeometry {

	/**
	 * @return if the arc is closed and thus represents a circle
	 */
	boolean isCircle();

	/**
	 * @return a representation of the arc by center point
	 */
	ArcByCenterPoint toArcByCenterPoint();

	/**
	 * @return a representation of the arc by three points
	 */
	ArcByPoints toArcByPoints();

}
