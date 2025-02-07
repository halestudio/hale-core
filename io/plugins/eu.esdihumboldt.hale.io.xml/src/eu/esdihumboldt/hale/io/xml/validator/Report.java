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
package eu.esdihumboldt.hale.io.xml.validator;

import java.util.List;

import org.xml.sax.SAXParseException;

/**
 * A validation report
 *
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 * @version $Id$
 */
public interface Report {

	/**
	 * Get if the XML document is valid
	 *
	 * @return if the XML document is valid
	 */
	public boolean isValid();

	/**
	 * @return the warnings
	 */
	public List<SAXParseException> getWarnings();

	/**
	 * @return the errors
	 */
	public List<SAXParseException> getErrors();

}
