
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
package eu.esdihumboldt.hale.common.core.io.project.extension;

import de.fhg.igd.eclipse.util.extension.simple.IdentifiableExtension.Identifiable;
import eu.esdihumboldt.hale.common.core.io.project.model.ProjectFile;

/**
 * Factory interface for {@link ProjectFile}s
 *
 * @author Simon Templer
 */
public interface ProjectFileFactory extends Identifiable {

	/**
	 * Create a project file
	 *
	 * @return the project file instance
	 */
	public ProjectFile createProjectFile();

}
