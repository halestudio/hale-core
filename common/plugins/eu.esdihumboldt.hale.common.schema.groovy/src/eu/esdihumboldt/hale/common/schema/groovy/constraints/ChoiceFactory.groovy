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
package eu.esdihumboldt.hale.common.schema.groovy.constraints

import groovy.transform.CompileStatic

import eu.esdihumboldt.hale.common.schema.model.Definition
import eu.esdihumboldt.hale.common.schema.model.constraint.property.ChoiceFlag


/**
 * Factory for {@link ChoiceFlag} constraint.
 *
 * @author Simon Templer
 */
@Singleton
@CompileStatic
class ChoiceFactory extends OptionalContextConstraintFactory<ChoiceFlag> {

	@Override
	public ChoiceFlag createConstraint(Object arg, Definition<?> context) {
		arg ? ChoiceFlag.ENABLED : ChoiceFlag.DISABLED
	}
}
