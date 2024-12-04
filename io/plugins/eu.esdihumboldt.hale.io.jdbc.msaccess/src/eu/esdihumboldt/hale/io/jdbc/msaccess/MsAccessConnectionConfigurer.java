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
package eu.esdihumboldt.hale.io.jdbc.msaccess;

import java.sql.Connection;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.io.jdbc.extension.ConnectionConfigurer;
import net.ucanaccess.jdbc.UcanaccessConnection;

/**
 * Enables extension on the given {@link Connection}.
 */
public class MsAccessConnectionConfigurer implements ConnectionConfigurer<UcanaccessConnection> {

	private static final ALogger log = ALoggerFactory.getLogger(MsAccessConnectionConfigurer.class);

	@Override
	public void configureConnection(UcanaccessConnection connection) {

		try {

			// TODO :: Extension to MS access.
		} catch (Exception e) {
			log.warn("Fail to load MS access extension. Please check help", e);
		}

	}

}
