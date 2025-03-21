/*
 * Copyright (c) 2014 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.inspire.apps.templates


/**
 * Execution context for the {@link GenerateTemplatesApplication}.
 *
 * @author Simon Templer
 */
class GenerateTemplatesContext {

	/**
	 * The target directory to write the template projects to.
	 */
	File targetDir

	/**
	 * If references to INSPIRE code lists should be explicit (to be backwards compatible with 2.8)
	 */
	boolean explicit = false
}
