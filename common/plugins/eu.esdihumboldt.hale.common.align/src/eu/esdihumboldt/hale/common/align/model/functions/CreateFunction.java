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
package eu.esdihumboldt.hale.common.align.model.functions;

/**
 * Create function constants.
 *
 * @author Simon Templer
 */
public interface CreateFunction {

	/**
	 * The function identifier.
	 */
	public static final String ID = "eu.esdihumboldt.hale.align.create";

	/**
	 * Name of the parameter representing the number of instances to create.
	 */
	public static final String PARAM_NUMBER = "number";

}
