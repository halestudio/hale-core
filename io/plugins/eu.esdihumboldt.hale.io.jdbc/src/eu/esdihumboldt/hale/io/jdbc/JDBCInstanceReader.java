
/*
 * Copyright (c) 2013 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.HashMap;
import java.util.Map;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.core.io.IOProviderConfigurationException;
import eu.esdihumboldt.hale.common.core.io.ProgressIndicator;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.report.IOReporter;
import eu.esdihumboldt.hale.common.core.io.report.impl.IOMessageImpl;
import eu.esdihumboldt.hale.common.instance.io.impl.AbstractInstanceReader;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.ext.impl.PerTypeInstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.impl.MultiInstanceCollection;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.io.jdbc.constraints.DatabaseTable;
import eu.esdihumboldt.hale.io.jdbc.constraints.SQLQuery;

/**
 * Reads instances from a JDBC database.
 *
 * @author Simon Templer
 */
public class JDBCInstanceReader extends AbstractInstanceReader
		implements JDBCConstants, JDBCProvider {

	private MultiInstanceCollection collection;
	private static final ALogger log = ALoggerFactory.getLogger(JDBCInstanceReader.class);

	/**
	 * Default constructor.
	 */
	public JDBCInstanceReader() {
		super();

		addSupportedParameter(PARAM_PASSWORD);
		addSupportedParameter(PARAM_USER);
	}

	@Override
	public InstanceCollection getInstances() {
		return collection;
	}

	@Override
	public boolean isCancelable() {
		return false;
	}

	/**
	 * To get Connection. Override this to load the customized connection
	 *
	 * @return Connection object after loading driver.
	 * @throws SQLException if connection could not be made.
	 */
	@Override
	public Connection getConnection() throws SQLException {
		return JDBCConnection.getConnection(this);
	}

	/**
	 * To test Connection. Override this to load the customized connection testing
	 *
	 * @return true if connection succeeded or <code>null</code>
	 * @throws SQLException if connection could not be made.
	 */
	protected boolean testConnection() throws SQLException {

		// test connection
		Connection connection = getConnection();
		try {
			connection.createStatement().executeQuery("SELECT 1;");
		} catch (SQLSyntaxErrorException e) {
			log.warn(
					"SELECT 1 query is not supported by Oracle database. Instead uses SELECT 1 from dual.");
			connection.createStatement().executeQuery("SELECT 1 from dual");
		} finally {
			// Database connection must be close.
			connection.close();
		}
		return true;
	}

	@Override
	protected IOReport execute(ProgressIndicator progress, IOReporter reporter)
			throws IOProviderConfigurationException, IOException {
		progress.begin("Configure database connection", ProgressIndicator.UNKNOWN);
		try {
			testConnection();

			String user = getParameter(PARAM_USER).as(String.class);
			String password = getParameter(PARAM_PASSWORD).as(String.class);

			Map<TypeDefinition, InstanceCollection> collections = new HashMap<>();

			// only load instances for mapping relevant types
			for (TypeDefinition type : getSourceSchema().getMappingRelevantTypes()) {
				// TODO test if table exists in DB?

				// check constraint if a Database table or not
				if (type.getConstraint(DatabaseTable.class).isTable()) {
					collections.put(type, new JDBCTableCollection(type, getSource().getLocation(),
							user, password, getCrsProvider(), getServiceProvider()) {

						// To provide extensibility for getting customized
						// database connection for
						// Instance reading.
						@Override
						protected Connection createConnection() throws SQLException {
							return JDBCInstanceReader.this.getConnection();
						}

					});
				}
				// also support SQL query types
				// FIXME any way to determine if this is the correct target
				// database?
				else if (type.getConstraint(SQLQuery.class).hasQuery()) {
					collections.put(type, new JDBCTableCollection(type, getSource().getLocation(),
							user, password, getCrsProvider(), getServiceProvider()) {

						// To provide extensibility for getting customized
						// database connection for
						// Instance reading.
						@Override
						protected Connection createConnection() throws SQLException {
							return JDBCInstanceReader.this.getConnection();
						}

					});
				}

			}

			collection = new PerTypeInstanceCollection(collections);
			reporter.setSuccess(true);
		} catch (Exception e) {
			reporter.error(new IOMessageImpl("Error configuring database connection", e));
			reporter.setSuccess(false);
		} finally {
			progress.end();
		}
		return reporter;
	}

	@Override
	protected String getDefaultTypeName() {
		return "Database";
	}

}
