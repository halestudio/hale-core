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
package eu.esdihumboldt.hale.io.html.svg

import static org.junit.Assert.*

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

import org.junit.Test

import eu.esdihumboldt.cst.test.TransformationExample
import eu.esdihumboldt.cst.test.TransformationExamples
import eu.esdihumboldt.hale.common.core.io.report.IOReport
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchemaSpace
import eu.esdihumboldt.hale.io.html.svg.mapping.MappingExporter
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for {@link MappingExporter}.
 *
 * @author Simon Templer
 */
@CompileStatic
class MappingExporterTest extends AbstractPlatformTest {

	/**
	 * Test creating a mapping documentation file based on
	 * {@link TransformationExamples#PROPERTY_JOIN}.
	 */
	@Test
	void testExportPropertyJoin() {
		// load example project
		TransformationExample ex = TransformationExamples.getExample(TransformationExamples.PROPERTY_JOIN)

		Path file = Files.createTempFile('mapping', '.html')
		try {
			MappingExporter exporter = new MappingExporter()
			exporter.alignment = ex.alignment
			exporter.sourceSchema = new DefaultSchemaSpace().addSchema(ex.sourceSchema)
			exporter.targetSchema = new DefaultSchemaSpace().addSchema(ex.targetSchema)
			exporter.target = new FileIOSupplier(file.toFile())
			//TODO also retrieve project information?

			IOReport rep = exporter.execute(null)
			assertTrue 'Export failed', rep.isSuccess()
			assertEquals 0, rep.errors.size()
		} finally {
			Files.delete(file)
		}
	}
}
