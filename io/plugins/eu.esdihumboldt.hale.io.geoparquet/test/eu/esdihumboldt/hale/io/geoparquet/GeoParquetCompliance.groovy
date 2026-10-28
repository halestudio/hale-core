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
package eu.esdihumboldt.hale.io.geoparquet

import static org.junit.Assert.fail

import groovy.json.JsonSlurper

import org.apache.parquet.column.statistics.Statistics
import org.apache.parquet.hadoop.metadata.BlockMetaData
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData
import org.apache.parquet.hadoop.metadata.ParquetMetadata
import org.apache.parquet.schema.ColumnOrder
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.LogicalTypeAnnotation.GeometryLogicalTypeAnnotation
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.PrimitiveType
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.Type
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.CoordinateSequenceFilter
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBReader

/**
 * In-Java compliance checks for GeoParquet files. Every check is a separate
 * method named after the rule it verifies, and fails with an
 * {@link AssertionError} whose message names that rule. Files without
 * <code>geo</code> metadata are only checked for the row group limit.
 */
class GeoParquetCompliance {

	private static final double TOLERANCE = 1e-9

	private static final List<String> BBOX_FIELDS = [
		'xmin',
		'ymin',
		'xmax',
		'ymax'
	]

	/**
	 * Check a Parquet file for GeoParquet compliance.
	 *
	 * @param file the file
	 * @param rowGroupRowLimit the maximum number of rows allowed in a row group
	 * @param requireGeo if <code>geo</code> metadata must be present; if
	 *   <code>false</code> files without it are only checked for the row group limit
	 * @param bbox2d if the file was written with a 2D column bbox also for
	 *   geometries with Z values (writer parameter
	 *   {@link GeoParquetInstanceWriter#PARAM_BBOX_2D}, not compliant with the
	 *   GeoParquet specification for 3D data); then a 4 value bbox is expected
	 *   for 3D data
	 * @throws AssertionError if a rule is violated, the message names the rule
	 */
	static void check(File file, int rowGroupRowLimit = 150000, boolean requireGeo = false,
			boolean bbox2d = false) {
		ParquetMetadata footer = GeoParquetTestUtil.readFooter(file)
		checkRowGroupLimit(file, footer, rowGroupRowLimit)

		String geoJson = footer.fileMetaData.keyValueMetaData['geo']
		if (geoJson == null) {
			if (requireGeo) {
				violation(file, 'geo metadata', "file with geometry has no 'geo' key-value metadata")
			}
			return
		}
		Map geo = (Map) new JsonSlurper().parseText(geoJson)
		MessageType schema = footer.fileMetaData.schema
		GeoParquetVersion version = checkGeoSchema(file, geoJson, geo)

		Map<String, Map> columns = (Map<String, Map>) geo.columns
		if (!columns) {
			violation(file, 'geo columns', "geo metadata declares no geometry columns")
		}
		if (footer.blocks.isEmpty()) {
			violation(file, 'row groups', "file has no row groups")
		}
		checkGeometryColumns(file, schema, columns, version)

		def coveringColumn = columns.find { it.value.covering != null }
		if (coveringColumn != null) {
			checkBboxCovering(file, schema, footer, (Map) coveringColumn.value.covering, (String) coveringColumn.key)
		}

		checkDataConsistency(file, columns, bbox2d)

		if (version == GeoParquetVersion.V2_0) {
			checkGeospatialStatistics(file, footer, columns.keySet())
		}
	}

	/**
	 * Determine if a file has a geometry column and thus must have <code>geo</code>
	 * metadata: a column with the GEOMETRY logical type or the primary geometry
	 * column named <code>geometry</code> together with the <code>bbox</code>
	 * covering column. A plain binary <code>geometry</code> column without
	 * <code>bbox</code> column is written for types without any geometry values
	 * (plain Parquet).
	 */
	static boolean hasGeometryColumn(File file) {
		MessageType schema = GeoParquetTestUtil.readFooter(file).fileMetaData.schema
		(schema.containsField('geometry') && schema.containsField('bbox')) || schema.fields.any {
			it.logicalTypeAnnotation instanceof GeometryLogicalTypeAnnotation
		}
	}

