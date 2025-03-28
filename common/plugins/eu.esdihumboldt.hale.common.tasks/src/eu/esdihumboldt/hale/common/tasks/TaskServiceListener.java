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
 * Dedicated listener for {@link TaskService}s
 *
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 */
public interface TaskServiceListener {

	/**
	 * Called when tasks have been added
	 *
	 * @param tasks the tasks that have been added
	 */
	void tasksAdded(Iterable<Task<?>> tasks);

	/**
	 * Called when tasks have been removed
	 *
	 * @param tasks the tasks that have been removed
	 */
	void tasksRemoved(Iterable<Task<?>> tasks);

	/**
	 * Called when the user data of a task has changed
	 *
	 * @param task the resolved task
	 */
	void taskUserDataChanged(ResolvedTask<?> task);

}
