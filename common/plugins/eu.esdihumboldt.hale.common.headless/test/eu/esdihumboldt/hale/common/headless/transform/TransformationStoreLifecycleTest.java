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
package eu.esdihumboldt.hale.common.headless.transform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.Test;

import com.google.common.util.concurrent.SettableFuture;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension;
import eu.esdihumboldt.util.test.AbstractPlatformTest;

/**
 * Tests for the lifecycle of the temporary instance store and the target sink
 * in {@link Transformation}.
 */
public class TransformationStoreLifecycleTest extends AbstractPlatformTest {

	/**
	 * Target sink recording if it was completed and disposed.
	 */
	private static class RecordingSink extends StoreTransformationSink {

		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final AtomicBoolean disposed = new AtomicBoolean();

		@Override
		protected void internalDone(boolean cancel) {
			cancelled.set(cancel);
			super.internalDone(cancel);
		}

		@Override
		public void dispose() {
			disposed.set(true);
			super.dispose();
		}
	}

	private static Job storeJob(IStatus status) {
		return new Job("Store job") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				return status;
			}
		};
	}

	private void testStoreJobNotOk(IStatus status) throws Exception {
		Path storeDir = Files.createTempDirectory("store-lifecycle-test");
		InstanceStore store = InstanceStoreExtension.getInstance().createStore(DataSet.SOURCE,
				storeDir, null);
		RecordingSink sink = new RecordingSink();
		SettableFuture<Boolean> result = SettableFuture.create();
		AtomicBoolean started = new AtomicBoolean();

		Runnable closeStore = Transformation.storeCloser(store);
		Job job = storeJob(status);
		job.addJobChangeListener(Transformation.createStoreJobListener(closeStore, sink, result,
				() -> started.set(true)));
		job.schedule();
		job.join();

		assertFalse("Transformation must not be started", started.get());
		assertFalse("Store directory should be deleted", Files.exists(storeDir));
		assertTrue("Sink should be completed with cancel", sink.cancelled.get());
		assertTrue("Sink should be disposed", sink.disposed.get());
		assertTrue(result.isDone());
		if (status.getException() != null) {
			try {
				result.get();
				fail("Exception expected");
			} catch (ExecutionException e) {
				assertSame(status.getException(), e.getCause());
			}
		}
		else {
			assertFalse(result.get(1, TimeUnit.SECONDS));
		}

		// closing again (e.g. by the transformation job listener) is harmless
		closeStore.run();
	}

	@Test
	public void testStoreJobFailure() throws Exception {
		testStoreJobNotOk(new Status(IStatus.ERROR, "test", "Loading failed",
				new IllegalStateException("Loading failed")));
	}

	@Test
	public void testStoreJobCancel() throws Exception {
		testStoreJobNotOk(Status.CANCEL_STATUS);
	}

	@Test
	public void testStoreJobSuccess() throws Exception {
		Path storeDir = Files.createTempDirectory("store-lifecycle-test");
		InstanceStore store = InstanceStoreExtension.getInstance().createStore(DataSet.SOURCE,
				storeDir, null);
		RecordingSink sink = new RecordingSink();
		try {
			SettableFuture<Boolean> result = SettableFuture.create();
			AtomicBoolean started = new AtomicBoolean();

			Job job = storeJob(Status.OK_STATUS);
			job.addJobChangeListener(Transformation.createStoreJobListener(
					Transformation.storeCloser(store), sink, result, () -> started.set(true)));
			job.schedule();
			job.join();

			assertTrue(started.get());
			assertTrue("Store must not be closed", Files.exists(storeDir));
			assertFalse(sink.disposed.get());
			assertFalse(result.isDone());
		} finally {
			store.close();
			sink.dispose();
		}
	}

	@Test
	public void testStoreClosedOnce() throws Exception {
		AtomicInteger closed = new AtomicInteger();
		Path storeDir = Files.createTempDirectory("store-lifecycle-test");
		InstanceStore store = InstanceStoreExtension.getInstance().createStore(DataSet.SOURCE,
				storeDir, null);
		InstanceStore counting = (InstanceStore) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { InstanceStore.class },
				(proxy, method, args) -> {
					if ("close".equals(method.getName())) {
						closed.incrementAndGet();
					}
					return method.invoke(store, args);
				});
		Runnable closeStore = Transformation.storeCloser(counting);
		closeStore.run();
		closeStore.run();
		assertEquals(1, closed.get());
		assertFalse(Files.exists(storeDir));
	}

	@Test
	public void testCreateStoreRuntimeException() throws Exception {
		AtomicReference<Path> dir = new AtomicReference<>();
		IllegalStateException failure = new IllegalStateException("No store available");
		try {
			Transformation.createTemporaryStore(d -> {
				dir.set(d);
				throw failure;
			});
			fail("Exception expected");
		} catch (IllegalStateException e) {
			assertSame(failure, e);
		}
		assertNotNull(dir.get());
		assertFalse("Store directory should be deleted", Files.exists(dir.get()));
	}

	@Test
	public void testCreateStoreIOException() throws Exception {
		AtomicReference<Path> dir = new AtomicReference<>();
		try {
			Transformation.createTemporaryStore(d -> {
				dir.set(d);
				Files.createFile(d.resolve("partial"));
				throw new IOException("Cannot create store");
			});
			fail("Exception expected");
		} catch (IOException e) {
			assertEquals("Cannot create store", e.getMessage());
		}
		assertNotNull(dir.get());
		assertFalse("Store directory should be deleted", Files.exists(dir.get()));
	}
}
