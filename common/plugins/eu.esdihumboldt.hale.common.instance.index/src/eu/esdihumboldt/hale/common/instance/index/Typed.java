/*
 * Copyright (c) 2017 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.instance.index;

import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Interface for objects that have a {@link TypeDefinition}
 *
 * @author Florian Esser
 */
public interface Typed {

	/**
	 * @return the type definition
	 */
	TypeDefinition getDefinition();
}
