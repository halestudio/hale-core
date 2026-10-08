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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;

/**
 * Assigns ids to type names. Thread-safe.
 */
public class TypeRegistry {

	private final ConcurrentHashMap<QName, Integer> ids = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, QName> names = new ConcurrentHashMap<>();
	private final AtomicInteger next = new AtomicInteger(1);

	/**
	 * @param name the type name
	 * @return the type id, assigned if not yet present
	 */
	public int idOf(QName name) {
		return ids.computeIfAbsent(name, n -> {
			int id = next.getAndIncrement();
			names.put(id, n);
			return id;
		});
	}

	/**
	 * @param name the type name
	 * @return the type id or <code>null</code> if the type is unknown
	 */
	public Integer findId(QName name) {
		return ids.get(name);
	}

	/**
	 * @param id the type id
	 * @return the type name
	 */
	public QName nameOf(int id) {
		return names.get(id);
	}

	/**
	 * @return the assigned ids
	 */
	public Set<Integer> ids() {
		return names.keySet();
	}

}
