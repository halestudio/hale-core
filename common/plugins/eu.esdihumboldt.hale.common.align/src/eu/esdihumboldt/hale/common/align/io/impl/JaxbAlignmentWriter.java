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
package eu.esdihumboldt.hale.common.align.io.impl;

import java.io.IOException;
import java.io.OutputStream;

import eu.esdihumboldt.hale.common.align.io.impl.internal.generated.AlignmentType;
import eu.esdihumboldt.hale.common.core.io.IOProvider;
import eu.esdihumboldt.hale.common.core.io.IOProviderConfigurationException;
import eu.esdihumboldt.hale.common.core.io.PathUpdate;
import eu.esdihumboldt.hale.common.core.io.ProgressIndicator;
import eu.esdihumboldt.hale.common.core.io.impl.AbstractIOProvider;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.report.IOReporter;
import eu.esdihumboldt.hale.common.core.io.report.impl.IOMessageImpl;

/**
 * HALE alignment writer
 *
 * @author Simon Templer
 */
public class JaxbAlignmentWriter extends AbstractAlignmentWriter {

	/**
	 * @see IOProvider#isCancelable()
	 */
	@Override
	public boolean isCancelable() {
		return false;
	}

	/**
	 * @see AbstractIOProvider#execute(ProgressIndicator, IOReporter)
	 */
	@Override
	protected IOReport execute(ProgressIndicator progress, IOReporter reporter)
			throws IOProviderConfigurationException, IOException {
		progress.begin("Save hale alignment", ProgressIndicator.UNKNOWN);

		PathUpdate pathUpdate = new PathUpdate(getProjectLocation(), getTarget().getLocation());
		AlignmentType alignment;
		try {
			alignment = JaxbAlignmentIO.convert(getAlignment(), reporter, pathUpdate);
		} catch (Exception e) {
			reporter.error(new IOMessageImpl("Error converting alignment to XML model", e));
			reporter.setSuccess(false);
			return reporter;
		}

		OutputStream out = getTarget().getOutput();
		try {
			JaxbAlignmentIO.save(alignment, reporter, out);
		} catch (Exception e) {
			reporter.error(new IOMessageImpl(e.getMessage(), e));
			reporter.setSuccess(false);
			return reporter;
		} finally {
			out.close();
		}

		progress.end();
		reporter.setSuccess(true);
		return reporter;
	}

	/**
	 * @see AbstractIOProvider#getDefaultTypeName()
	 */
	@Override
	protected String getDefaultTypeName() {
		return "hale alignment";
	}

}
