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
package eu.esdihumboldt.hale.common.instance.store.sqlite

import static org.junit.Assert.*

import java.nio.file.Files
import java.nio.file.Path

import javax.xml.namespace.QName

import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.After
import org.junit.Before
import org.junit.Test

import eu.esdihumboldt.hale.common.core.report.Report
import eu.esdihumboldt.hale.common.core.report.ReportHandler
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.instance.model.DataSet
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.instance.model.InstanceReference
import eu.esdihumboldt.hale.common.instance.store.InstanceStore
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter
import eu.esdihumboldt.hale.common.instance.store.StoreInstancesJob
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.test.TestUtil
import eu.esdihumboldt.util.test.AbstractPlatformTest

class StoreInstancesJobTest extends AbstractPlatformTest {

	Path dir
	SqliteInstanceStore store
	Schema schema

	@Before
	void setUp() {
		TestUtil.startConversionService()
		schema = new SchemaBuilder().schema {
			PersonType { name(String) }
		}
		dir = Files.createTempDirectory('store-job-test')
		store = new SqliteInstanceStore(DataSet.SOURCE, dir)
	}

	@After
	void tearDown() {
		store.close()
	}

	InstanceCollection persons(int count) {
		new InstanceBuilder(types: schema).createCollection {
			(1..count).each { n ->
				PersonType {
					name("p$n")
				}
			}
		}
	}

	@Test
	void testStoresAllAndReports() {
		List<Report> reports = []
		List<Instance> processed = []
		def job = new StoreInstancesJob('Store', store, persons(25), schema, null,
				{ Report r -> reports << r } as ReportHandler, false) {
					@Override
					protected void processInstance(Instance instance) {
						processed << instance
					}
				}

		IStatus status = job.run(new NullProgressMonitor())

		assertTrue status.isOK()
		assertEquals 25, processed.size()
		assertEquals 25, store.getInstances(schema).toList().size()
		assertEquals 1, reports.size()
		assertTrue reports[0].isSuccess()
	}

	@Test
	void testUnstorableInstanceIsSkipped() {
		List<Instance> instances = persons(2).toList()
		instances.add(1, new eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance(null, null))
		List<Report> reports = []
		def job = new StoreInstancesJob('Store', store,
				new eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstanceCollection(instances),
				schema, null, { Report r -> reports << r } as ReportHandler, false)

		IStatus status = job.run(new NullProgressMonitor())

		assertTrue status.isOK()
		assertEquals 2, store.getInstances(schema).toList().size()
		assertFalse reports[0].errors.isEmpty()
	}

	@Test
	void testWriterFailureFailsJob() {
		InstanceStore failing = [
			openWriter: {
				[add: { Instance i -> throw new IllegalStateException('disk full') },
					flush: {},
					close: {}] as InstanceStoreWriter
			},
			getInstances: { types ->
				store.getInstances(types)
			}
		] as InstanceStore
		List<Report> reports = []
		def job = new StoreInstancesJob('Store', failing, persons(3), schema, null,
				{ Report r -> reports << r } as ReportHandler, false)

		IStatus status = job.run(new NullProgressMonitor())

		assertEquals IStatus.ERROR, status.severity
		assertFalse reports[0].isSuccess()
	}
}
