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

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Temporary store for the instances of one data set. Closing the store deletes
 * its files.
 */
public interface InstanceStore extends Closeable {

	/**
	 * Get a writer to add instances. Returns the currently open writer if there is
	 * one.
	 *
	 * @return the writer, thread-safe
	 */
	InstanceStoreWriter openWriter();

	/**
	 * Get the stored instances. The collection is reiterable and resolves
	 * references returned by the writer. Iteration reflects the committed state
	 * (see {@link InstanceStoreWriter#flush()}).
	 *
	 * @param types the type index used to determine the instance types; if
	 *            <code>null</code> the collection can only be used to resolve
	 *            references
	 * @return the instance collection, an
	 *         {@link eu.esdihumboldt.hale.common.instance.model.ext.InstanceCollection2}
	 *         if the store supports fan-out
	 */
	InstanceCollection getInstances(TypeIndex types);

	/**
	 * @param types the type index
	 * @return the types of the type index that are present in the store
	 */
	Set<TypeDefinition> getStoredTypes(TypeIndex types);

	/**
	 * Remove all instances. An open writer is closed.
	 */
	void clear();

	/**
	 * Close the store and delete its files.
	 */
	@Override
	void close() throws IOException;

}
