
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

/**
 * Tests creation of {@link Binding} constraints w/ {@link BindingFactory}.
 *
 * @author Simon Templer
 */
@CompileStatic
class BindingFactoryTest {

	/*
	 * NOTE: In Eclipse in the editor there might be errors shown here,
	 * even if the code actually compiles.
	 */

	@Test
	void testClass() {
		assertEquals String, BindingFactory.instance.createConstraint(String).binding
		assertEquals Integer, BindingFactory.instance.createConstraint(Integer).binding
	}
}
