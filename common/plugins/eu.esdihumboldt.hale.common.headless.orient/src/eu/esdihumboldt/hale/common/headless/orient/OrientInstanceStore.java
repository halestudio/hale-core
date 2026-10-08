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
package eu.esdihumboldt.hale.common.headless.orient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Filter;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.model.impl.FilteredInstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.impl.InstanceDecorator;
import eu.esdihumboldt.hale.common.instance.orient.OInstance;
import eu.esdihumboldt.hale.common.instance.orient.storage.BrowseOrientInstanceCollection;
import eu.esdihumboldt.hale.common.instance.orient.storage.LocalOrientDB;
import eu.esdihumboldt.hale.common.instance.orient.storage.OrientInstanceSink;
import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Temporary instance store based on OrientDB (legacy, fallback).
 */
public class OrientInstanceStore implements InstanceStore {

	private static final ALogger log = ALoggerFactory.getLogger(OrientInstanceStore.class);

	/**
	 * Only yields instances that were actually inserted (nested instances are
	 * stored as separate records of their type).
	 */
	private static final Filter INSERTED = instance -> {
		Instance root = InstanceDecorator.getRoot(instance);
		return !(root instanceof OInstance) || ((OInstance) root).isInserted();
	};

	private class OrientWriter implements InstanceStoreWriter {

		// OrientDB 1.5 connections are not thread-safe - write on one thread
		private final ExecutorService thread = Executors.newSingleThreadExecutor();
		private final OrientInstanceSink sink = new OrientInstanceSink(database, false);
		private volatile boolean closed;

		@Override
		public InstanceReference add(Instance instance) {
			if (closed) {
				throw new IllegalStateException("Writer is closed");
			}
			if (instance.getDefinition() == null) {
				throw new IllegalArgumentException(
						"Instance without type definition cannot be stored");
			}
			Future<InstanceReference> future = thread.submit(() -> {
				OInstance conv = new OInstance(instance);
				conv.setDataSet(dataSet);
				return sink.putInstance(conv);
			});
			try {
				return future.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while storing instance", e);
			} catch (ExecutionException e) {
				throw new IllegalStateException("Failed to store instance", e.getCause());
			}
		}

		@Override
		public void flush() {
			// instances are written synchronously
		}

		@Override
		public void close() throws IOException {
			if (closed) {
				return;
			}
			closed = true;
			try {
				thread.submit(() -> {
					sink.close();
					return null;
				}).get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				throw new IOException("Failed to close OrientDB instance sink", e.getCause());
			} finally {
				thread.shutdown();
				try {
					thread.awaitTermination(100, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	private final DataSet dataSet;
	private final Path directory;
	private final LocalOrientDB database;
	private OrientWriter writer;

	/**
	 * @param dataSet the data set
	 * @param directory the database directory
	 */
	public OrientInstanceStore(DataSet dataSet, Path directory) {
		this.dataSet = dataSet;
		this.directory = directory;
		this.database = new LocalOrientDB(directory.toFile());
	}

	@Override
	public synchronized InstanceStoreWriter openWriter() {
		if (writer == null || writer.closed) {
			writer = new OrientWriter();
		}
		return writer;
	}

	@Override
	public InstanceCollection getInstances(TypeIndex types) {
		return FilteredInstanceCollection.applyFilter(
				new BrowseOrientInstanceCollection(database, types, dataSet), INSERTED);
	}

	@Override
	public Set<TypeDefinition> getStoredTypes(TypeIndex types) {
		Set<TypeDefinition> result = new LinkedHashSet<>();
		try (ResourceIterator<Instance> it = getInstances(types).iterator()) {
			while (it.hasNext()) {
				result.add(it.next().getDefinition());
			}
		}
		return result;
	}

	@Override
	public synchronized void clear() {
		closeWriter();
		database.clear();
	}

	@Override
	public synchronized void close() throws IOException {
		closeWriter();
		database.delete();
		FileUtils.deleteQuietly(directory.toFile());
	}

	private void closeWriter() {
		if (writer != null) {
			try {
				writer.close();
			} catch (IOException e) {
				log.error("Error closing OrientDB instance writer", e);
			}
			writer = null;
		}
	}

}
