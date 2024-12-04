/*
 * Copyright (c) 2024 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.service.helper.population;

import java.util.Collection;

import eu.esdihumboldt.hale.common.align.model.EntityDefinition;

/**
 * Notifier for Population count helper class
 *
 * @author Arun
 *
 */
public interface IPopulationUpdater {

	/**
	 * Increase count for given entity
	 *
	 * @param def A {@link EntityDefinition}
	 * @param count counts for given EntityDefinition
	 */
	public void increaseForEntity(EntityDefinition def, int count);

	/**
	 * Get children of entity definition
	 *
	 * @param entityDef A {@link EntityDefinition}
	 * @return All the children including contexts.
	 */
	public Collection<? extends EntityDefinition> getChildren(EntityDefinition entityDef);

}
