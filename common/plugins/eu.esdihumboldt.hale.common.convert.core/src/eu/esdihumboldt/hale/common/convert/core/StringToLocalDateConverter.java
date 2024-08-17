
/*
 * Copyright (c) 2020 wetransform GmbH
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Convert a {@link String} to a {@link LocalDate}.
 *
 * @author Simon Templer
 */
public class StringToLocalDateConverter extends AbstractStringToDateTimeTypeConverter<LocalDate> {

	private static DateTimeFormatter SLASH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	/**
	 * @see eu.esdihumboldt.hale.common.convert.core.AbstractStringToDateTimeTypeConverter#parse(java.lang.String)
	 */
	@Override
	protected LocalDate parse(String source) {
		try {
			return LocalDate.parse(source);
		} catch (DateTimeParseException e) {
			// try alternative format
			return LocalDate.parse(source, SLASH_FORMATTER);
		}
	}

}
