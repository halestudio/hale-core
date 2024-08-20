
/*
 * Copyright (c) 2017 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.haleconnect;

import eu.esdihumboldt.hale.common.core.service.ServiceFactory;
import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.hale.io.haleconnect.internal.HaleConnectServiceImpl;

/**
 * Service factory for the hale connect service
 *
 * @author Florian Esser
 */
public class HaleConnectServiceFactory implements ServiceFactory {

	/**
	 * @see eu.esdihumboldt.hale.common.core.service.ServiceFactory#createService(java.lang.Class,
	 *      eu.esdihumboldt.hale.common.core.service.ServiceProvider)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> T createService(Class<T> serviceInterface, ServiceProvider serviceLocator) {
		if (serviceInterface.equals(HaleConnectService.class)) {
			return (T) new HaleConnectServiceImpl();
		}

		return null;
	}

}
