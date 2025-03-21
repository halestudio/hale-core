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

import eu.esdihumboldt.hale.common.schema.model.constraint.type.AbstractFlag

/**
 * Tests creation of {@link AbstractFlag} constraints w/ {@link TypeAbstractFactory}.
 *
 * @author Simon Templer
 */
@CompileStatic
class TypeAbstractFactoryTest {

	/*
	 * NOTE: In Eclipse in the editor there might be errors shown here,
	 * even if the code actually compiles.
	 */

	@Test
	void testTrueFalse() {
		assertEquals AbstractFlag.ENABLED, TypeAbstractFactory.instance.createConstraint(true)
		assertEquals AbstractFlag.DISABLED, TypeAbstractFactory.instance.createConstraint(false)
	}
}
