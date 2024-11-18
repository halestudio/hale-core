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
package eu.esdihumboldt.hale.common.style.io;

import org.geotools.api.style.Style;

import eu.esdihumboldt.hale.common.core.io.ImportProvider;

/**
 * Provides support for reading styles.
 *
 * @author Simon Templer
 */
public interface StyleReader extends ImportProvider {

	/**
	 * Get the styles that have been read.
	 *
	 * @return the styles
	 */
	public Style[] getStyles();

}
