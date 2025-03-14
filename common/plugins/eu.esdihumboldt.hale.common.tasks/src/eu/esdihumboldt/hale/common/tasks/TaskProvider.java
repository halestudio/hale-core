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
package eu.esdihumboldt.hale.common.tasks;

/**
 * Task provider interface
 *
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 */
public interface TaskProvider {

	/**
	 * Register the task types provided by this task provider. This method must be
	 * called before calling {@link #activate(TaskService)}
	 *
	 * @param taskRegistry the task type registry to register the types at
	 */
	public void registerTaskTypes(TaskRegistry taskRegistry);

	/**
	 * Initialize the task provider. This method is called after registering the
	 * task types
	 *
	 * @param taskService the task service
	 */
	public void activate(TaskService taskService);
}
