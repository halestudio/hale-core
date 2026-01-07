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
package eu.esdihumboldt.hale.common.core.io.impl;

import org.springframework.core.convert.ConversionService;
import org.w3c.dom.Element;

import eu.esdihumboldt.hale.common.core.HalePlatform;
import eu.esdihumboldt.hale.common.core.io.Value;

/**
 * Simple value represented as string.
 *
 * @author Simon Templer
 */
public class StringBuilderValue extends Value {

	private static final long serialVersionUID = 6992194329380349404L;

	private final StringBuilder value;

	/**
	 * Create a new string value.
	 *
	 * @param value the value string
	 */
	public StringBuilderValue(StringBuilder value) {
		super();
		this.value = value;
	}

	/**
	 * Create a new string value from an arbitrary value. The value is converted to
	 * string using the {@link ConversionService} or with <code>toString()</code>.
	 *
	 * @param value the value
	 */
	public StringBuilderValue(Object value) {
		super();

		if (value == null) {
			this.value = null;
		}
		else {
			String tmpValue = null;
			ConversionService cs = HalePlatform.getService(ConversionService.class);
			if (cs != null) {
				try {
					tmpValue = cs.convert(value.toString(), String.class);
				} catch (Exception e) {
					System.out.println("+++++ StringBuilderValue: conversion failed for "
							+ value.getClass().getName());
				}
			}

			if (tmpValue == null) {
				tmpValue = value.toString();
			}

			this.value = new StringBuilder(tmpValue);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T as(Class<T> expectedType) {
		if (value == null) {
			return null;
		}

		if (expectedType.isAssignableFrom(String.class)) {
			return (T) value;
		}

		// TODO handle primitive wrappers even w/o conversion service?

		// conversion using service
		ConversionService cs = HalePlatform.getService(ConversionService.class);
		if (cs != null) {
			try {
				return cs.convert(value, expectedType);
			} catch (Exception e) {
				// ignore
			}
		}

		return null;
	}

	@Override
	public StringBuilder getValue() {
		return value;
	}

	@Override
	public boolean isRepresentedAsDOM() {
		return false;
	}

	@Override
	public Element getDOMRepresentation() {
		return null;
	}

	@Override
	public String getStringRepresentation() {
		return value.toString();
	}

	@Override
	public boolean isEmpty() {
		return value == null || value.isEmpty();
	}

	@Override
	public String toString() {
		return getValue().toString();
	}

	@Override
	public <T> T as(Class<T> expectedType, T defValue) {
		T value = as(expectedType);
		if (value == null)
			return defValue;
		return value;
	}

}
