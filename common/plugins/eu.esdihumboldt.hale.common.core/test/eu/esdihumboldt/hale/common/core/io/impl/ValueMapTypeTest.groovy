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
package eu.esdihumboldt.hale.common.core.io.impl

import static org.junit.Assert.*

import org.junit.Test
import org.w3c.dom.Element

import eu.esdihumboldt.hale.common.core.io.HaleIO
import eu.esdihumboldt.hale.common.core.io.Value
import eu.esdihumboldt.hale.common.core.io.ValueList
import eu.esdihumboldt.hale.common.core.io.ValueMap
import eu.esdihumboldt.util.nonosgi.Init
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests XML serialization of {@link ValueMap}.
 *
 * @author Simon Templer
 */
class ValueMapTypeTest extends AbstractPlatformTest {

	/**
	 * Test if a map containing simple values and complex values is the same
	 * when converted to DOM and back again.
	 */
	@Test
	public void testValueMap() {
		ValueMap vm = new ValueMap()
		vm[Value.of('languages')] = Value.of(new ValueList([
			Value.of('de'),
			Value.of('en')
		]))
		vm[6*7 as Value] = 42 as Value
		vm[Value.of(new ValueList([
				1 as Value,
				2 as Value,
				3 as Value
			]))] = 123 as Value

		// convert to DOM
		Element fragment = HaleIO.getComplexElement(vm)

		// convert back
		ValueMap conv = HaleIO.getComplexValue(fragment, ValueMap, null)

		assertNotNull conv
		assertEquals 3, conv.keySet().size()
		assertEquals vm, conv
	}

	/**
	 * Test if a map containing simple values and complex values is the same
	 * when converted to JSON and back again.
	 */
	@Test
	public void testValueMapJson() {
		ValueMap vm = new ValueMap()
		vm[Value.of('languages')] = Value.of(new ValueList([
			Value.of('de'),
			Value.of('en')
		]))
		vm[6*7 as Value] = 42 as Value
		vm[Value.of(new ValueList([
				1 as Value,
				2 as Value,
				3 as Value
			]))] = 123 as Value

		// converter
		ValueMapType vmt = new ValueMapType()

		// convert to Json
		StringWriter writer = new StringWriter()
		vmt.toJson(vm, writer);

		System.out.println(writer.toString())

		// convert back
		ValueMap conv = vmt.fromJson(new StringReader(writer.toString()), null)

		assertNotNull conv
		assertEquals 3, conv.keySet().size()
		assertEquals vm, conv
	}
}
