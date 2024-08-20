
/*
 * Copyright (c) 2017 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.schema.model.constraint.factory;

import java.util.Map;
import java.util.Optional;

import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Type reference builder backed by a map.
 *
 * @author Simon Templer
 */
public class MapTypeReferenceBuilder implements TypeReferenceBuilder {

	private final Map<TypeDefinition, Value> map;

	/**
	 * Create a new type reference builder based on the given map.
	 *
	 * @param map the map with references mapped to the respective type definitions
	 */
	public MapTypeReferenceBuilder(Map<TypeDefinition, Value> map) {
		super();
		this.map = map;
	}

	@Override
	public Optional<Value> createReference(TypeDefinition type) {
		return Optional.ofNullable(map.get(type));
	}

}
