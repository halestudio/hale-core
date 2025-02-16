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
package eu.esdihumboldt.hale.app.cli.commands.alignment;

import groovy.cli.picocli.OptionAccessor
import groovy.transform.CompileStatic

import eu.esdihumboldt.hale.common.cli.project.AbstractProjectEnvironmentCommand
import eu.esdihumboldt.hale.common.core.io.report.IOReport
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier
import eu.esdihumboldt.hale.common.core.report.ReportHandler
import eu.esdihumboldt.hale.common.headless.impl.ProjectTransformationEnvironment
import eu.esdihumboldt.hale.io.html.svg.mapping.json.JsonMappingExporter
import eu.esdihumboldt.util.cli.CommandContext

/**
 * Command for exporting JSON alignment representation.
 *
 * @author Simon Templer
 */
@CompileStatic
class ExportJsonCommand extends AbstractProjectEnvironmentCommand {

	@Override
	boolean runForProject(ProjectTransformationEnvironment projectEnv, URI projectLocation,
			OptionAccessor options, CommandContext context, ReportHandler reports) {
		// configure writer
		JsonMappingExporter writer = new JsonMappingExporter()
		writer.alignment = projectEnv.alignment
		writer.projectInfo = projectEnv.project
		writer.projectLocation = projectLocation
		writer.serviceProvider = projectEnv
		writer.sourceSchema = projectEnv.sourceSchema
		writer.targetSchema = projectEnv.targetSchema

		//XXX only supported for files right now
		File projectFile = new File(projectLocation)

		// derive file name for HTML file
		File mappingTable = new File(projectFile.parentFile, projectFile.name + '.json')

		writer.target = new FileIOSupplier(mappingTable)

		IOReport report = writer.execute(null)
		reports.publishReport(report)

		report.isSuccess() && !report.errors
	}

	final String shortDescription = 'Generate JSON represenation of hale alignments'
}
