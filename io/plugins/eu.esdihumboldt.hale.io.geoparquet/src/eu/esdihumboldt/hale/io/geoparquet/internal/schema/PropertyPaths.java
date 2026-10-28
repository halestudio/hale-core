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
import java.util.List;

import javax.xml.namespace.QName;

import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.DefinitionUtil;
import eu.esdihumboldt.hale.common.schema.model.PropertyDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Helpers to follow property paths (as used in column mappings) in type
 * definitions and instances.
 */
public final class PropertyPaths {

	private PropertyPaths() {
		// utility class
	}

	/**
	 * Find the property definition for a property path.
	 *
	 * @param type the type definition the path starts at
	 * @param path the property path, may include group names
	 * @return the property definition, <code>null</code> if not found
	 */
	public static PropertyDefinition findProperty(TypeDefinition type, List<QName> path) {
		return findProperty(DefinitionUtil.getAllChildren(type), path, 0);
	}

	private static PropertyDefinition findProperty(
			Collection<? extends ChildDefinition<?>> children, List<QName> path, int index) {
		for (ChildDefinition<?> child : children) {
			if (!child.getName().equals(path.get(index))) {
				continue;
			}
			if (index == path.size() - 1) {
				return child.asProperty();
			}
			Collection<? extends ChildDefinition<?>> next = child.asGroup() != null
					? DefinitionUtil.getAllChildren(child.asGroup())
					: DefinitionUtil.getAllChildren(child.asProperty().getPropertyType());
			PropertyDefinition found = findProperty(next, path, index + 1);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/**
	 * Find the first value for a property path in an instance.
	 *
	 * @param instance the instance
	 * @param path the property path, may include group names
	 * @return the value (instances are unwrapped to their value), <code>null</code>
	 *         if there is none
	 */
	public static Object findValue(Instance instance, List<QName> path) {
		Object current = instance;
		for (QName name : path) {
			Object[] values = current instanceof Group ? ((Group) current).getProperty(name) : null;
			current = null;
			if (values != null) {
				for (Object value : values) {
					if (value != null) {
						current = value;
						break;
					}
				}
			}
			if (current == null) {
				return null;
			}
		}
		if (current instanceof Instance) {
			current = ((Instance) current).getValue();
		}
		return current;
	}

}
