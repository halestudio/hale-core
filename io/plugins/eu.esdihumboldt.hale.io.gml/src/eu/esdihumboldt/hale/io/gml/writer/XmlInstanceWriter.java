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
package eu.esdihumboldt.hale.io.gml.writer;

import eu.esdihumboldt.hale.io.gml.writer.internal.StreamGmlWriter;

/**
 * Writes instances to a XML file
 *
 * @author Simon Templer
 */
public class XmlInstanceWriter extends StreamGmlWriter {

	/**
	 * Default constructor
	 */
	public XmlInstanceWriter() {
		super(false);
	}

}
