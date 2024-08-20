
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
package eu.esdihumboldt.hale.io.jdbc.msaccess;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.MessageFormat;

import eu.esdihumboldt.hale.common.core.io.supplier.LocatableInputSupplier;
import eu.esdihumboldt.hale.io.jdbc.JDBCInstanceReader;
import net.ucanaccess.jdbc.UcanaccessConnection;

/**
 * Reads instances from MsAccess Database
 *
 * @author Arun
 */
public class MsAccessInstanceReader extends JDBCInstanceReader {

	private static final String UTF8 = "UTF-8";

	private URI jdbcUri;

	/**
	 * Default Constructor
	 */
	public MsAccessInstanceReader() {
		super();
	}

	@Override
	public void setSource(LocatableInputSupplier<? extends InputStream> source) {
		MsAccessJdbcIOSupplier inputSource = new MsAccessJdbcIOSupplier(
				new File(MsAccessURIBuilder.getDatabase(source.getLocation())));
		jdbcUri = inputSource.getLocation();
		super.setSource(inputSource);
	}

	/**
	 * @see eu.esdihumboldt.hale.io.jdbc.JDBCInstanceReader#testConnection()
	 */
	@Override
	protected boolean testConnection() throws SQLException {
		// Ms Access database does support Select 1 if it is run in MsAccess
		// Editor but not through UCanAccess Library.
		return true;
	}

	@Override
	public UcanaccessConnection getConnection() throws SQLException {

		String user = getParameter(PARAM_USER).as(String.class);
		String password = getParameter(PARAM_PASSWORD).as(String.class);

		return (UcanaccessConnection) DriverManager.getConnection(getDecodedURI(), user, password);
	}

	private String getDecodedURI() {
		try {
			return URLDecoder.decode(this.jdbcUri.toString(), UTF8);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(
					MessageFormat.format("Encoding \"{0}\" is not supported", UTF8));
		}
	}

}
