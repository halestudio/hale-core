
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
package eu.esdihumboldt.hale.common.schema.model.constraint.factory.type

import org.junit.Test

import eu.esdihumboldt.hale.common.schema.model.constraint.factory.AbstractPropertiesCompareConstraintFactoryTest
import eu.esdihumboldt.hale.common.schema.model.constraint.type.Enumeration


/**
 * Tests for {@link EnumerationFactory}.
 *
 * @author Simon Templer
 */
class EnumerationFactoryTest extends AbstractPropertiesCompareConstraintFactoryTest<Enumeration> {

	@Test
	void testEmpty() {
		storeRestoreTest(new Enumeration())
	}

	@Test
	void testValues() {
		storeRestoreTest(new Enumeration([
			'value1',
			'some value',
			'val2'
		], false))
	}

	@Test
	void testValuesAllowOthers() {
		storeRestoreTest(new Enumeration([
			'value1',
			'some value',
			'val2'
		], true))
	}

	@Override
	protected List<String> getPropertiesToCompare() {
		['values', 'allowOthers']
	}
}
