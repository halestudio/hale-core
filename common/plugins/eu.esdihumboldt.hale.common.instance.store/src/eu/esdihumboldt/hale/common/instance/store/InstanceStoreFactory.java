/*
 * Copyright (c) 2026 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.instance.store;

import java.io.IOException;
import java.nio.file.Path;

import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.hale.common.instance.model.DataSet;

/**
 * Creates instance stores. Contributed via the extension point
 * {@value InstanceStoreExtension#EXTENSION_ID}.
 */
public interface InstanceStoreFactory {

	/**
	 * Create a new empty store.
	 *
	 * @param dataSet the data set of the instances to store
	 * @param directory the directory the store may use exclusively, it is deleted
	 *            when the store is closed
	 * @param services the service provider, may be <code>null</code>
	 * @return the store
	 * @throws IOException if the store cannot be created
	 */
	InstanceStore create(DataSet dataSet, Path directory, ServiceProvider services)
			throws IOException;

}
