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
package eu.esdihumboldt.hale.io.xls.test.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.eclipse.core.runtime.content.IContentType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import eu.esdihumboldt.cst.test.TransformationExample;
import eu.esdihumboldt.cst.test.TransformationExamples;
import eu.esdihumboldt.hale.common.core.HalePlatform;
import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.impl.MultiInstanceCollection;
import eu.esdihumboldt.hale.common.schema.model.Schema;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchemaSpace;
import eu.esdihumboldt.hale.common.test.TestUtil;
import eu.esdihumboldt.hale.io.csv.InstanceTableIOConstants;
import eu.esdihumboldt.hale.io.xls.writer.XLSInstanceWriter;
import eu.esdihumboldt.util.test.AbstractPlatformTest;

/**
 * Test class for {@link XLSInstanceWriter}
 *
 * @author Yasmina Kammeyer
 */
public class XLSInstanceWriterTest extends AbstractPlatformTest {

	/**
	 * a temporary folder to safely store tmp files. Will be deleted after test
	 * (successfully or not)
	 */
	@Rule
	public TemporaryFolder tmpFolder = new TemporaryFolder();

	/**
	 * Wait for needed services to be running
	 */
	@BeforeClass
	public static void waitForServices() {
		TestUtil.startConversionService();
	}

	/**
	 * Test - write simple data, without nested properties and useSchema=true - test
	 * if order/number of column persisted from original schema when in the instance
	 * an attribute has no values
	 *
	 * @throws Exception , if an error occurs
	 */
	@Test
	public void testWriteSimpleSchemaColOrder() throws Exception {

		// create an example schema with 3 properties and an instance with two
		// missing values
		Schema schema = XLSInstanceWriterTestUtil.createExampleSchema();
		InstanceCollection instance = XLSInstanceWriterTestUtil
				.createExampleInstancesNoPopulation(schema);

		List<String> header = Arrays.asList("name", "population", "country");
		List<String> firstDataRow = Arrays.asList("Darmstadt", "", "");

		// set instances to xls instance writer
		XLSInstanceWriter writer = new XLSInstanceWriter();
		IContentType contentType = HalePlatform.getContentTypeManager()
				.getContentType("eu.esdihumboldt.hale.io.xls.xls");
		writer.setParameter(InstanceTableIOConstants.SOLVE_NESTED_PROPERTIES, Value.of(true));
		writer.setParameter(InstanceTableIOConstants.USE_SCHEMA, Value.of(true));
		writer.setParameter(InstanceTableIOConstants.EXPORT_IGNORE_EMPTY_FEATURETYPES,
				Value.of(false));
		writer.setParameter(InstanceTableIOConstants.EXPORT_TYPE, Value.of("city"));

		File tmpFile = tmpFolder.newFile("excelTestWriteSimpleSchema.xls");

		writer.setInstances(instance);
		DefaultSchemaSpace ss = new DefaultSchemaSpace();
		ss.addSchema(schema);
		writer.setTargetSchema(ss);
		// write instances to a temporary XLS file
		writer.setTarget(new FileIOSupplier(tmpFile));
		writer.setContentType(contentType);
		IOReport report = writer.execute(null);
		assertTrue(report.isSuccess());

		Workbook wb;
//		https: // poi.apache.org/components/spreadsheet/quick-guide.html#FileInputStream
		try (POIFSFileSystem fs = new POIFSFileSystem(tmpFile)) {
			wb = new HSSFWorkbook(fs.getRoot(), true);
		}
		Sheet sheet = wb.getSheetAt(0);

		checkHeader(sheet, header);
		checkSheetName(sheet, "city");
		checkFirstDataRow(sheet, firstDataRow);
		checkHeaderOrder(sheet, header);
		tmpFolder.delete();
	}

