
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
package eu.esdihumboldt.hale.common.align.transformation.function.impl;

import eu.esdihumboldt.hale.common.align.transformation.engine.TransformationEngine;
import eu.esdihumboldt.hale.common.instance.model.Instance;

/**
 * Transformation function base class
 *
 * @param <E> the transformation engine type
 *
 * @author Simon Templer
 */
public abstract class AbstractSingleTypeTransformation<E extends TransformationEngine>
		extends AbstractTypeTransformation<E> {

	/**
	 * Get the source instance.
	 *
	 * @return the source instance
	 */
	public Instance getSourceInstance() {
		return getSource();
	}
}