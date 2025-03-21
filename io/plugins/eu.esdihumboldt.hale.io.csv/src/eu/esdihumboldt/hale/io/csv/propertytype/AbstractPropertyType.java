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
package eu.esdihumboldt.hale.io.csv.propertytype;

import javax.xml.namespace.QName;

import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.Binding;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.HasValueFlag;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultTypeDefinition;
import eu.esdihumboldt.hale.io.csv.PropertyType;

/**
 * Abstract class for property types
 *
 * @author Kevin Mais
 */
public abstract class AbstractPropertyType implements PropertyType {

	private DefaultTypeDefinition td;

	/**
	 * Default Constructor
	 *
	 * @param binding the given binding
	 */
	public AbstractPropertyType(Class<?> binding) {
		this.td = new DefaultTypeDefinition(new QName(binding.getName()));

		td.setConstraint(HasValueFlag.ENABLED);
		td.setConstraint(Binding.get(binding));
	}

	/**
	 * @see eu.esdihumboldt.hale.io.csv.PropertyType#getTypeDefinition()
	 */
	@Override
	public TypeDefinition getTypeDefinition() {
		return td;
	}

}
