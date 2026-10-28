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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.parquet.io.api.Binary;
import org.apache.parquet.io.api.RecordConsumer;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs;
import eu.esdihumboldt.hale.io.geoparquet.internal.meta.GeoColumnStats;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ColumnMapping;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.TypeMapping;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ValueKind;

/**
 * Writes instances as records to a Parquet {@link RecordConsumer} according to
 * a {@link TypeMapping}. Converts the property values to the types of the
 * columns, conversion problems are reported to the log and result in null
 * values.
 */
public class ValueWriter {

	private final TypeMapping mapping;
	private final MessageType schema;
	private final Map<String, GeoParquetCrs> crs;
	private final GeometryConverter converter;
	private final SimpleLog log;

	private final LinkedHashMap<String, GeoColumnStats> stats = new LinkedHashMap<>();
	private final IsoWkbWriter wkbWriter = new IsoWkbWriter();

	private final ValueConverter valueConverter;

	private RecordConsumer consumer;

	/**
	 * Create a value writer.
	 *
	 * @param mapping the type mapping
	 * @param crs the CRS description per geometry column name
	 * @param converter the geometry converter
	 * @param log the log for reporting problems
	 */
	public ValueWriter(TypeMapping mapping, Map<String, GeoParquetCrs> crs,
			GeometryConverter converter, SimpleLog log) {
		this.mapping = mapping;
		this.schema = mapping.getSchema();
		this.crs = crs;
		this.converter = converter;
		this.log = log;
		this.valueConverter = new ValueConverter(log,
				new JsonValueSerializer(mapping.getPrimaryGeometryPath()));
		for (String column : mapping.getGeometryColumns()) {
			stats.put(column, new GeoColumnStats());
		}
	}

	/**
	 * @param consumer the record consumer to write to
	 */
	public void setRecordConsumer(RecordConsumer consumer) {
		this.consumer = consumer;
	}

	/**
	 * @return the statistics of the geometries written so far, per geometry column,
	 *         primary first
	 */
	public LinkedHashMap<String, GeoColumnStats> getStats() {
		return stats;
	}

	/**
	 * Write a record.
	 *
	 * @param instance the instance
	 */
	public void writeRecord(Instance instance) {
		consumer.startMessage();
		for (ColumnMapping column : mapping.getColumns()) {
			if (isPrimaryGeometry(column)) {
				writeGeometry(schema, column, instance, true);
			}
			else {
				writeColumn(schema, column, instance, List.of());
			}
		}
		consumer.endMessage();
	}

	private boolean isPrimaryGeometry(ColumnMapping column) {
		return mapping.getPrimaryGeometryPath() != null
				&& TypeMapping.PRIMARY_GEOMETRY_COLUMN.equals(column.getColumnName());
	}

	/**
	 * Write the field for a column.
	 *
	 * @param parentType the type of the enclosing struct (or message)
	 * @param column the column mapping
	 * @param parent the instance/group corresponding to the enclosing struct
	 * @param parentPath the absolute property path of the enclosing struct
	 */
	private void writeColumn(GroupType parentType, ColumnMapping column, Object parent,
			List<QName> parentPath) {
		if (column.getValueKind() == ValueKind.WKB) {
			writeGeometry(parentType, column, parent, false);
			return;
		}

		List<Object> values = lookup(parent, column.getPropertyPath());
		if (values.isEmpty()) {
			return;
		}
		List<QName> path = new ArrayList<>(parentPath);
		path.addAll(column.getPropertyPath());

		String name = column.getColumnName();
		int index = parentType.getFieldIndex(name);

		if (!column.isRepeated()) {
			Object value = first(column, values);
			if (column.isStruct()) {
				consumer.startField(name, index);
				writeStruct(parentType.getType(index).asGroupType(), column, value, path);
				consumer.endField(name, index);
			}
			else {
				Object converted = valueConverter.convert(column, value, path);
				if (converted != null) {
					consumer.startField(name, index);
					writePrimitive(converted);
					consumer.endField(name, index);
				}
			}
			return;
		}

		// 3-level list: <name> (LIST) { repeated group list { optional element } }
		Type element = parentType.getType(index).asGroupType().getType(0).asGroupType().getType(0);
		GroupType elementType = element.isPrimitive() ? null : element.asGroupType();
		consumer.startField(name, index);
		consumer.startGroup();
		consumer.startField("list", 0);
		for (Object value : values) {
			consumer.startGroup();
			if (column.isStruct()) {
				consumer.startField("element", 0);
				writeStruct(elementType, column, value, path);
				consumer.endField("element", 0);
			}
			else {
				Object converted = valueConverter.convert(column, value, path);
				if (converted != null) {
					consumer.startField("element", 0);
					writePrimitive(converted);
					consumer.endField("element", 0);
				}
			}
			consumer.endGroup();
		}
		consumer.endField("list", 0);
		consumer.endGroup();
		consumer.endField(name, index);
	}

