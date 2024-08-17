
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

import java.text.MessageFormat;
import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;

/**
 * Docker client abstraction for use in hale tests.
 *
 * Original implementation used Spotify Docker client. Now replaced by using
 * testcontainers, but it is encouraged to instead use testcontainers directly.
 *
 * @author Sameer Sheikh
 *
 * @deprecated Preferably use testcontainers directly instead of this class
 */
@Deprecated
public class HaleDockerClient implements DockerContainer {

	private static final ALogger LOGGER = ALoggerFactory.getLogger(HaleDockerClient.class);

	private GenericContainer container;
	private final ContainerParameters dbc;

	/**
	 * A parameterized constructor
	 *
	 * @param dbc parameters related to a general docker client
	 */
	public HaleDockerClient(ContainerParameters dbc) {
		this.dbc = dbc;
	}

	/**
	 * creates a container using the parameters.
	 *
	 */
	public void createContainer() {
		String configuredDocker = dbc.getDockerHost();
		if (configuredDocker != null) {
			LOGGER.warn(
					"Setting for docker host ({}) is ignored when using testcontainers, instead configure testcontainers",
					configuredDocker);
			// see also https://java.testcontainers.org/features/configuration/
		}

		container = new GenericContainer(DockerImageName.parse(dbc.getImageName()))
				.withExposedPorts(dbc.getExposedPortList().stream().map(Integer::parseInt)
						.toArray(Integer[]::new))
				.withCommand(dbc.getCommands().toArray(String[]::new))
				.withLogConsumer(new Slf4jLogConsumer(LOGGER))
				.withPrivilegedMode(dbc.isPrivileged())
				.withImagePullPolicy(PullPolicy.ageBased(Duration.ofMinutes(10)));
	}

	/**
	 * Gets the host name from the url configured in the docker configuration
	 *
	 * @return host name configured
	 */
	@Override
	public String getHostName() {
		return container.getHost();
	}

	/**
	 * @return the container IP address
	 */
	public String getContainerIp() {
		// Note: IP of container is not available, return host ports are mapped to
		return container.getHost();
	}

	/**
	 * start a container. Container can be started in the privileged mode if the
	 * 'isPrivileged' key in the configuration is set as true.
	 *
	 * @throws InterruptedException interrupted exception
	 */
	public void startContainer() throws InterruptedException {
		LOGGER.info(
				MessageFormat.format("Preparing container for image {0}...", container.getImage()));
		container.start();
		LOGGER.info(MessageFormat.format("Created container with ID {0}, now starting...",
				container.getContainerId()));
	}

	/**
	 * gets the binded docker host port
	 *
	 * @param port the configured port number
	 * @return the binded docker host port number for the given port
	 */
	@Override
	public int getHostPort(int port) {
		return container.getMappedPort(port);
	}

	/**
	 * kill and remove the container
	 *
	 * @throws Exception if fails to kill the container or remove it
	 */
	public void killAndRemoveContainer() throws Exception {
		if (container == null) {
			return;
		}

		LOGGER.info(MessageFormat.format("Stopping container {0}...", container.getContainerId()));
		container.stop();
	}

	/**
	 * @return the container ID or <code>null</code> if no container was created yet
	 */
	public String getContainerId() {
		if (container != null) {
			return container.getContainerId();
		}
		else {
			return null;
		}
	}
}
