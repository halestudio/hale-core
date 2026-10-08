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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.xml.namespace.QName;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.MutableGroup;
import eu.esdihumboldt.hale.common.instance.model.MutableInstance;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultGroup;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance;
import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.DefinitionGroup;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Serializes instances to bytes and back. Thread-safe. Uses in-memory
 * dictionaries, so serialized data can only be read by the same codec.
 *
 * <pre>
 * Instance := typeRef? value metadata group   (typeRef only for nested instances)
 * Group    := propertyCount { nameId valueCount { value } }
 * metadata := keyCount { keyId valueCount { value } }
 * </pre>
 */
public class InstanceCodec {

	private static final ALogger log = ALoggerFactory.getLogger(InstanceCodec.class);

	private final ValueCodec values = new ValueCodec(new Dictionary<>(), new Dictionary<>());
	private final Dictionary<QName> names = new Dictionary<>();
	private final Dictionary<String> metadataKeys = new Dictionary<>();

	private final Pool<Kryo> kryoPool = new Pool<Kryo>(true, false, 32) {

		@Override
		protected Kryo create() {
			return values.createKryo();
		}
	};

	/**
	 * Encode a top-level instance. Its type is not included.
	 *
	 * @param instance the instance
	 * @return the encoded instance
	 */
	public byte[] encode(Instance instance) {
		Kryo kryo = kryoPool.obtain();
		try (Output out = new Output(1024, -1)) {
			new TreeCodec(null).writeInstanceBody(kryo, out, instance);
			return out.toBytes();
		} finally {
			kryoPool.free(kryo);
		}
	}

	/**
	 * Decode a top-level instance.
	 *
	 * @param data the encoded instance
	 * @param type the instance type
	 * @param dataSet the data set
	 * @param storeKey the store key
	 * @param id the instance id in the store
	 * @param types the type index to resolve types of nested instances, may be
	 *            <code>null</code>
	 * @return the instance
	 */
	public StoredInstance decode(byte[] data, TypeDefinition type, DataSet dataSet, UUID storeKey,
			long id, TypeIndex types) {
		Kryo kryo = kryoPool.obtain();
		try (Input in = new Input(data)) {
			StoredInstance instance = new StoredInstance(type, dataSet, storeKey, id);
			new TreeCodec(types).readInstanceBody(kryo, in, instance);
			return instance;
		} finally {
			kryoPool.free(kryo);
		}
	}

	private static ChildDefinition<?> child(ChildContext context) {
		if (context == null || context.parent() == null) {
			return null;
		}
		return context.parent().getChild(context.name());
	}

	private static TypeDefinition declaredType(ChildContext context) {
		ChildDefinition<?> child = child(context);
		return (child != null && child.asProperty() != null) ? child.asProperty().getPropertyType()
				: null;
	}

	private static DefinitionGroup declaredGroup(ChildContext context) {
		ChildDefinition<?> child = child(context);
		return child != null ? child.asGroup() : null;
	}

	private class TreeCodec implements NestedCodec {

		private final TypeIndex types;

		TreeCodec(TypeIndex types) {
			this.types = types;
		}

		void writeInstanceBody(Kryo kryo, Output out, Instance instance) {
			values.write(kryo, out, instance.getValue(), null, this);

			Set<String> keys = instance.getMetaDataNames();
			out.writeVarInt(keys.size(), true);
			for (String key : keys) {
				out.writeVarInt(metadataKeys.idOf(key), true);
				List<Object> data = instance.getMetaData(key);
				out.writeVarInt(data.size(), true);
				for (Object value : data) {
					values.write(kryo, out, value, null, this);
				}
			}

			writeGroup(kryo, out, instance);
		}

		void writeGroup(Kryo kryo, Output out, Group group) {
			List<QName> properties = new ArrayList<>();
			group.getPropertyNames().forEach(properties::add);
			out.writeVarInt(properties.size(), true);
			for (QName name : properties) {
				out.writeVarInt(names.idOf(name), true);
				Object[] propertyValues = group.getProperty(name);
				int count = propertyValues == null ? 0 : propertyValues.length;
				out.writeVarInt(count, true);
				ChildContext context = new ChildContext(group.getDefinition(), name);
				for (int i = 0; i < count; i++) {
					values.write(kryo, out, propertyValues[i], context, this);
				}
			}
		}

		void readInstanceBody(Kryo kryo, Input in, MutableInstance instance) {
			Object value = values.read(kryo, in, null, this);
			instance.setValue(value == ValueCodec.DROPPED ? null : value);

			int keyCount = in.readVarInt(true);
			for (int k = 0; k < keyCount; k++) {
				String key = metadataKeys.valueOf(in.readVarInt(true));
				int count = in.readVarInt(true);
				List<Object> data = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					Object item = values.read(kryo, in, null, this);
					if (item != ValueCodec.DROPPED) {
						data.add(item);
					}
				}
				instance.setMetaData(key, data.toArray());
			}

			readGroup(kryo, in, instance);
		}

		void readGroup(Kryo kryo, Input in, MutableGroup group) {
			int propertyCount = in.readVarInt(true);
			for (int p = 0; p < propertyCount; p++) {
				QName name = names.valueOf(in.readVarInt(true));
				int count = in.readVarInt(true);
				ChildContext context = new ChildContext(group.getDefinition(), name);
				for (int i = 0; i < count; i++) {
					Object value = values.read(kryo, in, context, this);
					if (value != ValueCodec.DROPPED) {
						group.addProperty(name, value);
					}
				}
			}
		}

		@Override
		public void write(Kryo kryo, Output out, Object value, ChildContext context) {
			if (value instanceof Instance) {
				Instance instance = (Instance) value;
				TypeDefinition actual = instance.getDefinition();
				if (actual != null && !Objects.equals(actual, declaredType(context))) {
					out.writeVarInt(names.idOf(actual.getName()) + 1, true);
				}
				else {
					out.writeVarInt(0, true);
				}
				writeInstanceBody(kryo, out, instance);
			}
			else {
				writeGroup(kryo, out, (Group) value);
			}
		}

		@Override
		public Object read(Kryo kryo, Input in, byte tag, ChildContext context) {
			if (tag == ValueCodec.TAG_INSTANCE) {
				int typeRef = in.readVarInt(true);
				TypeDefinition type = declaredType(context);
				if (typeRef > 0) {
					QName typeName = names.valueOf(typeRef - 1);
					TypeDefinition found = types != null ? types.getType(typeName) : null;
					if (found != null) {
						type = found;
					}
					else {
						log.warn("Type {} of a nested instance not found, using declared type",
								typeName);
					}
				}
				DefaultInstance instance = new DefaultInstance(type, null);
				readInstanceBody(kryo, in, instance);
				return instance;
			}
			DefaultGroup group = new DefaultGroup(declaredGroup(context));
			readGroup(kryo, in, group);
			return group;
		}
	}

}
