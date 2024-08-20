
/*
 * Copyright (c) 2015 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.align.transformation.service;

import eu.esdihumboldt.hale.common.schema.SchemaSpaceID;
import eu.esdihumboldt.hale.common.schema.model.SchemaSpace;

/**
 * Service interface for services that provide the schemas used in context of a
 * transformation.
 *
 * @author Simon Templer
 */
public interface TransformationSchemas {

	/**
	 * Get the source or target schema space.
	 *
	 * @param spaceID the schema space ID, either {@link SchemaSpaceID#SOURCE} or
	 *            {@link SchemaSpaceID#TARGET}
	 * @return the schema space
	 */
	public SchemaSpace getSchemas(SchemaSpaceID spaceID);

}
