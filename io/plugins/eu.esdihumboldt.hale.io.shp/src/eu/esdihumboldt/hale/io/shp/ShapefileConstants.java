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
package eu.esdihumboldt.hale.io.shp;

import javax.xml.namespace.QName;

import eu.esdihumboldt.hale.io.shp.reader.internal.ShapeInstanceReader;

/**
 * Constants for Shapefile I/O
 *
 * @author Simon Templer
 */
public interface ShapefileConstants {

	/**
	 * The default shapefile namespace
	 */
	public static final String SHAPEFILE_NS = "http://www.esdi-humboldt.eu/hale/shp";

	/**
	 * The shapefile augmentation namespace - types and properties that really are
	 * not part of the data, but are some kind of augmented information or metadata
	 * should use this namespace.
	 */
	public static final String SHAPEFILE_AUGMENT_NS = "http://www.esdi-humboldt.eu/hale/shp/augment";

	/**
	 * The local name of the augmented property specifying the Shapefile file name
	 * an instance originated from.
	 */
	public static final String AUGMENTED_PROPERTY_FILENAME = "filename";

	/**
	 * The default type name
	 */
	public static final String DEFAULT_TYPE_NAME = "Shapefile";

	/**
	 * Name of the parameter for {@link ShapeInstanceReader} to select the type the
	 * instances should be associated to. The value is a {@link QName} encoded as
	 * String.
	 */
	public static final String PARAM_TYPENAME = "typename";

	/**
	 * Name of the parameter for {@link ShapeInstanceReader} to auto detect schema
	 * types when selecting instances for multiple schemas.
	 */
	public static final String PARAM_AUTO_DETECT_SCHEMA_TYPES = "autoDetectSchemaTypes";

	/**
	 * Name of the parameter for {@link ShapeInstanceReader} to activate matching of
	 * Shapefile property names to schema property names by checking if there is
	 * exactly one schema property whose name starts with the Shapefile property
	 * name.
	 */
	public static final String PARAM_MATCH_SHORT_PROPERTY_NAMES = "matchShortPropertyNames";

	/**
	 * Constant for the geometry property for the shape files. Used when exporting
	 * the data to the shape files.
	 */
	public static final String THE_GEOM = "the_geom";

	/**
	 * Constant for the shape file extension.
	 */
	public static final String SHP_EXTENSION = ".shp";

	/**
	 * Constant for the CPG file extension.
	 */
	public static final String CPG_EXTENSION = ".cpg";

	/**
	 * Constant for underscore.
	 */
	public static final String UNDERSCORE = "_";

	/**
	 * Constant for the url. Used to create schema for shape file writer.
	 */
	public static final String URL_STRING = "url";

	/**
	 * Constant used to create default transaction when writing to the shape file.
	 */
	public static final String CREATE_CONSTANT = "create";

}
