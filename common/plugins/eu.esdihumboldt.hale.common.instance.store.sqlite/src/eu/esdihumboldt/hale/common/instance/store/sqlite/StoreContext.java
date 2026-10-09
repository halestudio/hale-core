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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.store.sqlite.codec.InstanceCodec;
import eu.esdihumboldt.hale.common.instance.store.sqlite.codec.StoredInstance;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * State shared by the store, its writer and collections.
 */
class StoreContext {

	/**
	 * Serialized instance not yet committed to the database.
	 *
	 * @param typeId the type id
	 * @param payload the serialized instance
	 */
	record PendingRecord(int typeId, byte[] payload) {
	}

	final UUID key = UUID.randomUUID();
	final DataSet dataSet;
	final InstanceCodec codec = new InstanceCodec();
	final TypeRegistry types = new TypeRegistry();
	final ConcurrentHashMap<Long, PendingRecord> pending = new ConcurrentHashMap<>();
	final ConcurrentHashMap<Integer, LongAdder> counts = new ConcurrentHashMap<>();
	final AtomicLong nextId = new AtomicLong(1);
	volatile SqliteDatabase database;

	StoreContext(DataSet dataSet) {
		this.dataSet = dataSet;
	}

	long committedCount(int typeId) {
		LongAdder count = counts.get(typeId);
		return count == null ? 0 : count.sum();
	}

	/**
	 * Load an instance by id, including instances not yet committed.
	 *
	 * @param id the instance id
	 * @param type the instance type
	 * @param types the type index for nested types, may be <code>null</code>
	 * @return the instance or <code>null</code> if it does not exist
	 */
	StoredInstance load(long id, TypeDefinition type, TypeIndex types) {
		PendingRecord record = pending.get(id);
		byte[] payload = record != null ? record.payload() : database.withReader(c -> {
			try (PreparedStatement p = c
					.prepareStatement("SELECT payload FROM instances WHERE id = ?")) {
				p.setLong(1, id);
				try (ResultSet rs = p.executeQuery()) {
					return rs.next() ? rs.getBytes(1) : null;
				}
			}
		});
		return payload == null ? null : codec.decode(payload, type, dataSet, key, id, types);
	}

	/**
	 * Forget all instances (after the database was recreated).
	 */
	void reset() {
		pending.clear();
		counts.clear();
	}

}
