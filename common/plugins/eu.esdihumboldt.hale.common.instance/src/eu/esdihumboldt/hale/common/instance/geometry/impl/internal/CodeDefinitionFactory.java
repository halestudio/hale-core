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
package eu.esdihumboldt.hale.common.instance.geometry.impl.internal;

import eu.esdihumboldt.hale.common.instance.geometry.CRSDefinitionFactory;
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition;

/**
 * Factory for {@link CodeDefinition}s
 *
 * @author Simon Templer
 */
public class CodeDefinitionFactory implements CRSDefinitionFactory<CodeDefinition> {

	/**
	 * @see CRSDefinitionFactory#getIdentifier()
	 */
	@Override
	public String getIdentifier() {
		return "code";
	}

	/**
	 * @see CRSDefinitionFactory#getObjectClass()
	 */
	@Override
	public Class<CodeDefinition> getObjectClass() {
		return CodeDefinition.class;
	}

	/**
	 * @see CRSDefinitionFactory#parse(String)
	 */
	@Override
	public CodeDefinition parse(String value) {
		return new CodeDefinition(value, null);
	}

	/**
	 * @see CRSDefinitionFactory#asString(Object)
	 */
	@Override
	public String asString(CodeDefinition crsDef) {
		return crsDef.getCode();
	}

}
