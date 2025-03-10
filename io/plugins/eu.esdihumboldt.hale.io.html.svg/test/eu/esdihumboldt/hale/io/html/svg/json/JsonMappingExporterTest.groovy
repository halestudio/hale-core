/*
 * Copyright (c) 2016 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.html.svg.json

import static org.junit.Assert.*

import groovy.json.JsonSlurper

import java.nio.file.Files
import java.nio.file.Path

import org.junit.Test
import org.osgi.framework.Version

import eu.esdihumboldt.cst.test.TransformationExample
import eu.esdihumboldt.cst.test.TransformationExamples
import eu.esdihumboldt.hale.common.core.io.Value
import eu.esdihumboldt.hale.common.core.io.project.ProjectInfo
import eu.esdihumboldt.hale.common.core.io.report.IOMessage
import eu.esdihumboldt.hale.common.core.io.report.IOReport
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchemaSpace
import eu.esdihumboldt.hale.io.html.svg.mapping.json.JsonMappingExporter
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for {@link JsonMappingExporter}.
 *
 * @author Simon Templer
 */
class JsonMappingExporterTest extends AbstractPlatformTest {

	/**
	 * Test creating a mapping documentation file based on
	 * {@link TransformationExamples#PROPERTY_JOIN}.
	 */
	@Test
	void testExportPropertyJoin() {
		// load example project
		TransformationExample ex = TransformationExamples.getExample(TransformationExamples.PROPERTY_JOIN)

		Path file = Files.createTempFile('mapping', '.json')
		try {
			JsonMappingExporter exporter = new JsonMappingExporter()
			exporter.alignment = ex.alignment
			exporter.sourceSchema = new DefaultSchemaSpace().addSchema(ex.sourceSchema)
			exporter.targetSchema = new DefaultSchemaSpace().addSchema(ex.targetSchema)
			exporter.target = new FileIOSupplier(file.toFile())

			exporter.projectInfo = new ProjectInfo() {
						String name = 'Test-Projekt'
						Version haleVersion = Version.parseVersion('1.0.0')
						String author = 'me'
						String description = '?'
						Date created = new Date()
						Date modified = new Date()
						@Override
						Value getSetting(String name) {
							Value.NULL
						}
					}

			IOReport rep = exporter.execute(null)

			rep.errors.forEach { IOMessage m ->
				println m.message
				if (m.stackTrace) {
					println m.stackTrace
				}
			}

			assertTrue 'Export failed', rep.isSuccess()
			assertEquals 0, rep.errors.size()

			def c = new JsonSlurper().parse(file.toFile())

			def cells = c.cells as List
			assertEquals 8, cells.size()

			def export = c.export as Map
			assertNotNull(export)
			assertNotNull(export.timestamp)

			def project = c.project as Map
			assertNotNull(project)
			assertEquals('Test-Projekt', project.name)
			assertNotNull(project.description)
			assertNotNull(project.author)
			assertNotNull(project.created)
			assertNotNull(project.modified)
			assertNotNull(project.haleVersion)
		} finally {
			Files.delete(file)
		}
	}
}
