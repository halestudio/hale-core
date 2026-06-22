/*
 * Copyright (c) 2026 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.csv.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.content.IContentType;
import org.junit.BeforeClass;
import org.junit.Test;

import au.com.bytecode.opencsv.CSVReader;
import eu.esdihumboldt.hale.common.core.HalePlatform;
import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.supplier.ByteArrayOutputStreamSupplier;
import eu.esdihumboldt.hale.common.lookup.LookupTableInfo;
import eu.esdihumboldt.hale.common.lookup.impl.LookupTableImpl;
import eu.esdihumboldt.hale.common.lookup.impl.LookupTableInfoImpl;
import eu.esdihumboldt.hale.common.test.TestUtil;
import eu.esdihumboldt.hale.io.csv.reader.CSVConstants;
import eu.esdihumboldt.hale.io.csv.writer.internal.CSVLookupWriter;
import eu.esdihumboldt.util.test.AbstractPlatformTest;

/**
 * Tests for {@link CSVLookupWriter}, in particular the export of lookup table
 * rows that have a <code>null</code> target value.
 *
 * @author Simon Templer
 */
public class CSVLookupWriterTest extends AbstractPlatformTest {

	/**
	 * Wait for needed services to be running
	 */
	@BeforeClass
	public static void waitForServices() {
		TestUtil.startConversionService();
	}

	/**
	 * A row whose target value is <code>null</code> must be exported (not dropped)
	 * with an empty target cell, while regular rows are exported unchanged.
	 *
	 * @throws Exception if an error occurs
	 */
	@Test
	public void testExportNullTargetValue() throws Exception {
		// use a LinkedHashMap so the row order is deterministic
		Map<Value, Value> values = new LinkedHashMap<Value, Value>();
		values.put(Value.of("a"), Value.of("1"));
		values.put(Value.of("b"), null);

		List<String[]> rows = writeLookupTable(values);

		// header + two data rows; the null-valued row must not be dropped
		assertEquals("Null-valued row must not be dropped from the export", 3, rows.size());

		assertEquals("source", rows.get(0)[0]);
		assertEquals("target", rows.get(0)[1]);

		assertEquals("a", rows.get(1)[0]);
		assertEquals("1", rows.get(1)[1]);

		assertEquals("b", rows.get(2)[0]);
		assertEquals("Null target value must be exported as an empty string", "", rows.get(2)[1]);
	}

	/**
	 * Run the {@link CSVLookupWriter} on the given table and read the result back.
	 *
	 * @param values the lookup table content
	 * @return the rows of the written CSV (including the header row)
	 * @throws Exception if an error occurs
	 */
	private List<String[]> writeLookupTable(Map<Value, Value> values) throws Exception {
		LookupTableInfo info = new LookupTableInfoImpl(new LookupTableImpl(values), "test", null);

		CSVLookupWriter writer = new CSVLookupWriter();
		writer.setParameter(LookupTableExportConstants.PARAM_SOURCE_COLUMN, Value.of("source"));
		writer.setParameter(LookupTableExportConstants.PARAM_TARGET_COLUMN, Value.of("target"));
		writer.setLookupTable(info);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.setTarget(new ByteArrayOutputStreamSupplier(out));
		IContentType contentType = HalePlatform.getContentTypeManager()
				.getContentType("eu.esdihumboldt.hale.io.csv");
		writer.setContentType(contentType);

		IOReport report = writer.execute(null);
		assertTrue("CSV lookup export was not successful", report.isSuccess());

		// the writer uses the CSV defaults (tab separator), so read back the same way
		try (CSVReader reader = new CSVReader(
				new InputStreamReader(new ByteArrayInputStream(out.toByteArray())),
				CSVConstants.DEFAULT_SEPARATOR, CSVConstants.DEFAULT_QUOTE,
				CSVConstants.DEFAULT_ESCAPE)) {
			return reader.readAll();
		}
	}

}