	/**
	 * Test - write data of complex schema and analyze result
	 *
	 * @throws Exception , if an error occurs
	 */
	@Test
	public void testWriteComplexSchema() throws Exception {
		TransformationExample example = TransformationExamples
				.getExample(TransformationExamples.SIMPLE_COMPLEX);
		// alternative the data could be generated by iterating through the
		// exempleproject's sourcedata
		List<String> header = Arrays.asList("id", "name", "details.age", "details.income",
				"details.address.street", "details.address.city");
		List<String> firstDataRow = Arrays.asList("id0", "name0", "age0", "income0", "street0",
				"city0");

		// set instances to xls instance writer
		XLSInstanceWriter writer = new XLSInstanceWriter();
		IContentType contentType = HalePlatform.getContentTypeManager()
				.getContentType("eu.esdihumboldt.hale.io.xls.xls");
		writer.setParameter(InstanceTableIOConstants.SOLVE_NESTED_PROPERTIES, Value.of(true));
		writer.setParameter(InstanceTableIOConstants.USE_SCHEMA, Value.of(false));
		writer.setParameter(InstanceTableIOConstants.EXPORT_IGNORE_EMPTY_FEATURETYPES,
				Value.of(true));
		writer.setParameter(InstanceTableIOConstants.EXPORT_TYPE, Value.of("person"));

		File tmpFile = tmpFolder.newFile("excelTestWriteComplexSchema.xls");

		writer.setInstances(example.getSourceInstances());
		DefaultSchemaSpace ss = new DefaultSchemaSpace();
		ss.addSchema(example.getSourceSchema());
		writer.setTargetSchema(ss);
		// write instances to a temporary XLS file
		writer.setTarget(new FileIOSupplier(tmpFile));
		writer.setContentType(contentType);
		IOReport report = writer.execute(null);
		assertTrue(report.isSuccess());

		Workbook wb;
//		https: // poi.apache.org/components/spreadsheet/quick-guide.html#FileInputStream
		try (POIFSFileSystem fs = new POIFSFileSystem(tmpFile)) {
			wb = new HSSFWorkbook(fs.getRoot(), true);
		}
		Sheet sheet = wb.getSheetAt(0);

		checkHeader(sheet, header);
		checkSheetName(sheet, "person");
		checkFirstDataRow(sheet, firstDataRow);
		tmpFolder.delete();
	}

	/**
	 * Test - write data of complex schema and analyze result The implementation,
	 * this test based on, does not work correctly at the moment.
	 *
	 * @throws Exception , if an error occurs
	 */
	@Test
	public void testWriteNotNestedProperties() throws Exception {
		TransformationExample example = TransformationExamples
				.getExample(TransformationExamples.SIMPLE_COMPLEX);
		// alternative the data could be generated by iterating through the
		// exempleproject's sourcedata
		List<String> header = Arrays.asList("id", "name");
		List<String> firstDataRow = Arrays.asList("id0", "name0");

		// set instances to xls instance writer
		XLSInstanceWriter writer = new XLSInstanceWriter();
		IContentType contentType = HalePlatform.getContentTypeManager()
				.getContentType("eu.esdihumboldt.hale.io.xls.xls");
		writer.setParameter(InstanceTableIOConstants.SOLVE_NESTED_PROPERTIES, Value.of(false));
		writer.setParameter(InstanceTableIOConstants.USE_SCHEMA, Value.of(false));
		writer.setParameter(InstanceTableIOConstants.EXPORT_IGNORE_EMPTY_FEATURETYPES,
				Value.of(true));
		writer.setParameter(InstanceTableIOConstants.EXPORT_TYPE, Value.of("person"));

		File tmpFile = tmpFolder.newFile("excelNotNestedProperties.xls");

		writer.setInstances(example.getSourceInstances());
		DefaultSchemaSpace ss = new DefaultSchemaSpace();
		ss.addSchema(example.getSourceSchema());
		writer.setTargetSchema(ss);
		// write instances to a temporary XLS file
		writer.setTarget(new FileIOSupplier(tmpFile));
		writer.setContentType(contentType);
		IOReport report = writer.execute(null);
		assertTrue(report.isSuccess());

		Workbook wb;
//		https: // poi.apache.org/components/spreadsheet/quick-guide.html#FileInputStream
		try (POIFSFileSystem fs = new POIFSFileSystem(tmpFile)) {
			wb = new HSSFWorkbook(fs.getRoot(), true);
		}
		Sheet sheet = wb.getSheetAt(0);

		checkHeader(sheet, header);
		checkSheetName(sheet, "person");
		checkFirstDataRow(sheet, firstDataRow);
		tmpFolder.delete();
	}

