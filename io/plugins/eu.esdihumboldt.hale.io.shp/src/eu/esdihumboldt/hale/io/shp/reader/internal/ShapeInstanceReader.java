/*
 * Copyright (c) 2012 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.shp.reader.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.apache.commons.io.FileUtils;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.type.Name;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.files.ShpFileType;

import eu.esdihumboldt.hale.common.core.io.IOProvider;
import eu.esdihumboldt.hale.common.core.io.IOProviderConfigurationException;
import eu.esdihumboldt.hale.common.core.io.ProgressIndicator;
import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.core.io.impl.AbstractIOProvider;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.report.IOReporter;
import eu.esdihumboldt.hale.common.core.io.report.impl.IOMessageImpl;
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier;
import eu.esdihumboldt.hale.common.core.parameter.AbstractParameterValueDescriptor;
import eu.esdihumboldt.hale.common.instance.io.InstanceReader;
import eu.esdihumboldt.hale.common.instance.io.impl.AbstractInstanceReader;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.ext.impl.PerTypeInstanceCollection;
import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.DefinitionUtil;
import eu.esdihumboldt.hale.common.schema.model.PropertyDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;
import eu.esdihumboldt.hale.io.shp.ShapefileConstants;
import eu.esdihumboldt.hale.io.shp.internal.Messages;
import eu.esdihumboldt.util.Pair;

/**
 * Reads instances from a shapefile.
 *
 * @author Thorsten Reitz
 * @author Simon Templer
 */
public class ShapeInstanceReader extends AbstractInstanceReader implements ShapefileConstants {

	private static final String FILE = "file";
	public static final String FORWARD_SLASH = "/";
	private InstanceCollection instances;

	/**
	 * Default constructor.
	 */
	public ShapeInstanceReader() {
		super();

		addSupportedParameter(PARAM_TYPENAME);
	}

	@SuppressWarnings("javadoc")
	public static class TypenameParameterDescriptor extends AbstractParameterValueDescriptor {

		public TypenameParameterDescriptor() {
			super(null, Value.of(new QName("namespace", "localname").toString()));
		}

		@Override
		public String getSampleDescription() {
			return "The type name is represented like in the given example, with the namespace in curly braces.";
		}
	}

	/**
	 * @see IOProvider#isCancelable()
	 */
	@Override
	public boolean isCancelable() {
		return false;
	}

	/**
	 * @see AbstractIOProvider#execute(ProgressIndicator, IOReporter)
	 */
	@Override
	protected IOReport execute(ProgressIndicator progress, IOReporter reporter)
			throws IOProviderConfigurationException, IOException {
		progress.begin(Messages.getString("ShapeSchemaProvider.1"), ProgressIndicator.UNKNOWN); //$NON-NLS-1$

		URI loc = getSource().getLocation();
		reporter.info("Loading shapefile from {0}", loc.toString());

		if (loc.getScheme() != null && !loc.getScheme().equals(FILE)) {
			File tempDirPath = downloadShpFilesIfNeeded(loc, reporter);
			// if files were downloaded to temp directory due to presence of .fix file, then
			// use it as the location
			loc = tempDirPath == null ? loc : tempDirPath.toURI();
		}
		// special handling for directory as source -> load single Shapefile

		try {
			File file = new File(loc);
			// special handling for directory as source -> load single Shapefile
			if (file.exists() && file.isDirectory()) {
				File[] candidates = file
						.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".shp"));
				if (candidates != null && candidates.length > 0) {
					// use first Shapefile found in folder
					loc = candidates[0].toURI();

					if (candidates.length > 1) {
						reporter.warn(
								"Picked file {} to load from folder, other Shapefiles in folder found but ignored",
								candidates[0].getName());
					}
					else {
						reporter.info("Picked file {} to load from folder",
								candidates[0].getName());
					}
				}
			}
		} catch (IllegalArgumentException e) {
			// ignore
		}

		ShapefileDataStore store = new ShapefileDataStore(loc.toURL());
		store.setCharset(getCharset());

		progress.setCurrentTask("Extracting shape instances");

		String typename = getParameter(PARAM_TYPENAME).as(String.class);

		// assign default false, otherwise will throw NPE for the test cases.
		boolean autoDetect = getParameter(PARAM_AUTO_DETECT_SCHEMA_TYPES) != Value.NULL
				? getParameter(PARAM_AUTO_DETECT_SCHEMA_TYPES).as(Boolean.class)
				: false;

		TypeDefinition defaultType = null;
