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

import org.apache.parquet.schema.MessageType;

import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * The mapping of a hale type to a Parquet schema.
 */
public final class TypeMapping {

	/** Name of the bbox covering column. */
	public static final String BBOX_COLUMN = "bbox";

	/** Name of the primary geometry column. */
	public static final String PRIMARY_GEOMETRY_COLUMN = "geometry";

	private final TypeDefinition type;
	private final MessageType schema;
	private final List<ColumnMapping> columns;
	private final List<String> geometryColumns;
	private final List<QName> primaryGeometryPath;
	private final boolean bboxColumn;

	/**
	 * Create a type mapping.
	 *
	 * @param type the mapped type
	 * @param schema the Parquet schema
	 * @param columns the column mappings (excluding bbox)
	 * @param geometryColumns the top-level geometry columns, primary first
	 * @param primaryGeometryPath the property path of the primary geometry, may be
	 *            <code>null</code>
	 * @param bboxColumn if the schema has a bbox column
	 */
	public TypeMapping(TypeDefinition type, MessageType schema, List<ColumnMapping> columns,
			List<String> geometryColumns, List<QName> primaryGeometryPath, boolean bboxColumn) {
		this.type = type;
		this.schema = schema;
		this.columns = List.copyOf(columns);
		this.geometryColumns = List.copyOf(geometryColumns);
		this.primaryGeometryPath = primaryGeometryPath == null ? null
				: List.copyOf(primaryGeometryPath);
		this.bboxColumn = bboxColumn;
	}

	/**
	 * @return the mapped type
	 */
	public TypeDefinition getType() {
		return type;
	}

	/**
	 * @return the Parquet schema
	 */
	public MessageType getSchema() {
		return schema;
	}

	/**
	 * @return the column mappings in schema order, excluding bbox
	 */
	public List<ColumnMapping> getColumns() {
		return columns;
	}

	/**
	 * @return the names of the top-level WKB columns, primary first
	 */
	public List<String> getGeometryColumns() {
		return geometryColumns;
	}

	/**
	 * @return the name of the primary geometry column, <code>null</code> if the
	 *         type has no geometry
	 */
	public String getPrimaryGeometryColumn() {
		return primaryGeometryPath == null ? null : PRIMARY_GEOMETRY_COLUMN;
	}

	/**
	 * @return the property path to the primary geometry, <code>null</code> if the
	 *         type has no geometry
	 */
	public List<QName> getPrimaryGeometryPath() {
		return primaryGeometryPath;
	}

	/**
	 * @return if the schema contains the bbox column
	 */
	public boolean hasBboxColumn() {
		return bboxColumn;
	}

}
