/*
 * Copyright (c) 2026 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.app.transform.test

import org.junit.AfterClass
import org.junit.BeforeClass

import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension

/**
 * Runs the store path transformation tests with the OrientDB instance store.
 */
class StorePathTransformationOrientTest extends StorePathTransformationTest {

	@BeforeClass
	static void useOrient() {
		System.setProperty(InstanceStoreExtension.SYSTEM_PROPERTY, 'orient')
	}

	@Override
	protected String getExpectedStoreClass() {
		'OrientInstanceStore'
	}

	@AfterClass
	static void resetStore() {
		System.clearProperty(InstanceStoreExtension.SYSTEM_PROPERTY)
	}
}
