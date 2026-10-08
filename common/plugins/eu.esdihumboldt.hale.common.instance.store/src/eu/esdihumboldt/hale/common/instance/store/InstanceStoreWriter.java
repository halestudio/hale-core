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

import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;

/**
 * Adds instances to an {@link InstanceStore}. Implementations are thread-safe.
 */
public interface InstanceStoreWriter extends Closeable {

	/**
	 * Add an instance.
	 *
	 * @param instance the instance, must have a type definition
	 * @return the reference to the stored instance, can be resolved immediately via
	 *         the store's instance collection
	 * @throws IllegalArgumentException if the instance has no type definition or
	 *             cannot be serialized (the instance is not stored)
	 * @throws IllegalStateException if the writer is closed or failed
	 */
	InstanceReference add(Instance instance);

	/**
	 * Block until all instances added so far are visible when iterating the store's
	 * instance collections.
	 *
	 * @throws IllegalStateException if writing failed
	 */
	void flush();

	/**
	 * Flush and close the writer.
	 *
	 * @throws IOException if writing failed
	 */
	@Override
	void close() throws IOException;

}
