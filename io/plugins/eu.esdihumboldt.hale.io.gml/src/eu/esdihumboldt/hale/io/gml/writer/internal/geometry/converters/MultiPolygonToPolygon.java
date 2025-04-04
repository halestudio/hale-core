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
package eu.esdihumboldt.hale.io.gml.writer.internal.geometry.converters;

import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

/**
 * Converts {@link MultiPolygon}s to {@link Polygon}s
 *
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 * @version $Id$
 */
public class MultiPolygonToPolygon
		extends AbstractGeometryCollectionConverter<MultiPolygon, Polygon> {

	/**
	 * Default constructor
	 */
	public MultiPolygonToPolygon() {
		super(MultiPolygon.class, Polygon.class);
	}

	/**
	 * @see AbstractGeometryCollectionConverter#createEmptyGeometry()
	 */
	@Override
	protected Polygon createEmptyGeometry() {
		return geomFactory.createPolygon(null, null);
	}

}
