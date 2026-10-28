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

import java.util.Collection;

import javax.xml.namespace.QName;

import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.DefinitionUtil;
import eu.esdihumboldt.hale.common.schema.model.PropertyDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.AugmentedValueFlag;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.GeometryType;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.HasValueFlag;
import eu.esdihumboldt.hale.io.xsd.constraint.XmlAttributeFlag;

/**
 * Classification of geometry properties.
 * <p>
 * A property holds a geometry if its type is a geometry type (
 * {@link GeometryType}, e.g. <code>gml:PointType</code> or a geometry binding)
 * or if its type is a <em>geometry property type</em>: a complex type without
 * own value whose elements (groups and choices flattened) are all of geometry
 * types, e.g. <code>gml:GeometryPropertyType</code>. Such a property is written
 * as a single geometry, the XML attributes of the property type (e.g.
 * <code>xlink:href</code>, <code>nilReason</code>) are not written.
 */
public final class GeometryProperties {

	private static final String GML_NAMESPACE_PREFIX = "http://www.opengis.net/gml";

	private GeometryProperties() {
		// utility class
	}

	/**
	 * Determine if a type holds a geometry, i.e. if it is a geometry type or a
	 * geometry property type.
	 *
	 * @param type the type definition
	 * @return if values of the type are written as geometry
	 */
	public static boolean isGeometry(TypeDefinition type) {
		return type.getConstraint(GeometryType.class).isGeometry() || isGeometryPropertyType(type);
	}

	/**
	 * Determine if a type is a geometry property type, i.e. a complex type without
	 * own value that contains only elements of geometry types (and attributes),
	 * like <code>gml:GeometryPropertyType</code>.
	 *
	 * @param type the type definition
	 * @return if the type is a geometry property type
	 */
	public static boolean isGeometryPropertyType(TypeDefinition type) {
		if (type.getConstraint(GeometryType.class).isGeometry()
				|| type.getConstraint(HasValueFlag.class).isEnabled()
				|| type.getConstraint(AugmentedValueFlag.class).isEnabled()) {
			return false;
		}
		Counts counts = new Counts();
		countElements(DefinitionUtil.getAllChildren(type), counts);
		return counts.geometry > 0 && counts.other == 0;
	}

	private static class Counts {

		int geometry;
		int other;
	}

	private static void countElements(Collection<? extends ChildDefinition<?>> children,
			Counts counts) {
		for (ChildDefinition<?> child : children) {
			if (child.asGroup() != null) {
				countElements(DefinitionUtil.getAllChildren(child.asGroup()), counts);
			}
			else {
				PropertyDefinition property = child.asProperty();
				if (property.getConstraint(XmlAttributeFlag.class).isEnabled()) {
					continue;
				}
				if (property.getPropertyType().getConstraint(GeometryType.class).isGeometry()) {
					counts.geometry++;
				}
				else {
					counts.other++;
				}
			}
		}
	}

	/**
	 * Determine if a property is <code>gml:boundedBy</code>. It is not written, the
	 * bbox covering column and the geo metadata provide the extent.
	 *
	 * @param name the property name
	 * @return if the property is the GML bounding box
	 */
	public static boolean isBoundedBy(QName name) {
		return isGml(name, "boundedBy");
	}

	/**
	 * Determine if a property is <code>gml:location</code>. It is only used as
	 * primary geometry if there is no other geometry.
	 *
	 * @param name the property name
	 * @return if the property is the GML location
	 */
	public static boolean isLocation(QName name) {
		return isGml(name, "location");
	}

	private static boolean isGml(QName name, String localName) {
		return localName.equals(name.getLocalPart())
				&& name.getNamespaceURI().startsWith(GML_NAMESPACE_PREFIX);
	}

}
