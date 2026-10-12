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
package eu.esdihumboldt.hale.common.instance.model;

import java.util.Set;

import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Filter that can only match instances of a known set of types. Instance
 * collections may use this information to only read instances of these types.
 * <p>
 * The types are matched exactly, i.e. the filter must not match instances of
 * sub-types of the given types (unless they are included in the set
 * themselves). When a selection is performed based on this information, the
 * iteration order across different types is not necessarily the order of the
 * source collection.
 */
public interface TypeAwareFilter extends Filter {

	/**
	 * @return the types an instance must have to be able to match the filter, never
	 *         <code>null</code>
	 */
	Set<TypeDefinition> getTypes();

}
