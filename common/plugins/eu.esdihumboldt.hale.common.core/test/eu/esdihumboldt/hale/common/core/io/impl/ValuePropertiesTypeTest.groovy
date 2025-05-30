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

import org.eclipse.equinox.nonosgi.registry.RegistryFactoryHelper
import org.junit.BeforeClass
import org.junit.Test
import org.w3c.dom.Element

import eu.esdihumboldt.hale.common.core.io.HaleIO
import eu.esdihumboldt.hale.common.core.io.Value
import eu.esdihumboldt.hale.common.core.io.ValueList
import eu.esdihumboldt.hale.common.core.io.ValueProperties
import eu.esdihumboldt.util.test.AbstractPlatformTest


/**
 * Tests XML serialization of {@link ValueProperties}.
 *
 * @author Simon Templer
 */
class ValuePropertiesTypeTest extends AbstractPlatformTest {

	/**
	 * Test if a simple properties map containing only simple values is the same
	 * when converted to DOM and back again.
	 */
	@Test
	public void testValueProperties() {
		ValueProperties vp = new ValueProperties()
		Value name = Value.of('Peter')
		vp['name'] = name
		vp['city'] = Value.of('Petersburg')
		vp['age'] = 2.power(5) as Value

		// convert to DOM
		Element fragment = HaleIO.getComplexElement(vp)

		// convert back
		ValueProperties conv = HaleIO.getComplexValue(fragment, ValueProperties, null)

		assertNotNull conv
		assertEquals vp, conv
	}

	/**
	 * Test if a simple properties map containing only simple values is the same
	 * when converted to JSON and back again.
	 */
	@Test
	public void testValuePropertiesJson() {
		ValueProperties vp = new ValueProperties()
		Value name = Value.of('Peter')
		vp['name'] = name
		vp['city'] = Value.of('Petersburg')
		vp['age'] = 2.power(5) as Value

		// converter
		ValuePropertiesType vpt = new ValuePropertiesType()

		// convert to Json
		StringWriter writer = new StringWriter()
		vpt.toJson(vp, writer);

		System.out.println(writer.toString())

		// convert back
		ValueProperties conv = vpt.fromJson(new StringReader(writer.toString()), null)

		assertNotNull conv
		assertEquals vp, conv
	}

	/**
	 * Test if a properties map containing simple value and a value list is the same
	 * when converted to DOM and back again.
	 */
	@Test
	public void testValuePropertiesList() {
		ValueProperties vp = new ValueProperties()
		vp['name'] = Value.of('Peter')
		vp['cities'] = Value.of(new ValueList([
			Value.of('Petersburg'),
			Value.of('Katzenhirn'),
			Value.of('Munich')
		]))
		vp['age'] = 42 - 10 as Value

		// convert to DOM
		Element fragment = HaleIO.getComplexElement(vp)

		// convert back
		ValueProperties conv = HaleIO.getComplexValue(fragment, ValueProperties, null)

		assertNotNull conv
		assertEquals 3, conv.keySet().size()
		assertEquals vp, conv
	}

	/**
	 * Test if a properties map containing simple value and a value list is the same
	 * when converted to DOM and back again.
	 */
	@Test
	public void testValuePropertiesListJson() {
		ValueProperties vp = new ValueProperties()
		vp['name'] = Value.of('Peter')
		vp['cities'] = Value.of(new ValueList([
			Value.of('Petersburg'),
			Value.of('Katzenhirn'),
			Value.of('Munich')
		]))
		vp['age'] = 42 - 10 as Value

		// converter
		ValuePropertiesType vpt = new ValuePropertiesType()

		// convert to Json
		StringWriter writer = new StringWriter()
		vpt.toJson(vp, writer);

		System.out.println(writer.toString())

		// convert back
		ValueProperties conv = vpt.fromJson(new StringReader(writer.toString()), null)

		assertNotNull conv
		assertEquals 3, conv.keySet().size()
		assertEquals vp, conv
	}
}
