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
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultTypeDefinition;

public class SqliteInstanceWriterTest {

	static final TypeDefinition TYPE = new DefaultTypeDefinition(new QName("T"));

	private Path dir;
	private StoreContext ctx;

	static Instance instance(String id) {
		DefaultInstance instance = new DefaultInstance(TYPE, null);
		instance.addProperty(new QName("name"), "n-" + id);
		instance.setMetaData("ID", id);
		return instance;
	}

	private long count(String sql) {
		return ctx.database.withReader(c -> {
			try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
				rs.next();
				return rs.getLong(1);
			}
		});
	}

	@Before
	public void setUp() throws Exception {
		dir = Files.createTempDirectory("sqlite-writer-test");
		ctx = new StoreContext(DataSet.SOURCE);
		ctx.database = new SqliteDatabase(dir);
	}

	@After
	public void tearDown() throws Exception {
		ctx.database.close();
		org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile());
	}

	@Test
	public void testAddFlush() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			InstanceReference r1 = writer.add(instance("a"));
			InstanceReference r2 = writer.add(instance("b"));
			assertNotEquals(r1, r2);
			assertEquals(DataSet.SOURCE, r1.getDataSet());
			writer.flush();
			assertTrue(ctx.pending.isEmpty());
			assertEquals(2, count("SELECT COUNT(*) FROM instances"));
			assertEquals(2, count("SELECT COUNT(*) FROM metadata WHERE key = 'ID'"));
			assertEquals(1, count("SELECT COUNT(*) FROM types"));
			assertEquals(2, ctx.committedCount(ctx.types.findId(TYPE.getName())));
		}
	}

	@Test
	public void testPendingResolvableBeforeCommit() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			StoreInstanceReference ref = (StoreInstanceReference) writer.add(instance("a"));
			Instance loaded = ctx.load(ref.getStoreId(), TYPE, null);
			assertNotNull(loaded);
			assertEquals("n-a", loaded.getProperty(new QName("name"))[0]);
		}
	}

	@Test
	public void testLargeBatches() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			for (int i = 0; i < 2500; i++) {
				writer.add(instance("i" + i));
			}
		}
		assertEquals(2500, count("SELECT COUNT(*) FROM instances"));
	}

	@Test
	public void testIdleCommit() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			writer.add(instance("a"));
			long deadline = System.currentTimeMillis() + 5000;
			while (!ctx.pending.isEmpty() && System.currentTimeMillis() < deadline) {
				Thread.sleep(20);
			}
			assertTrue("instance should be committed without flush", ctx.pending.isEmpty());
		}
	}

	@Test
	public void testConcurrentAdds() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			ExecutorService executor = Executors.newFixedThreadPool(4);
			List<Future<?>> futures = new ArrayList<>();
			for (int t = 0; t < 4; t++) {
				int thread = t;
				futures.add(executor.submit(() -> {
					for (int i = 0; i < 250; i++) {
						writer.add(instance(thread + "-" + i));
					}
				}));
			}
			for (Future<?> f : futures) {
				f.get();
			}
			executor.shutdown();
			writer.flush();
		}
		assertEquals(1000, count("SELECT COUNT(DISTINCT id) FROM instances"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstanceWithoutType() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			writer.add(new DefaultInstance(null, null));
		}
	}

	@Test
	public void testUnserializableInstance() throws Exception {
		DefaultInstance broken = new DefaultInstance(TYPE, null) {

			@Override
			public Iterable<QName> getPropertyNames() {
				throw new IllegalStateException("broken instance");
			}
		};
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			try {
				writer.add(broken);
				fail("Expected exception");
			} catch (IllegalArgumentException e) {
				// expected
			}
			// writer still usable
			writer.add(instance("a"));
			writer.flush();
		}
		assertEquals(1, count("SELECT COUNT(*) FROM instances"));
	}

	@Test
	public void testMetadataFailureAfterSerialization() throws Exception {
		QName typeName = new QName("FlakyMetadata");
		AtomicInteger metadataReads = new AtomicInteger();
		DefaultInstance flaky = new DefaultInstance(new DefaultTypeDefinition(typeName), null) {

			@Override
			public Set<String> getMetaDataNames() {
				// first read (serialization) works, the second one fails
				if (metadataReads.incrementAndGet() > 1) {
					throw new IllegalStateException("metadata no longer readable");
				}
				return super.getMetaDataNames();
			}
		};
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			try {
				writer.add(flaky);
				fail("Expected exception");
			} catch (IllegalArgumentException e) {
				// expected
			}
			// writer still usable
			writer.add(instance("a"));
			writer.flush();
		}
		assertNull("type of an instance that was not stored must not be registered",
				ctx.types.findId(typeName));
		assertEquals(1, count("SELECT COUNT(*) FROM instances"));
	}

	@Test
	public void testUnserializableInstanceRegistersNoType() throws Exception {
		QName typeName = new QName("Broken");
		DefaultInstance broken = new DefaultInstance(new DefaultTypeDefinition(typeName), null) {

			@Override
			public Iterable<QName> getPropertyNames() {
				throw new IllegalStateException("broken instance");
			}
		};
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			try {
				writer.add(broken);
				fail("Expected exception");
			} catch (IllegalArgumentException e) {
				// expected
			}
		}
		assertNull("type of an instance that was not stored must not be registered",
				ctx.types.findId(typeName));
	}

	@Test(timeout = 30000)
	public void testConcurrentCloseWaitsForWriterThread() throws Exception {
		int perRound = 5000;
		int rounds = 5;
		for (int round = 0; round < rounds; round++) {
			SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx);
			for (int i = 0; i < perRound; i++) {
				writer.add(instance(round + "-" + i));
			}

			CountDownLatch start = new CountDownLatch(1);
			ExecutorService executor = Executors.newFixedThreadPool(2);
			List<Future<Boolean>> writerAliveAfterClose = new ArrayList<>();
			for (int t = 0; t < 2; t++) {
				writerAliveAfterClose.add(executor.submit(() -> {
					start.await();
					writer.close();
					return writer.isWriterThreadAlive();
				}));
			}
			start.countDown();
			for (Future<Boolean> alive : writerAliveAfterClose) {
				assertFalse("close() returned while the writer thread was still running",
						alive.get());
			}
			executor.shutdown();
		}
		assertEquals(perRound * rounds, count("SELECT COUNT(*) FROM instances"));
	}

	@Test
	public void testAddAfterClose() throws Exception {
		SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx);
		writer.close();
		assertTrue(writer.isClosed());
		try {
			writer.add(instance("a"));
			fail("Expected exception");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	@Test
	public void testFailurePropagates() throws Exception {
		SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx);
		ctx.database.writerConnection().close();
		writer.add(instance("a"));
		try {
			writer.flush();
			fail("Expected exception");
		} catch (IllegalStateException e) {
			// expected
		}
		try {
			writer.add(instance("b"));
			fail("Expected exception");
		} catch (IllegalStateException e) {
			// expected
		}
		try {
			writer.close();
			fail("Expected exception");
		} catch (java.io.IOException e) {
			// expected
		}
	}

	@Test(timeout = 30000)
	public void testCloseWhileAdding() throws Exception {
		for (int round = 0; round < 5; round++) {
			SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx);
			java.util.concurrent.ConcurrentLinkedQueue<StoreInstanceReference> refs = new java.util.concurrent.ConcurrentLinkedQueue<>();
			java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(
					4);
			ExecutorService executor = Executors.newFixedThreadPool(5);
			List<Future<?>> futures = new ArrayList<>();
			for (int t = 0; t < 4; t++) {
				int thread = t;
				futures.add(executor.submit(() -> {
					started.countDown();
					for (int i = 0; i < 100000; i++) {
						try {
							refs.add((StoreInstanceReference) writer
									.add(instance(thread + "-" + i)));
							if (i % 500 == 0) {
								writer.flush();
							}
						} catch (IllegalStateException e) {
							return; // closed
						}
					}
					fail("writer was never closed");
				}));
			}
			futures.add(executor.submit(() -> {
				started.await();
				Thread.sleep(50);
				writer.close();
				return null;
			}));
			for (Future<?> f : futures) {
				f.get();
			}
			executor.shutdown();
			assertTrue(ctx.pending.isEmpty());
			for (StoreInstanceReference ref : refs) {
				assertEquals(1,
						count("SELECT COUNT(*) FROM instances WHERE id = " + ref.getStoreId()));
			}
			assertEquals(refs.size(), count("SELECT COUNT(*) FROM instances"));
			ctx.database.withReader(c -> {
				try (Statement s = c.createStatement()) {
					s.executeUpdate("SELECT 1");
				}
				return null;
			});
			// clear for next round
			ctx.database.close();
			ctx.reset();
			org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile());
			dir = Files.createTempDirectory("sqlite-writer-test");
			ctx.database = new SqliteDatabase(dir);
		}
	}

	@Test(timeout = 30000)
	public void testCloseInterrupted() throws Exception {
		SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx);
		writer.add(instance("a"));
		Thread.currentThread().interrupt();
		try {
			writer.close();
			assertTrue("interrupt flag must be restored", Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted(); // clear
		}
		assertTrue(writer.isClosed());
		assertTrue(!writer.isWriterThreadAlive());
		assertEquals(1, count("SELECT COUNT(*) FROM instances"));
	}

	@Test(timeout = 30000)
	public void testSecondCloseNoop() throws Exception {
		SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx);
		writer.add(instance("a"));
		writer.close();
		writer.close();
		assertTrue(!writer.isWriterThreadAlive());
		assertEquals(1, count("SELECT COUNT(*) FROM instances"));
	}
}