	private static void violation(File file, String rule, String message) {
		fail("GeoParquet compliance violation [$rule] in ${file.name}: $message")
	}

	/**
	 * Rule: every row group has at most the configured number of rows.
	 */
	static void checkRowGroupLimit(File file, ParquetMetadata footer, int limit) {
		footer.blocks.eachWithIndex { BlockMetaData block, int i ->
			if (block.rowCount > limit) {
				violation(file, 'row group limit', "row group $i has ${block.rowCount} rows, limit is $limit")
			}
		}
	}

	/**
	 * Rule: the <code>geo</code> metadata validates against the JSON schema of its version.
	 *
	 * @return the version declared in the metadata
	 */
	static GeoParquetVersion checkGeoSchema(File file, String geoJson, Map geo) {
		GeoParquetVersion version = GeoParquetVersion.values().find { it.versionString == geo.version }
		if (version == null) {
			violation(file, 'geo schema', "unknown version '${geo.version}'")
		}
		List<String> errors = GeoParquetTestUtil.validateGeo(version, geoJson)
		if (!errors.isEmpty()) {
			violation(file, 'geo schema', "geo metadata does not validate against the schema of $version.versionString: $errors")
		}
		version
	}

	/**
	 * Rule: every geometry column exists at the schema root, is not repeated and
	 * is BINARY; for GeoParquet 2.0 it carries the GEOMETRY logical type with a
	 * CRS consistent with the <code>geo</code> metadata.
	 */
	static void checkGeometryColumns(File file, MessageType schema, Map<String, Map> columns,
			GeoParquetVersion version) {
		columns.each { String name, Map column ->
			if (!schema.containsField(name)) {
				violation(file, 'geometry column', "column '$name' does not exist at the schema root")
			}
			Type type = schema.getType(name)
			if (!type.isPrimitive() || type.asPrimitiveType().primitiveTypeName != PrimitiveTypeName.BINARY) {
				violation(file, 'geometry column', "column '$name' is not a BINARY column")
			}
			if (type.repetition == Type.Repetition.REPEATED) {
				violation(file, 'geometry column', "column '$name' is repeated")
			}
			if (version == GeoParquetVersion.V2_0) {
				LogicalTypeAnnotation annotation = type.logicalTypeAnnotation
				if (!(annotation instanceof GeometryLogicalTypeAnnotation)) {
					violation(file, 'geometry logical type', "column '$name' has no GEOMETRY logical type annotation but $annotation")
				}
				checkCrsConsistency(file, name, ((GeometryLogicalTypeAnnotation) annotation).crs, column)
			}
		}
	}

	/**
	 * Rule: the CRS of the Parquet logical type is consistent with the
	 * <code>crs</code> of the <code>geo</code> metadata.
	 */
	static void checkCrsConsistency(File file, String name, String logicalCrs, Map column) {
		boolean hasKey = column.containsKey('crs')
		def crs = column.crs
		if (logicalCrs == null) {
			// CRS84: not set in the logical type, absent in geo
			if (hasKey) {
				violation(file, 'crs consistency', "column '$name' has no CRS in the logical type (CRS84), but geo metadata has crs: $crs")
			}
		}
		else if (logicalCrs == 'srid:0') {
			// unknown CRS: explicit null in geo
			if (!hasKey || crs != null) {
				violation(file, 'crs consistency', "column '$name' has unknown CRS (srid:0) in the logical type, but geo metadata crs is: ${hasKey ? crs : '<absent>'}")
			}
		}
		else if (logicalCrs.startsWith('EPSG:')) {
			def code = logicalCrs.substring('EPSG:'.length()) as Integer
			if (!(crs instanceof Map) || crs.id?.code != code) {
				violation(file, 'crs consistency', "column '$name' has CRS $logicalCrs in the logical type, but geo metadata crs is: ${hasKey ? crs : '<absent>'}")
			}
		}
		else {
			violation(file, 'crs consistency', "column '$name' has unexpected logical type CRS '$logicalCrs'")
		}
	}

