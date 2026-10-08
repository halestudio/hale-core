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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import de.fhg.igd.osgi.util.OsgiUtils;
import eu.esdihumboldt.hale.common.core.report.SimpleLogContext;
import eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty;
import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;

/**
 * Writes and reads property and metadata values with Kryo.
 */
public class ValueCodec {

	/** Value tags */
	static final byte TAG_NULL = 0;
	/** Nested instance */
	public static final byte TAG_INSTANCE = 1;
	/** Nested group */
	public static final byte TAG_GROUP = 2;
	static final byte TAG_KRYO = 3;
	static final byte TAG_GEOMETRY = 4;
	static final byte TAG_GEOMETRY_PROPERTY = 5;
	static final byte TAG_LIST = 6;
	static final byte TAG_SET = 7;
	static final byte TAG_ARRAY = 8;
	static final byte TAG_JAVA = 9;
	static final byte TAG_DROPPED = 10;

	/**
	 * Returned by {@link #read(Kryo, Input, ChildContext, NestedCodec)} for a value
	 * that could not be stored.
	 */
	public static final Object DROPPED = new Object();

	/**
	 * Value types handled by Kryo. Registration ids are derived from the position -
	 * only append to this list.
	 */
	private static final List<Class<?>> REGISTERED_TYPES = List.of(Integer.class, Long.class,
			Short.class, Byte.class, Double.class, Float.class, Boolean.class, Character.class,
			BigDecimal.class, BigInteger.class, byte[].class, int[].class, long[].class,
			short[].class, double[].class, float[].class, char[].class, boolean[].class,
			java.util.Date.class, java.sql.Date.class, java.sql.Time.class,
			java.sql.Timestamp.class, Instant.class, LocalDate.class, LocalTime.class,
			LocalDateTime.class, OffsetDateTime.class, OffsetTime.class, ZonedDateTime.class,
			Duration.class, Period.class, Year.class, YearMonth.class, MonthDay.class, URI.class,
			URL.class, UUID.class);

	private static final int FIRST_REGISTRATION_ID = 20;

	private final Dictionary<CRSDefinition> crsDictionary;
	private final Dictionary<Class<?>> classDictionary;

	/**
	 * @param crsDictionary dictionary for CRS definitions
	 * @param classDictionary dictionary for array component classes
	 */
	public ValueCodec(Dictionary<CRSDefinition> crsDictionary,
			Dictionary<Class<?>> classDictionary) {
		this.crsDictionary = crsDictionary;
		this.classDictionary = classDictionary;
	}

	/**
	 * @return a new configured Kryo instance (not thread-safe, use a pool)
	 */
	public Kryo createKryo() {
		Kryo kryo = new Kryo();
		kryo.setRegistrationRequired(true);
		kryo.setReferences(false);
		int id = FIRST_REGISTRATION_ID;
		for (Class<?> type : REGISTERED_TYPES) {
			if (type == URI.class) {
				kryo.register(type, new URISerializer(), id++);
			}
			else if (type == java.sql.Timestamp.class) {
				kryo.register(type, new TimestampSerializer(), id++);
			}
			else if (type == UUID.class) {
				kryo.register(type, new UUIDSerializer(), id++);
			}
			else if (type == URL.class) {
				kryo.register(type, new URLSerializer(), id++);
			}
			else {
				kryo.register(type, id++);
			}
		}
		kryo.register(QName.class, new QNameSerializer(), id++);
		return kryo;
	}

