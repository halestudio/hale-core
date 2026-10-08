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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory dictionary assigning ids to values. Ids are only valid
 * for the lifetime of the dictionary.
 *
 * @param <T> the value type
 */
public class Dictionary<T> {

	private final ConcurrentHashMap<T, Integer> ids = new ConcurrentHashMap<>();
	private final List<T> values = new CopyOnWriteArrayList<>();

	/**
	 * @param value the value, not <code>null</code>
	 * @return the id of the value, assigned if not yet present
	 */
	public int idOf(T value) {
		return ids.computeIfAbsent(value, v -> {
			synchronized (values) {
				values.add(v);
				return values.size() - 1;
			}
		});
	}

	/**
	 * @param id the id
	 * @return the value with the given id
	 */
	public T valueOf(int id) {
		return values.get(id);
	}

}
