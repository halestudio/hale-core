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
package eu.esdihumboldt.hale.common.schema.model;

/**
 * Marks constraints for {@link TypeDefinition}s
 *
 * @author Simon Templer
 */
public interface TypeConstraint {

	/**
	 * Determines if the constraint is inheritable, meaning it may be used for a
	 * sub-type. A constraint may only be inherited if it is not overriden in the
	 * sub-type.
	 *
	 * @return if the constraint is inheritable
	 */
	public boolean isInheritable();

}
