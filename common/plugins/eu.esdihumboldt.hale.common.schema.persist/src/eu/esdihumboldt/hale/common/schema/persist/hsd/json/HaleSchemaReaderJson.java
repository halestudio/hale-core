
/*
 * Copyright (c) 2017 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.schema.persist.hsd.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import eu.esdihumboldt.hale.common.core.io.IOProviderConfigurationException;
import eu.esdihumboldt.hale.common.core.io.ProgressIndicator;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.report.IOReporter;
import eu.esdihumboldt.hale.common.core.io.report.impl.IOMessageImpl;
import eu.esdihumboldt.hale.common.schema.io.impl.AbstractSchemaReader;
import eu.esdihumboldt.hale.common.schema.model.Schema;
import eu.esdihumboldt.hale.common.schema.model.constraint.factory.OsgiClassResolver;
import eu.esdihumboldt.hale.common.schema.persist.hsd.HaleSchemaUtil;

/**
 * Reads the HALE schema model from JSON (HALE Schema Definition).
 *
 * @author Simon Templer
 */
public class HaleSchemaReaderJson extends AbstractSchemaReader {

	private Schema schema;

	@Override
	public boolean isCancelable() {
		return false;
	}

	@Override
	protected IOReport execute(ProgressIndicator progress, IOReporter reporter)
			throws IOProviderConfigurationException, IOException {
		progress.begin("Load schema", ProgressIndicator.UNKNOWN);
		try (InputStream in = getSource().getInput();
				Reader reader = new InputStreamReader(in, getCharset())) {
			Iterable<Schema> schemas = new JsonToSchema(null, new OsgiClassResolver(), reporter)
					.parseSchemas(reader);

			List<Schema> schemaList = StreamSupport.stream(schemas.spliterator(), false)
					.collect(Collectors.toList());
			if (!schemaList.isEmpty()) {
				schema = HaleSchemaUtil.combineSchema(schemaList, reporter);
				reporter.setSuccess(true);
			}
			else {
				reporter.setSuccess(false);
				reporter.setSummary("No schema definition found");
			}
		} catch (Exception e) {
			reporter.error(new IOMessageImpl(e.getMessage(), e));
			reporter.setSuccess(false);
		} finally {
			progress.end();
		}
		return reporter;
	}

	@Override
	protected String getDefaultTypeName() {
		return "hale Schema Definition (JSON)";
	}

	@Override
	public Schema getSchema() {
		return schema;
	}

}
