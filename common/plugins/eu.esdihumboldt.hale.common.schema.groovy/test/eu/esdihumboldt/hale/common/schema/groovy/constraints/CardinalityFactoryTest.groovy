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

import eu.esdihumboldt.hale.common.schema.model.constraint.property.Cardinality

/**
 * Tests creation of {@link Cardinality} constraints w/ {@link CardinalityFactory}.
 *
 * @author Simon Templer
 */
@CompileStatic
class CardinalityFactoryTest {

	/*
	 * NOTE: In Eclipse in the editor there might be errors shown here,
	 * even if the code actually compiles.
	 */

	@Test
	void testRange() {
		CardinalityFactory cf = CardinalityFactory.instance
		assertEquals Cardinality.CC_OPTIONAL, cf.createConstraint(0..1)
		assertEquals Cardinality.CC_EXACTLY_ONCE, cf.createConstraint(1..1)
		assertEquals Cardinality.get(0, 5), cf.createConstraint(0..5)
		assertEquals Cardinality.get(1, 8), cf.createConstraint(1..8)
	}

	@Test
	void testFixed() {
		CardinalityFactory cf = CardinalityFactory.instance
		assertEquals Cardinality.CC_EXACTLY_ONCE, cf.createConstraint(1)
		assertEquals Cardinality.get(8, 8), cf.createConstraint(8)
		assertEquals Cardinality.get(2, 2), cf.createConstraint(2)
	}

	@Test
	void testText() {
		CardinalityFactory cf = CardinalityFactory.instance

		assertEquals Cardinality.CC_OPTIONAL, cf.createConstraint('?')
		assertEquals Cardinality.CC_AT_LEAST_ONCE, cf.createConstraint('+')
		assertEquals Cardinality.CC_ANY_NUMBER, cf.createConstraint('*')

		assertEquals Cardinality.CC_OPTIONAL, cf.createConstraint('0..1')
		assertEquals Cardinality.CC_OPTIONAL, cf.createConstraint('0-1')
		assertEquals Cardinality.CC_AT_LEAST_ONCE, cf.createConstraint('1..n')
		assertEquals Cardinality.CC_AT_LEAST_ONCE, cf.createConstraint('1..unbounded')
		assertEquals Cardinality.CC_AT_LEAST_ONCE, cf.createConstraint('1..*')
		assertEquals Cardinality.CC_ANY_NUMBER, cf.createConstraint('0..n')
		assertEquals Cardinality.CC_EXACTLY_ONCE, cf.createConstraint('1..1')

		assertEquals Cardinality.get(1, 8), cf.createConstraint('1..8')
	}
}
