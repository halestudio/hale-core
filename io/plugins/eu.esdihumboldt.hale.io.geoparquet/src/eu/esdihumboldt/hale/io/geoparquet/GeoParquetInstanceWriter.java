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
package eu.esdihumboldt.hale.io.geoparquet;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.locationtech.jts.geom.Geometry;

import eu.esdihumboldt.hale.common.core.io.IOProviderConfigurationException;
import eu.esdihumboldt.hale.common.core.io.ProgressIndicator;
import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.report.IOReporter;
import eu.esdihumboldt.hale.common.core.io.report.impl.IOMessageImpl;
import eu.esdihumboldt.hale.common.core.io.supplier.MultiLocationOutputSupplier;
import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition;
import eu.esdihumboldt.hale.common.instance.geometry.impl.WKTDefinition;
import eu.esdihumboldt.hale.common.instance.io.impl.AbstractGeoInstanceWriter;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;
import eu.esdihumboldt.hale.common.schema.model.PropertyDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.GeometryMetadata;
import eu.esdihumboldt.hale.io.geoparquet.internal.crs.GeoParquetCrs;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ColumnMapping;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.MappingOptions;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.ParquetSchemaMapper;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.PropertyPaths;
import eu.esdihumboldt.hale.io.geoparquet.internal.schema.TypeMapping;
import eu.esdihumboldt.hale.io.geoparquet.internal.sort.TypeSortIndex;
import eu.esdihumboldt.hale.io.geoparquet.internal.write.GeometryConverter;
import eu.esdihumboldt.hale.io.geoparquet.internal.write.GeometryReprojector;
import eu.esdihumboldt.hale.io.geoparquet.internal.write.TypeFileWriter;
import eu.esdihumboldt.hale.io.geoparquet.internal.write.TypeOutputs;
import eu.esdihumboldt.util.Pair;

/**
 * Writes instances to GeoParquet files, one file per type.
 * <p>
 * The file for a type is named
 * <code>&lt;base&gt;_&lt;type name&gt;.parquet</code> and located in the
 * directory of the target, where the base is the name of the target file
 * without extension. If only one file is written, it is renamed to the target
 * file name.
 * <p>
 * Geometries in top-level geometry properties are written as WKB in the CRS of
 * the respective column and as lon/lat for geographic CRS. Geometries in nested
 * properties are written as WKT and geometries embedded in JSON columns are
 * written as GeoJSON-like JSON, in both cases in their source CRS (no
 * conversion).
 *
 * @author Simon Templer
 */
