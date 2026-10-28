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
			Path target = Paths.get(getTarget().getLocation());
			outputs = new TypeOutputs(target);
			boolean overwrite = getParameter(PARAM_OVERWRITE_TARGET_FILE).as(Boolean.class, false);
			if (Files.exists(target) && Files.size(target) > 0 && !overwrite) {
				throw new IOException("Target file already exists and overwriting is not enabled: "
						+ target.toAbsolutePath());
			}

			Settings settings = readSettings();
			// streaming: instances are written as they come in
			writeInstances(getInstances(), settings, outputs, progress);

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
			progress.end();
		}
		return reporter;
	}

	private void writeInstances(InstanceCollection instances, Settings settings,
			TypeOutputs outputs, ProgressIndicator progress) throws IOException {
		ParquetSchemaMapper mapper = new ParquetSchemaMapper(settings.options());

		try (ResourceIterator<Instance> it = instances.iterator()) {
			while (it.hasNext() && !progress.isCanceled()) {
				Instance instance = it.next();
				TypeDefinition type = instance.getDefinition();
				TypeFileWriter writer = outputs.getWriter(type);
				if (writer == null) {
					Path file = outputs.allocate(type);
					Map<String, GeoParquetCrs> crs = determineCrs(type, mapper, instance);
					TypeMapping mapping = mapper.map(type, crs, reporter);
					writer = new TypeFileWriter(file, mapping, settings.options().version(), crs,
							settings.codec(), settings.rowGroupRowLimit(), this, reporter);
					outputs.setWriter(type, writer);
				}
				writer.write(instance);
				progress.advance(1);
			}
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
	 * Determine the CRS for the geometry columns of a type. In order: target CRS
	 * parameter, geometry metadata of the property type, CRS of the first geometry
	 * of the given (first) instance of the type.
	 */
	private Map<String, GeoParquetCrs> determineCrs(TypeDefinition type, ParquetSchemaMapper mapper,
			Instance first) {
		Map<String, GeoParquetCrs> result = new HashMap<>();
		List<String> geometryColumns = mapper.geometryColumnNames(type);
		if (geometryColumns.isEmpty()) {
			return result;
		}
		TypeMapping preliminary = mapper.map(type, Map.of(), SimpleLog.NO_LOG);
		for (ColumnMapping column : preliminary.getColumns()) {
			if (geometryColumns.contains(column.getColumnName())) {
				result.put(column.getColumnName(), determineColumnCrs(type, column.getColumnName(),
						column.getPropertyPath(), first));
			}
		}
		return result;
	}

	private GeoParquetCrs determineColumnCrs(TypeDefinition type, String column, List<QName> path,
			Instance first) {
		List<CRSDefinition> candidates = new ArrayList<>();
		candidates.add(targetCrsParam);

		PropertyDefinition property = PropertyPaths.findProperty(type, path);
		if (property != null) {
			GeometryMetadata metadata = property.getPropertyType()
					.getConstraint(GeometryMetadata.class);
			if (metadata.getAuthName() != null && metadata.getSrs() != null) {
				candidates
						.add(new CodeDefinition(metadata.getAuthName() + ":" + metadata.getSrs()));
			}
			else if (metadata.getSrsText() != null) {
				candidates.add(new WKTDefinition(metadata.getSrsText(), null));
			}
		}

		candidates.add(findInstanceCrs(first, path));

		CRSDefinition firstCandidate = null;
		for (CRSDefinition candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			if (firstCandidate == null) {
				firstCandidate = candidate;
			}
			GeoParquetCrs crs = GeoParquetCrs.resolve(candidate, SimpleLog.NO_LOG);
			if (!crs.isUnknown()) {
				return crs;
			}
		}
		reporter.warn("Could not determine the CRS for geometry column " + column + " of type "
				+ type.getName().getLocalPart());
		return GeoParquetCrs.resolve(firstCandidate, reporter);
	}

	private CRSDefinition findInstanceCrs(Instance instance, List<QName> path) {
		Object value = PropertyPaths.findValue(instance, path);
		Pair<Geometry, CRSDefinition> pair = extractGeometry(value, false, reporter);
		return pair == null ? null : pair.getSecond();
	}

	@Override
	public Geometry convert(Object value, GeoParquetCrs target, SimpleLog log) {
		Pair<Geometry, CRSDefinition> pair = extractGeometry(value, false, reporter);
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
				reporter.error(new IOMessageImpl(
						"Could not convert geometry to target CRS: No source CRS provided"
								+ describeGeometryContext(pair.getFirst()),
						null));
				// geometry is not in the target CRS
				return pair.getFirst();
			}
			geometry = reprojector.transform(pair.getFirst(), sourceCrs.getCRS(), paramCrs, false,
					reporter, describeGeometryContext(pair.getFirst()));
			if (geometry == null) {
				// conversion failed (already reported), geometry is not in the target CRS
				return pair.getFirst();
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
		return unifyGeometry(geometry, reporter,
				target.isSwapAxes() ? null : target.getTargetCrs());
	}

	/**
	 * Settings read from the parameters.
	 */
	private record Settings(MappingOptions options, CompressionCodecName codec,
			int rowGroupRowLimit) {
	}

}
