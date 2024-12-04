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
package eu.esdihumboldt.hale.common.test.docker.helper;

import eu.esdihumboldt.hale.common.test.docker.config.DockerConfigInstance;
import eu.esdihumboldt.hale.common.test.docker.config.HaleDockerClient;
import eu.esdihumboldt.hale.common.test.docker.config.RunWithContainer;

/**
 * The docker config helper which cretaes a docker container, starts a
 * container, executes the run method from a anonymous class, and then kills the
 * container.
 *
 * @author Sameer Sheikh
 *
 */
public class DockerConfigHelper {

	/**
	 * fetches the configuration parameters for the key <b> configName </b> which is
	 * usefull in creating a docker container. It starts a container, executes the
	 * logic and kills the container.
	 *
	 * @param configName a name for the configuration group
	 * @param runInstance an anonymous class object
	 * @param cl A class loader to fetch configuration from classpath
	 * @return the result of run method execution of a RunWithContainer class
	 * @throws Exception exception if it fails to kill the container
	 */
	public static <T> T withContainer(String configName, RunWithContainer<T> runInstance,
			ClassLoader cl) throws Exception {

		DockerConfigInstance dci = new DockerConfigInstance(configName, cl);
		HaleDockerClient client = new HaleDockerClient(dci);
		client.createContainer();
		try {
			client.startContainer();
			return runInstance.run(client, dci);

		} finally {
			client.killAndRemoveContainer();
		}
	}
}
