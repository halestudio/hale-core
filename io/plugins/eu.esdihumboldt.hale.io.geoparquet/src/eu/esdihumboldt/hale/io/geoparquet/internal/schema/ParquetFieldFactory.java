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
package eu.esdihumboldt.hale.io.geoparquet.internal.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;

import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;

/**
 * Determines value kinds for property bindings and creates the corresponding
 * Parquet fields.
 */
class ParquetFieldFactory {

	private static final int DECIMAL_PRECISION = 38;

	private static final int DECIMAL_BYTES = 16;

	private final MappingOptions options;

	ParquetFieldFactory(MappingOptions options) {
		this.options = options;
	}

	int decimalScale(ValueKind kind, Class<?> binding) {
		if (kind != ValueKind.DECIMAL) {
			return 0;
		}
		return BigInteger.class.equals(binding) ? 0 : options.decimalScale();
	}

	/**
	 * Determine the kind of value for a binding.
	 *
	 * @param binding the property binding, may be <code>null</code>
	 * @return the value kind
	 */
	ValueKind determineValueKind(Class<?> binding) {
		if (binding == null || String.class.equals(binding) || URI.class.equals(binding)) {
			return ValueKind.STRING;
		}
		if (Boolean.class.equals(binding)) {
			return ValueKind.BOOLEAN;
		}
		if (Byte.class.equals(binding) || Short.class.equals(binding)
				|| Integer.class.equals(binding)) {
			return ValueKind.INT32;
		}
		if (Long.class.equals(binding)) {
			return ValueKind.INT64;
		}
		if (Float.class.equals(binding)) {
			return ValueKind.FLOAT;
		}
		if (Double.class.equals(binding)) {
			return ValueKind.DOUBLE;
		}
		if (BigInteger.class.equals(binding) || BigDecimal.class.equals(binding)) {
			return switch (options.bigNumberEncoding()) {
			case DOUBLE -> ValueKind.DOUBLE;
			case DECIMAL -> ValueKind.DECIMAL;
			case STRING -> ValueKind.DECIMAL_AS_STRING;
			};
		}
		if (LocalDate.class.equals(binding) || java.sql.Date.class.equals(binding)) {
			return ValueKind.DATE;
		}
		if (LocalTime.class.equals(binding) || java.sql.Time.class.equals(binding)) {
			return ValueKind.TIME_MILLIS;
		}
		if (Instant.class.equals(binding) || LocalDateTime.class.equals(binding)
				|| Date.class.isAssignableFrom(binding)
				|| Calendar.class.isAssignableFrom(binding)) {
			return ValueKind.TIMESTAMP_MILLIS;
		}
		if (byte[].class.equals(binding)) {
			return ValueKind.BINARY;
		}
		return ValueKind.STRING;
	}

	/**
	 * Create the Parquet field for a column with values.
	 *
	 * @param name the field name
	 * @param kind the value kind
	 * @param scale the decimal scale
	 * @param repeated if the field is a list
	 * @return the field
	 */
	Type createField(String name, ValueKind kind, int scale, boolean repeated) {
		LogicalTypeAnnotation annotation = annotation(kind, scale);
		int length = kind == ValueKind.DECIMAL ? DECIMAL_BYTES : 0;
		if (repeated) {
			var element = Types.optionalList().optionalElement(primitiveName(kind));
			if (annotation != null) {
				element = element.as(annotation);
			}
			if (length > 0) {
				element = element.length(length);
			}
			return element.named(name);
		}
		var builder = Types.optional(primitiveName(kind));
		if (annotation != null) {
			builder = builder.as(annotation);
		}
		if (length > 0) {
			builder = builder.length(length);
		}
		return builder.named(name);
	}

	private LogicalTypeAnnotation annotation(ValueKind kind, int scale) {
		return switch (kind) {
		case STRING, DECIMAL_AS_STRING, JSON, WKT -> LogicalTypeAnnotation.stringType();
		case DATE -> LogicalTypeAnnotation.dateType();
		case TIMESTAMP_MILLIS -> LogicalTypeAnnotation.timestampType(true, TimeUnit.MILLIS);
		case TIME_MILLIS -> LogicalTypeAnnotation.timeType(true, TimeUnit.MILLIS);
		case DECIMAL -> LogicalTypeAnnotation.decimalType(scale, DECIMAL_PRECISION);
		default -> null;
		};
	}

	private PrimitiveTypeName primitiveName(ValueKind kind) {
		return switch (kind) {
		case BOOLEAN -> PrimitiveTypeName.BOOLEAN;
		case INT32, DATE, TIME_MILLIS -> PrimitiveTypeName.INT32;
		case INT64, TIMESTAMP_MILLIS -> PrimitiveTypeName.INT64;
		case FLOAT -> PrimitiveTypeName.FLOAT;
		case DOUBLE -> PrimitiveTypeName.DOUBLE;
		case DECIMAL -> PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY;
		case STRING, BINARY, DECIMAL_AS_STRING, WKB, WKT, JSON -> PrimitiveTypeName.BINARY;
		};
	}

}
