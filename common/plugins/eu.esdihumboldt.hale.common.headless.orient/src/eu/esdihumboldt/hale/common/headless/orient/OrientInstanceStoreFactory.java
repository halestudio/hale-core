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
package eu.esdihumboldt.hale.common.headless.orient;

import java.nio.file.Path;

import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreFactory;

/**
 * Factory for {@link OrientInstanceStore}s.
 */
public class OrientInstanceStoreFactory implements InstanceStoreFactory {

	/**
	 * The store identifier.
	 */
	public static final String ID = "orient";

	@Override
	public InstanceStore create(DataSet dataSet, Path directory, ServiceProvider services) {
		return new OrientInstanceStore(dataSet, directory);
	}

}
