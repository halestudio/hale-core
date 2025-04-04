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

import java.sql.Date;
import java.time.Instant;

import org.springframework.core.convert.converter.Converter;

/**
 * Convert a {@link Instant} to an SQL {@link Date}.
 *
 * @author Simon Templer
 */
public class InstantToSqlDateConverter implements Converter<Instant, Date> {

	@Override
	public Date convert(Instant source) {
		if (source == null) {
			return null;
		}
		return new Date(source.toEpochMilli());
	}

}
