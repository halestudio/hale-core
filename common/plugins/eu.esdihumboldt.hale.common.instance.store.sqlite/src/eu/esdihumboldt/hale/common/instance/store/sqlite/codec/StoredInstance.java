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
package eu.esdihumboldt.hale.common.instance.store.sqlite.codec;

import java.util.UUID;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Identifiable;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Instance read from a temporary instance store. Detached copy, changes are not
 * persisted.
 */
public class StoredInstance extends DefaultInstance implements Identifiable {

	private final UUID storeKey;
	private final long storeId;

	/**
	 * @param definition the type definition
	 * @param dataSet the data set
	 * @param storeKey the key of the store the instance was read from
	 * @param storeId the instance id in the store
	 */
	public StoredInstance(TypeDefinition definition, DataSet dataSet, UUID storeKey, long storeId) {
		super(definition, dataSet);
		this.storeKey = storeKey;
		this.storeId = storeId;
	}

	/**
	 * @return the key of the store the instance was read from
	 */
	public UUID getStoreKey() {
		return storeKey;
	}

	/**
	 * @return the instance id in the store
	 */
	public long getStoreId() {
		return storeId;
	}

	@Override
	public Object getId() {
		return storeId;
	}

}
