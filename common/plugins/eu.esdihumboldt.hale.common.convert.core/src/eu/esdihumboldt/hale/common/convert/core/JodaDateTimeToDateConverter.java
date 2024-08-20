
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
package eu.esdihumboldt.hale.common.convert.core;

import java.util.Date;

import org.joda.time.DateTime;
import org.springframework.core.convert.converter.Converter;

/**
 * Converts a {@link DateTime} to a {@link Date}.
 *
 * @author Simon Templer
 */
public class JodaDateTimeToDateConverter implements Converter<DateTime, Date> {

	@Override
	public Date convert(DateTime source) {
		if (source == null) {
			return null;
		}
		return source.toDate();
	}

}
