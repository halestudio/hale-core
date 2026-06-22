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
package eu.esdihumboldt.hale.io.xls.test.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.eclipse.core.runtime.content.IContentType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import eu.esdihumboldt.hale.common.core.HalePlatform;
import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier;
import eu.esdihumboldt.hale.common.lookup.LookupTableInfo;
import eu.esdihumboldt.hale.common.lookup.impl.LookupTableImpl;
import eu.esdihumboldt.hale.common.lookup.impl.LookupTableInfoImpl;
import eu.esdihumboldt.hale.io.csv.writer.LookupTableExportConstants;
import eu.esdihumboldt.hale.io.xls.writer.XLSLookupTableWriter;
import eu.esdihumboldt.util.test.AbstractPlatformTest;

/**
 * Tests for {@link XLSLookupTableWriter}, in particular the export of lookup
 * table rows that have a <code>null</code> target value.
 *
 * @author Simon Templer
 */
public class XLSLookupTableWriterTest extends AbstractPlatformTest {

	/**
	 * a temporary folder to safely store tmp files. Will be deleted after test
	 * (successfully or not)
	 */
	@Rule
	public TemporaryFolder tmpFolder = new TemporaryFolder();

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

		File tmpFile = tmpFolder.newFile("lookupNullTarget.xls");
		LookupTableInfo info = new LookupTableInfoImpl(new LookupTableImpl(values), "test", null);

		XLSLookupTableWriter writer = new XLSLookupTableWriter();
		writer.setParameter(LookupTableExportConstants.PARAM_SOURCE_COLUMN, Value.of("source"));
		writer.setParameter(LookupTableExportConstants.PARAM_TARGET_COLUMN, Value.of("target"));
		writer.setLookupTable(info);
		writer.setTarget(new FileIOSupplier(tmpFile));
		IContentType contentType = HalePlatform.getContentTypeManager()
				.getContentType("eu.esdihumboldt.hale.io.xls.xls");
		writer.setContentType(contentType);

		IOReport report = writer.execute(null);
		assertTrue("XLS lookup export was not successful", report.isSuccess());

		Workbook wb;
		try (POIFSFileSystem fs = new POIFSFileSystem(tmpFile)) {
			wb = new HSSFWorkbook(fs.getRoot(), true);
		}
		Sheet sheet = wb.getSheetAt(0);

		// header row + two data rows; the null-valued row must not be dropped
		assertEquals("Null-valued row must not be dropped from the export", 2,
				sheet.getLastRowNum());

		// header
		assertEquals("source", sheet.getRow(0).getCell(0).getStringCellValue());
		assertEquals("target", sheet.getRow(0).getCell(1).getStringCellValue());

		// mapped row
		assertEquals("a", sheet.getRow(1).getCell(0).getStringCellValue());
		assertEquals("1", sheet.getRow(1).getCell(1).getStringCellValue());

		// unmapped row - target exported as empty string
		assertEquals("b", sheet.getRow(2).getCell(0).getStringCellValue());
		assertEquals("Null target value must be exported as an empty string", "",
				sheet.getRow(2).getCell(1).getStringCellValue());

		wb.close();
	}

}
