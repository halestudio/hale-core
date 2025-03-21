/*
 * Copyright (c) 2013 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.codelist.config

import groovy.transform.Immutable


/**
 * Code list reference identifying a code list by namespace and identifier.
 *
 * @author Simon Templer
 */
@Immutable
class CodeListReference {

	/**
	 * The code list namespace.
	 */
	String namespace

	/**
	 * The code list identifier.
	 */
	String identifier
}
