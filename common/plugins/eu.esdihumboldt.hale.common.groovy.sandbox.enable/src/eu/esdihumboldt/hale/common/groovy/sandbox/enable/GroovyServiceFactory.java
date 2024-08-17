
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
package eu.esdihumboldt.hale.common.groovy.sandbox.enable;

import eu.esdihumboldt.hale.common.core.service.ServiceFactory;
import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.util.groovy.sandbox.DefaultGroovyService;
import eu.esdihumboldt.util.groovy.sandbox.GroovyService;

/**
 * Service factory for GroovyService.
 *
 * @author Kai Schwierczek
 */
public class GroovyServiceFactory implements ServiceFactory {

	@SuppressWarnings("unchecked")
	@Override
	public <T> T createService(Class<T> serviceInterface, ServiceProvider serviceLocator) {
		if (serviceInterface.equals(GroovyService.class)) {
			return (T) new DefaultGroovyService();
		}

		return null;
	}

}
