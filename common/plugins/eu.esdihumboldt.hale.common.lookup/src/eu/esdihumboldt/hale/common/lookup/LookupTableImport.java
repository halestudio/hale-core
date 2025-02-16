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
package eu.esdihumboldt.hale.common.lookup;

import eu.esdihumboldt.hale.common.core.io.ImportProvider;

/**
 * Import provider interface for lookup tables.
 *
 * @author Simon Templer
 */
public interface LookupTableImport extends ImportProvider {

	/**
	 * Name of the parameter specifying the lookup table name.
	 */
	public static final String PARAM_NAME = "name";

	/**
	 * Name of the parameter specifying the lookup table description.
	 */
	public static final String PARAM_DESCRIPTION = "description";

	/**
	 * Set the human readable name for the lookup table to import.
	 *
	 * @param name the lookup table name
	 */
	public void setName(String name);

	/**
	 * Set the lookup table description.
	 *
	 * @param description the lookup table description, may be <code>null</code>
	 */
	public void setDescription(String description);

	/**
	 * Get the loaded lookup table.
	 *
	 * @return the lookup table
	 */
	public LookupTableInfo getLookupTable();

}