	private Object first(ColumnMapping column, List<Object> values) {
		if (values.size() > 1 && valueConverter.firstTime(column, "multiple")) {
			log.warn("Column " + column.getColumnName()
					+ " is not a list, but the property has multiple values, only the first value is written");
		}
		return values.get(0);
	}

	private void writeStruct(GroupType structType, ColumnMapping column, Object value,
			List<QName> path) {
		consumer.startGroup();
		for (ColumnMapping child : column.getChildren()) {
			writeColumn(structType, child, value, path);
		}
		consumer.endGroup();
	}

	/**
	 * Find the values of a property path below an instance or group. The returned
	 * values are not unwrapped, the elements of an empty path is the parent itself.
	 */
	private List<Object> lookup(Object parent, List<QName> path) {
		List<Object> current = List.of(parent);
		for (QName name : path) {
			List<Object> next = new ArrayList<>();
			for (Object o : current) {
				if (o instanceof Group) {
					Object[] values = ((Group) o).getProperty(name);
					if (values != null) {
						for (Object v : values) {
							if (v != null) {
								next.add(v);
							}
						}
					}
				}
			}
			current = next;
			if (current.isEmpty()) {
				break;
			}
		}
		return current;
	}

	static Object unwrap(Object value) {
		if (value instanceof Instance) {
			return ((Instance) value).getValue();
		}
		return value;
	}

	private void writePrimitive(Object value) {
		if (value instanceof Binary) {
			consumer.addBinary((Binary) value);
		}
		else if (value instanceof Boolean) {
			consumer.addBoolean((Boolean) value);
		}
		else if (value instanceof Integer) {
			consumer.addInteger((Integer) value);
		}
		else if (value instanceof Long) {
			consumer.addLong((Long) value);
		}
		else if (value instanceof Float) {
			consumer.addFloat((Float) value);
		}
		else if (value instanceof Double) {
			consumer.addDouble((Double) value);
		}
		else {
			throw new IllegalStateException("Unsupported value " + value.getClass());
		}
	}

	/**
	 * Write a geometry column, for the primary geometry including the bbox covering
	 * column.
	 */
	private void writeGeometry(GroupType parentType, ColumnMapping column, Object parent,
			boolean primary) {
		List<Object> values = lookup(parent, column.getPropertyPath());
		Geometry geometry;
		byte[] wkb;
		try {
			geometry = toGeometry(column, values);
			if (geometry == null) {
				return;
			}
			// everything that can fail is done before anything is emitted
			wkb = wkbWriter.write(geometry);
			geometry.getEnvelopeInternal();
		} catch (RuntimeException e) {
			log.error("Could not write geometry of column " + column.getColumnName() + ": "
					+ e.getMessage(), e);
			return;
		}
		stats.get(column.getColumnName()).add(geometry);

		if (primary && mapping.hasBboxColumn()) {
			Envelope env = geometry.getEnvelopeInternal();
			int bboxIndex = parentType.getFieldIndex(TypeMapping.BBOX_COLUMN);
			consumer.startField(TypeMapping.BBOX_COLUMN, bboxIndex);
			consumer.startGroup();
			double[] coords = { env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY() };
			String[] names = { "xmin", "ymin", "xmax", "ymax" };
			for (int i = 0; i < names.length; i++) {
				consumer.startField(names[i], i);
				consumer.addDouble(coords[i]);
				consumer.endField(names[i], i);
			}
			consumer.endGroup();
			consumer.endField(TypeMapping.BBOX_COLUMN, bboxIndex);
		}

		String name = column.getColumnName();
		int index = parentType.getFieldIndex(name);
		consumer.startField(name, index);
		consumer.addBinary(Binary.fromConstantByteArray(wkb));
		consumer.endField(name, index);
	}

	/**
	 * Convert the values of a geometry column to a single geometry.
	 *
	 * @return the geometry, <code>null</code> if there is no non-empty geometry
	 */
	private Geometry toGeometry(ColumnMapping column, List<Object> values) {
		GeoParquetCrs target = crs.get(column.getColumnName());
		if (target == null) {
			target = GeoParquetCrs.crs84();
		}

		List<Geometry> geometries = new ArrayList<>();
		for (Object raw : values) {
			Object value = unwrap(raw);
			if (value == null) {
				continue;
			}
			try {
				Geometry g = converter.convert(value, target, log);
				if (g != null && !g.isEmpty()) {
					geometries.add(g);
				}
			} catch (RuntimeException e) {
				log.error("Could not convert geometry of column " + column.getColumnName() + ": "
						+ e.getMessage(), e);
			}
		}

		if (geometries.isEmpty()) {
			return null;
		}
		if (geometries.size() == 1) {
			return geometries.get(0);
		}

		Geometry combined = geometries.get(0).getFactory().buildGeometry(geometries);
		if (!combined.getGeometryType().equals(geometries.get(0).getGeometryType())
				&& valueConverter.firstTime(column, "combined")) {
			log.warn("Multiple geometries in column " + column.getColumnName()
					+ " are combined to a " + combined.getGeometryType()
					+ " (further combinations in this column are not reported)");
		}
		return combined;
	}

}
