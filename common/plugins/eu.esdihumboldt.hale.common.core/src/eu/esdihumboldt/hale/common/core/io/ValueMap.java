/*
 * Copyright (c) 2014 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.core.io;

import java.util.HashMap;
import java.util.Map;

/**
 * A map of {@link Value}s to {@link Value}s that can itself be represented as
 * complex value.
 *
 * @author Simon Templer
 */
public class ValueMap extends HashMap<Value, Value> {

	private static final long serialVersionUID = 4516400982335531765L;

	/**
	 * @see HashMap#HashMap()
	 */
	public ValueMap() {
		super();
	}

	/**
	 * @see HashMap#HashMap(int, float)
	 */
	public ValueMap(int initialCapacity, float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	/**
	 * @see HashMap#HashMap(int)
	 */
	public ValueMap(int initialCapacity) {
		super(initialCapacity);
	}

	/**
	 * @see HashMap#HashMap(Map)
	 */
	public ValueMap(Map<? extends Value, ? extends Value> m) {
		super(m);
	}

	/**
	 * Creates a {@link Value} wrapping the map.
	 *
	 * @return the {@link Value} representation of this map
	 */
	public Value toValue() {
		return Value.complex(this);
	}

	/**
	 * Get the value for the given key.
	 *
	 * @param key the key
	 * @return the value or the NULL value, never <code>null</code>
	 */
	public Value getSafe(Value key) {
		Value val = get(key);
		if (val == null) {
			val = Value.NULL;
		}
		return val;
	}

}
