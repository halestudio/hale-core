
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
package eu.esdihumboldt.hale.common.test.docker;

import eu.esdihumboldt.hale.common.test.docker.config.RunWithContainer;
import eu.esdihumboldt.hale.common.test.docker.helper.DockerConfigHelper;

/**
 * An abstract base class which new unit test will extend to delegate the docker
 * container creation, startup, and removal jobs to the helper class.
 *
 * @author Sameer Sheikh
 */
public abstract class AbstractDockerTest {

	/**
	 * a class loader
	 */
	private ClassLoader cl;

	/**
	 * default constructor
	 */
	public AbstractDockerTest() {

	}

	/**
	 * Parameterized constructor
	 *
	 * @param cl a class loader
	 */
	public AbstractDockerTest(ClassLoader cl) {
		this.cl = cl;
	}

	/**
	 * It gets the class loader of the unit test class which will be useful for
	 * fetching the configuration from the classpath. It delegates the call with the
	 * given class loader.
	 *
	 * @see DockerConfigHelper#withContainer(String, RunWithContainer, ClassLoader)
	 * @param configName a configuration name key
	 * @param runInstance an anonymous class object
	 * @throws Exception if it fails delegate the method call
	 */
	protected void withContainer(String configName, RunWithContainer<?> runInstance)
			throws Exception {

		if (cl != null) {
			DockerConfigHelper.withContainer(configName, runInstance, cl);
		}
		else {
			DockerConfigHelper.withContainer(configName, runInstance, getClass().getClassLoader());
		}
	}
}
