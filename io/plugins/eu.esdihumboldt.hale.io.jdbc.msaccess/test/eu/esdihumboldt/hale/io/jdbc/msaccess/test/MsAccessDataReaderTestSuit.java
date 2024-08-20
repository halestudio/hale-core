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
 * 
 * Contributors:
 *     wetransform GmbH <http://www.wetransform.to>
 */

package eu.esdihumboldt.hale.io.jdbc.msaccess.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.core.io.impl.LogProgressIndicator;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.schema.model.Schema;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.io.jdbc.JDBCInstanceReader;
import eu.esdihumboldt.hale.io.jdbc.JDBCSchemaReader;
import eu.esdihumboldt.hale.io.jdbc.msaccess.MsAccessInstanceReader;
import eu.esdihumboldt.hale.io.jdbc.msaccess.MsAccessSchemaReader;

/**
 * Abstract suit class to test Access database
 * 
 * @author Arun
 *
 */
public abstract class MsAccessDataReaderTestSuit {

	/**
	 * Source Database name
	 */
	protected String SOURCE_DB_NAME;

	/**
	 * Source Database Extension
	 */
	protected String SOURCE_DB_EXT;

	/**
	 * Source Database path
	 */
	protected String SOURCE_DB_PATH;

	/**
	 * User name to connect to database
	 */
	protected String USER_NAME;

	/**
	 * password to connect to database
	 */
	protected String PASSWORD;

	/**
	 * Query to execute
	 */
	protected String SQL_QUERY;

	private File TEMP_SOURCE_FILE_NAME = null;

	private static String[] tablesShouldNotInSchema = new String[] { "prop", "columns",
			"columns_view", "tables" };

	/**
	 * Copies the source database to a temporary file.
	 * 
	 * @throws IOException if temp file can't be created
	 */
	public void createSourceTempFile() throws IOException {
		ByteSource source = Resources.asByteSource(
				MsAccessDataReaderTestSuit.class.getClassLoader().getResource(SOURCE_DB_PATH));
		ByteSink dest = Files.asByteSink(getSourceTempFilePath());
		source.copyTo(dest);
	}

	/**
	 * Generates a random path (within the system's temporary folder) for the source
	 * database.
	 * 
	 * @return the absolute path of the source temp file
	 */
	public File getSourceTempFilePath() {

		if (TEMP_SOURCE_FILE_NAME == null) {
			try {
				TEMP_SOURCE_FILE_NAME = File.createTempFile(SOURCE_DB_NAME, SOURCE_DB_EXT);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return TEMP_SOURCE_FILE_NAME;

	}

	/**
	 * Deletes the source temp file.
	 */
	public void deleteSourceTempFile() {
		deleteTempFile(getSourceTempFilePath());
	}

	private void deleteTempFile(File tempFile) {
		if (tempFile.exists()) {
			tempFile.delete();
		}
	}

	/**
	 * Test - reads a sample MsAccess Database schema. UCanAccess lib should not
	 * throw any error.
	 * 
	 * @throws Exception if an error occurs
	 */
	public void schemaReaderTest() throws Exception {
		MsAccessSchemaReader schemaReader = new MsAccessSchemaReader();
		schemaReader.setSource(new FileIOSupplier(getSourceTempFilePath()));
		schemaReader.setParameter(JDBCSchemaReader.PARAM_USER, Value.of(USER_NAME));
		schemaReader.setParameter(JDBCSchemaReader.PARAM_PASSWORD, Value.of(PASSWORD));

		IOReport report = schemaReader.execute(new LogProgressIndicator());
		assertTrue(report.isSuccess());

		TEMP_SOURCE_FILE_NAME = null;
		Schema schema = schemaReader.getSchema();
		assertTrue(schema != null);
		Collection<? extends TypeDefinition> k = schema.getMappingRelevantTypes();

		for (TypeDefinition def : k)
			System.out.println(def.getDisplayName());

		checkTables(k);
	}

	/**
	 * Test - reads a sample MsAccess schema and data.
	 * 
	 * @throws Exception if an error occurs
	 */
	public void instanceRaderTest() throws Exception {

		// ****** read Schema ******//

		Schema schema = readSchema(getSourceTempFilePath());
		assertNotNull(schema);
		System.out.println("MappingRelevantTypes:" + schema.getMappingRelevantTypes().size());
		// assertEquals(1, schema.getMappingRelevantTypes().size());

		// Test properties
		// TypeDefinition schemaType =
		// schema.getMappingRelevantTypes().iterator().next();
		// Check every property for their existence

		// ****** read Instances ******//
		InstanceCollection instances = readInstances(schema, getSourceTempFilePath());
		assertTrue(instances.hasSize());
		System.out.println("instances size:" + instances.size());
		// assertEquals(SOURCE_INSTANCES_COUNT, instances.size());

	}

	/**
	 * Reads a schema from a MsAccess database file.
	 * 
	 * @param sourceFile the file of the source database.
	 * @return the schema
	 * @throws Exception any exception thrown by {@link MsAccessSchemaReader}
	 */

	public Schema readSchema(File sourceFile) throws Exception {

		MsAccessSchemaReader schemaReader = new MsAccessSchemaReader();
		schemaReader.setSource(new FileIOSupplier(sourceFile));
		schemaReader.setParameter(JDBCSchemaReader.PARAM_USER, Value.of(USER_NAME));
		schemaReader.setParameter(JDBCSchemaReader.PARAM_PASSWORD, Value.of(PASSWORD));

		IOReport report = schemaReader.execute(new LogProgressIndicator());
		assertTrue(report.isSuccess());

		return schemaReader.getSchema();
	}

	/**
	 * Reads instances from from a MsAccess database file with the provided schema.
	 * 
	 * @param sourceSchema the schema of the source database
	 * @param sourceFile the file of the source database.
	 * @return the read instances
	 * @throws Exception any exception thrown by {@link MsAccessInstanceReader}
	 */
	public InstanceCollection readInstances(Schema sourceSchema, File sourceFile) throws Exception {

		MsAccessInstanceReader instanceReader = new MsAccessInstanceReader();
		instanceReader.setSource(new FileIOSupplier(sourceFile));
		instanceReader.setSourceSchema(sourceSchema);
		instanceReader.setParameter(JDBCInstanceReader.PARAM_USER, Value.of(USER_NAME));
		instanceReader.setParameter(JDBCInstanceReader.PARAM_PASSWORD, Value.of(PASSWORD));

		// Test instances
		IOReport report = instanceReader.execute(new LogProgressIndicator());
		assertTrue("Data import was not successfull.", report.isSuccess());

		return instanceReader.getInstances();

	}

	/**
	 * Check table names should not be in excluded table list (UCA_METADATA table
	 * list)
	 * 
	 * @param tableNames table names collection return from Schema
	 */
	public void checkTables(Collection<? extends TypeDefinition> tableNames) {

		for (TypeDefinition def : tableNames) {
			for (String table : tablesShouldNotInSchema)
				assertFalse(def.getDisplayName().equalsIgnoreCase(table));
		}
	}

}