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
package eu.esdihumboldt.hale.io.gml.writer.internal.geometry.writers;

import javax.xml.namespace.QName;

import org.locationtech.jts.geom.MultiPolygon;

/**
 * Writes {@link MultiPolygon}s as MultiPolygon or MultiSurface.
 *
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 */
public class MultiPolygonWriter extends AbstractMultiPolygonWriter {

	/**
	 * Default constructor
	 */
	public MultiPolygonWriter() {
		// compatible types to serve as entry point
		addCompatibleType(new QName(Pattern.GML_NAMESPACE_PLACEHOLDER, "MultiPolygonType")); //$NON-NLS-1$
		addCompatibleType(new QName(Pattern.GML_NAMESPACE_PLACEHOLDER, "MultiSurfaceType")); //$NON-NLS-1$
	}

}
