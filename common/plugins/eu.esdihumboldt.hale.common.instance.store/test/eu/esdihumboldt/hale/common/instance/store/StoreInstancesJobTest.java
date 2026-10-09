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
package eu.esdihumboldt.hale.common.instance.store;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for {@link StoreInstancesJob}.
 */
public class StoreInstancesJobTest {

	/**
	 * The report task type is the key of the report in transformation statistics
	 * and must not change (e.g. used in success evaluation scripts).
	 */
	@Test
	public void testTaskTypeCompatibility() {
		assertEquals("eu.esdihumboldt.hale.instance.orient.store", StoreInstancesJob.TASK_TYPE);
	}
}
