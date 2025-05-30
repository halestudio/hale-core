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
import java.net.URI;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.xml.namespace.QName;

import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.type.Name;
import org.geotools.data.shapefile.ShapefileDataStore;

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
					defaultType = getSourceSchema().getType(QName.valueOf(typename));
				} catch (Exception e) {
					// ignore
				}
			}
			if (defaultType == null) {
				// check if typename was supplied w/o namespace
				try {
					defaultType = getSourceSchema()
							.getType(new QName(ShapefileConstants.SHAPEFILE_NS, typename));
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
