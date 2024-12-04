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
package eu.esdihumboldt.hale.io.jdbc.spatialite;

import java.net.URI;

import eu.esdihumboldt.hale.io.jdbc.extension.URIBuilder;

/**
 * Create JDBC URIs for SQLite/SpatiaLite.
 *
 * @author Stefano Costa, GeoSolutions
 */
public class SpatiaLiteURIBuilder implements URIBuilder {

	private static final String PREFIX = "jdbc:sqlite:";

	/**
	 * {@code host} parameter is ignored.
	 */
	@Override
	public URI createJdbcUri(String host, String database) {
		if (database == null || database.isEmpty()) {
			throw new IllegalArgumentException("A database name must be provided");
		}

		return URI.create(PREFIX + database);
	}

	/**
	 * @param jdbcUri the JDBC URI
	 * @return the file system path to the SpatiaLite database
	 */
	public static String getDatabase(URI jdbcUri) {
		if (jdbcUri == null) {
			throw new IllegalArgumentException("JDBC URI must be provided");
		}

		return jdbcUri.toString().substring(PREFIX.length());
	}

}
