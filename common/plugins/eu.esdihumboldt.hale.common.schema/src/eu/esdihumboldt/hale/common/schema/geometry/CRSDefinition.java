/*
 * Copyright (c) 2012 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.schema.geometry;

import java.io.Serializable;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;

/**
 * Represents a CRS definition using WKT or a CODE
 *
 * @author Simon Templer
 */
public interface CRSDefinition extends Serializable {

	/**
	 * Get the coordinate reference system
	 *
	 * @return the coordinate reference system
	 */
	public CoordinateReferenceSystem getCRS();

}