public class GeoParquetInstanceWriter extends AbstractGeoInstanceWriter
		implements GeometryConverter {

	/**
	 * Maximum number of instances of a type that are buffered while waiting for a
	 * geometry to determine the CRS (streaming mode).
	 */
	private static final int MAX_BUFFERED_INSTANCES = 1000;

	/**
	 * The identifier of the writer as registered to the I/O provider extension.
	 */
	public static final String ID = "eu.esdihumboldt.hale.io.geoparquet.instance.writer";

	/** Parameter for the GeoParquet version. */
	public static final String PARAM_VERSION = "geoparquet.version";

	/** Parameter for the compression codec (zstd, snappy, gzip, none). */
	public static final String PARAM_COMPRESSION = "compression";

	/** Parameter for sorting the instances spatially. Defaults to true. */
	public static final String PARAM_SORT = "sort";

	/** Parameter for the maximum number of rows per row group. */
	public static final String PARAM_ROW_GROUP_ROW_LIMIT = "rowGroupRowLimit";

	/**
	 * Parameter for the maximum nesting depth, properties nested deeper are written
	 * as JSON text.
	 */
	public static final String PARAM_NESTING_DEPTH = "nestingDepth";

	/** Parameter for the encoding of BigInteger/BigDecimal values. */
	public static final String PARAM_BIG_NUMBER_ENCODING = "bigNumberEncoding";

	/** Parameter for the scale of DECIMAL encoded BigDecimal values. */
	public static final String PARAM_DECIMAL_SCALE = "decimalScale";

	/**
	 * The parameter name for the flag specifying if the target file should be
	 * overwritten if it exists. Defaults to <code>false</code>.
	 */
	public static final String PARAM_OVERWRITE_TARGET_FILE = "overwriteTargetFile";

	private static final int DEFAULT_ROW_GROUP_ROW_LIMIT = 150000;

	private static final int DEFAULT_NESTING_DEPTH = 5;

	private static final int DEFAULT_DECIMAL_SCALE = 9;

	/** Maximum decimal scale (the precision of DECIMAL columns is 38). */
	private static final int MAX_DECIMAL_SCALE = 38;

	private final GeometryReprojector reprojector = new GeometryReprojector();

	/** The target CRS parameter of the current execution. */
	private CRSDefinition targetCrsParam;

	/**
	 * The column CRS for the target CRS parameter of the current execution,
	 * <code>null</code> if no target CRS is set.
	 */
	private GeoParquetCrs targetColumnCrs;

	/** The reporter of the current execution. */
	private IOReporter reporter;

	/**
	 * Set the GeoParquet version.
	 *
	 * @param version the version
	 */
	public void setVersion(GeoParquetVersion version) {
		setParameter(PARAM_VERSION, Value.of(version.getVersionString()));
	}

	/**
	 * Set the compression codec.
	 *
	 * @param compression one of <code>zstd</code>, <code>snappy</code>,
	 *            <code>gzip</code> or <code>none</code>
	 */
	public void setCompression(String compression) {
		setParameter(PARAM_COMPRESSION, Value.of(compression));
	}

	/**
	 * Set if the instances should be sorted spatially.
	 *
	 * @param sort true to sort the instances
	 */
	public void setSort(boolean sort) {
		setParameter(PARAM_SORT, Value.of(sort));
	}

	/**
	 * Set the maximum number of rows per row group.
	 *
	 * @param limit the row limit
	 */
	public void setRowGroupRowLimit(int limit) {
		setParameter(PARAM_ROW_GROUP_ROW_LIMIT, Value.of(limit));
	}

	/**
	 * Set the maximum nesting depth of properties written as structs.
	 *
	 * @param depth the nesting depth, properties nested deeper are written as JSON
	 *            text
	 */
	public void setNestingDepth(int depth) {
		setParameter(PARAM_NESTING_DEPTH, Value.of(depth));
	}

	/**
	 * Set the encoding of BigInteger/BigDecimal values.
	 *
	 * @param encoding the encoding
	 */
	public void setBigNumberEncoding(BigNumberEncoding encoding) {
		setParameter(PARAM_BIG_NUMBER_ENCODING, Value.of(encoding.name().toLowerCase(Locale.ROOT)));
	}

	/**
	 * Set the scale for BigDecimal values encoded as DECIMAL.
	 *
	 * @param scale the scale
	 */
	public void setDecimalScale(int scale) {
		setParameter(PARAM_DECIMAL_SCALE, Value.of(scale));
	}

	/**
	 * Set if an existing target file should be overwritten.
	 *
	 * @param overwriteTargetFile true to overwrite the target file if it exists
	 */
	public void setOverwriteTargetFile(boolean overwriteTargetFile) {
		setParameter(PARAM_OVERWRITE_TARGET_FILE, Value.of(overwriteTargetFile));
	}

	@Override
	public boolean isPassthrough() {
		return !isSort();
	}

	private boolean isSort() {
		return getParameter(PARAM_SORT).as(Boolean.class, true);
	}

	@Override
	protected String getDefaultTypeName() {
		return "GeoParquet";
	}

	@Override
	public boolean isCancelable() {
		return true;
	}

	@Override
	public void validate() throws IOProviderConfigurationException {
		super.validate();
		validateParameters();
	}

	/**
	 * Validate the numeric parameters.
	 *
	 * @throws IOProviderConfigurationException if a parameter value is invalid
	 */
	private void validateParameters() throws IOProviderConfigurationException {
		int scale = getDecimalScale();
		if (scale < 0 || scale > MAX_DECIMAL_SCALE) {
			throw new IOProviderConfigurationException("Parameter " + PARAM_DECIMAL_SCALE
					+ " must be between 0 and " + MAX_DECIMAL_SCALE + ", but is " + scale);
		}
		int depth = getNestingDepth();
		if (depth < 1) {
			throw new IOProviderConfigurationException(
					"Parameter " + PARAM_NESTING_DEPTH + " must be at least 1, but is " + depth);
		}
		int rowLimit = getRowGroupRowLimit();
		if (rowLimit < 1) {
			throw new IOProviderConfigurationException("Parameter " + PARAM_ROW_GROUP_ROW_LIMIT
					+ " must be at least 1, but is " + rowLimit);
		}
	}

	private int getDecimalScale() {
		return getParameter(PARAM_DECIMAL_SCALE).as(Integer.class, DEFAULT_DECIMAL_SCALE);
	}

	private int getNestingDepth() {
		return getParameter(PARAM_NESTING_DEPTH).as(Integer.class, DEFAULT_NESTING_DEPTH);
	}

	private int getRowGroupRowLimit() {
		return getParameter(PARAM_ROW_GROUP_ROW_LIMIT).as(Integer.class,
				DEFAULT_ROW_GROUP_ROW_LIMIT);
	}

	@Override
	protected IOReport execute(ProgressIndicator progress, IOReporter reporter)
			throws IOProviderConfigurationException, IOException {
		// configuration errors are reported before anything is written
		validateParameters();
		progress.begin("Generating GeoParquet", ProgressIndicator.UNKNOWN);
		this.reporter = reporter;
		this.targetCrsParam = getTargetCRS();
		TypeOutputs outputs = null;
		try {
			// the target CRS is used for all geometry columns, also if it has no
			// EPSG code (unknown CRS, reported once)
			this.targetColumnCrs = targetCrsParam == null ? null
					: GeoParquetCrs.resolve(targetCrsParam, reporter);
			Path target = Paths.get(getTarget().getLocation());
			outputs = new TypeOutputs(target);
			boolean overwrite = getParameter(PARAM_OVERWRITE_TARGET_FILE).as(Boolean.class, false);
			if (Files.exists(target) && Files.size(target) > 0 && !overwrite) {
				throw new IOException("Target file already exists and overwriting is not enabled: "
						+ target.toAbsolutePath());
			}

			Settings settings = readSettings();
			if (isSort()) {
				writeSorted(getInstances(), settings, outputs, progress);
			}
			else {
				// streaming: instances are written as they come in
				writeInstances(getInstances(), settings, outputs, progress);
			}

			outputs.closeAll();
			if (progress.isCanceled()) {
				outputs.deleteAll();
				reporter.warn("Export was cancelled, no file has been written");
				reporter.setSuccess(false);
			}
			else {
				finish(outputs, overwrite);
				reporter.setSuccess(true);
			}
		} catch (Exception e) {
			if (outputs != null) {
				outputs.closeQuietly();
				outputs.deleteAll();
			}
			reporter.error(new IOMessageImpl(e.getLocalizedMessage(), e));
			reporter.setSuccess(false);
			reporter.setSummary("Saving instances to GeoParquet failed.");
		} finally {
			this.reporter = null;
			this.targetCrsParam = null;
			this.targetColumnCrs = null;
			progress.end();
		}
		return reporter;
	}

	private void writeInstances(InstanceCollection instances, Settings settings,
			TypeOutputs outputs, ProgressIndicator progress) throws IOException {
		ParquetSchemaMapper mapper = new ParquetSchemaMapper(settings.options());

		// types for which the file is not yet opened because the CRS of the primary
		// geometry is not yet known, in order of first occurrence
		Map<TypeDefinition, PendingType> pending = new LinkedHashMap<>();

		try (ResourceIterator<Instance> it = instances.iterator()) {
			while (it.hasNext() && !progress.isCanceled()) {
				Instance instance = it.next();
				TypeDefinition type = instance.getDefinition();
				TypeFileWriter writer = outputs.getWriter(type);
				if (writer != null) {
					writer.write(instance);
				}
				else {
					PendingType pendingType = pending.get(type);
					if (pendingType == null) {
						pendingType = new PendingType(new CrsResolver(type, mapper));
						pending.put(type, pendingType);
					}
					pendingType.resolver().observe(instance);
					pendingType.buffer().add(instance);
					if (pendingType.resolver().isPrimaryResolved()
							|| pendingType.buffer().size() >= MAX_BUFFERED_INSTANCES) {
						pending.remove(type);
						openAndFlush(type, pendingType, mapper, settings, outputs);
					}
				}
				progress.advance(1);
			}
		}

		if (!progress.isCanceled()) {
			// input ended before the CRS could be determined for all types
			for (Map.Entry<TypeDefinition, PendingType> entry : pending.entrySet()) {
				openAndFlush(entry.getKey(), entry.getValue(), mapper, settings, outputs);
			}
		}
	}

	/**
	 * Open the file of a type and write the instances that were buffered while
	 * waiting for a geometry to determine the CRS.
	 */
	private void openAndFlush(TypeDefinition type, PendingType pendingType,
			ParquetSchemaMapper mapper, Settings settings, TypeOutputs outputs) throws IOException {
		Map<String, GeoParquetCrs> crs = pendingType.resolver().finish();
		TypeContext context = new TypeContext(mapper.map(type, crs, reporter), crs);
		TypeFileWriter writer = openWriter(outputs.allocate(type), context, settings);
		outputs.setWriter(type, writer);
		for (Instance buffered : pendingType.buffer()) {
			writer.write(buffered);
		}
		pendingType.buffer().clear();
	}

	/**
	 * Instances of a type that are buffered until the CRS of its primary geometry
	 * is known.
	 */
	private record PendingType(CrsResolver resolver, List<Instance> buffer) {

		PendingType(CrsResolver resolver) {
			this(resolver, new ArrayList<>());
		}
	}

	/**
	 * Write the instances ordered spatially along a Hilbert curve. In a first pass
	 * references to the instances are collected per type together with the center
	 * of their primary geometry, in a second pass the files are written one type
	 * after the other.
	 */
	private void writeSorted(InstanceCollection instances, Settings settings, TypeOutputs outputs,
			ProgressIndicator progress) throws IOException {
		ParquetSchemaMapper mapper = new ParquetSchemaMapper(settings.options());

		// in order of first occurrence
		Map<TypeDefinition, SortedType> types = new LinkedHashMap<>();

		progress.setCurrentTask("Collecting instances for sorting");
		try (ResourceIterator<Instance> it = instances.iterator()) {
			while (it.hasNext() && !progress.isCanceled()) {
				Instance instance = it.next();
				TypeDefinition type = instance.getDefinition();
				SortedType sorted = types.get(type);
				if (sorted == null) {
					sorted = new SortedType(new CrsResolver(type, mapper), this);
					types.put(type, sorted);
				}
				InstanceReference ref = instances.getReference(instance);
				if (ref == null) {
					reporter.error("No reference available for an instance of type "
							+ type.getName().getLocalPart() + ", the instance is not written");
				}
				else {
					sorted.add(instance, ref);
				}
				progress.advance(1);
			}
		}

		progress.setCurrentTask("Writing sorted instances");
		for (Iterator<Map.Entry<TypeDefinition, SortedType>> typeIt = types.entrySet()
				.iterator(); typeIt.hasNext() && !progress.isCanceled();) {
			Map.Entry<TypeDefinition, SortedType> entry = typeIt.next();
			// CRS of columns for which no geometry was found
			Map<String, GeoParquetCrs> crs = entry.getValue().resolver().finish();
			TypeContext context = new TypeContext(mapper.map(entry.getKey(), crs, reporter), crs);
			TypeFileWriter writer = openWriter(outputs.allocate(entry.getKey()), context, settings);
			outputs.setWriter(entry.getKey(), writer);
			for (InstanceReference ref : entry.getValue().index().sorted()) {
				if (progress.isCanceled()) {
					break;
				}
				Instance instance = instances.getInstance(ref);
				if (instance == null) {
					reporter.error("Instance of type " + entry.getKey().getName().getLocalPart()
							+ " could not be resolved from its reference (" + ref
							+ "), the instance is not written");
				}
				else {
					writer.write(instance);
				}
				progress.advance(1);
			}
			writer.close();
			// release the references of the type
			typeIt.remove();
		}
	}

	private TypeFileWriter openWriter(Path file, TypeContext context, Settings settings)
			throws IOException {
		return new TypeFileWriter(file, context.mapping(), settings.options().version(),
				context.crs(), settings.codec(), settings.rowGroupRowLimit(), this, reporter);
	}

	/**
	 * The mapping of a type and the CRS of its geometry columns.
	 */
	private record TypeContext(TypeMapping mapping, Map<String, GeoParquetCrs> crs) {
	}

	/**
	 * A type being sorted, with the references to its instances.
	 */
	private static final class SortedType {

		private final CrsResolver resolver;
		private final TypeSortIndex index;

		SortedType(CrsResolver resolver, GeometryConverter converter) {
			this.resolver = resolver;
			// the CRS map is filled by the resolver as geometries are seen
			this.index = new TypeSortIndex(resolver.preliminary, resolver.resolved, converter);
		}

		void add(Instance instance, InstanceReference ref) {
			// determines the CRS when the first geometry is seen
			resolver.observe(instance);
			index.add(instance, ref);
		}

		CrsResolver resolver() {
			return resolver;
		}

		TypeSortIndex index() {
			return index;
		}
	}

	/**
	 * Move the temporary files to their final names and update the target. If a
	 * final file exists and overwriting is not enabled, nothing is moved and
	 * existing files stay untouched.
	 */
	private void finish(TypeOutputs outputs, boolean overwrite) throws IOException {
		List<Path> temps = outputs.getTempFiles();
		if (temps.isEmpty()) {
			reporter.warn("No instances written");
			return;
		}
		List<Path> files = outputs.getFinalFiles();

		if (!overwrite) {
			for (Path file : files) {
				// an empty target file may be replaced
				if (Files.exists(file) && Files.size(file) > 0) {
					throw new IOException("File already exists and overwriting is not enabled: "
							+ file.toAbsolutePath());
				}
			}
		}
		for (int i = 0; i < files.size(); i++) {
			moveTo(temps.get(i), files.get(i));
		}

		if (files.size() > 1) {
			List<URI> uris = files.stream().map(f -> f.toFile().toURI()).toList();
			// Reset the target so that a caller can find out which files were
			// created
			setTarget(new MultiLocationOutputSupplier(uris));
			reporter.info("Multiple GeoParquet files have been exported. The file names are: "
					+ String.join(", ",
							files.stream().map(f -> f.getFileName().toString()).toList()));
		}
	}

	private void moveTo(Path temp, Path file) throws IOException {
		try {
			// replaces an existing file atomically where supported
			Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
			return;
		} catch (IOException e) {
			// e.g. atomic move not supported or file in use, try again below
		}
		if (Files.exists(file) && !deleteTargetFile(file.toFile())) {
			throw new IOException("Cannot overwrite existing GeoParquet file (file may be in use): "
					+ file.toAbsolutePath());
		}
		Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Delete the existing target file before overwriting it.
	 * <p>
	 * Extracted as a separate method so that tests can simulate a deletion failure.
	 *
	 * @param file the existing target file to delete
	 * @return <code>true</code> if the file was deleted, <code>false</code>
	 *         otherwise
	 */
	protected boolean deleteTargetFile(File file) {
		return file.delete();
	}

	private Settings readSettings() {
		GeoParquetVersion version = GeoParquetVersion
				.fromParameter(getParameter(PARAM_VERSION).as(String.class));
		int nestingDepth = getNestingDepth();
		BigNumberEncoding bigNumbers = BigNumberEncoding
				.fromParameter(getParameter(PARAM_BIG_NUMBER_ENCODING).as(String.class));
		int scale = getDecimalScale();
		int rowLimit = getRowGroupRowLimit();
		return new Settings(new MappingOptions(version, nestingDepth, bigNumbers, scale),
				parseCodec(getParameter(PARAM_COMPRESSION).as(String.class)), rowLimit);
	}

	private static CompressionCodecName parseCodec(String value) {
		if (value == null || value.isBlank()) {
			return CompressionCodecName.ZSTD;
		}
		switch (value.trim().toLowerCase(Locale.ROOT)) {
		case "zstd":
			return CompressionCodecName.ZSTD;
		case "snappy":
			return CompressionCodecName.SNAPPY;
		case "gzip":
			return CompressionCodecName.GZIP;
		case "none":
		case "uncompressed":
			return CompressionCodecName.UNCOMPRESSED;
		default:
			throw new IllegalArgumentException("Unsupported compression: " + value);
		}
	}

	// --- CRS handling

	/**
	 * Determines the CRS of the geometry columns of a type. If the target CRS
	 * parameter is set, it is used for all columns (even if it has no EPSG code and
	 * is written as unknown CRS). Otherwise in order: geometry metadata of the
	 * property type, CRS of the first geometry in the column that is found in the
	 * instances (see {@link #observe(Instance)}).
	 */
	private class CrsResolver {

		private final TypeDefinition type;
		/** Mapping without CRS information. */
		private final TypeMapping preliminary;
		/** The CRS of the columns that are already determined. */
		private final Map<String, GeoParquetCrs> resolved = new HashMap<>();
		/**
		 * Columns that are not yet determined, with the candidates not based on
		 * instances.
		 */
		private final Map<String, PendingColumn> pending = new LinkedHashMap<>();

		CrsResolver(TypeDefinition type, ParquetSchemaMapper mapper) {
			this.type = type;
			this.preliminary = mapper.map(type, Map.of(), SimpleLog.NO_LOG);
			List<String> geometryColumns = preliminary.getGeometryColumns();
			for (ColumnMapping column : preliminary.getColumns()) {
				if (geometryColumns.contains(column.getColumnName())) {
					if (targetColumnCrs != null) {
						// the target CRS takes precedence
						resolved.put(column.getColumnName(), targetColumnCrs);
						continue;
					}
					List<CRSDefinition> candidates = staticCandidates(column.getPropertyPath());
					GeoParquetCrs known = firstKnown(candidates);
					if (known != null) {
						resolved.put(column.getColumnName(), known);
					}
					else {
						pending.put(column.getColumnName(),
								new PendingColumn(column.getPropertyPath(), candidates));
					}
				}
			}
		}

		/**
		 * Determine the CRS of pending columns for which the instance has a geometry,
		 * using the CRS of that geometry.
		 */
		void observe(Instance instance) {
			if (pending.isEmpty()) {
				return;
			}
			for (Map.Entry<String, PendingColumn> entry : new ArrayList<>(pending.entrySet())) {
				PendingColumn column = entry.getValue();
				Pair<Geometry, CRSDefinition> pair = extractGeometry(
						PropertyPaths.findValue(instance, column.path()), false, reporter);
				if (pair != null && pair.getFirst() != null) {
					List<CRSDefinition> candidates = new ArrayList<>(column.candidates());
					candidates.add(pair.getSecond());
					resolve(entry.getKey(), type, candidates);
				}
			}
		}

		/**
		 * @return if the CRS of the primary geometry column is determined (or there is
		 *         none)
		 */
		boolean isPrimaryResolved() {
			String primary = preliminary.getPrimaryGeometryColumn();
			return primary == null || resolved.containsKey(primary);
		}

		/**
		 * Determine the CRS for the columns for which no geometry was seen.
		 *
		 * @return the CRS per geometry column
		 */
		Map<String, GeoParquetCrs> finish() {
			for (Map.Entry<String, PendingColumn> entry : new ArrayList<>(pending.entrySet())) {
				resolve(entry.getKey(), type, entry.getValue().candidates());
			}
			return resolved;
		}

		private void resolve(String column, TypeDefinition type, List<CRSDefinition> candidates) {
			pending.remove(column);
			GeoParquetCrs known = firstKnown(candidates);
			if (known != null) {
				resolved.put(column, known);
				return;
			}
			reporter.warn("Could not determine the CRS for geometry column " + column + " of type "
					+ type.getName().getLocalPart());
			CRSDefinition first = candidates.stream().filter(c -> c != null).findFirst()
					.orElse(null);
			resolved.put(column, GeoParquetCrs.resolve(first, reporter));
		}

		private GeoParquetCrs firstKnown(List<CRSDefinition> candidates) {
			for (CRSDefinition candidate : candidates) {
				if (candidate == null) {
					continue;
				}
				GeoParquetCrs crs = GeoParquetCrs.resolve(candidate, SimpleLog.NO_LOG);
				if (!crs.isUnknown()) {
					return crs;
				}
			}
			return null;
		}

		private List<CRSDefinition> staticCandidates(List<QName> path) {
			List<CRSDefinition> candidates = new ArrayList<>();

			PropertyDefinition property = PropertyPaths.findProperty(type, path);
			if (property != null) {
				GeometryMetadata metadata = property.getPropertyType()
						.getConstraint(GeometryMetadata.class);
				if (metadata.getAuthName() != null && metadata.getSrs() != null) {
					candidates.add(
							new CodeDefinition(metadata.getAuthName() + ":" + metadata.getSrs()));
				}
				else if (metadata.getSrsText() != null) {
					candidates.add(new WKTDefinition(metadata.getSrsText(), null));
				}
			}
			return candidates;
		}
	}

	private record PendingColumn(List<QName> path, List<CRSDefinition> candidates) {
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * If the transformation to the column CRS fails (no transformation found or the
	 * transformation of the geometry failed), the error is reported and
	 * <code>null</code> is returned, so the geometry is not written - its
	 * coordinates are not in the column CRS. Deliberate exception: if an explicit
	 * target CRS is set and the geometry has no source CRS, the error is reported,
	 * but the geometry is written unchanged (as the abstract geo instance writer
	 * does), assuming it is in the target CRS.
	 */
	@Override
	public Geometry convert(Object value, GeoParquetCrs target, SimpleLog log) {
		// all problems are reported to the given log only (nothing is reported for
		// the sort index)
		IOReporter report = log instanceof IOReporter ? (IOReporter) log : null;
		Pair<Geometry, CRSDefinition> pair = extractGeometry(value, false, report);
		if (pair == null || pair.getFirst() == null) {
			return null;
		}

		Geometry geometry;
		CoordinateReferenceSystem paramCrs = targetCrsParam == null ? null
				: targetCrsParam.getCRS();
		if (paramCrs != null && paramCrs.equals(target.getTargetCrs())) {
			// the column CRS is the target CRS parameter: convert like the abstract
			// writer (strict transformation lookup, error if there is no source CRS)
			CRSDefinition sourceCrs = pair.getSecond();
			if (sourceCrs == null || sourceCrs.getCRS() == null) {
				log.error("Could not convert geometry to target CRS: No source CRS provided"
						+ describeGeometryContext(pair.getFirst()), (Throwable) null);
				// deliberately written unchanged (like the abstract writer): without
				// source CRS the geometry is assumed to be in the target CRS
				return pair.getFirst();
			}
			geometry = reprojector.transform(pair.getFirst(), sourceCrs.getCRS(), paramCrs, false,
					log, describeGeometryContext(pair.getFirst()));
			if (geometry == null) {
				// conversion failed (already reported): the geometry is not in the
				// target CRS and must not be written
				return null;
			}
			if (target.isSwapAxes()) {
				// the given geometry must not be modified
				geometry = GeometryReprojector.swapXY(geometry, geometry != pair.getFirst());
			}
		}
		else {
			// column CRS determined from schema or data
			geometry = reprojector.reproject(pair.getFirst(), pair.getSecond(), target, log,
					describeGeometryContext(pair.getFirst()));
		}

		// unify the winding order in the written (lon/lat) axis order
		return unifyGeometry(geometry, report, target.isSwapAxes() ? null : target.getTargetCrs());
	}

	/**
	 * Settings read from the parameters.
	 */
	private record Settings(MappingOptions options, CompressionCodecName codec,
			int rowGroupRowLimit) {
	}

}
