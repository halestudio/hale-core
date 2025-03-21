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
package eu.esdihumboldt.hale.common.convert.core;

import java.util.Calendar;

import org.joda.time.DateTime;
import org.springframework.core.convert.converter.Converter;

/**
 * Convert a {@link Calendar} to a {@link DateTime}.
 *
 * @author Simon Templer
 */
public class CalendarToJodaDateTimeConverter implements Converter<Calendar, DateTime> {

	@Override
	public DateTime convert(Calendar source) {
		if (source == null) {
			return null;
		}
		return new DateTime(source);
	}

}
