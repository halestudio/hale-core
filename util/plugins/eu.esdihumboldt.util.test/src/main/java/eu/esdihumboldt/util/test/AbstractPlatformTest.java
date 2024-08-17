
/*
 * Copyright (c) 2024 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.util.test;

import org.junit.BeforeClass;

import eu.esdihumboldt.util.nonosgi.Init;

/**
 * Base class for unit tests that makes sure the platform is initialized.
 */
public abstract class AbstractPlatformTest {

	@BeforeClass
	public static void init() {
		Init.init();
	}

}
