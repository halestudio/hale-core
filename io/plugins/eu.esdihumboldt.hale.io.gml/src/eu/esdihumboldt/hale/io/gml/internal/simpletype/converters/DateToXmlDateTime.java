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
package eu.esdihumboldt.hale.io.gml.internal.simpletype.converters;

import java.util.Date;

import org.apache.xmlbeans.XmlDateTime;
import org.springframework.core.convert.converter.Converter;

/**
 * Convert {@link Date} to xs:datetime
 */
public class DateToXmlDateTime implements Converter<Date, XmlDateTime> {

	/**
	 * @see Converter#convert(Object)
	 */
	@Override
	public XmlDateTime convert(Date value) {
		XmlDateTime result = XmlDateTime.Factory.newInstance();
		result.setDateValue(value);
		return result;
	}
}
