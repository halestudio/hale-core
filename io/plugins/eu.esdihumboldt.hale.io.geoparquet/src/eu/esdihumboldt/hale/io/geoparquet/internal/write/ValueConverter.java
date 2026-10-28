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
package eu.esdihumboldt.hale.io.geoparquet.internal.write;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.parquet.io.api.Binary;

import eu.esdihumboldt.hale.common.convert.ConversionUtil;
import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ColumnMapping;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ValueKind;

/**
 * Converts property values to the representation written to Parquet columns
 * (everything except geometry columns). Conversion problems are reported to the
 * log and result in null values.
 */
class ValueConverter {

	private static final int DECIMAL_PRECISION = 38;

	private static final int DECIMAL_BYTES = 16;

	private final SimpleLog log;
	private final JsonValueSerializer json;

	/** Warnings that were already reported, per column. */
	private final Map<ColumnMapping, Set<String>> warned = new IdentityHashMap<>();

	ValueConverter(SimpleLog log, JsonValueSerializer json) {
		this.log = log;
		this.json = json;
	}

	/**
	 * @return if the warning with the given key is reported for the first time for
	 *         the column
	 */
	boolean firstTime(ColumnMapping column, String key) {
		return warned.computeIfAbsent(column, c -> new HashSet<>()).add(key);
	}

	/**
	 * Convert a value to the representation written to the column.
	 *
	 * @return a {@link Binary}, {@link Boolean}, {@link Integer}, {@link Long},
	 *         {@link Float} or {@link Double}, <code>null</code> if there is no
	 *         value or the conversion failed (reported to the log)
	 */
	Object convert(ColumnMapping column, Object raw, List<QName> path) {
		ValueKind kind = column.getValueKind();
		try {
			if (kind == ValueKind.JSON) {
				return Binary.fromString(json.toJson(raw, path));
			}
			if (kind == ValueKind.WKT) {
				// geometries may be held below the value (e.g. GML geometry property)
				String wkt = JsonValueSerializer.toWkt(raw);
				return wkt == null ? null : Binary.fromString(wkt);
			}
			Object value = ValueWriter.unwrap(raw);
			if (value == null) {
				return null;
			}
			Object result = switch (kind) {
			case STRING -> Binary.fromString(as(value, String.class));
			case BOOLEAN -> as(value, Boolean.class);
			case INT32 -> as(value, Integer.class);
			case INT64 -> as(value, Long.class);
			case FLOAT -> value instanceof Number ? (Object) ((Number) value).floatValue()
					: as(value, Float.class);
			case DOUBLE -> value instanceof Number ? (Object) ((Number) value).doubleValue()
					: as(value, Double.class);
			case DATE -> (int) as(value, LocalDate.class).toEpochDay();
			case TIMESTAMP_MILLIS -> toInstant(value).toEpochMilli();
			case TIME_MILLIS -> (int) (toLocalTime(value).toNanoOfDay() / 1_000_000L);
			case BINARY -> Binary.fromConstantByteArray(as(value, byte[].class));
			case DECIMAL_AS_STRING -> Binary.fromString(toBigDecimalString(value));
			case DECIMAL -> toDecimal(column, value);
			case WKB, JSON, WKT -> throw new IllegalStateException("Unexpected kind " + kind);
			};
			if (result == null) {
				throw new IllegalStateException("Conversion yielded no value");
			}
			return result;
		} catch (RuntimeException e) {
			log.error("Could not convert value of column " + column.getColumnName() + " (" + kind
					+ "): " + e.getMessage(), e);
			return null;
		}
	}

	private static <T> T as(Object value, Class<T> type) {
		if (type.isInstance(value)) {
			return type.cast(value);
		}
		return ConversionUtil.getAs(value, type);
	}

	private static Instant toInstant(Object value) {
		if (value instanceof LocalDateTime) {
			return ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
		}
		return as(value, Instant.class);
	}

	private static LocalTime toLocalTime(Object value) {
		if (value instanceof java.sql.Time) {
			return ((java.sql.Time) value).toLocalTime();
		}
		return as(value, LocalTime.class);
	}

	private static String toBigDecimalString(Object value) {
		if (value instanceof BigInteger) {
			return value.toString();
		}
		return as(value, BigDecimal.class).toPlainString();
	}

	/**
	 * Convert a number to a decimal with the scale of the column (rounded
	 * half-even), encoded as big-endian two's complement unscaled value in 16
	 * bytes.
	 */
	private Binary toDecimal(ColumnMapping column, Object value) {
		BigDecimal decimal = value instanceof BigInteger ? new BigDecimal((BigInteger) value)
				: as(value, BigDecimal.class);
		BigDecimal scaled;
		try {
			scaled = decimal.setScale(column.getDecimalScale(), RoundingMode.UNNECESSARY);
		} catch (ArithmeticException e) {
			scaled = decimal.setScale(column.getDecimalScale(), RoundingMode.HALF_EVEN);
			if (firstTime(column, "rounding")) {
				log.warn("Value " + decimal.toPlainString() + " of column " + column.getColumnName()
						+ " is rounded to scale " + column.getDecimalScale()
						+ " (further roundings in this column are not reported)");
			}
		}
		BigInteger unscaled = scaled.unscaledValue();
		if (unscaled.abs().toString().length() > DECIMAL_PRECISION) {
			throw new IllegalArgumentException("Value " + decimal.toPlainString()
					+ " exceeds the precision of " + DECIMAL_PRECISION + " digits");
		}
		byte[] bytes = unscaled.toByteArray();
		byte[] fixed = new byte[DECIMAL_BYTES];
		Arrays.fill(fixed, (byte) (unscaled.signum() < 0 ? 0xFF : 0x00));
		System.arraycopy(bytes, 0, fixed, DECIMAL_BYTES - bytes.length, bytes.length);
		return Binary.fromConstantByteArray(fixed);
	}

}
