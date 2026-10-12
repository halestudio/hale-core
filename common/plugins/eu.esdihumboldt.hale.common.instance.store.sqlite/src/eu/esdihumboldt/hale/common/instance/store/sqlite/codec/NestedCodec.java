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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Writes and reads nested instances and groups for {@link ValueCodec}.
 */
public interface NestedCodec {

	/**
	 * Write the body of a nested instance or group (the tag is already written).
	 *
	 * @param kryo the Kryo instance
	 * @param out the output
	 * @param value the instance or group
	 * @param context the property context, may be <code>null</code>
	 */
	void write(Kryo kryo, Output out, Object value, ChildContext context);

	/**
	 * Read a nested instance or group.
	 *
	 * @param kryo the Kryo instance
	 * @param in the input
	 * @param tag {@link ValueCodec#TAG_INSTANCE} or {@link ValueCodec#TAG_GROUP}
	 * @param context the property context, may be <code>null</code>
	 * @return the instance or group
	 */
	Object read(Kryo kryo, Input in, byte tag, ChildContext context);

}
