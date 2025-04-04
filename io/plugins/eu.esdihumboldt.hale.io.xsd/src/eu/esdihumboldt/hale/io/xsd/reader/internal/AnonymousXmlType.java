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
package eu.esdihumboldt.hale.io.xsd.reader.internal;

import javax.xml.namespace.QName;

import eu.esdihumboldt.hale.common.schema.model.impl.AbstractDefinition;

/**
 * An anonymous XML type
 *
 * @author Simon Templer
 */
public class AnonymousXmlType extends XmlTypeDefinition {

	/**
	 * @see XmlTypeDefinition#XmlTypeDefinition(QName)
	 */
	public AnonymousXmlType(QName name) {
		super(name);
	}

	/**
	 * @see AbstractDefinition#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		if (getSuperType() == null) {
			return "?"; //$NON-NLS-1$
		}
		else {
			return "? extends " + getSuperType().getDisplayName(); //$NON-NLS-1$
		}
	}

}
