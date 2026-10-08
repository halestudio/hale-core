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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Filter;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Reiterable transformation sink based on an {@link InstanceStore}. The first
 * iterator streams instances while the transformation is running, later
 * iterators read from the store after the transformation completed.
 */
public class StoreTransformationSink extends AbstractTransformationSink {

	private static final ALogger log = ALoggerFactory.getLogger(StoreTransformationSink.class);

	private class StoreLimboCollection implements InstanceCollection {

		private boolean firstIterator = true;
		private boolean limboOpen = false;

		private InstanceCollection stored() {
			return store.getInstances(types);
		}

		@Override
		public InstanceReference getReference(Instance instance) {
			if (instance instanceof InstanceWithReference) {
				return ((InstanceWithReference) instance).getReference();
			}
			return stored().getReference(instance);
		}

		@Override
		public Instance getInstance(InstanceReference reference) {
			return stored().getInstance(reference);
		}

		@Override
		public synchronized ResourceIterator<Instance> iterator() {
			if (!complete.get() && !skipLimbo.get() && firstIterator) {
				firstIterator = false;
				limboOpen = true;
				return limboSink.getInstanceCollection().iterator();
			}
			if (firstIterator && !skipLimbo.get()) {
				firstIterator = false;
				limboOpen = true;
				// transformation already completed, but limbo sink still holds
				// the instances - serve them as the first iteration
				return limboSink.getInstanceCollection().iterator();
			}
			waitToComplete();
			return stored().iterator();
		}

		@Override
		public boolean hasSize() {
			return false;
		}

		@Override
		public int size() {
			return UNKNOWN_SIZE;
		}

		@Override
		public boolean isEmpty() {
			// have to return false, even if it actually may be empty
			return false;
		}

		@Override
		public InstanceCollection select(Filter filter) {
			waitToComplete();
			return stored().select(filter);
		}

		private void waitToComplete() {
			if (!limboOpen) {
				// skip limbo sink (prevent blocking when adding to it)
				skipLimbo.set(true);
				// consume limbo sink, make sure it does not block any more
				limboSink.done(true);
			}
			while (!complete.get()) {
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					log.error("Waiting for transformation completion interrupted", e);
				}
			}
		}
	}

	private final Path location;
	private final InstanceStore store;
	private final InstanceStoreWriter writer;
	private final LimboInstanceSink limboSink = new LimboInstanceSink();
	private final StoreLimboCollection collection = new StoreLimboCollection();

	private volatile TypeIndex types;

	private final AtomicBoolean complete = new AtomicBoolean();
	private final AtomicBoolean skipLimbo = new AtomicBoolean();
	private final AtomicInteger counter = new AtomicInteger();

	/**
	 * Create a sink with a new temporary store.
	 */
	public StoreTransformationSink() {
		try {
			location = Files.createTempDirectory("transformationSink");
			store = InstanceStoreExtension.getInstance().createStore(DataSet.TRANSFORMED, location,
					null);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot create temporary instance store", e);
		}
		writer = store.openWriter();
	}

	@Override
	public void setTypes(TypeIndex types) {
		this.types = types;
	}

	@Override
	protected void internalAddInstance(Instance instance) {
		InstanceReference ref = writer.add(instance);
		if (!skipLimbo.get()) {
			// possible problem: limbo sink may block
			limboSink.addInstance(new InstanceWithReference(instance, ref));
		}
		counter.incrementAndGet();
	}

	@Override
	protected void internalDone(boolean cancel) {
		try {
			writer.close();
		} catch (IOException e) {
			log.error("Failed to write transformed instances to temporary store", e);
		}
		limboSink.done(cancel);
		complete.set(true);
		log.debug("Instance sink completed (cancel={}), processed {} instances", cancel,
				counter.get());
	}

	@Override
	public void dispose() {
		try {
			store.close();
		} catch (IOException e) {
			log.warn("Could not delete temporary instance store at " + location, e);
		}
		super.dispose();
	}

	@Override
	public InstanceCollection getInstanceCollection() {
		return collection;
	}

}
