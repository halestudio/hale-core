
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
 * A generic docker client
 *
 * @author Sameer Sheikh
 *
 */
public interface DockerContainer {

	/**
	 * gets the host name for a docker client
	 *
	 * @return host name
	 */
	String getHostName();

	/**
	 * gets the host port of a docker client for a local port number
	 *
	 * @param port local port number
	 * @return host port number
	 */
	int getHostPort(int port);
}
