
/*
 * Copyright (c) 2018 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.config

import static org.junit.Assert.*

import org.eclipse.equinox.nonosgi.registry.RegistryFactoryHelper
import org.junit.BeforeClass
import org.junit.Test

import eu.esdihumboldt.hale.common.core.io.Value
import eu.esdihumboldt.util.config.Config

/**
 * Tests for Config to/from Value conversion.
 *
 * @author Simon Templer
 */
class ConfigValueTest {

	@BeforeClass
	static void init() {
		// initialize registry
		RegistryFactoryHelper.getRegistry();
	}

	@Test
	void testConvert() {
		def c = new Config([name: 'foo', sub: [id: 'bar', list: ['x', 'y', 'z']]])

		def value = ConfigValue.fromConfig(c)
		assertTrue(value instanceof Value)

		Config newC = ConfigValue.fromValue(value)

		assertEquals(c.asMap(), newC.asMap())
	}
}
