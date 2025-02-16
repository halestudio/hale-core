/*
 * Copyright (c) 2012 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.instance.model.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import eu.esdihumboldt.hale.common.core.report.LogAware;
import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Filter;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;

/**
 * An instance collection which consists of multiple instance collections. For
 * instance references it uses the underlying instance collections mechanism
 * which may be inefficient. The iterator supports
 * {@link ResourceIterator#remove()} if the underlying InstanceCollection's
 * ResourceIterator does so.
 *
 * @author Kai Schwierczek
 */
public class MultiInstanceCollection implements InstanceCollection, LogAware {

	private final List<InstanceCollection> collections;
	private SimpleLog log;

	/**
	 * Constructor using a list of instance collections..
	 *
	 * @param collections the list of instance collections
	 */
	public MultiInstanceCollection(List<InstanceCollection> collections) {
		this.collections = Collections
				.unmodifiableList(new ArrayList<InstanceCollection>(collections));
	}

	@Override
	public InstanceReference getReference(Instance instance) {
		MultiInstanceCollectionInstance inst = (MultiInstanceCollectionInstance) instance;
		return new MultiInstanceCollectionReference(
				collections.get(inst.listIndex).getReference(inst.getOriginalInstance()),
				inst.listIndex);
	}

	@Override
	public Instance getInstance(InstanceReference reference) {
		MultiInstanceCollectionReference ref = (MultiInstanceCollectionReference) reference;
		return new MultiInstanceCollectionInstance(
				collections.get(ref.listIndex).getInstance(ref.reference), ref.listIndex);
	}

	@Override
	public ResourceIterator<Instance> iterator() {
		return new MultiInstanceCollectionResourceIterator();
	}

	@Override
	public boolean hasSize() {
		for (InstanceCollection collection : collections) {
			if (!collection.hasSize()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int size() {
		int result = 0;
		for (InstanceCollection collection : collections) {
			int size = collection.size();
			if (size == UNKNOWN_SIZE) {
				return UNKNOWN_SIZE;
			}
			else {
				result += size;
			}
		}
		return result;
	}

	@Override
	public boolean isEmpty() {
		for (InstanceCollection collection : collections) {
			if (!collection.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public InstanceCollection select(Filter filter) {
		/*
		 * Delegate filter to each collection - there may some optimization take place,
		 * e.g. with type filters
		 */
		List<InstanceCollection> result = new ArrayList<>();
		for (InstanceCollection collection : collections) {
			result.add(FilteredInstanceCollection.applyFilter(collection, filter));
		}

		return createNew(result);
	}

	/**
	 * Create a new multi instance collection with filtered child collections
	 *
	 * @param filtered the filtered child collections
	 * @return the multi instance collection
	 */
	protected MultiInstanceCollection createNew(List<InstanceCollection> filtered) {
		MultiInstanceCollection result = new MultiInstanceCollection(filtered);
		if (log != null) {
			result.setLog(log);
		}
		return result;
	}

	/**
	 * Internal resource iterator iterating over all given instance collections in
	 * order. Supports {@link #remove()} if the underlying iterator supports remove.
	 *
	 * @author Kai Schwierczek
	 */
	private class MultiInstanceCollectionResourceIterator implements ResourceIterator<Instance> {

		private int currentCollection = -1;
		private ResourceIterator<Instance> currentIterator = null;
		private int hasNextIndex = -1; // index of the next collection that has
										// elements
		private boolean closed = false;

		/**
		 * @see java.util.Iterator#hasNext()
		 */
		@Override
		public boolean hasNext() {
			if (closed)
				return false;
			// do not advance here, because after hasNext(), before next(),
			// a call to remove may still work
			if (currentIterator != null && currentIterator.hasNext()) {
				hasNextIndex = currentCollection;
				return true;
			}
			for (int i = currentCollection + 1; i < collections.size(); i++)
				if (!collections.get(i).isEmpty()) {
					hasNextIndex = i;
					return true;
				}
			hasNextIndex = -1;
			return false;
		}

		@Override
		public Instance next() {
			// check for hasNext first, because after a unsuccessful call to
			// next(),
			// remove() may still work
			if (hasNext()) {
				// advance iterator if necessary
				if (currentCollection != hasNextIndex) {
					if (currentIterator != null)
						currentIterator.close();
					currentCollection = hasNextIndex;
					currentIterator = collections.get(currentCollection).iterator();
				}

				// return next of current iterator
				return new MultiInstanceCollectionInstance(currentIterator.next(),
						currentCollection);
			}
			else
				throw new NoSuchElementException();
		}

		@Override
		public void remove() {
			if (currentIterator != null) {
				currentIterator.remove();
			}
			else {
				throw new IllegalStateException();
			}
		}

		@Override
		public void close() {
			closed = true;
			if (currentIterator != null) {
				currentIterator.close();
				currentIterator = null;
			}
		}
	}

	/**
	 * Internal class for decorating the instances with the index of the instance
	 * collection list.
	 *
	 * @author Kai Schwierczek
	 */
	private static class MultiInstanceCollectionInstance extends InstanceDecorator {

		private final int listIndex;

		/**
		 * Default constructor.
		 *
		 * @param instance the instance to decorate
		 * @param listIndex the index of the list this instance originated from
		 */
		public MultiInstanceCollectionInstance(Instance instance, int listIndex) {
			super(instance);
			this.listIndex = listIndex;
		}
	}

	/**
	 * Internal class for decorating the references with the index of the instance
	 * collection list.
	 *
	 * @author Kai Schwierczek
	 */
	private static class MultiInstanceCollectionReference implements InstanceReference {

		private final InstanceReference reference;
		private final int listIndex;

		/**
		 * Default constructor.
		 *
		 * @param reference the reference to decorate
		 * @param listIndex the index of the list this reference originated from
		 */
		public MultiInstanceCollectionReference(InstanceReference reference, int listIndex) {
			this.reference = reference;
			this.listIndex = listIndex;
		}

		@Override
		public DataSet getDataSet() {
			return reference.getDataSet();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + listIndex;
			result = prime * result + reference.hashCode();
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MultiInstanceCollectionReference) {
				MultiInstanceCollectionReference other = (MultiInstanceCollectionReference) obj;
				return listIndex == other.listIndex && reference.equals(other.reference);
			}
			else {
				return false;
			}
		}
	}

	@Override
	public void setLog(SimpleLog log) {
		this.log = log;

		for (InstanceCollection collection : collections) {
			if (collection instanceof LogAware) {
				((LogAware) collection).setLog(log);
			}
		}
	}
}