	/**
	 * Write a value.
	 *
	 * @param kryo the Kryo instance
	 * @param out the output
	 * @param value the value
	 * @param context the property context, may be <code>null</code>
	 * @param nested the codec for nested instances and groups, may be
	 *            <code>null</code> if none are expected
	 */
	public void write(Kryo kryo, Output out, Object value, ChildContext context,
			NestedCodec nested) {
		if (value == null) {
			out.writeByte(TAG_NULL);
		}
		else if (value instanceof Group) {
			if (nested == null) {
				throw new IllegalStateException("Nested instances or groups not supported here");
			}
			out.writeByte(value instanceof eu.esdihumboldt.hale.common.instance.model.Instance
					? TAG_INSTANCE
					: TAG_GROUP);
			nested.write(kryo, out, value, context);
		}
		else if (value instanceof GeometryProperty) {
			GeometryProperty<?> property = (GeometryProperty<?>) value;
			out.writeByte(TAG_GEOMETRY_PROPERTY);
			CRSDefinition crs = property.getCRSDefinition();
			out.writeVarInt(crs == null ? 0 : crsDictionary.idOf(crs) + 1, true);
			Geometry geometry = property.getGeometry();
			out.writeBoolean(geometry != null);
			if (geometry != null) {
				writeGeometry(out, geometry);
			}
		}
		else if (value instanceof Geometry) {
			out.writeByte(TAG_GEOMETRY);
			writeGeometry(out, (Geometry) value);
		}
		else if (kryo.getClassResolver().getRegistration(value.getClass()) != null
				|| value instanceof String) {
			out.writeByte(TAG_KRYO);
			kryo.writeClassAndObject(out, value);
		}
		else if (value instanceof List || value instanceof Set) {
			Collection<?> collection = (Collection<?>) value;
			out.writeByte(value instanceof List ? TAG_LIST : TAG_SET);
			out.writeVarInt(collection.size(), true);
			for (Object element : collection) {
				write(kryo, out, element, context, nested);
			}
		}
		else if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
			Object[] array = (Object[]) value;
			out.writeByte(TAG_ARRAY);
			out.writeVarInt(classDictionary.idOf(value.getClass().getComponentType()), true);
			out.writeVarInt(array.length, true);
			for (Object element : array) {
				write(kryo, out, element, context, nested);
			}
		}
		else {
			byte[] bytes = (value instanceof Serializable) ? javaSerialize(value) : null;
			if (bytes != null) {
				out.writeByte(TAG_JAVA);
				out.writeVarInt(bytes.length, true);
				out.writeBytes(bytes);
			}
			else {
				out.writeByte(TAG_DROPPED);
				SimpleLogContext.getLog()
						.warn("Value of type " + value.getClass().getName()
								+ " cannot be stored in the temporary database and is dropped",
								(Throwable) null);
			}
		}
	}

	/**
	 * Read a value.
	 *
	 * @param kryo the Kryo instance
	 * @param in the input
	 * @param context the property context, may be <code>null</code>
	 * @param nested the codec for nested instances and groups, may be
	 *            <code>null</code> if none are expected
	 * @return the value or {@link #DROPPED}
	 */
	public Object read(Kryo kryo, Input in, ChildContext context, NestedCodec nested) {
		byte tag = in.readByte();
		switch (tag) {
		case TAG_NULL:
			return null;
		case TAG_INSTANCE:
		case TAG_GROUP:
			if (nested == null) {
				throw new IllegalStateException("Nested instances or groups not supported here");
			}
			return nested.read(kryo, in, tag, context);
		case TAG_GEOMETRY_PROPERTY:
			int crsId = in.readVarInt(true);
			CRSDefinition crs = crsId == 0 ? null : crsDictionary.valueOf(crsId - 1);
			Geometry geometry = in.readBoolean() ? readGeometry(in) : null;
			return new DefaultGeometryProperty<>(crs, geometry);
		case TAG_GEOMETRY:
			return readGeometry(in);
		case TAG_KRYO:
			return kryo.readClassAndObject(in);
		case TAG_LIST:
		case TAG_SET:
			int size = in.readVarInt(true);
			Collection<Object> collection = tag == TAG_LIST ? new ArrayList<>(size)
					: new LinkedHashSet<>();
			for (int i = 0; i < size; i++) {
				Object element = read(kryo, in, context, nested);
				if (element != DROPPED) {
					collection.add(element);
				}
			}
			return collection;
		case TAG_ARRAY:
			Class<?> component = classDictionary.valueOf(in.readVarInt(true));
			int length = in.readVarInt(true);
			List<Object> elements = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				Object element = read(kryo, in, context, nested);
				if (element != DROPPED) {
					elements.add(element);
				}
			}
			Object array = Array.newInstance(component, elements.size());
			for (int i = 0; i < elements.size(); i++) {
				Array.set(array, i, elements.get(i));
			}
			return array;
		case TAG_JAVA:
			return javaDeserialize(in.readBytes(in.readVarInt(true)));
		case TAG_DROPPED:
			return DROPPED;
		default:
			throw new IllegalStateException("Unknown value tag " + tag);
		}
	}

	private static void writeGeometry(Output out, Geometry geometry) {
		Coordinate first = geometry.getCoordinate();
		int dimension = (first == null || Double.isNaN(first.getZ())) ? 2 : 3;
		byte[] wkb = new ExtendedWKBWriter(dimension).write(geometry);
		out.writeVarInt(wkb.length, true);
		out.writeBytes(wkb);
	}

	private static Geometry readGeometry(Input in) {
		byte[] wkb = in.readBytes(in.readVarInt(true));
		try {
			return new ExtendedWKBReader().read(wkb);
		} catch (ParseException e) {
			throw new IllegalStateException("Could not read geometry", e);
		}
	}

	private static byte[] javaSerialize(Object value) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(value);
		} catch (IOException e) {
			return null;
		}
		return bytes.toByteArray();
	}

	private static Object javaDeserialize(byte[] bytes) {
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes)) {

			@Override
			protected Class<?> resolveClass(ObjectStreamClass desc)
					throws IOException, ClassNotFoundException {
				try {
					Class<?> result = OsgiUtils.loadClass(desc.getName(), null);
					if (result != null) {
						return result;
					}
				} catch (Throwable e) {
					// not running in OSGi or class not found via OSGi
				}
				return super.resolveClass(desc);
			}
		}) {
			return in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			throw new IllegalStateException("Could not deserialize stored value", e);
		}
	}

	private static class QNameSerializer extends Serializer<QName> {

		@Override
		public void write(Kryo kryo, Output output, QName name) {
			output.writeString(name.getNamespaceURI());
			output.writeString(name.getLocalPart());
			output.writeString(name.getPrefix());
		}

		@Override
		public QName read(Kryo kryo, Input input, Class<? extends QName> type) {
			return new QName(input.readString(), input.readString(), input.readString());
		}
	}

	private static class URISerializer extends Serializer<URI> {

		@Override
		public void write(Kryo kryo, Output output, URI uri) {
			output.writeString(uri.toString());
		}

		@Override
		public URI read(Kryo kryo, Input input, Class<? extends URI> type) {
			return URI.create(input.readString());
		}
	}

	private static class URLSerializer extends Serializer<URL> {

		@Override
		public void write(Kryo kryo, Output output, URL url) {
			output.writeString(url.toExternalForm());
		}

		@Override
		public URL read(Kryo kryo, Input input, Class<? extends URL> type) {
			try {
				return new URL(input.readString());
			} catch (java.net.MalformedURLException e) {
				throw new IllegalStateException("Could not read URL", e);
			}
		}
	}

	private static class UUIDSerializer extends Serializer<UUID> {

		@Override
		public void write(Kryo kryo, Output output, UUID uuid) {
			output.writeLong(uuid.getMostSignificantBits());
			output.writeLong(uuid.getLeastSignificantBits());
		}

		@Override
		public UUID read(Kryo kryo, Input input, Class<? extends UUID> type) {
			return new UUID(input.readLong(), input.readLong());
		}
	}

	private static class TimestampSerializer extends Serializer<java.sql.Timestamp> {

		@Override
		public void write(Kryo kryo, Output output, java.sql.Timestamp value) {
			output.writeLong(value.getTime());
			output.writeVarInt(value.getNanos(), true);
		}

		@Override
		public java.sql.Timestamp read(Kryo kryo, Input input,
				Class<? extends java.sql.Timestamp> type) {
			java.sql.Timestamp result = new java.sql.Timestamp(input.readLong());
			result.setNanos(input.readVarInt(true));
			return result;
		}
	}

}
