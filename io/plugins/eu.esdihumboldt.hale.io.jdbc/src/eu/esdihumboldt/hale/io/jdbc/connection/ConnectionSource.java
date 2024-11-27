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
package eu.esdihumboldt.hale.io.jdbc.connection;

import java.sql.Connection;
import java.util.function.Consumer;

import us.fatehi.utility.datasource.DatabaseConnectionSource;

/**
 * Implementation of the SchemaCrawler interface for database connections that
 * uses an existing {@link Connection} object. The provided {@code Connection}
 * must be managed externally, i.e. it will not be closed by this class.
 */
public class ConnectionSource implements DatabaseConnectionSource {

	private final Connection connection;

	public ConnectionSource(Connection connection) {
		this.connection = new NoCloseConnection(connection);
	}

	@Override
	public boolean releaseConnection(Connection connection) {
		return false;
	}

	@Override
	public void setFirstConnectionInitializer(Consumer<Connection> connectionInitializer) {

	}

	@Override
	public void close() throws Exception {
		// ignore - connection is closed outside
	}

	@Override
	public Connection get() {
		return connection;
	}
}
