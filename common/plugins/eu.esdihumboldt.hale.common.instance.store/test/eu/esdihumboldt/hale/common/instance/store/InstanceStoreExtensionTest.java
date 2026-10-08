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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension.Candidate;

public class InstanceStoreExtensionTest {

	private static final Path DIR = Path.of("unused");

	private static InstanceStore dummyStore() {
		return (InstanceStore) java.lang.reflect.Proxy.newProxyInstance(
				InstanceStore.class.getClassLoader(), new Class<?>[] { InstanceStore.class },
				(proxy, method, args) -> null);
	}

	private static Candidate candidate(String id, int priority, InstanceStoreFactory factory) {
		return new Candidate() {

			@Override
			public String getId() {
				return id;
			}

			@Override
			public int getStorePriority() {
				return priority;
			}

			@Override
			public InstanceStoreFactory createFactory() {
				return factory;
			}
		};
	}

	private static Candidate working(String id, int priority, InstanceStore store) {
		return candidate(id, priority, (ds, dir, sp) -> store);
	}

	private static Candidate failing(String id, int priority) {
		return candidate(id, priority, (ds, dir, sp) -> {
			throw new IOException("cannot create " + id);
		});
	}

	private static List<String> ids(List<Candidate> candidates) {
		return candidates.stream().map(Candidate::getId).collect(Collectors.toList());
	}

	@Test
	public void testOrderByPriority() {
		List<Candidate> ordered = InstanceStoreExtension
				.order(List.of(working("orient", 0, null), working("sqlite", 10, null)), null);
		assertEquals(List.of("sqlite", "orient"), ids(ordered));
	}

	@Test
	public void testRequestedFirst() {
		List<Candidate> ordered = InstanceStoreExtension
				.order(List.of(working("orient", 0, null), working("sqlite", 10, null)), "orient");
		assertEquals(List.of("orient", "sqlite"), ids(ordered));
	}

	@Test
	public void testRequestedUnknown() {
		List<Candidate> ordered = InstanceStoreExtension
				.order(List.of(working("orient", 0, null), working("sqlite", 10, null)), "foo");
		assertEquals(List.of("sqlite", "orient"), ids(ordered));
	}

	@Test
	public void testFallbackOnFailure() throws IOException {
		InstanceStore store = dummyStore();
		InstanceStore result = InstanceStoreExtension.createStore(
				List.of(failing("sqlite", 10), working("orient", 0, store)), null, DataSet.SOURCE,
				DIR, null);
		assertSame(store, result);
	}

	@Test
	public void testAllFailing() {
		try {
			InstanceStoreExtension.createStore(List.of(failing("sqlite", 10), failing("orient", 0)),
					null, DataSet.SOURCE, DIR, null);
			fail("Expected exception");
		} catch (IOException e) {
			assertEquals(2, e.getSuppressed().length);
		}
	}

	@Test
	public void testNoCandidates() {
		try {
			InstanceStoreExtension.createStore(List.of(), null, DataSet.SOURCE, DIR, null);
			fail("Expected exception");
		} catch (IOException e) {
			assertTrue(e.getMessage().contains("No instance store"));
		}
	}
}
