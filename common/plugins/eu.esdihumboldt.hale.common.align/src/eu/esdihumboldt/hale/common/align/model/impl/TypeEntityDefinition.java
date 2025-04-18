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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import eu.esdihumboldt.hale.common.align.model.ChildContext;
import eu.esdihumboldt.hale.common.align.model.EntityDefinition;
import eu.esdihumboldt.hale.common.instance.model.Filter;
import eu.esdihumboldt.hale.common.schema.SchemaSpaceID;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Entity definition for a type
 *
 * @author Simon Templer
 */
@Immutable
public class TypeEntityDefinition implements EntityDefinition {

	private final SchemaSpaceID schemaSpace;

	private final TypeDefinition typeDefinition;

	private final Filter filter;

	/**
	 * Create an entity definition for the given type
	 *
	 * @param typeDefinition the type definition
	 * @param schemaSpace the schema space identifier
	 * @param filter the entity filter, may be <code>null</code>
	 */
	public TypeEntityDefinition(TypeDefinition typeDefinition, SchemaSpaceID schemaSpace,
			Filter filter) {
		super();

		checkNotNull(typeDefinition, "Null type definition not allowed for type entity definition");

		this.typeDefinition = typeDefinition;
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
	public TypeDefinition getDefinition() {
		return typeDefinition;
	}

	/**
	 * @see EntityDefinition#getType()
	 */
	@Override
	public TypeDefinition getType() {
		return typeDefinition;
	}

	/**
	 * @see EntityDefinition#getPropertyPath()
	 */
	@Override
	public List<ChildContext> getPropertyPath() {
		return Collections.emptyList();
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
		result = prime * result + ((schemaSpace == null) ? 0 : schemaSpace.hashCode());
		result = prime * result + ((typeDefinition == null) ? 0 : typeDefinition.hashCode());
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
		TypeEntityDefinition other = (TypeEntityDefinition) obj;
		if (filter == null) {
			if (other.filter != null)
				return false;
		}
		else if (!filter.equals(other.filter))
			return false;
		if (schemaSpace != other.schemaSpace)
			return false;
		if (typeDefinition == null) {
			if (other.typeDefinition != null)
				return false;
		}
		else if (!typeDefinition.equals(other.typeDefinition))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return typeDefinition.toString();
	}

}
