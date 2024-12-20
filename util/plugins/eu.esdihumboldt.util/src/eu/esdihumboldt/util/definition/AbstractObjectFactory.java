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
package eu.esdihumboldt.util.definition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import eu.esdihumboldt.util.Pair;

/**
 * Provides support for converting certain objects to a definition string and
 * vice versa based on the {@link ObjectDefinition}ies available for the
 * supported object type and its sub-types.
 *
 * @param <T> the supported object type
 * @param <D> the supported definition type
 *
 * @author Simon Templer
 */
public abstract class AbstractObjectFactory<T, D extends ObjectDefinition<? extends T>> {

	/**
	 * The cached sorted definitions.
	 */
	private Iterable<D> sortedDefinitions;

	/**
	 * Default constructor.
	 */
	public AbstractObjectFactory() {
		super();
	}

	/**
	 * Get all available definitions compatible with the supported type.
	 *
	 * @return the definitions
	 */
	protected abstract List<D> getDefinitions();

	/**
	 * Represent the given object as a definition string, so that it can be used to
	 * again create an object instance using {@link #parse(String)}.
	 *
	 * @param <X> the object type, an {@link ObjectDefinition} supporting this type
	 *            must be available
	 * @param object the object to create a string representation for
	 * @return the string representation of the object or <code>null</code> if no
	 *         corresponding {@link ObjectDefinition} is available
	 *
	 * @see #getDefinitions()
	 */
	public <X extends T> String asString(X object) {
		Pair<String, String> idString = asPair(object);

		if (idString != null) {
			return idString.getFirst() + ':' + idString.getSecond();
		}

		return null;
	}

	/**
	 * Get the object type identifier and the string representation of the object.
	 * Please note that the string representation is not the same as the definition
	 * string retrieved through {@link #asString(Object)}.
	 *
	 * @param <X> the object type, an {@link ObjectDefinition} supporting this type
	 *            must be available
	 * @param object the object to create a string representation for
	 * @return the object type identifier and string representation of the object,
	 *         <code>null</code> if no corresponding {@link ObjectDefinition} is
	 *         available
	 *
	 * @see #getDefinitions()
	 */
	@SuppressWarnings("unchecked")
	public <X extends T> Pair<String, String> asPair(X object) {
		// check if the given object is null
		if (object == null) {
			return null;
		}

		for (D definition : getSortedDefinitions()) {
			if (definition.getObjectClass() == null) {
				continue;
			}

			// comparison based on classes
			if (definition.getObjectClass() != null
					&& definition.getObjectClass().equals(object.getClass())) {
				return new Pair<String, String>(definition.getIdentifier(),
						((ObjectDefinition<T>) definition).asString(object));
			}
			// compare based on interfaces
			else if (definition.getObjectClass().isInterface() && compare(object, definition)) {
				return new Pair<String, String>(definition.getIdentifier(),
						((ObjectDefinition<T>) definition).asString(object));
			}
		}

		return null;
	}

	/**
	 * Compares two objects based on there implemented interfaces.
	 *
	 * @param object object to check
	 * @param definition definition to check on
	 * @return true if they implemented the same interfaces
	 */
	private boolean compare(T object, D definition) {
		if (definition.getObjectClass().isAssignableFrom(object.getClass())) {
			return true;
		}

		return false;
	}

	/**
	 * Provides the definitions in a topological order.
	 *
	 * @return sorted definitions
	 */
	private Iterable<D> getSortedDefinitions() {
		if (sortedDefinitions == null) {
			// create a copy of the definition list to ensure we are dealing
			// with the same instances
			List<D> list = new ArrayList<D>(getDefinitions());

			// result list
			ArrayList<D> result = new ArrayList<D>(list.size());

			while (!list.isEmpty()) {
				Iterator<D> iter = list.iterator();
				while (iter.hasNext()) {
					D def = iter.next();
					boolean isSuper = false;

					for (D d : list) {
						// skip if it's the same object
						if (def.equals(d))
							continue;

						// check if it's super class
						if (def.getObjectClass().isAssignableFrom(d.getObjectClass())) {
							isSuper = true;
							break;
						}
					}

					/*
					 * if it's not a super class/interface add it to result and remove it from list
					 */
					if (!isSuper) {
						result.add(def);
						iter.remove();
					}
				}
			}

			sortedDefinitions = result;
		}

		// return the sorted list
		return sortedDefinitions;
	}

	/**
	 * Parse the given definition string and create a corresponding object.
	 *
	 * @param value the definition string to parse
	 * @return the created object or <code>null</code>
	 */
	public T parse(String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}

		for (D definition : getDefinitions()) {
			String prefix = definition.getIdentifier() + ":"; //$NON-NLS-1$
			if (value.startsWith(prefix)) {
				String main = value.substring(prefix.length());
				return definition.parse(main);
			}
		}

		return null;
	}

	/**
	 * Recreate an object from type identifier and string representation as
	 * retrieved using {@link #asPair(Object)}.
	 *
	 * @param typeId the type identifier
	 * @param value the object string representation
	 * @return the created object or <code>null</code>
	 */
	public T from(String typeId, String value) {
		for (D definition : getDefinitions()) {
			if (definition.getIdentifier().equals(typeId)) {
				return definition.parse(value);
			}
		}

		return null;
	}

}
