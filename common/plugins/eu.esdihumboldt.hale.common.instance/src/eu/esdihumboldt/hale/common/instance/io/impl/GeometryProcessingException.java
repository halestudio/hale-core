/*
 * Copyright (c) 2026 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.instance.io.impl;

/**
 * Exception thrown when a geometry cannot be processed (e.g. unified) while
 * writing instances. The message is enriched with information about the
 * affected source element (feature type, feature identifier, geometry excerpt),
 * where available, to help locate the problematic geometry in the source data.
 */
public class GeometryProcessingException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Create a new instance.
	 *
	 * @param message the enriched error message
	 * @param cause the original cause
	 */
	public GeometryProcessingException(String message, Throwable cause) {
		super(message, cause);
	}

}