	/**
	 * Test - write data for 2 example and create a common excel with multiple
	 * sheets
	 *
	 * @throws Exception , if an error occurs
	 */
	@Test
	public void testExportMultiFeatureToExcel() throws Exception {
		ArrayList<InstanceCollection> examples = new ArrayList<>();
		TransformationExample example = TransformationExamples
				.getExample(TransformationExamples.SIMPLE_COMPLEX);
		TransformationExample example2 = TransformationExamples
				.getExample(TransformationExamples.CARDINALITY_MERGE_1);
		// collect instance collections - need to be in memory because they will
		// be iterated over twice
		examples.add(new DefaultInstanceCollection(example.getTargetInstances()));
		examples.add(new DefaultInstanceCollection(example2.getTargetInstances()));

		// set instances to xls instance writer
		XLSInstanceWriter writer = new XLSInstanceWriter();
		IContentType contentType = HalePlatform.getContentTypeManager()
				.getContentType("eu.esdihumboldt.hale.io.xls.xls");
		writer.setParameter(InstanceTableIOConstants.SOLVE_NESTED_PROPERTIES, Value.of(false));
		writer.setParameter(InstanceTableIOConstants.USE_SCHEMA, Value.of(false));
		writer.setParameter(InstanceTableIOConstants.EXPORT_IGNORE_EMPTY_FEATURETYPES,
				Value.of(false));
		writer.setParameter(InstanceTableIOConstants.EXPORT_TYPE,
				Value.of("{http://www.example.org/t2/}PersonType,{http://www.example.org/t2/}T2"));

		File tmpFile = tmpFolder.newFile("excelWith2Sheets.xls");
		// write instances to a temporary XLS file
		writer.setTarget(new FileIOSupplier(tmpFile));
		writer.setContentType(contentType);

		DefaultSchemaSpace ss = new DefaultSchemaSpace();
		ss.addSchema(example.getTargetSchema());
		ss.addSchema(example2.getTargetSchema());
		writer.setTargetSchema(ss);

		InstanceCollection multiInstanceCollection = new MultiInstanceCollection(examples);
		writer.setInstances(multiInstanceCollection);

		IOReport report = writer.execute(null);
		assertTrue(report.isSuccess());

		Workbook wb;
//		https: // poi.apache.org/components/spreadsheet/quick-guide.html#FileInputStream
		try (POIFSFileSystem fs = new POIFSFileSystem(tmpFile)) {
			wb = new HSSFWorkbook(fs.getRoot(), true);
		}
		int sheetNumber = wb.getNumberOfSheets();
		assertEquals(2, sheetNumber);
		Sheet sheet1 = wb.getSheetAt(0);
		assertNotNull(sheet1.getRow(0));
		Sheet sheet2 = wb.getSheetAt(1);
		assertNotNull(sheet2.getRow(0));
		tmpFolder.delete();
	}

	/**
	 *
	 * @param sheet the excel file sheet
	 * @param sheetName The sheet name
	 * @throws Exception , if an error occurs
	 */
	private void checkSheetName(Sheet sheet, String sheetName) throws Exception {

		assertTrue("There is no sheet in the file named: " + sheet.getSheetName(),
				sheetName.equals(sheet.getSheetName()));

	}

	private void checkHeader(Sheet sheet, List<String> headerNames) throws Exception {

		Row header = sheet.getRow(sheet.getFirstRowNum());

		assertEquals("There are not enough header cells.", headerNames.size(),
				header.getPhysicalNumberOfCells());

		for (Cell cell : header) {
			assertTrue("Not expecting header cell value.",
					headerNames.contains(cell.getStringCellValue()));
		}
	}

	private void checkHeaderOrder(Sheet sheet, List<String> headerNames) throws Exception {

		Row header = sheet.getRow(sheet.getFirstRowNum());

		int i = 0;
		for (Cell cell : header) {
			assertEquals("Not same cell order as in the original schema.",
					cell.getStringCellValue(), headerNames.get(i));
			i++;
		}
	}

	private void checkFirstDataRow(Sheet sheet, List<String> firstDataRow) {
		Row datarow = sheet.getRow(sheet.getFirstRowNum() + 1);

		assertEquals("There are not enough data cells.", firstDataRow.size(),
				datarow.getPhysicalNumberOfCells());

		for (Cell cell : datarow) {
			assertTrue("Not expecting data value.",
					firstDataRow.contains(cell.getStringCellValue()));
		}
	}

	/**
	 * test - if a complex schema with data is present and this schema contains more
	 * than one type, the exporter should export all types (one sheet per type) or
	 * the selected one XXX not supported under current circumstances
	 */
	public void testExportChoosenType() {
		// TODO
	}

	/**
	 * test - if a complex schema has a type containing an object attribute with
	 * maxOccures > 1, one type can contain more than one instance of that object.
	 * If this is the case than... TODO Export special case.
	 */
	public void testMultipleInstances() {
		// TransformationExample example =
		// TransformationExamples.getExample(TransformationExamples.CM_NESTED_1);
		// TODO
	}

}
