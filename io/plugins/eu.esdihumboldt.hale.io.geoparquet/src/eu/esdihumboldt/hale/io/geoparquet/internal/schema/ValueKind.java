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

/**
 * The kind of value stored in a Parquet column.
 */
public enum ValueKind {
	/** UTF8 string. */
	STRING,
	/** Boolean. */
	BOOLEAN,
	/** 32 bit integer. */
	INT32,
	/** 64 bit integer. */
	INT64,
	/** 32 bit floating point. */
	FLOAT,
	/** 64 bit floating point. */
	DOUBLE,
	/** Date (days since epoch). */
	DATE,
	/** Timestamp in milliseconds (UTC adjusted). */
	TIMESTAMP_MILLIS,
	/** Time of day in milliseconds. */
	TIME_MILLIS,
	/** Binary data. */
	BINARY,
	/** Big number as UTF8 string. */
	DECIMAL_AS_STRING,
	/** Decimal as FIXED_LEN_BYTE_ARRAY(16) with precision 38. */
	DECIMAL,
	/** Geometry as WKB. */
	WKB,
	/** Geometry as WKT string. */
	WKT,
	/** Value serialized as JSON string. */
	JSON
}
