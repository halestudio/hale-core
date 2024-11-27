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

/**
 * Decorator for {@link Connection}s that ignores calls to {@link #close()}.
 */
public class NoCloseConnection extends ConnectionDecorator {

	public NoCloseConnection(Connection connection) {
		super(connection);
	}

	@Override
	public void close() {
		// ignore
	}
}
