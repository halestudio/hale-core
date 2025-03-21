/*
 * Copyright (c) 2013 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.util.blueprints.entities.test

import eu.esdihumboldt.util.blueprints.entities.VertexEntity


/**
 * Entity with a default value on a property.
 *
 * @author Simon Templer
 */
@VertexEntity('def_value')
class DefaultValue {

	String name = 'somethin\''

	int value = 2;
}
