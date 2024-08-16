/*
 * Copyright (c) 2014 Data Harmonisation Panel
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Data Harmonisation Panel <http://www.dhpanel.eu>
 */

package eu.esdihumboldt.hale.common.schema.model.constraint.factory.type

import eu.esdihumboldt.hale.common.schema.model.constraint.factory.AbstractPropertiesCompareConstraintFactoryTest
import eu.esdihumboldt.hale.common.schema.model.constraint.type.Binding
import org.junit.Test


/**
 * Tests for {@link BindingFactory}.
 *
 * @author Simon Templer
 */
class BindingFactoryTest extends AbstractPropertiesCompareConstraintFactoryTest<Binding> {

	@Test
	void testObject() {
		storeRestoreTest(Binding.get(Object))
	}

	@Test
	void testString() {
		storeRestoreTest(Binding.get(String))
	}

	/**
	 * Even as primitive types should not be used as binding,
	 * storing an restoring them should work in case they are used.
	 */
	@Test
	void testPrimitive() {
		storeRestoreTest(Binding.get(byte))
	}

	@Test
	void testPrimitive2() {
		storeRestoreTest(Binding.get(int))
	}

	@Test
	void testPrimitiveArray() {
		storeRestoreTest(Binding.get(byte[]))
	}

	@Test
	void testPrimitiveArray2() {
		storeRestoreTest(Binding.get(int[]))
	}

	@Test
	void testArray() {
		storeRestoreTest(Binding.get(Object[]))
	}

	@Override
	protected List<String> getPropertiesToCompare() {
		['binding']
	}
}
