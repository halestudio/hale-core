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
package eu.esdihumboldt.hale.common.core.io.extension;

import java.util.Set;

import org.eclipse.core.runtime.content.IContentType;

import de.fhg.igd.eclipse.util.extension.ExtensionObjectFactory;
import eu.esdihumboldt.hale.common.core.io.ResourceAdvisor;

/**
 * Descriptor and factory for a {@link ResourceAdvisor}
 *
 * @author Simon Templer
 */
public interface ResourceAdvisorDescriptor extends ExtensionObjectFactory<ResourceAdvisor> {

	/**
	 * Get the associated content types.
	 *
	 * @return the set of associated content types
	 */
	public Set<IContentType> getAssociatedTypes();

}
