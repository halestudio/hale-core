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
package eu.esdihumboldt.hale.common.core.io.impl

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import eu.esdihumboldt.hale.common.core.io.ComplexValueJson
import eu.esdihumboldt.hale.common.core.io.Text


/**
 * Converts a {@link Text} to JSON, representing it as a JSON string.
 *
 * @author Simon Templer
 */
class TextTypeJson implements ComplexValueJson<Text, Void> {

	@Override
	Text fromJson(Reader json, Void context) {
		//XXX the following line only works with Groovy 2.3!
		//		String text = new JsonSlurper().parse(json) as String

		// instead create a dummy object
		String text = new JsonSlurper().parseText('{"v":' + json.text + '}').v as String

		new Text(text)
	}

	@Override
	void toJson(Text value, Writer writer) {
		writer << JsonOutput.toJson(value.text)
	}

	@Override
	public Text fromJson(Object json, Void context) {
		return new Text(json as String)
	}

	@Override
	public Object toJson(Text value) {
		return value.text
	}

	@Override
	Class<Void> getContextType() {
		Void
	}
}
