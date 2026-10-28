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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import eu.esdihumboldt.hale.common.instance.model.Group;
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
	 * Find the values for a property path below an instance or group. The values
	 * are not unwrapped, <code>null</code> values are omitted.
	 *
	 * @param parent the instance or group the path starts at, a value that is no
	 *            group yields no values for a non-empty path
	 * @param path the property path, may include group names, for an empty path the
	 *            parent itself is the only value
	 * @return the values, empty if there are none
	 */
	public static List<Object> findValues(Object parent, List<QName> path) {
		List<Object> current = new ArrayList<>();
		if (parent != null) {
			current.add(parent);
		}
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

}
