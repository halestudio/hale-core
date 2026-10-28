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

import java.util.List;

import javax.xml.namespace.QName;

/**
 * Describes how a hale property is mapped to a Parquet column.
 */
public final class ColumnMapping {

	/**
	 * Name of the struct field holding the value of a property that has both a
	 * value and child properties.
	 */
	public static final String VALUE_FIELD = "value";

	private final String columnName;
	private final List<QName> propertyPath;
	private final boolean repeated;
	private final boolean optional;
	private final ValueKind valueKind;
	private final int decimalScale;
	private final List<ColumnMapping> children;

	/**
	 * Create a mapping for a column holding values.
	 *
	 * @param columnName the column name
	 * @param propertyPath the property path from the parent group/instance
	 * @param repeated if the column is a list
	 * @param optional if the column is optional
	 * @param valueKind the kind of value
	 * @param decimalScale the decimal scale (only relevant for DECIMAL)
	 */
	public ColumnMapping(String columnName, List<QName> propertyPath, boolean repeated,
			boolean optional, ValueKind valueKind, int decimalScale) {
		this.columnName = columnName;
		this.propertyPath = List.copyOf(propertyPath);
		this.repeated = repeated;
		this.optional = optional;
		this.valueKind = valueKind;
		this.decimalScale = decimalScale;
		this.children = List.of();
	}

	/**
	 * Create a mapping for a struct column.
	 *
	 * @param columnName the column name
	 * @param propertyPath the property path from the parent group/instance
	 * @param repeated if the column is a list
	 * @param optional if the column is optional
	 * @param children the mappings of the struct fields
	 */
	public ColumnMapping(String columnName, List<QName> propertyPath, boolean repeated,
			boolean optional, List<ColumnMapping> children) {
		this.columnName = columnName;
		this.propertyPath = List.copyOf(propertyPath);
		this.repeated = repeated;
		this.optional = optional;
		this.valueKind = null;
		this.decimalScale = 0;
		this.children = List.copyOf(children);
	}

	/**
	 * @return the column name
	 */
	public String getColumnName() {
		return columnName;
	}

	/**
	 * @return the property path from the parent group/instance, including flattened
	 *         group/choice names
	 */
	public List<QName> getPropertyPath() {
		return propertyPath;
	}

	/**
	 * @return if the column is a list
	 */
	public boolean isRepeated() {
		return repeated;
	}

	/**
	 * @return if the column is optional
	 */
	public boolean isOptional() {
		return optional;
	}

	/**
	 * @return the value kind, <code>null</code> for a struct
	 */
	public ValueKind getValueKind() {
		return valueKind;
	}

	/**
	 * @return the decimal scale, only meaningful for {@link ValueKind#DECIMAL},
	 *         otherwise 0
	 */
	public int getDecimalScale() {
		return decimalScale;
	}

	/**
	 * @return the mappings of the struct fields, empty if not a struct
	 */
	public List<ColumnMapping> getChildren() {
		return children;
	}

	/**
	 * @return if the column is a struct
	 */
	public boolean isStruct() {
		return valueKind == null;
	}

}
