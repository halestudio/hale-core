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
package eu.esdihumboldt.hale.common.instance.model.impl;

import java.util.Arrays;

import javax.xml.namespace.QName;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ListMultimap;

import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.MutableGroup;
import eu.esdihumboldt.hale.common.schema.model.DefinitionGroup;

/**
 * Group implementation backed by a {@link ListMultimap}.
 *
 * @author Simon Templer
 */
public class DefaultGroup implements MutableGroup {

	private final ListMultimap<QName, Object> properties = ArrayListMultimap.create();

	private final DefinitionGroup definition;

	/**
	 * Create an empty group.
	 *
	 * @param definition the associated definition
	 */
	public DefaultGroup(DefinitionGroup definition) {
		super();
		this.definition = definition;
	}

	/**
	 * Copy constructor. Creates a group based on the properties and values of the
	 * given group.
	 *
	 * @param org the instance to copy
	 */
	public DefaultGroup(Group org) {
		this(org.getDefinition());

		for (QName property : org.getPropertyNames()) {
			setPropertyCopy(property, org.getProperty(property));
		}
	}

	/**
	 * @see Group#getProperty(QName)
	 */
	@Override
	public Object[] getProperty(QName propertyName) {
		return properties.get(propertyName).toArray();
	}

	/**
	 * @see Group#getPropertyNames()
	 */
	@Override
	public Iterable<QName> getPropertyNames() {
		return properties.keySet();
	}

	/**
	 * @see Group#getDefinition()
	 */
	@Override
	public DefinitionGroup getDefinition() {
		return definition;
	}

	/**
	 * @see MutableGroup#addProperty(QName, Object)
	 */
	@Override
	public void addProperty(QName propertyName, Object value) {
		properties.put(propertyName, value);
	}

	/**
	 * @see MutableGroup#setProperty(QName, Object[])
	 */
	@Override
	public void setProperty(QName propertyName, Object... values) {
		properties.replaceValues(propertyName, Arrays.asList(values));
	}

	private void setPropertyCopy(QName propertyName, Object... values) {
		properties.replaceValues(propertyName,
				Collections2.transform(Arrays.asList(values), new Function<Object, Object>() {

					@Override
					public Object apply(Object input) {
						/*
						 * If the input is a Group/Instance. This is to prevent OInstances/OGroups
						 * to live on in a copy.
						 */
						if (input instanceof Instance) {
							return new DefaultInstance((Instance) input);
						}
						if (input instanceof Group) {
							return new DefaultGroup((Group) input);
						}

						return input;
					}
				}));
	}

}
