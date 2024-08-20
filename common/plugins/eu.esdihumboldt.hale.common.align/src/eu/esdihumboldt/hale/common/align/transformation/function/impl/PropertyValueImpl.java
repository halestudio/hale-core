
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

import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;

import eu.esdihumboldt.hale.common.align.model.impl.PropertyEntityDefinition;
import eu.esdihumboldt.hale.common.align.transformation.function.PropertyValue;
import eu.esdihumboldt.hale.common.convert.ConversionServiceNotAvailableException;
import eu.esdihumboldt.hale.common.core.HalePlatform;
import eu.esdihumboldt.hale.common.instance.model.Instance;

/**
 * Default {@link PropertyValue} implementation.
 *
 * @author Simon Templer
 */
public class PropertyValueImpl implements PropertyValue {

	private final Object value;

	private final PropertyEntityDefinition property;

	/**
	 * Create a property value associated with its definition
	 *
	 * @param value the property value
	 * @param property the property entity definition
	 */
	public PropertyValueImpl(Object value, PropertyEntityDefinition property) {
		super();
		this.value = value;
		this.property = property;
	}

	/**
	 * @see PropertyValue#getValue()
	 */
	@Override
	public Object getValue() {
		return value;
	}

	/**
	 * @see PropertyValue#getValueAs(Class)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> T getValueAs(Class<T> type) throws ConversionException {
		if (value == null) {
			return null;
		}

		if (type.isAssignableFrom(value.getClass())) {
			return (T) value;
		}

		if (value instanceof Instance) {
			return (T) ((Instance) value).getValue();
		}

		ConversionService cs = HalePlatform.getService(ConversionService.class);
		if (cs == null) {
			throw new ConversionServiceNotAvailableException();
		}
		else {
			return cs.convert(value, type);
		}
	}

	/**
	 * @see PropertyValue#getProperty()
	 */
	@Override
	public PropertyEntityDefinition getProperty() {
		return property;
	}

}
