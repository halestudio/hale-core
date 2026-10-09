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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

import eu.esdihumboldt.hale.common.instance.model.ContextAwareFilter;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Filter;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResolvableInstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.model.TypeAwareFilter;
import eu.esdihumboldt.hale.common.instance.model.TypeFilter;
import eu.esdihumboldt.hale.common.instance.model.ext.InstanceCollection2;
import eu.esdihumboldt.hale.common.instance.model.ext.InstanceIterator;
import eu.esdihumboldt.hale.common.instance.model.ext.helper.EmptyInstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.ext.helper.InstanceCollectionDecorator;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Instance collection that wraps an instance collection and represents a
 * selection that contains the instances matching a given {@link Filter}.
 *
 * @author Simon Templer
 */
public class FilteredInstanceCollection extends InstanceCollectionDecorator {

	/**
	 * Create an instance collection that applies a filter to the given instance
	 * collection.
	 *
	 * @param instances the instance collection to filter
	 * @param filter the filter
	 * @return the filtered instance collection
	 */
	public static InstanceCollection applyFilter(InstanceCollection instances, Filter filter) {
		if (filter instanceof TypeFilter && instances instanceof InstanceCollection2) {
			/*
			 * For type filters check if we can make use of fan-out.
			 */
			InstanceCollection2 instances2 = (InstanceCollection2) instances;

			if (instances2.supportsFanout()) {
				TypeDefinition type = ((TypeFilter) filter).getType();
				InstanceCollection result = instances2.fanout().get(type);
				if (result == null) {
					result = EmptyInstanceCollection.INSTANCE;
				}
				return result;
			}
		}

		if (filter instanceof TypeAwareFilter && instances instanceof InstanceCollection2) {
			InstanceCollection2 instances2 = (InstanceCollection2) instances;

			if (instances2.supportsFanout()) {
				// only read the collections of the types the filter can match
				Map<TypeDefinition, InstanceCollection> fanout = instances2.fanout();
				List<InstanceCollection> parts = new ArrayList<>();
				for (TypeDefinition type : ((TypeAwareFilter) filter).getTypes()) {
					InstanceCollection part = fanout.get(type);
					if (part != null) {
						parts.add(new FilteredInstanceCollection(part, filter));
					}
				}
				if (parts.isEmpty()) {
					return EmptyInstanceCollection.INSTANCE;
				}
				if (parts.size() == 1) {
					return parts.get(0);
				}
				return new FanoutSelection(parts, instances, fanout);
			}
		}

		// create a filtered collection
		return new FilteredInstanceCollection(instances, filter);
	}

	/**
	 * Selection on a collection supporting fan-out, combining the filtered
	 * collections of the individual types. Iteration and size are based on the
	 * filtered per type collections.
	 * <p>
	 * In addition to references of the instances provided by this collection,
	 * references can also be created for undecorated instances (e.g. retrieved via
	 * {@link InstanceDecorator#getRoot(Instance)}, as done by the index join
	 * handler). Those are resolved via the original collection, or, if the original
	 * collection itself is a {@link MultiInstanceCollection} (that only accepts its
	 * own decorated instances), via the fan-out collection of the instance type.
	 */
	private static class FanoutSelection extends MultiInstanceCollection {

		private final InstanceCollection original;
		private final Map<TypeDefinition, InstanceCollection> fanout;

		/**
		 * Create a selection on a fan-out collection.
		 *
		 * @param parts the filtered per type collections
		 * @param original the original collection
		 * @param fanout the fan-out of the original collection
		 */
		public FanoutSelection(List<InstanceCollection> parts, InstanceCollection original,
				Map<TypeDefinition, InstanceCollection> fanout) {
			super(parts);
			this.original = original;
			this.fanout = fanout;
		}

		@Override
		public InstanceReference getReference(Instance instance) {
			if (isCollectionInstance(instance)) {
				return super.getReference(instance);
			}
			if (!(original instanceof MultiInstanceCollection)) {
				return original.getReference(instance);
			}
			TypeDefinition type = instance.getDefinition();
			InstanceCollection part = fanout.get(type);
			if (part == null) {
				return null;
			}
			InstanceReference reference = part.getReference(instance);
			return reference == null ? null : new FanoutTypeReference(reference, type);
		}

		@Override
		public Instance getInstance(InstanceReference reference) {
			if (isCollectionReference(reference)) {
				return super.getInstance(reference);
			}
			if (reference instanceof FanoutTypeReference) {
				FanoutTypeReference typeRef = (FanoutTypeReference) reference;
				InstanceCollection part = fanout.get(typeRef.type);
				return part == null ? null : part.getInstance(typeRef.reference);
			}
			return original.getInstance(reference);
		}
	}

