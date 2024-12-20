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
package eu.esdihumboldt.hale.common.core.io.internal;

import eu.esdihumboldt.hale.common.core.io.Value;
import groovy.lang.DelegatingMetaClass;
import groovy.lang.MetaClass;

/**
 * Adds implicit groovy conversions to {@link Value} via the asType() method.
 *
 * @author Simon Templer
 */
public class AsValueMetaClass extends DelegatingMetaClass {

	/**
	 * @see DelegatingMetaClass#DelegatingMetaClass(Class)
	 */
	public AsValueMetaClass(@SuppressWarnings("rawtypes") Class theClass) {
		super(theClass);
		initialize();
	}

	/**
	 * @see DelegatingMetaClass#DelegatingMetaClass(MetaClass)
	 */
	public AsValueMetaClass(MetaClass delegate) {
		super(delegate);
		initialize();
	}

	@Override
	public Object invokeMethod(Object object, String methodName, Object[] arguments) {
		if (arguments != null && arguments.length == 1 && "asType".equals(methodName)
				&& Value.class.equals(arguments[0])) {
			// call to asType(Value.class)
			if (object == null) {
				return Value.NULL;
			}
			else if (object instanceof Value) {
				// return the value itself if it is already a value
				return object;
			}
			else if (object instanceof String) {
				return Value.of((String) object);
			}
			else if (object instanceof Boolean) {
				return Value.of((Boolean) object);
			}
			else if (object instanceof Number) {
				return Value.of((Number) object);
			}
			else {
				return Value.of(object);
			}
		}
		return super.invokeMethod(object, methodName, arguments);
	}

}
