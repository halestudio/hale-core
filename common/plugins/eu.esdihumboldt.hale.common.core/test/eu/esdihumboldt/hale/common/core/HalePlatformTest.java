/*
 * Copyright (c) 2016 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.core;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.equinox.nonosgi.registry.RegistryFactoryHelper;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Version;

/**
 * Tests for the {@link HalePlatform} helper class.
 *
 * @author Simon Templer
 */
public class HalePlatformTest {

	@BeforeClass
	public static void init() {
		// initialize registry
		RegistryFactoryHelper.getRegistry();
	}

	@SuppressWarnings("javadoc")
	@Test
	@Ignore("Does not work in dev environment because manifest file is not present yet")
	public void testGetVersion() {
		Version version = HalePlatform.getCoreVersion();
		assertNotNull(version);
		assertNotEquals(Version.emptyVersion, version);
		assertTrue(version.getMajor() >= 2);

		System.out.println("Core version: " + version);
	}

}
