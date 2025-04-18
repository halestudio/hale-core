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
package eu.esdihumboldt.hale.common.schema.model.constraint.property;

import javax.annotation.concurrent.Immutable;

import eu.esdihumboldt.hale.common.schema.model.Constraint;
import eu.esdihumboldt.hale.common.schema.model.GroupPropertyConstraint;
import eu.esdihumboldt.hale.common.schema.model.constraint.AbstractFlagConstraint;

/**
 * Flags if a property group is a choice, disabled by default
 *
 * @author Simon Templer
 */
@Immutable
@Constraint(mutable = false)
public class ChoiceFlag extends AbstractFlagConstraint implements GroupPropertyConstraint {

	/**
	 * Enabled choice flag
	 */
	public static final ChoiceFlag ENABLED = new ChoiceFlag(true);

	/**
	 * Disabled choice flag
	 */
	public static final ChoiceFlag DISABLED = new ChoiceFlag(false);

	/**
	 * Get the choice flag
	 *
	 * @param isChoice if the flag shall be enabled
	 * @return the flag
	 */
	public static ChoiceFlag get(boolean isChoice) {
		return (isChoice) ? (ENABLED) : (DISABLED);
	}

	/**
	 * Creates a default choice flag, which is disabled. If possible, instead of
	 * creating an instance, use {@link #get(boolean)}, {@link #ENABLED} or
	 * {@link #DISABLED}.
	 *
	 * @see Cardinality
	 */
	public ChoiceFlag() {
		this(false);
	}

	/**
	 * @see AbstractFlagConstraint#AbstractFlagConstraint(boolean)
	 */
	private ChoiceFlag(boolean enabled) {
		super(enabled);
	}
}
