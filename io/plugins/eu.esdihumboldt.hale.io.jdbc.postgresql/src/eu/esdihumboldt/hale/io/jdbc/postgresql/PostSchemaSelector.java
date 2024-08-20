
/*
 * Copyright (c) 2016 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.jdbc.postgresql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.io.jdbc.extension.SchemaSelector;

/**
 * PostgreSQL Schema Selector
 *
 * @author Arun
 */
public class PostSchemaSelector implements SchemaSelector {

	private static final ALogger log = ALoggerFactory.getLogger(PostSchemaSelector.class);

	/**
	 *
	 * @see eu.esdihumboldt.hale.io.jdbc.extension.SchemaSelector#getSchemas(java.sql.Connection)
	 */
	@Override
	public List<String> getSchemas(Connection connection) throws SQLException {
		List<String> schemas = new ArrayList<String>();
		Statement statemnt = null;
		String sql = "select schema_name from information_schema.schemata;";
		ResultSet rs;
		try {
			statemnt = connection.createStatement();
			rs = statemnt.executeQuery(sql);
			while (rs.next()) {
				schemas.add(rs.getString("schema_name"));
			}

			// return after apply filteration
			return applyFilter(schemas);

		} finally {
			if (statemnt != null) {
				try {
					statemnt.close();
				} catch (SQLException e) {
					log.error(e.getMessage(), e);
				}
			}
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					log.error(e.getMessage(), e);
				}
			}
		}

	}

	private List<String> applyFilter(List<String> list) {

		List<String> filteredList = new ArrayList<String>();

		for (String value : list) {
			if (!(value.equalsIgnoreCase("information_schema") || value.startsWith("pg_"))) {
				filteredList.add(value);
			}
		}

		return filteredList;

	}
}
