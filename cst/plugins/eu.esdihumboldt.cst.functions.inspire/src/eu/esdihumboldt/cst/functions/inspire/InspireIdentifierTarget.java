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
package eu.esdihumboldt.cst.functions.inspire;

import eu.esdihumboldt.hale.common.align.model.Entity;
import eu.esdihumboldt.hale.common.align.model.Property;
import eu.esdihumboldt.hale.common.align.model.condition.EntityCondition;
import eu.esdihumboldt.hale.common.align.model.condition.PropertyCondition;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Target condition for the {@link Identifier} function.
 *
 * @author Simon Templer
 */
public class InspireIdentifierTarget implements PropertyCondition {

	/**
	 * @see EntityCondition#accept(Entity)
	 */
	@Override
	public boolean accept(Property entity) {
		TypeDefinition propertyType = entity.getDefinition().getDefinition().getPropertyType();
		return propertyType.getName().getLocalPart().equals("IdentifierPropertyType")
				&& propertyType.getName().getNamespaceURI()
						.startsWith("urn:x-inspire:specification:gmlas:BaseTypes");
	}

}
