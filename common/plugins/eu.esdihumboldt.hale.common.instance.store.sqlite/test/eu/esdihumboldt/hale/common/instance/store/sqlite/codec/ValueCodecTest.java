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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.core.report.SimpleLogContext;
import eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty;
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;

public class ValueCodecTest {

	private final ValueCodec codec = new ValueCodec(new Dictionary<>(), new Dictionary<>());
	private final GeometryFactory gf = new GeometryFactory();

	public static class SerializableValue implements Serializable {

		private static final long serialVersionUID = 1L;
		private final String text;

		public SerializableValue(String text) {
			this.text = text;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof SerializableValue
					&& Objects.equals(text, ((SerializableValue) o).text);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(text);
		}
	}

	private Object roundTrip(Object value) {
		Kryo kryo = codec.createKryo();
		Output out = new Output(256, -1);
		codec.write(kryo, out, value, null, null);
		Input in = new Input(out.toBytes());
		return codec.read(kryo, in, null, null);
	}

	private void assertRoundTrip(Object value) {
		Object result = roundTrip(value);
		assertEquals(value, result);
		if (value != null) {
			assertEquals(value.getClass(), result.getClass());
		}
	}

	@Test
	public void testSimpleValues() throws Exception {
		for (Object value : Arrays.asList(null, "text", 42, 42L, (short) 4, (byte) 2, 1.5d, 2.5f,
				true, 'c', new BigDecimal("123.456"), new BigInteger("12345678901234567890"),
				new Date(1234567890123L), new java.sql.Date(1234567890000L),
				Instant.parse("2026-10-08T10:15:30.123Z"), LocalDate.of(2026, 10, 8),
				ZonedDateTime.parse("2026-10-08T10:15:30+02:00[Europe/Berlin]"),
				URI.create("http://example.com/a"), new URL("http://example.com/b"),
				UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
				new QName("http://example.com", "local", "ex"))) {
			assertRoundTrip(value);
		}
	}

	private static final Set<Class<?>> WRAPPERS = Set.of(Integer.class, Long.class, Short.class,
			Byte.class, Double.class, Float.class, Boolean.class, Character.class);

	@Test
	public void testRegistrationIdsFollowTablePosition() {
		Kryo kryo = codec.createKryo();
		for (int i = 0; i < ValueCodec.REGISTRATIONS.size(); i++) {
			Class<?> type = ValueCodec.REGISTRATIONS.get(i).type();
			if (WRAPPERS.contains(type)) {
				// Kryo resolves primitive wrappers to the built-in primitive
				// registrations; the table entry only reserves the id
				continue;
			}
			assertEquals(type.getName(), ValueCodec.FIRST_REGISTRATION_ID + i,
					kryo.getRegistration(type).getId());
		}
	}

	@Test
	public void testQName() {
		assertRoundTrip(new QName("http://example.com", "local", "ex"));
		assertRoundTrip(new QName("local"));
	}

	@Test
	public void testTimestampKeepsNanos() {
		Timestamp ts = new Timestamp(1234567890123L);
		ts.setNanos(123456789);
		assertRoundTrip(ts);
	}

	@Test
	public void testByteArray() {
		byte[] bytes = { 1, 2, 3 };
		assertArrayEquals(bytes, (byte[]) roundTrip(bytes));
	}

	@Test
	public void testObjectArrayKeepsComponentType() {
		String[] array = { "a", "b" };
		Object result = roundTrip(array);
		assertTrue(result instanceof String[]);
		assertArrayEquals(array, (String[]) result);
	}

	@Test
	public void testCollections() {
		List<Object> list = new ArrayList<>(List.of("a", 1, List.of(2L, "b")));
		assertEquals(list, roundTrip(list));
		Set<Object> set = new LinkedHashSet<>(List.of("x", "y"));
		Object result = roundTrip(set);
		assertTrue(result instanceof Set);
		assertEquals(set, result);
	}

	@Test
	public void testGeometries() {
		Point point3d = gf.createPoint(new Coordinate(1, 2, 3));
		Geometry result = (Geometry) roundTrip(point3d);
		assertTrue(point3d.equalsExact(result));
		assertEquals(3.0, result.getCoordinate().getZ(), 0.0);

		LinearRing ring = gf.createLinearRing(new Coordinate[] { new Coordinate(0, 0),
				new Coordinate(1, 0), new Coordinate(1, 1), new Coordinate(0, 0) });
		Object ringResult = roundTrip(ring);
		assertTrue(ringResult instanceof LinearRing);
		assertTrue(ring.equalsExact((Geometry) ringResult));
	}

	@Test
	public void testGeometryPropertyKeepsCrs() {
		CRSDefinition crs = new CodeDefinition("EPSG:4326");
		Point point = gf.createPoint(new Coordinate(8, 50));
		GeometryProperty<?> result = (GeometryProperty<?>) roundTrip(
				new DefaultGeometryProperty<>(crs, point));
		assertSame(crs, result.getCRSDefinition());
		assertTrue(point.equalsExact(result.getGeometry()));
	}

	@Test
	public void testSerializableFallback() {
		assertRoundTrip(new SerializableValue("hello"));
	}

	@Test
	public void testNotSerializableIsDropped() {
		List<String> warnings = new ArrayList<>();
		SimpleLog log = new SimpleLog() {

			@Override
			public void warn(String message, Throwable e) {
				warnings.add(message);
			}

			@Override
			public void error(String message, Throwable e) {
				warnings.add(message);
			}

			@Override
			public void info(String message, Throwable e) {
				// ignore
			}
		};
		Object result = SimpleLogContext.withLog(log, () -> roundTrip(new Object()));
		assertSame(ValueCodec.DROPPED, result);
		assertEquals(1, warnings.size());
	}

	/**
	 * Serializable value that fails with a runtime exception when serialized.
	 */
	private static class FailingSerializableValue implements java.io.Serializable {

		private static final long serialVersionUID = 1L;

		private void writeObject(java.io.ObjectOutputStream out) {
			throw new IllegalStateException("Cannot serialize");
		}
	}

	@Test
	public void testSerializationRuntimeExceptionIsDropped() {
		List<String> warnings = new ArrayList<>();
		SimpleLog log = new SimpleLog() {

			@Override
			public void warn(String message, Throwable e) {
				warnings.add(message);
			}

			@Override
			public void error(String message, Throwable e) {
				warnings.add(message);
			}

			@Override
			public void info(String message, Throwable e) {
				// ignore
			}
		};
		Object result = SimpleLogContext.withLog(log,
				() -> roundTrip(new FailingSerializableValue()));
		assertSame(ValueCodec.DROPPED, result);
		assertEquals(1, warnings.size());
	}
}
