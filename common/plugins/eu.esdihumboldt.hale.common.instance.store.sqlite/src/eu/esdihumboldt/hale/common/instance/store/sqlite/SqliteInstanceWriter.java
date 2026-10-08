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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import javax.xml.namespace.QName;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter;
import eu.esdihumboldt.hale.common.instance.store.sqlite.StoreContext.PendingRecord;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Writer serializing instances on the calling thread and inserting them on a
 * dedicated writer thread in batches.
 */
class SqliteInstanceWriter implements InstanceStoreWriter {

	static final int BATCH_SIZE = 1000;
	static final long IDLE_COMMIT_MILLIS = 100;
	static final int QUEUE_CAPACITY = 10_000;

	private static final ALogger log = ALoggerFactory.getLogger(SqliteInstanceWriter.class);

	private static final Object STOP = new Object();

	private record Item(long id, int typeId, QName typeName, byte[] payload,
			List<String[]> metadata) {
	}

	private final StoreContext ctx;
	private final BlockingQueue<Object> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
	private final AtomicReference<Throwable> failure = new AtomicReference<>();
	private final Thread thread;
	private volatile boolean closed;

	// writer thread state
	private final List<Item> batch = new ArrayList<>();
	private final Set<Integer> storedTypes = new HashSet<>();

	SqliteInstanceWriter(StoreContext ctx) {
		this.ctx = ctx;
		this.thread = new Thread(this::run, "hale-instance-store-writer");
		thread.setDaemon(true);
		thread.start();
	}

	boolean isClosed() {
		return closed;
	}

	@Override
	public InstanceReference add(Instance instance) {
		checkUsable();
		TypeDefinition type = instance.getDefinition();
		if (type == null) {
			throw new IllegalArgumentException("Instance without type definition cannot be stored");
		}
		long id = ctx.nextId.getAndIncrement();
		int typeId = ctx.types.idOf(type.getName());
		byte[] payload;
		try {
			payload = ctx.codec.encode(instance);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Instance cannot be serialized", e);
		}
		ctx.pending.put(id, new PendingRecord(typeId, payload));
		put(new Item(id, typeId, type.getName(), payload, stringMetadata(instance)));
		return new StoreInstanceReference(ctx.key, id, ctx.dataSet, type);
	}

	@Override
	public void flush() {
		checkUsable();
		CountDownLatch latch = new CountDownLatch(1);
		put(latch);
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for flush", e);
		}
		rethrowFailure();
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		put(STOP);
		boolean interrupted = false;
		while (thread.isAlive()) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
		Throwable error = failure.get();
		if (error != null) {
			throw new IOException("Writing to the temporary instance database failed", error);
		}
	}

	private void checkUsable() {
		if (closed) {
			throw new IllegalStateException("Writer is closed");
		}
		rethrowFailure();
	}

	private void rethrowFailure() {
		Throwable error = failure.get();
		if (error != null) {
			throw new IllegalStateException("Writing to the temporary instance database failed",
					error);
		}
	}

	private void put(Object item) {
		try {
			queue.put(item);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while adding to the instance store", e);
		}
	}

	private static List<String[]> stringMetadata(Instance instance) {
		List<String[]> result = new ArrayList<>();
		for (String key : instance.getMetaDataNames()) {
			for (Object value : instance.getMetaData(key)) {
				if (value instanceof String) {
					result.add(new String[] { key, (String) value });
				}
			}
		}
		return result;
	}

	private void run() {
		while (true) {
			Object item;
			try {
				item = queue.poll(IDLE_COMMIT_MILLIS, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				// ignore interrupts (e.g. job cancellation), only stopped via close
				continue;
			}
			try {
				if (item == null) {
					commit();
				}
				else if (item == STOP) {
					commit();
					return;
				}
				else if (item instanceof CountDownLatch) {
					try {
						commit();
					} finally {
						((CountDownLatch) item).countDown();
					}
				}
				else if (failure.get() == null) {
					insert((Item) item);
					if (batch.size() >= BATCH_SIZE) {
						commit();
					}
				}
			} catch (Throwable e) {
				if (failure.compareAndSet(null, e)) {
					log.error("Writing to the temporary instance database failed", e);
				}
				batch.clear();
				try {
					ctx.database.writerConnection().rollback();
				} catch (Throwable e2) {
					// ignore
				}
				if (item == STOP) {
					return;
				}
			}
		}
	}

	private void insert(Item item) throws SQLException {
		Connection c = ctx.database.writerConnection();
		if (c.getAutoCommit()) {
			c.setAutoCommit(false);
		}
		if (storedTypes.add(item.typeId())) {
			try (PreparedStatement p = c
					.prepareStatement("INSERT OR IGNORE INTO types (id, name) VALUES (?, ?)")) {
				p.setInt(1, item.typeId());
				p.setString(2, item.typeName().toString());
				p.executeUpdate();
			}
		}
		try (PreparedStatement p = c
				.prepareStatement("INSERT INTO instances (id, type, payload) VALUES (?, ?, ?)")) {
			p.setLong(1, item.id());
			p.setInt(2, item.typeId());
			p.setBytes(3, item.payload());
			p.executeUpdate();
		}
		if (!item.metadata().isEmpty()) {
			try (PreparedStatement p = c.prepareStatement(
					"INSERT OR IGNORE INTO metadata (key, value, instance) VALUES (?, ?, ?)")) {
				for (String[] entry : item.metadata()) {
					p.setString(1, entry[0]);
					p.setString(2, entry[1]);
					p.setLong(3, item.id());
					p.executeUpdate();
				}
			}
		}
		batch.add(item);
	}

	private void commit() throws SQLException {
		if (batch.isEmpty()) {
			return;
		}
		ctx.database.writerConnection().commit();
		for (Item item : batch) {
			ctx.counts.computeIfAbsent(item.typeId(), t -> new LongAdder()).increment();
			ctx.pending.remove(item.id());
		}
		batch.clear();
	}

}
