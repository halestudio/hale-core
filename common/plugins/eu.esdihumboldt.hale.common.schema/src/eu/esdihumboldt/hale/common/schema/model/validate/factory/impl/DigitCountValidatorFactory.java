/*
 * Copyright (c) 2014 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.schema.model.validate.factory.impl;

import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.core.io.ValueProperties;
import eu.esdihumboldt.hale.common.schema.model.validate.factory.ValidatorFactory;
import eu.esdihumboldt.util.validator.DigitCountValidator;
import eu.esdihumboldt.util.validator.DigitCountValidator.Type;
import eu.esdihumboldt.util.validator.Validator;

/**
 * Converts {@link DigitCountValidator}s to {@link Value}s and vice versa.
 *
 * @author Simon Templer
 */
public class DigitCountValidatorFactory implements ValidatorFactory<DigitCountValidator> {

	/**
	 * Name of the property holding the length to compare against.
	 */
	private static final String P_LENGTH = "length";

	/**
	 * Name of the property holding the mode how to compare the length.
	 */
	private static final String P_TYPE = "compare";

	@Override
	public Value store(DigitCountValidator validator) throws Exception {
		ValueProperties props = new ValueProperties();

		props.put(P_TYPE, Value.of(validator.getType().name()));
		props.put(P_LENGTH, Value.of(validator.getLength()));

		return props.toValue();
	}

	@Override
	public Validator restore(Value value) throws Exception {
		ValueProperties props = value.as(ValueProperties.class);

		Type type = Type.valueOf(props.getSafe(P_TYPE).as(String.class));
		int length = props.getSafe(P_LENGTH).as(Integer.class);

		return new DigitCountValidator(type, length);
	}

}
