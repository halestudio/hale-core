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
package eu.esdihumboldt.hale.common.align.model.impl;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import eu.esdihumboldt.hale.common.align.model.ChildContext;
import eu.esdihumboldt.hale.common.align.model.EntityDefinition;
import eu.esdihumboldt.hale.common.instance.model.Filter;
import eu.esdihumboldt.hale.common.schema.SchemaSpaceID;
import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Entity definition for a property or group property
 *
 * @author Simon Templer
 */
@Immutable
public class ChildEntityDefinition implements EntityDefinition {

	private final TypeDefinition type;

	private final Filter filter;

	private final List<ChildContext> path;

	private final SchemaSpaceID schemaSpace;

	/**
	 * Create an entity definition specified by the given child path.
	 *
	 * @param type the topmost parent of the property
	 * @param path the child path down from the type
	 * @param schemaSpace the schema space identifier
	 * @param filter the entity filter on the type, may be <code>null</code>
	 */
	public ChildEntityDefinition(TypeDefinition type, List<ChildContext> path,
			SchemaSpaceID schemaSpace, Filter filter) {
		super();

		this.type = type;
		this.path = Collections.unmodifiableList(path);
		this.schemaSpace = schemaSpace;
		this.filter = filter;
	}

	/**
	 * @see EntityDefinition#getSchemaSpace()
	 */
	@Override
	public SchemaSpaceID getSchemaSpace() {
		return schemaSpace;
	}

	/**
	 * @see EntityDefinition#getDefinition()
	 */
	@Override
	public ChildDefinition<?> getDefinition() {
		return path.get(path.size() - 1).getChild();
	}

	/**
	 * @see EntityDefinition#getType()
	 */
	@Override
	public TypeDefinition getType() {
		return type;
	}

	/**
	 * @see EntityDefinition#getPropertyPath()
	 */
	@Override
	public List<ChildContext> getPropertyPath() {
		return path;
	}

	/**
	 * @see EntityDefinition#getFilter()
	 */
	@Override
	public Filter getFilter() {
		return filter;
	}

	/**
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((filter == null) ? 0 : filter.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((schemaSpace == null) ? 0 : schemaSpace.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ChildEntityDefinition other = (ChildEntityDefinition) obj;
		if (filter == null) {
			if (other.filter != null)
				return false;
		}
		else if (!filter.equals(other.filter))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		}
		else if (!path.equals(other.path))
			return false;
		if (schemaSpace != other.schemaSpace)
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		}
		else if (!type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuffer result = new StringBuffer(getType().toString());
		for (ChildContext childContext : getPropertyPath()) {
			result.append('/');
			result.append(childContext.getChild().getName().getLocalPart());
		}
		return result.toString();
	}

}
