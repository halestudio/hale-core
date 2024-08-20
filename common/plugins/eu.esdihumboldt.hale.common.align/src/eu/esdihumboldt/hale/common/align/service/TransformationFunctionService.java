
/*
 * Copyright (c) 2015 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.align.service;

import java.util.List;

import eu.esdihumboldt.hale.common.align.extension.transformation.PropertyTransformationFactory;
import eu.esdihumboldt.hale.common.align.extension.transformation.TypeTransformationFactory;

/**
 * Service for resolving transformation functions.
 *
 * @author Simon Templer
 */
public interface TransformationFunctionService {

	/**
	 * Get all transformations for a property function.
	 *
	 * @param functionId the function ID
	 * @return the transformations matching the function
	 */
	List<PropertyTransformationFactory> getPropertyTransformations(final String functionId);

	/**
	 * Get all transformations for a type function.
	 *
	 * @param functionId the function ID
	 * @return the transformations matching the function
	 */
	List<TypeTransformationFactory> getTypeTransformations(final String functionId);

}
