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
package eu.esdihumboldt.hale.common.core.io.report;

import eu.esdihumboldt.hale.common.core.report.Message;

/**
 * I/O report message
 *
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 * @since 2.2
 */
public interface IOMessage extends Message {

	/**
	 * Get the line number the message refers to
	 *
	 * @return the line number, <code>-1</code> if no line is referenced
	 */
	public int getLineNumber();

	/**
	 * Get the column the message refers to
	 *
	 * @return the column, <code>-1</code> if no column is referenced
	 */
	public int getColumn();

}
