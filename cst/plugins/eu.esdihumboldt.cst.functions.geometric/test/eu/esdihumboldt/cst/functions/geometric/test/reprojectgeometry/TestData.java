
/*
 * Copyright (c) 2024 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.cst.functions.geometric.test.reprojectgeometry;

import java.net.URI;
import java.net.URISyntaxException;

import eu.esdihumboldt.cst.test.TransformationExampleImpl;

@SuppressWarnings("javadoc")
public class TestData extends TransformationExampleImpl {

	public TestData(TestDataConfiguration configuration) throws URISyntaxException {
		super(toLocalURI(configuration.getSourceSchema()),
				toLocalURI(configuration.getTargetSchema()),
				toLocalURI(configuration.getAlignment()), toLocalURI(configuration.getSourceData()),
				null, null, null);
	}

	private static URI toLocalURI(String location) throws URISyntaxException {
		return TestData.class.getResource(location).toURI();
	}

}
