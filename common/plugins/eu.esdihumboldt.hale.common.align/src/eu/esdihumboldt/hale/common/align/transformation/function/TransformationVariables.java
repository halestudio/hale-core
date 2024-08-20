/*
 * Copyright (c) 2016 Data Harmonisation Panel
 * 
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Data Harmonisation Panel <http://www.dhpanel.eu>
 */

package eu.esdihumboldt.hale.common.align.transformation.function;

import eu.esdihumboldt.hale.common.core.io.Value;

/**
 * Transformation variables for use in transformation functions.
 * 
 * @author Simon Templer
 */
public interface TransformationVariables {

	/**
	 * Get the value for a given variable name and scope.
	 * 
	 * @param scope the variable scope
	 * @param name the variable name
	 * @return the variable value or {@link Value#NULL}
	 */
	public Value getVariable(TransformationVariableScope scope, String name);

	/**
	 * Replace variable references in a String by variable values.
	 * 
	 * @param input the input string
	 * @return the input string w/ variable references replaced by values, if
	 *         present
	 */
	default public String replaceVariables(String input) {
		return replaceVariables(input, false);
	}

	/**
	 * Replace variable references in a String by variable values.
	 * 
	 * @param input the input string
	 * @param failUnresolved <code>true</code> if the replacement should fail with
	 *            an exception if a variable was identifier but cannot be resolved
	 * @return the input string w/ variable references replaced by values
	 * @throws IllegalStateException if a variable cannot be resolved and
	 *             failUnresolved was passed as <code>true</code>
	 */
	public String replaceVariables(String input, boolean failUnresolved)
			throws IllegalStateException;

	/**
	 * Replace variable references in a string-represented value by variable values.
	 * 
	 * @param input the input value
	 * @return the input value w/ variable references replaced by values, if
	 *         applicable
	 */
	default public Value replaceVariables(Value input) {
		if (input.isSimple()) {
			String strValue = input.as(String.class);
			if (strValue != null) {
				String replaced = replaceVariables(strValue);
				if (!strValue.equals(replaced)) {
					return Value.of(replaced);
				}
			}
		}

		return input;
	}

}
