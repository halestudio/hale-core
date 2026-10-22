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
package eu.esdihumboldt.hale.app.transform.test

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

import org.junit.Test

import eu.esdihumboldt.hale.common.core.io.HaleIO
import eu.esdihumboldt.hale.common.core.io.ProgressIndicator
import eu.esdihumboldt.hale.common.core.io.Value
import eu.esdihumboldt.hale.common.core.io.report.IOReport
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier
import eu.esdihumboldt.hale.common.core.report.Report
import eu.esdihumboldt.hale.common.core.report.ReportHandler
import eu.esdihumboldt.hale.common.headless.impl.ProjectTransformationEnvironment
import eu.esdihumboldt.hale.common.headless.transform.DefaultTransformationSettings
import eu.esdihumboldt.hale.common.headless.transform.StoreTransformationSink
import eu.esdihumboldt.hale.common.headless.transform.Transformation
import eu.esdihumboldt.hale.common.instance.io.GeoInstanceWriter
import eu.esdihumboldt.hale.common.instance.io.InstanceReader
import eu.esdihumboldt.hale.common.instance.io.InstanceWriter
import eu.esdihumboldt.hale.common.instance.io.util.GeoInstanceWriterDecorator
import eu.esdihumboldt.hale.common.instance.model.DataSet
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator
import eu.esdihumboldt.hale.common.instance.store.InstanceStore
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension
import eu.esdihumboldt.hale.common.instance.store.StoreInstancesJob
import eu.esdihumboldt.hale.common.test.TestUtil
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Runs transformations of the CLI test projects with the temporary instance
 * store for the source instances and a reiterable target sink.
 * <p>
 * The CLI does not allow to force this (the use of the store is determined by
 * the alignment, the sink by the writer), so the headless transformation used
 * by the CLI is called directly with settings forcing the store and a writer
 * that is not passthrough.
 */
@CompileStatic
class StorePathTransformationTest extends AbstractPlatformTest {

	private static final String HYDRO_PROJECT = "projects/hydro/project.halex"
	private static final String HYDRO_DATA = "projects/hydro/hydro-source.gml.gz"

	private static final String MULTI_TYPE_PROJECT = "projects/multitype/project.halex"
	private static final String MULTI_TYPE_DATA = "projects/multitype/multi-type-source.xml"

	/**
	 * Writer that is not passthrough and iterates the instances an additional
	 * time before writing them.
	 */
	@CompileStatic
	static class ReiteratingWriter extends GeoInstanceWriterDecorator<GeoInstanceWriter> {

		InstanceCollection instances
		int firstCount = -1

		ReiteratingWriter(GeoInstanceWriter writer) {
			super(writer)
		}

		@Override
		boolean isPassthrough() {
			false
		}

		@Override
		void setInstances(InstanceCollection instances) {
			this.instances = instances
			super.setInstances(instances)
		}

		@Override
		IOReport execute(ProgressIndicator progress) {
			// first iteration (served while the transformation is running)
			int count = 0
			ResourceIterator<Instance> it = instances.iterator()
			try {
				while (it.hasNext()) {
					it.next()
					count++
				}
			} finally {
				it.close()
			}
			firstCount = count

			// second iteration by the writer (served from the store)
			super.execute(progress)
		}
	}

	/**
	 * @return the simple class name of the instance store expected to be used
	 */
	protected String getExpectedStoreClass() {
		'SqliteInstanceStore'
	}

	/**
	 * Check that the expected instance store is used.
	 */
	private void checkStoreType() {
		Path dir = Files.createTempDirectory('store-path-test')
		InstanceStore store = InstanceStoreExtension.getInstance().createStore(DataSet.SOURCE, dir,
				null)
		try {
			assert store.getClass().simpleName == expectedStoreClass
		} finally {
			store.close()
		}
	}

	private static URI getProjectURI(String path) {
		URL url = StorePathTransformationTest.class.getClassLoader().getResource(path)
		if (!url) {
			throw new IllegalStateException("Could not find " + path)
		}
		url.toURI()
	}

	/**
	 * Transform the given source data with the given project.
	 *
	 * @return the target file
	 */
	private File transform(String project, String data, String writerId,
			Map<String, String> writerSettings, int expectedCount) {
		TestUtil.startConversionService()
		TestUtil.startInstanceFactory()
		TestUtil.startTransformationService()

		checkStoreType()

		List<Report<?>> reports = new CopyOnWriteArrayList<>()
		ReportHandler reportHandler = { Report<?> report -> reports.add(report) } as ReportHandler

		def env = new ProjectTransformationEnvironment('store-path-test',
				new DefaultInputSupplier(getProjectURI(project)), reportHandler)

		def sourceIn = new DefaultInputSupplier(getProjectURI(data))
		InstanceReader reader = HaleIO.findIOProvider(InstanceReader, sourceIn,
				getProjectURI(data).path)
		reader.setSource(sourceIn)

		File targetFile = File.createTempFile('transform-store-path', '.gml')
		targetFile.deleteOnExit()
		InstanceWriter target = HaleIO.createIOProvider(InstanceWriter, null, writerId)
		assert target instanceof GeoInstanceWriter
		target.setTarget(new FileIOSupplier(targetFile))
		target.setTargetSchema(env.targetSchema)
		def factory = HaleIO.findIOProviderFactory(InstanceWriter, null, writerId)
		target.setContentType(factory.supportedTypes.iterator().next())
		writerSettings.each { String name, String value ->
			target.setParameter(name, Value.of(value))
		}
		ReiteratingWriter writer = new ReiteratingWriter((GeoInstanceWriter) target)

		boolean success = Transformation.transform([reader], writer, env, reportHandler,
		'store-path-test', new DefaultTransformationSettings(Optional.of(true))).get()
		assert success

		// source instances were loaded into the temporary store
		def storeReports = reports.findAll { it.taskType == StoreInstancesJob.TASK_TYPE }
		assert storeReports.size() == 1
		assert storeReports[0].isSuccess()

		// target instances were provided by the reiterable store sink
		assert writer.instances.getClass().enclosingClass == StoreTransformationSink
		assert writer.firstCount == expectedCount

		targetFile
	}

	@Test
	@CompileStatic(TypeCheckingMode.SKIP)
	void testHydro() {
		File targetFile = transform(HYDRO_PROJECT, HYDRO_DATA,
				'eu.esdihumboldt.hale.io.inspiregml.writer', ['inspire.sds.localId': '1234'], 982)

		def root = new XmlSlurper().parse(targetFile)
		assert root.name() == 'SpatialDataSet'
		assert root.member.Watercourse.size() == 982
		assert root.identifier.Identifier.localId[0].text() == '1234'
	}

	@Test
	@CompileStatic(TypeCheckingMode.SKIP)
	void testMultiType() {
		File targetFile = transform(MULTI_TYPE_PROJECT, MULTI_TYPE_DATA,
				'eu.esdihumboldt.hale.io.xml.writer', ['xml.rootElement.name': 'collection'], 30)

		def root = new XmlSlurper().parse(targetFile)
		assert root.name() == 'collection'
		assert root.item.size() == 30
	}
}
