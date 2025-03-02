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
package eu.esdihumboldt.hale.common.core.io.impl;

import eu.esdihumboldt.hale.common.core.io.ProgressIndicator;

/**
 * Dummy progress indicator ignoring everything you tell him.
 *
 * @author Simon Templer
 */
public class NullProgressIndicator implements ProgressIndicator {

	@Override
	public void begin(String taskName, int totalWork) {
		// ignore
	}

	@Override
	public void setCurrentTask(String taskName) {
		// ignore
	}

	@Override
	public void advance(int workUnits) {
		// ignore
	}

	@Override
	public boolean isCanceled() {
		return false;
	}

	@Override
	public void end() {
		// ignore
	}

}
