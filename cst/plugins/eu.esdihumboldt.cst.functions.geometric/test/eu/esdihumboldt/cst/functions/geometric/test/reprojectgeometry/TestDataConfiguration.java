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

/**
 * Test data configurations for geometry transformation tests.
 */
@SuppressWarnings("javadoc")
public enum TestDataConfiguration {

	REPROJECT("/testdata/reproject/geom-gml2.xsd", "/testdata/reproject/geom-gml2.xsd",
			"/testdata/reproject/reproject.halex.alignment.xml",
			"/testdata/reproject/sample-point-gml2.xml");

	private String sourceSchema;
	private String targetSchema;
	private String alignment;
	private String sourceData;

	private TestDataConfiguration(String sourceSchema, String targetSchema, String alignment,
			String sourceData) {
		this.sourceSchema = sourceSchema;
		this.targetSchema = targetSchema;
		this.alignment = alignment;
		this.sourceData = sourceData;
	}

	public String getAlignment() {
		return alignment;
	}

	public String getSourceSchema() {
		return sourceSchema;
	}

	public String getTargetSchema() {
		return targetSchema;
	}

	public String getSourceData() {
		return sourceData;
	}

}
