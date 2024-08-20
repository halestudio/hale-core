
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
package eu.esdihumboldt.hale.common.instance.extension.metadata;

/**
 * Interface for Actions used on meta data
 *
 * @author Sebastian Reinhardt
 */
public interface MetadataAction {

	/**
	 * executes the action
	 *
	 * @param key the metadata key
	 * @param value the metadata value
	 * @throws Exception the execution may throw an exception
	 */
	public void execute(Object key, Object value) throws Exception;

}
