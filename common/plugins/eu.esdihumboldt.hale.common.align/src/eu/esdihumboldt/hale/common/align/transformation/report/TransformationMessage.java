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
package eu.esdihumboldt.hale.common.align.transformation.report;

import eu.esdihumboldt.hale.common.core.report.Message;

/**
 * Transformation message
 *
 * @author Simon Templer
 */
public interface TransformationMessage extends Message {

	/**
	 * Get the transformation cell identifier.
	 *
	 * @return the identifier of the cell the message is associated to
	 */
	public String getCellId();

}
