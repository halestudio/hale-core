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

import static org.junit.Assert.*

import groovy.transform.CompileStatic

import org.junit.Test

import eu.esdihumboldt.hale.common.schema.model.constraint.property.ChoiceFlag

/**
 * Tests creation of {@link ChoiceFlag} constraints w/ {@link ChoiceFactory}.
 *
 * @author Simon Templer
 */
@CompileStatic
class ChoiceFactoryTest {

	/*
	 * NOTE: In Eclipse in the editor there might be errors shown here,
	 * even if the code actually compiles.
	 */

	@Test
	void testTrueFalse() {
		assertEquals ChoiceFlag.ENABLED, ChoiceFactory.instance.createConstraint(true)
		assertEquals ChoiceFlag.DISABLED, ChoiceFactory.instance.createConstraint(false)
	}
}
