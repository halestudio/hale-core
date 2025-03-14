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
package eu.esdihumboldt.cst.functions.string;

/**
 * Date extraction constants
 *
 * @author Kevin Mais
 */
public interface DateExtractionFunction {

	/**
	 * the date extraction function Id
	 */
	public static final String ID = "eu.esdihumboldt.cst.functions.string.dateextraction";

	/**
	 * Name of the parameter specifying the date format of the source entity.<br>
	 * See the function definition on
	 * <code>eu.esdihumboldt.hale.common.align</code>.
	 */
	public static final String PARAMETER_DATE_FORMAT = "dateFormat";

	/**
	 * Name of the parameter specifying whether the interpretation of the date and
	 * time of SimpleDateFormat object is to be lenient or not. With lenient
	 * interpretation, a date such as "February 942, 1996" will be treated as being
	 * equivalent to the 941st day after February 1, 1996. With strict (non-lenient)
	 * interpretation, such dates will cause an exception to be thrown. The default
	 * is lenient.
	 */
	public static final String PARAMETER_LENIENCY = "leniency";

}
