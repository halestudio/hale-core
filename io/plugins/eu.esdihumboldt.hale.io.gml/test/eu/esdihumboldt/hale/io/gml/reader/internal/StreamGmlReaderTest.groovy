/*
 * Copyright (c) 2018 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.gml.reader.internal

import static org.junit.Assert.*

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import java.time.Duration

import org.geotools.api.referencing.crs.CoordinateReferenceSystem
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.images.builder.ImageFromDockerfile

import eu.esdihumboldt.hale.common.core.io.Value
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier
import eu.esdihumboldt.hale.common.instance.geometry.GeometryUtil
import eu.esdihumboldt.hale.common.instance.helper.PropertyResolver
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator
import eu.esdihumboldt.hale.common.instance.model.ext.InstanceIterator
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.io.xsd.reader.XmlSchemaReader
import eu.esdihumboldt.util.test.AbstractPlatformTest
import io.qameta.allure.Link

/**
 * Tests for {@link StreamGmlReader}
 *
 * @author Florian Esser
 */
@SuppressWarnings("restriction")
@CompileStatic
class StreamGmlReaderTest extends AbstractPlatformTest {

	@SuppressWarnings("rawtypes")
	static GenericContainer deegreeContainer

	@BeforeClass
	@CompileDynamic
	static void startDeegreeContainer() {
		def image = new ImageFromDockerfile()
				.withFileFromClasspath('Dockerfile', 'data/deegree-wfs/Dockerfile')
				.withFileFromClasspath('workspace/services/wfs.xml', 'data/deegree-wfs/workspace/services/wfs.xml')
				.withFileFromClasspath('workspace/datasources/feature/memory.xml', 'data/deegree-wfs/workspace/datasources/feature/memory.xml')
				.withFileFromClasspath('workspace/appschemas/testfeature.xsd', 'data/deegree-wfs/workspace/appschemas/testfeature.xsd')
				.withFileFromClasspath('workspace/data/features.gml', 'data/deegree-wfs/workspace/data/features.gml')

		def waitStrategy = new HttpWaitStrategy()
				.forPort(8080)
				.forPath('/deegree-webservices/services/wfs?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&typenames=tf:River&COUNT=1')
				.forResponsePredicate({ body -> body.contains('tf:River') })
				.withStartupTimeout(Duration.ofSeconds(120))

		deegreeContainer = new GenericContainer(image)
				.withExposedPorts(8080)
				.waitingFor(waitStrategy)
		deegreeContainer.start()
	}

	@AfterClass
	static void stopDeegreeContainer() {
		deegreeContainer?.stop()
	}

	static String wfsBaseUrl() {
		"http://${deegreeContainer.host}:${deegreeContainer.getMappedPort(8080)}/deegree-webservices/services/wfs"
	}

	@Before
	void clearResolverCache() {
		PropertyResolver.clearCache()
	}

	/**
	 * Test whether the reader detects the CRS from the GML srsName attribute
	 * correctly.
	 *
	 * @throws Exception
	 */
	@Test
	public void testSrs() throws Exception {
		def schemaLocation = getClass().getResource("/data/hydro/hydroEx.xsd").toURI();
		def gmlLocation = getClass().getResource("/data/hydro/hydro.gml").toURI();

		def sourceSchema = loadSchema(schemaLocation)
		def instances = loadGml(gmlLocation, sourceSchema, null)

		instances.iterator().withCloseable { it ->
			def inst = ((ResourceIterator<Instance>)it).next();
			assertNotNull(inst)

			def geoms = GeometryUtil.getAllGeometries(inst)
			assertEquals(1, geoms.size())
			GeometryProperty<?> geom = geoms.iterator().next();
			assertNotNull(geom);
			assertNotNull(geom.getCRSDefinition());

			CoordinateReferenceSystem crs = geom.getCRSDefinition().getCRS();
			assertNotNull(crs);
			assertEquals("27700", crs.getIdentifiers().iterator().next().getCode());
		}
	}

