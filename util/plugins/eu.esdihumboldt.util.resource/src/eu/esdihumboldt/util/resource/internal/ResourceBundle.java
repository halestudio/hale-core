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
package eu.esdihumboldt.util.resource.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Resource bundle activator
 *
 * @author Simon Templer
 */
public class ResourceBundle implements BundleActivator {

	private static BundleContext context;

	/**
	 * Get the bundle context.
	 *
	 * @return the bundle context or <code>null</code> if it was not started
	 */
	public static BundleContext getBundleContext() {
		return context;
	}

	/**
	 * @see BundleActivator#start(BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		ResourceBundle.context = context;
	}

	/**
	 * @see BundleActivator#stop(BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
		ResourceBundle.context = null;
	}

}
