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
package eu.esdihumboldt.hale.common.instance.geometry;

import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;
import eu.esdihumboldt.util.definition.ObjectDefinition;

/**
 * Provides support for creating a {@link CRSDefinition} from a definition
 * string and vice versa.
 *
 * @param <T> the CRS definition type
 *
 * @author Simon Templer
 */
public interface CRSDefinitionFactory<T extends CRSDefinition> extends ObjectDefinition<T> {

	// concrete typed interface

}