	/**
	 * Test whether the reader honours the "defaultSrs" attribute when there is
	 * no srsName in the data.
	 *
	 * @throws Exception
	 */
	@Test
	public void testDefaultSrs() throws Exception {
		def schemaLocation = getClass().getResource("/data/hydro/hydroEx.xsd").toURI();
		def gmlLocation = getClass().getResource("/data/hydro/hydro-nosrs.gml").toURI();

		def sourceSchema = loadSchema(schemaLocation)
		def instances = loadGml(gmlLocation, sourceSchema, ["defaultSrs":"code:EPSG:4326"])

		instances.iterator().withCloseable { it ->
			def inst = ((ResourceIterator<Instance>)it).next();
			assertNotNull(inst)

			def geoms = GeometryUtil.getAllGeometries(inst)
			assertEquals(1, geoms.size())
			GeometryProperty<?> geom = geoms.iterator().next();
			assertNotNull(geom);
			assertNotNull(geom.getCRSDefinition());

			CoordinateReferenceSystem crs = geom.getCRSDefinition().getCRS();
			assertNotNull(crs);
			assertEquals("4326", crs.getIdentifiers().iterator().next().getCode());
		}
	}

	/**
	 * Test retrieving all features from a local WFS using pagination.
	 * The test data contains 250 River features served by a deegree WFS backed
	 * by a Memory Feature Store.
	 */
	@Test
	public void testWfsPagination() {
		def base = wfsBaseUrl()
		def schemaUrl = "${base}?SERVICE=WFS&VERSION=2.0.0&REQUEST=DescribeFeatureType"
		def dataUrl = "${base}?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&typenames=tf:River"
		def paging = 100
		def expected = 250

		def schema = loadSchema(URI.create(schemaUrl))

		Map<String, String> params = [
			(StreamGmlReader.PARAM_FEATURES_PER_WFS_REQUEST): paging as String,
			(StreamGmlReader.PARAM_PAGINATE_REQUEST): 'true'
		]

		def instances = loadGml(URI.create(dataUrl), schema, params)

		int count = 0
		instances.iterator().withCloseable { it ->
			while (it.hasNext()) {
				((InstanceIterator) it).skip()
				count++
			}
		}

		assertEquals(expected, count)
	}

	/**
	 * Test retrieving all features from a local WFS when using pagination and resolvedepth.
	 * Each River feature references a Basin via xlink:href. With resolvedepth=*, deegree
	 * inlines the referenced Basin inside the River's property on each paginated response.
	 * The {@link eu.esdihumboldt.hale.io.gml.reader.internal.wfs.DuplicateIDsFilterIterator}
	 * is activated by the presence of RESOLVEDEPTH in the URL.
	 * Since each River has a unique GML id the iterator returns all 250 Rivers.
	 */
	@Link(value = "1084", type = "hale")
	@Link(value = "ING-4128", type = "JIRA")
	@Test
	public void testWfsPaginationWithResolvedepth() {
		def base = wfsBaseUrl()
		def schemaUrl = "${base}?SERVICE=WFS&VERSION=2.0.0&REQUEST=DescribeFeatureType"
		def dataUrl = "${base}?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&typenames=tf:River&resolvedepth=*"
		def paging = 100
		def expected = 250

		def schema = loadSchema(URI.create(schemaUrl))

		Map<String, String> params = [
			(StreamGmlReader.PARAM_FEATURES_PER_WFS_REQUEST): paging as String,
			(StreamGmlReader.PARAM_PAGINATE_REQUEST): 'true'
		]

		def instances = loadGml(URI.create(dataUrl), schema, params)

		int count = 0
		instances.iterator().withCloseable { it ->
			while (it.hasNext()) {
				((InstanceIterator) it).skip()
				count++
			}
		}

		assertEquals(expected, count)
	}

	// helpers

	Schema loadSchema(URI schemaLocation) throws Exception {
		def schemaReader = new XmlSchemaReader()
		schemaReader.sharedTypes = null
		schemaReader.source = new DefaultInputSupplier(schemaLocation)
		def schemaReport = schemaReader.execute(null)
		assertTrue(schemaReport.isSuccess())

		schemaReader.getSchema()
	}

	InstanceCollection loadGml(URI gmlLocation, Schema schema, Map<String, String> readerParams) {
		def gmlReader = new StreamGmlReader(true)
		gmlReader.source = new DefaultInputSupplier(gmlLocation)
		gmlReader.sourceSchema = schema
		if (readerParams) {
			readerParams.each { k, v ->
				gmlReader.setParameter(k, Value.of(v))
			}
		}
		def gmlReport = gmlReader.execute(null)
		assertTrue(gmlReport.isSuccess())

		gmlReader.getInstances();
	}
}