	/**
	 * Rule: a declared bbox covering refers to a struct <code>bbox</code> at the
	 * root with DOUBLE fields that have the type defined column order and
	 * min/max statistics in every row group that has at least one geometry
	 * (row groups where the geometry column is entirely null are skipped, as
	 * there is no bbox to describe).
	 *
	 * @param geometryColumn the name of the geometry column the covering belongs to,
	 *   <code>null</code> to check all row groups
	 */
	static void checkBboxCovering(File file, MessageType schema, ParquetMetadata footer, Map covering,
			String geometryColumn = null) {
		Map bbox = (Map) covering.bbox
		if (bbox == null) {
			return
		}
		if (!schema.containsField('bbox') || schema.getType('bbox').isPrimitive()) {
			violation(file, 'bbox covering', "no struct column 'bbox' at the schema root")
		}
		Set<List<String>> paths = bbox.values().collect { it as List<String> } as Set
		if (!(paths*.get(0) as Set == ['bbox'] as Set) || paths.size() != 4) {
			violation(file, 'bbox covering', "covering paths do not refer to the four fields of the root struct 'bbox': $paths")
		}
		paths.each { List<String> path ->
			if (path.size() != 2 || !schema.containsPath(path as String[])) {
				violation(file, 'bbox covering', "covering path $path does not exist in the schema")
			}
			Type leaf = schema.getType(path as String[])
			if (!leaf.isPrimitive() || leaf.asPrimitiveType().primitiveTypeName != PrimitiveTypeName.DOUBLE) {
				violation(file, 'bbox covering', "covering path $path is not a DOUBLE")
			}
			ColumnOrder order = leaf.asPrimitiveType().columnOrder()
			if (order.columnOrderName != ColumnOrder.ColumnOrderName.TYPE_DEFINED_ORDER) {
				violation(file, 'bbox column order', "field $path has column order ${order.columnOrderName}, expected TYPE_DEFINED_ORDER (readers like pyarrow ignore the statistics otherwise)")
			}
			footer.blocks.eachWithIndex { BlockMetaData block, int i ->
				if (geometryColumn != null && isAllNull(block, geometryColumn)) {
					return
				}
				ColumnChunkMetaData chunk = block.columns.find { it.path.toList() == path }
				Statistics stats = chunk?.statistics
				if (stats == null || !stats.hasNonNullValue()) {
					violation(file, 'bbox statistics', "field $path has no min/max statistics in row group $i")
				}
			}
		}
	}

	/**
	 * Determine if a column has only null values in a row group.
	 */
	private static boolean isAllNull(BlockMetaData block, String column) {
		ColumnChunkMetaData chunk = block.columns.find { it.path.toList() == [column] }
		chunk != null && chunk.statistics != null && chunk.statistics.numNulls >= block.rowCount
	}

