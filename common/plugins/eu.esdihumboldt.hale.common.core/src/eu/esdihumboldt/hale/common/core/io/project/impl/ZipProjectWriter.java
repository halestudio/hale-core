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
package eu.esdihumboldt.hale.common.core.io.project.impl;

/**
 * Writes archived projects
 *
 * @author Simon Templer
 */
public class ZipProjectWriter extends DefaultProjectWriter {

	/**
	 * Default constructor
	 */
	public ZipProjectWriter() {
		super(true);
	}

}
