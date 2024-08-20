
/*
 * Copyright (c) 2015 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.xsd.anytype

import groovy.transform.CompileStatic
import groovy.transform.Immutable


/**
 * Configuration of
 * @author Simon Templer
 */
@CompileStatic
@Immutable
class CustomTypeContentConfiguration {

	List<CustomTypeContentAssociation> associations
}