	/**
	 * Rules: the column-level <code>bbox</code> equals the extent of the data and
	 * <code>geometry_types</code> equals the set of types in the data.
	 *
	 * @param bbox2d if a 2D bbox (4 values) is expected also for 3D data
	 */
	static void checkDataConsistency(File file, Map<String, Map> columns, boolean bbox2d = false) {
		def rows = GeoParquetTestUtil.readRows(file)
		WKBReader reader = new WKBReader()
		columns.each { String name, Map column ->
			Envelope extent = new Envelope()
			double[] zRange = [
				Double.POSITIVE_INFINITY,
				Double.NEGATIVE_INFINITY
			] as double[]
			SortedSet<String> types = new TreeSet<>()
			rows.each { row ->
				if (row.getFieldRepetitionCount(name) > 0) {
					Geometry g = reader.read(row.getBinary(name, 0).bytes)
					if (!g.empty) {
						extent.expandToInclude(g.envelopeInternal)
						types << typeName(g)
						g.coordinates.each { c ->
							if (!Double.isNaN(c.z)) {
								zRange[0] = Math.min(zRange[0], c.z)
								zRange[1] = Math.max(zRange[1], c.z)
							}
						}
					}
				}
			}

			List<Number> bbox = (List<Number>) column.bbox
			if (extent.isNull()) {
				if (bbox != null) {
					violation(file, 'column bbox', "column '$name' has bbox $bbox but no geometries")
				}
			}
			else {
				if (bbox == null || !(bbox.size() in [4, 6])) {
					violation(file, 'column bbox', "column '$name' has no valid bbox, expected ${bboxString(extent)}, got $bbox")
				}
				// 3D bboxes are [xmin, ymin, zmin, xmax, ymax, zmax]
				int off = bbox.size() == 6 ? 1 : 0
				List<Double> xy = [
					bbox[0],
					bbox[1],
					bbox[2 + off],
					bbox[3 + off]
				]*.doubleValue()
				List<Double> expected = [
					extent.minX,
					extent.minY,
					extent.maxX,
					extent.maxY
				]
				if ((0..3).any { Math.abs(xy[it] - expected[it]) > TOLERANCE }) {
					violation(file, 'column bbox', "column '$name' has bbox $bbox, but the data has extent $expected")
				}

				// the bbox has a range for each dimension: 3D if any geometry type is 3D
				// (unless a 2D bbox was explicitly requested)
				boolean threeD = !bbox2d && column.geometry_types.any { (it as String).endsWith(' Z') }
				int expectedSize = threeD ? 6 : 4
				if (bbox.size() != expectedSize) {
					violation(file, 'column bbox dimensions', "column '$name' has geometry types ${column.geometry_types}, so the bbox must have $expectedSize values, but has $bbox")
				}
				if (threeD && (Math.abs(bbox[2].doubleValue() - zRange[0]) > TOLERANCE
						|| Math.abs(bbox[5].doubleValue() - zRange[1]) > TOLERANCE)) {
					violation(file, 'column bbox', "column '$name' has bbox $bbox, but the data has z range ${zRange as List}")
				}
			}

			if (types != (column.geometry_types as Set)) {
				violation(file, 'geometry_types', "column '$name' declares ${column.geometry_types}, but the data has $types")
			}
		}
	}

	private static String bboxString(Envelope e) {
		"[${e.minX}, ${e.minY}, ${e.maxX}, ${e.maxY}]"
	}

	private static String typeName(Geometry g) {
		boolean z = false
		g.apply(new CoordinateSequenceFilter() {

					void filter(CoordinateSequence seq, int i) {
						if (!Double.isNaN(seq.getZ(i))) {
							z = true
						}
					}

					boolean isDone() {
						false
					}

					boolean isGeometryChanged() {
						false
					}
				})
		g.geometryType + (z ? ' Z' : '')
	}

	/**
	 * Rule (GeoParquet 2.0): geometry columns carry native geospatial statistics
	 * in every row group with geometries.
	 */
	static void checkGeospatialStatistics(File file, ParquetMetadata footer, Collection<String> geometryColumns) {
		footer.blocks.eachWithIndex { BlockMetaData block, int i ->
			if (block.rowCount == 0) {
				return
			}
			geometryColumns.each { String name ->
				ColumnChunkMetaData chunk = block.columns.find { it.path.toList() == [name] }
				if (chunk == null) {
					violation(file, 'geospatial statistics', "no column chunk for '$name' in row group $i")
				}
				// a chunk without any non-null value has nothing to describe
				if (chunk.statistics == null) {
					violation(file, 'geospatial statistics', "column '$name' has no statistics in row group $i")
				}
				if (chunk.statistics.numNulls >= block.rowCount) {
					return
				}
				def geoStats = chunk.geospatialStatistics
				if (geoStats == null || !geoStats.valid) {
					violation(file, 'geospatial statistics', "column '$name' has no valid geospatial statistics in row group $i")
				}
			}
		}
	}
}
