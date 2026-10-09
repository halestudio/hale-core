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
package eu.esdihumboldt.hale.common.instance.store.sqlite

import static org.junit.Assert.*

import java.nio.file.Files

import org.junit.Test

import eu.esdihumboldt.hale.common.instance.model.DataSet
import eu.esdihumboldt.hale.common.instance.store.InstanceStore
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension
import eu.esdihumboldt.util.test.AbstractPlatformTest

class SqliteStoreRegistrationTest extends AbstractPlatformTest {

	@Test
	void testSqliteIsDefault() {
		def dir = Files.createTempDirectory('store-registration')
		InstanceStore store = InstanceStoreExtension.instance.createStore(DataSet.SOURCE, dir, null)
		try {
			assertTrue store instanceof SqliteInstanceStore
		} finally {
			store.close()
		}
	}
}
