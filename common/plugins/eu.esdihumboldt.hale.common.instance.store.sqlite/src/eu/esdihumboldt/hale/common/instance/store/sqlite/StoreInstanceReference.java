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

import java.util.Objects;
import java.util.UUID;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Identifiable;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Reference to an instance in a SQLite instance store.
 */
public final class StoreInstanceReference implements InstanceReference, Identifiable {

	private final UUID storeKey;
	private final long id;
	private final DataSet dataSet;
	private final TypeDefinition type;

	/**
	 * @param storeKey the store key
	 * @param id the instance id in the store
	 * @param dataSet the data set
	 * @param type the instance type
	 */
	public StoreInstanceReference(UUID storeKey, long id, DataSet dataSet, TypeDefinition type) {
		this.storeKey = storeKey;
		this.id = id;
		this.dataSet = dataSet;
		this.type = type;
	}

	@Override
	public DataSet getDataSet() {
		return dataSet;
	}

	@Override
	public Object getId() {
		return id;
	}

	/**
	 * @return the store key
	 */
	public UUID getStoreKey() {
		return storeKey;
	}

	/**
	 * @return the instance id in the store
	 */
	public long getStoreId() {
		return id;
	}

	/**
	 * @return the instance type
	 */
	public TypeDefinition getType() {
		return type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(storeKey, id, dataSet);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof StoreInstanceReference)) {
			return false;
		}
		StoreInstanceReference other = (StoreInstanceReference) obj;
		return id == other.id && storeKey.equals(other.storeKey) && dataSet == other.dataSet;
	}

	@Override
	public String toString() {
		return "StoreInstanceReference [id=" + id + ", dataSet=" + dataSet + "]";
	}

}