//		If the autoDetect is checked then do not get the typename from the TypeSelectionPage.
		if (!autoDetect) {
			if (typename != null && !typename.isEmpty()) {
				try {

					if ((defaultType = getSourceSchema()
							.getType(QName.valueOf(typename))) == null) {
						// check if typename was supplied w/o default Shapefile namespace
						if ((defaultType = getSourceSchema().getType(
								new QName(ShapefileConstants.SHAPEFILE_NS, typename))) == null) {
							// check if typename was supplied w/o namespace
							List<TypeDefinition> matchingTypes = getSourceSchema().getTypes().stream()
									.filter(type -> type.getName().getLocalPart().equals(typename))
									.collect(Collectors.toList());
							
							if (matchingTypes.size() == 1) {
								// unique match found, use it
								defaultType = matchingTypes.get(0);
							} else if (matchingTypes.size() > 1) {
								// multiple matches found, log warning
								reporter.warn(
										"Multiple types found with local name ''{0}'' in different namespaces. "
										+ "Please specify the type name with namespace (e.g., '{{namespace}}localname''). "
										+ "Matching namespaces: {1}",
										typename,
										matchingTypes.stream()
												.map(type -> type.getName().getNamespaceURI())
												.collect(Collectors.joining(", ")));
								defaultType = null;
							}
							// else: no matches found, defaultType remains null
						}
					}
				} catch (Exception e) {
					// ignore
					// TODO report?
				}
			}
		}

		if (defaultType == null) {
			reporter.info(new IOMessageImpl(
					"No type name supplied as parameter, trying to auto-detect the schema type.",
					null));
			TypeDefinition dataType = ShapeSchemaReader
					.readShapeType(new DefaultInputSupplier(loc));
			if (dataType == null) {
				throw new IOException("Could not read shapefile structure information");
			}
			String preferredName = null;
			Name name = store.getNames().iterator().next();
			if (name != null) {
				preferredName = name.getLocalPart();
			}
			Pair<TypeDefinition, Integer> tp = getMostCompatibleShapeType(getSourceSchema(),
					dataType, preferredName, false);
			if (tp == null) {
				// try with types that don't use ShapeSchemaReader conventions
				tp = getMostCompatibleShapeType(getSourceSchema(), dataType, preferredName, true);
			}
			if (tp == null) {
				throw new IOProviderConfigurationException(
						"No schema type specified and auto-detection failed");
			}
			defaultType = tp.getFirst();
			reporter.info(new IOMessageImpl(MessageFormat.format(
					"Auto-deteted {0} as schema type, with a {1}% compatibility rating.",
					defaultType.getName(), tp.getSecond()), null));
		}

		Map<TypeDefinition, InstanceCollection> collections = new HashMap<>();

		// create a collection for each type
		for (Name name : store.getNames()) {
			SimpleFeatureSource features = store.getFeatureSource(name);
			TypeDefinition type = defaultType;
			if (type == null) {
				QName typeName = new QName(ShapefileConstants.SHAPEFILE_NS, name.getLocalPart());
				type = getSourceSchema().getType(typeName);
			}
			boolean matchShortParameterNames = getParameter(PARAM_MATCH_SHORT_PROPERTY_NAMES)
					.as(Boolean.class, false);
			collections.put(type, new ShapesInstanceCollection(features, type, getCrsProvider(),
					name.getLocalPart(), matchShortParameterNames));
		}

		instances = new PerTypeInstanceCollection(collections);

		reporter.setSuccess(true);
		store.dispose();
		return reporter;
	}

	@Override
	protected Charset getDefaultCharset() {
		// default charset: ISO-8859-1
		return Charset.forName("ISO-8859-1");
	}

	/**
	 * Determine the type out of the the mapping relevant types in the given type
	 * index, that matches the given data type best.
	 *
	 * @param types the type index
	 * @param dataType the Shapefile data type
	 * @param preferredName the name of the preferred type
	 * @param allowNonShapefileTypes if types should be considered that don't follow
	 *            the conventions of the ShapeSchemaReader
	 * @return the most compatible type found together with is compatibility rating
	 *         or <code>null</code> if there is no type that at least has one
	 *         matching property
	 *
	 * @see #checkCompatibility(TypeDefinition, TypeDefinition, boolean)
	 */
	public static Pair<TypeDefinition, Integer> getMostCompatibleShapeType(TypeIndex types,
			TypeDefinition dataType, String preferredName, boolean allowNonShapefileTypes) {
		int maxCompatibility = -1;
		TypeDefinition maxType = null;

		// check preferred name first
		TypeDefinition preferredType = types
				.getType(new QName(ShapefileConstants.SHAPEFILE_NS, preferredName));
		if (allowNonShapefileTypes && preferredType == null) {
			preferredType = findPreferred(preferredName, findName -> {
				return types.getMappingRelevantTypes().stream()
						.filter(t -> t.getName().getLocalPart().equals(findName)).findFirst()
						.orElse(null);
			});
		}
		if (allowNonShapefileTypes && preferredType == null) {
			preferredType = findPreferred(preferredName, findName -> {
				return types.getMappingRelevantTypes().stream()
						// check displayname as well (e.g. in case of XSD where this
						// represents the XML element)
						.filter(t -> t.getDisplayName().equals(preferredName)).findFirst()
						.orElse(null);
			});
		}
		if (preferredType != null) {
			int comp = checkCompatibility(preferredType, dataType, allowNonShapefileTypes);
			if (comp >= 100) {
				// return an exact match directly
				return new Pair<TypeDefinition, Integer>(preferredType, 100);
			}
			else {
				maxType = preferredType;
				maxCompatibility = comp;
			}
		}

		for (TypeDefinition schemaType : types.getMappingRelevantTypes()) {
			if (allowNonShapefileTypes || ShapefileConstants.SHAPEFILE_NS
					.equals(schemaType.getName().getNamespaceURI())) {
				// is a shapefile type

				int comp = checkCompatibility(schemaType, dataType, allowNonShapefileTypes);
				if (comp >= 100) {
					// return an exact match directly
					return new Pair<TypeDefinition, Integer>(schemaType, 100);
				}
				else if (comp > maxCompatibility) {
					maxType = schemaType;
					maxCompatibility = comp;
				}
				else if (maxCompatibility > 0 && comp == maxCompatibility) {
					// TODO debug message? possible duplicate?
				}
			}
		}

		if (maxType != null && maxCompatibility > 0) {
			// return the type with the maximum compatibility rating
			return new Pair<TypeDefinition, Integer>(maxType, maxCompatibility);
		}

		return null;
	}

	private static TypeDefinition findPreferred(String preferredName,
			Function<String, TypeDefinition> find) {
		TypeDefinition result = find.apply(preferredName);

		if (result == null) {
			// also allow for type w/o result_ prefix as the case for transformation results
			// on hale-connect by default
			String checkName = preferredName;
			while (result == null && checkName.startsWith("result_")) {
				checkName = checkName.substring("result_".length());
				result = find.apply(checkName);
			}
		}

		return result;
	}

	/**
	 * Determines if the compatibility rating between the two Shapefile type
	 * definitions.
	 *
	 * @param schemaType the type to test for compatibility
	 * @param dataType the type representing the data to read
	 * @param allowNonShapefileProperties if properties should be considered that
	 *            don't follow the conventions of the ShapeSchemaReader
	 * @return the percentage of compatibility (value from <code>0</code> to
	 *         <code>100</code>), where <code>100</code> represents an exact match
	 *         and <code>0</code> no compatibility
	 */
	public static int checkCompatibility(TypeDefinition schemaType, TypeDefinition dataType,
			boolean allowNonShapefileProperties) {
		// Shapefile types are flat, so only regard properties
		Collection<? extends PropertyDefinition> children = DefinitionUtil
				.getAllProperties(dataType);
		int count = children.size();
		int schemaCount = DefinitionUtil.getAllProperties(schemaType).size();

		// check for every property if it exists with the schema, with the same
		// name
		int num = 0;
		for (PropertyDefinition property : children) {
			ChildDefinition<?> child = getChild(schemaType, property.getName(),
					allowNonShapefileProperties);
			if (child != null && child.asProperty() != null) {
				num++;
			}
		}

		if (num == count && count == schemaCount) {
			// exact match
			return 100;
		}
		else {
			int percentage = (int) Math.round((double) (num * 100) / (double) count);
			if (percentage > 1) {
				// reduce value by one, to ensure 100 is not returned, but only
				// return zero if there actually is no match
				percentage -= 1;
			}
			// compatibility measure with a max of 99
			return percentage;
		}
	}

	/**
	 * Get child of the given schema type with the given name.
	 *
	 * @param schemaType the schema type.
	 * @param propertyName the property name
	 * @param allowNonShapefileProperties if properties should be considered even if
	 *            the namespace does not match
	 * @return the child that was found or <code>null</code>
	 */
	private static ChildDefinition<?> getChild(TypeDefinition schemaType, QName propertyName,
			boolean allowNonShapefileProperties) {
		ChildDefinition<?> result = schemaType.getChild(propertyName);
		if (allowNonShapefileProperties && result == null) {
			// also allow match by local name only
			return schemaType.getChildren().stream()
					.filter(c -> c.getName().getLocalPart().equals(propertyName.getLocalPart()))
					.findFirst().orElse(null);
		}
		return result;
	}

	/**
	 * Download Shapefiles from the given URL if a .fix file exists at the URL.
	 * Otherwise, IOException related to `Read Channel is not a File Channel` is
	 * thrown when reading indexes.
	 *
	 * @param loc the URI location of the shapefile
	 * @param reporter the reporter to report errors
	 * @return the temporary directory where the shapefiles are downloaded, or
	 *         <code>null</code> if no files were downloaded
	 */
	private File downloadShpFilesIfNeeded(URI loc, IOReporter reporter) {

		try {
			URL locUrl = loc.toURL();
			// extract baseName of the file so that we can download all the files if they
			// exist.
			String baseName = getBaseName(locUrl);

			// create URL for .fix file from whatever URI is passed
			URL fixFileUrl = URI.create(baseName.concat(ShpFileType.FIX.extensionWithPeriod))
					.toURL();
			boolean fixFileExists = checkFileExistence(fixFileUrl, ShpFileType.FIX, reporter);
			if (fixFileExists) {
				reporter.info(
						"Found FIX file at the URL {0}. Proceeding to download Shapefiles as geotools raises exception reading FIX file from URL.",
						loc.toString());

				// proceed to download all the files from the URL
				File tempDir = Files.createTempDirectory("shp_from_url_").toFile();
				// add shutdown hook to make sure that the directory is deleted only after
				// Shapefile processing is complete and JVM terminates as there is no resource
				// clean up or housekeeping method available to clean up the resources.
				shutdownHookToDeleteTempDir(tempDir, reporter);
				String fileName = baseName.substring(baseName.lastIndexOf(FORWARD_SLASH) + 1);

				// iterate over all the ShpFileTypes and download them if they exist at the URL.
				for (ShpFileType type : ShpFileType.values()) {
					URL fileToDownload = URI.create(baseName.concat(type.extensionWithPeriod))
							.toURL();
					if (checkFileExistence(fileToDownload, type, reporter)) {
						File file = new File(tempDir, fileName + type.extensionWithPeriod);
						FileUtils.copyURLToFile(fileToDownload, file);
					}
				}
				reporter.info("Downloaded Shapefiles to temporary directory: {0}",
						tempDir.getAbsolutePath(), null);
				return tempDir;
			}
			else {
				reporter.info(
						"No FIX file found at the URL {0}. Proceeding with Shapefile processing without downloading files.",
						loc.toString());
				// No need to download anything as the fix file does not exist at the URL and
				// loading files from the URL will work.
				return null;
			}
		} catch (IOException e) {
			reporter.error("Exception downloading Shapefiles from the given URL  {0}.", loc, e);
		}
		// No need to download anything as the fix file does not exist at the URL and
		// loading files from the URL will work.
		return null;
	}

	/**
	 * Check if file in the URL exists.
	 *
	 * @param url the URL to check
	 * @param type the Shapefile type
	 * @param reporter the reporter to report errors
	 * @return <code>true</code> if the file exists, <code>false</code> otherwise
	 */
	private boolean checkFileExistence(URL url, ShpFileType type, IOReporter reporter) {
		try {
			URLConnection urlConnection = url.openConnection();
			if (urlConnection instanceof HttpURLConnection httpURLConnection) {
				// If the URL is HTTP, we can check the response code
				int responseCode = httpURLConnection.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_OK) {
					return true;
				}
			}
			else {
				reporter.info("Checking file existence at URL: {0}", url.toString());
				try (InputStream in = urlConnection.getInputStream()) {
					if (in == null) {
						reporter.warn("InputStream is null for shapefile type: {0} at URL: {1}.",
								type.extensionWithPeriod, url.toString());
						return false;
					}
					// using read() method to check if InputStream has content
					boolean hasContent = true;
					try {
						var b = in.read();
						if (b == -1) {
							hasContent = false;
						}
					} catch (Exception e) {
						hasContent = false;
					}
					if (hasContent) {
						reporter.info(
								"Shapefile type: {0} is valid and has some content returned from BucketService",
								type.extensionWithPeriod);
					}
					else {
						reporter.info(
								"Shapefile type: {0} is either null or a 0 byte file returned from BucketService",
								type.extensionWithPeriod);
					}
					return hasContent;
				}
			}
		} catch (IOException e) {
			reporter.warn(
					"Exception when reading file or file not present at given URL {0}, message: {1}",
					url.toString(), e.getLocalizedMessage());
		}

		return false;
	}

	/**
	 * Get the base name of the shapefile from the given object.
	 *
	 * @param url URL
	 * @return the base name of the shapefile, or <code>null</code> if no base name
	 */
	public String getBaseName(URL url) {
		ShpFileType[] values = ShpFileType.values();
		String base = null;
		for (ShpFileType type : values) {
			base = type.toBase(url);
			if (base != null) {
				return base;
			}
		}
		return base;
	}

	/**
	 * Get the base name of the shapefile from the given object.
	 *
	 * @param file File
	 * @return the base name of the shapefile, or <code>null</code> if no base name
	 */
	public String getBaseName(File file) {
		ShpFileType[] values = ShpFileType.values();
		String base = null;
		for (ShpFileType type : values) {
			base = type.toBase(file);
			if (base != null) {
				return base;
			}
		}
		return base;
	}

	/**
	 * Register a shutdown hook to delete the temporary directory created As
	 * File.deleteOnExit only deletes a directory if it is empty, so using
	 * FileUtils.deleteDirectory() to force delete all files and the directory
	 *
	 * @param dir the temporary directory to delete
	 * @param reporter the reporter to report errors
	 */
	private void shutdownHookToDeleteTempDir(File dir, IOReporter reporter) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				FileUtils.deleteDirectory(dir);
				reporter.info("Temporary directory {0} deleted successfully.",
						dir.getAbsolutePath());
			} catch (IOException e) {
				reporter.error("Failed to delete temporary directory {0}.", dir.getAbsolutePath(),
						e);
			}
		}));

	}

	/**
	 * @see AbstractIOProvider#getDefaultTypeName()
	 */
	@Override
	protected String getDefaultTypeName() {
		return ShapefileConstants.DEFAULT_TYPE_NAME;
	}

	/**
	 * @see InstanceReader#getInstances()
	 */
	@Override
	public InstanceCollection getInstances() {
		return instances;
	}

}
