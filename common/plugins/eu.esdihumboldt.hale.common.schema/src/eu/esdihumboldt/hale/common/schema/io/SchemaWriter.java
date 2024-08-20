
/*
 * Copyright (c) 2012 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.schema.io;

import eu.esdihumboldt.hale.common.core.io.ExportProvider;
import eu.esdihumboldt.hale.common.schema.model.SchemaSpace;

/**
 * Provides support for writing schemas.
 *
 * @author Simon Templer
 * @since 2.7
 */
public interface SchemaWriter extends ExportProvider {

	/**
	 * Set the schemas to write.
	 *
	 * @param schemas the schema space
	 */
	public void setSchemas(SchemaSpace schemas);

}
