
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
package eu.esdihumboldt.hale.common.test.docker.config;

/**
 * It exposes functionality to run a unit test logic on a docker client.
 *
 * @author Sameer Sheikh
 *
 * @param <T> type
 */
public interface RunWithContainer<T> {

	/**
	 * responsible to run a unit test login on the docker image.
	 *
	 * @param client a docker client
	 * @param config a docker configuration
	 * @return a type
	 * @throws Exception if fails while executing the run method
	 */
	T run(DockerContainer client, ContainerParameters config) throws Exception;
}
