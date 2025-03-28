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
package eu.esdihumboldt.hale.common.core.io;

import java.io.OutputStream;

import eu.esdihumboldt.hale.common.core.io.supplier.LocatableOutputSupplier;

/**
 * Base interface for export providers
 *
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 * @since 2.2
 */
public interface ExportProvider extends IOProvider {

	/**
	 * The configuration parameter name for the target URI
	 */
	public static final String PARAM_TARGET = "target";

	/**
	 * Set the export target
	 *
	 * @param target the target output supplier
	 */
	public void setTarget(LocatableOutputSupplier<? extends OutputStream> target);

	/**
	 * Get the export target
	 *
	 * @return the target output supplier
	 */
	public LocatableOutputSupplier<? extends OutputStream> getTarget();

}
