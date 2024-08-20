
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
package eu.esdihumboldt.hale.io.schematron.validator;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.opengis.cite.validation.SchematronValidator;

import eu.esdihumboldt.hale.common.core.io.IOProviderConfigurationException;
import eu.esdihumboldt.hale.common.core.io.ProgressIndicator;
import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.report.IOReporter;
import eu.esdihumboldt.hale.common.core.io.report.impl.IOMessageImpl;
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier;
import eu.esdihumboldt.hale.common.instance.io.impl.AbstractInstanceValidator;
import eu.esdihumboldt.hale.io.schematron.util.SchematronReportParser;
import eu.esdihumboldt.hale.io.validation.ConfigurableInstanceValidator;
import eu.esdihumboldt.hale.io.validation.ValidatorConfiguration;

/***
 *
 * Validator for ISO Schematron
 *
 * @author Florian Esser
 */
public class SchematronInstanceValidator extends AbstractInstanceValidator
		implements ConfigurableInstanceValidator {

	/**
	 * Name of the parameter specifying the schematron location.
	 */
	public static final String PARAM_SCHEMATRON_LOCATION = "schematron.location";

	/**
	 * The identifier of the registration as I/O provider.
	 */
	public static final String PROVIDER_ID = "eu.esdihumboldt.hale.io.schematron.validator";

	/**
	 * Set the location of the schematron rules definition to use for validation.
	 *
	 * @param location the schematron location
	 */
	public void setSchematronLocation(URI location) {
		if (location == null) {
			setParameter(PARAM_SCHEMATRON_LOCATION, Value.NULL);
		}
		else {
			setParameter(PARAM_SCHEMATRON_LOCATION, Value.of(location.toString()));
		}
	}

	/**
	 * @return the location of the schematron rules definition
	 */
	public URI getSchematronLocation() {
		String paramString = getParameter(PARAM_SCHEMATRON_LOCATION).as(String.class);
		if (paramString != null) {
			return URI.create(paramString);
		}
		return null;
	}

	@Override
	protected IOReport execute(ProgressIndicator progress, IOReporter reporter)
			throws IOProviderConfigurationException, IOException {
		URI schematronLoc = getSchematronLocation();
		if (schematronLoc == null) {
			throw new IOProviderConfigurationException("Providing a schematron file is required");
		}

		progress.begin("Performing Schematron validation", ProgressIndicator.UNKNOWN);

		final InputStream sourceInput = this.getSource().getInput();
		if (sourceInput == null) {
			throw new RuntimeException("No input for Schematron validator");
		}
		final Source xmlSource = new StreamSource(sourceInput);

		final DefaultInputSupplier schematronInputSupplier = new DefaultInputSupplier(
				schematronLoc);
		final InputStream schematronInput = schematronInputSupplier.getInput();
		if (schematronInput == null) {
			throw new RuntimeException("No rules input for Schematron validator");
		}
		final Source schematronSource = new StreamSource(schematronInput);

		try {
			final SchematronValidator validator = new SchematronValidator(schematronSource);
			final Result result = validator.validate(xmlSource, /* svrlReport */true);

			final StringWriter reportWriter = new StringWriter();
			SchematronUtils.convertValidatorResult(result, reportWriter);

			reporter.setSuccess(!validator.ruleViolationsDetected());
			if (validator.ruleViolationsDetected()) {
				SchematronReportParser parser = new SchematronReportParser(reportWriter.toString());
				parser.reportFailedAssertions(reporter);
			}
		} catch (Exception e) {
			reporter.error(new IOMessageImpl("Error running schematron validation", e));
			reporter.setSuccess(false);
		} finally {
			schematronInput.close();
			progress.end();
		}

		return reporter;
	}

	@Override
	protected String getReportLabel() {
		return "Schematron validation";
	}

	@Override
	protected String getDefaultFailSummary() {
		return "Validating the XML file against the schematron rules failed";
	}

	@Override
	protected String getDefaultSuccessSummary() {
		return "The XML file is valid according to the schematron rules";
	}

	@Override
	protected String getDefaultTypeName() {
		return "XML file";
	}

	@Override
	public boolean isCancelable() {
		return false;
	}

	/**
	 * Checks if the validator is properly configured. This method does not call
	 * <code>validate()</code> from {@link AbstractInstanceValidator}.
	 *
	 * @see eu.esdihumboldt.hale.common.instance.io.impl.AbstractInstanceValidator#validate()
	 */
	@Override
	public void validate() throws IOProviderConfigurationException {
		if (this.getSchematronLocation() == null) {
			throw new IOProviderConfigurationException("No schematron rules file is configured");
		}
	}

	/**
	 * @see eu.esdihumboldt.hale.io.validation.ConfigurableInstanceValidator#configure(eu.esdihumboldt.hale.io.validation.ValidatorConfiguration)
	 */
	@Override
	public void configure(ValidatorConfiguration configuration) {
		this.setSchematronLocation(configuration.getLocation());
	}

	@Override
	protected String getReportType() {
		return PROVIDER_ID;
	}
}