	/**
	 * Reference to an instance in a fan-out collection of a specific type.
	 * <p>
	 * Intentionally no {@link InstanceReferenceDecorator}, as decorators are
	 * removed when resolving a {@link ResolvableInstanceReference}.
	 */
	private static class FanoutTypeReference implements InstanceReference {

		private final InstanceReference reference;
		private final TypeDefinition type;

		/**
		 * @param reference the reference in the fan-out collection
		 * @param type the type the fan-out collection is associated to
		 */
		public FanoutTypeReference(InstanceReference reference, TypeDefinition type) {
			this.reference = reference;
			this.type = type;
		}

		@Override
		public DataSet getDataSet() {
			return reference.getDataSet();
		}

		@Override
		public int hashCode() {
			return Objects.hash(reference, type);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof FanoutTypeReference)) {
				return false;
			}
			FanoutTypeReference other = (FanoutTypeReference) obj;
			return Objects.equals(type, other.type) && Objects.equals(reference, other.reference);
		}
	}

	/**
	 * Filtered resource iterator.
	 */
	public class FilteredIterator implements ResourceIterator<Instance> {

		private final ResourceIterator<Instance> decoratee;

		/**
		 * The next matching instance
		 */
		private Instance preview;

		/**
		 * States if the value in {@link #preview} represents a valid element
		 */
		private boolean previewPresent;

		/**
		 * States if {@link #preview}/{@link #previewPresent} must be updated
		 */
		private boolean updatePreview = true;

		/**
		 * Iteration context for filters.
		 */
		private final Map<Object, Object> context;

		/**
		 * Create a filtered resource iterator.
		 *
		 * @param decoratee the original iterator
		 */
		public FilteredIterator(ResourceIterator<Instance> decoratee) {
			this.decoratee = decoratee;

			if (filter instanceof ContextAwareFilter) {
				context = Collections.synchronizedMap(new HashMap<>());
			}
			else {
				context = null;
			}
		}

		@Override
		public boolean hasNext() {
			update(); // ensure previewPresent/preview are set

			return previewPresent;
		}

		@Override
		public Instance next() {
			update(); // ensure previewPresent/preview are set

			if (!previewPresent) {
				throw new NoSuchElementException();
			}

			updatePreview = true; // next time, update the preview

			return preview;
		}

		/**
		 * Move {@link #preview} to the next match if possible, update
		 * {@link #previewPresent}.
		 */
		private void update() {
			if (updatePreview) {
				previewPresent = false;

				// find first instance matching the filter
				while (!previewPresent && decoratee.hasNext()) {
					Instance instance = decoratee.next();
					boolean match;
					if (context != null) {
						match = ((ContextAwareFilter) filter).match(instance, context);
					}
					else {
						match = filter.match(instance);
					}
					if (match) {
						previewPresent = true;
						preview = instance;
					}
				}

				if (!previewPresent) {
					preview = null;
				}

				updatePreview = false;
			}
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException(
					"Removing instances not supported on filtered collections");
		}

		@Override
		public void close() {
			decoratee.close();

			// in case the iterator is kept around, clear the context
			if (context != null) {
				context.clear();
			}
		}

	}

	private final Filter filter;

	/**
	 * Create a filtered instance collection.
	 *
	 * @param decoratee the instance collection to perform the selection on
	 * @param filter the filter representing the selection
	 */
	private FilteredInstanceCollection(InstanceCollection decoratee, Filter filter) {
		super(decoratee);
		this.filter = filter;
	}

	@Override
	public ResourceIterator<Instance> iterator() {
		ResourceIterator<Instance> it = decoratee.iterator();
		if (filter instanceof TypeFilter && it instanceof InstanceIterator
				&& ((InstanceIterator) it).supportsTypePeek()) {
			// make use of type peek if possible
			return new TypeFilteredIterator(it, ((TypeFilter) filter).getType());
		}
		return new FilteredIterator(it);
	}

	@Override
	public boolean hasSize() {
		// the size cannot be pre-determined
		return false;
	}

	@Override
	public int size() {
		return UNKNOWN_SIZE;
	}

	@Override
	public boolean isEmpty() {
		ResourceIterator<Instance> it = iterator();
		try {
			return !it.hasNext();
		} finally {
			it.close();
		}
	}

	@Override
	public InstanceCollection select(Filter filter) {
		return new FilteredInstanceCollection(this, filter);
	}

	@Override
	public Map<TypeDefinition, InstanceCollection> fanout() {
		Map<TypeDefinition, InstanceCollection> fanout = super.fanout();
		if (fanout != null) {
			return Maps.transformValues(fanout,
					new Function<InstanceCollection, InstanceCollection>() {

						@Override
						public InstanceCollection apply(InstanceCollection from) {
							return new FilteredInstanceCollection(from, filter);
						}
					});
		}

		return null;
	}

	@Override
	public String toString() {
		return "FilteredInstanceCollection{" + "filter=" + filter + ", decoratee=" + decoratee
				+ '}';
	}
}
