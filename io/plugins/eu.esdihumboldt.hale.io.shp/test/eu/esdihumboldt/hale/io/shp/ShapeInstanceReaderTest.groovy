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
package eu.esdihumboldt.hale.io.shp

import static org.assertj.core.api.Assertions.*
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.function.Consumer

import javax.xml.namespace.QName

import org.apache.commons.io.IOUtils
import org.junit.Test
import org.locationtech.jts.geom.Geometry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

import eu.esdihumboldt.hale.common.core.io.impl.LogProgressIndicator
import eu.esdihumboldt.hale.common.core.io.report.IOReport
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier
import eu.esdihumboldt.hale.common.instance.groovy.InstanceAccessor
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.test.TestUtil
import eu.esdihumboldt.hale.io.shp.reader.internal.ShapeInstanceReader
import eu.esdihumboldt.util.test.AbstractPlatformTest
import io.qameta.allure.Link
/**
 * Tests for reading Shapefiles.
 *
 * @author Simon Templer
 */
@CompileStatic
class ShapeInstanceReaderTest extends AbstractPlatformTest {

	/**
	 * Test reading Shapefile instances using the Shapefile as schema.
	 */
	@Test
	void testReadShapeInstances() {
		Schema xmlSchema = TestUtil.loadSchema(getClass().getClassLoader().getResource("testdata/arokfnp/ikg.shp").toURI())

		InstanceCollection instances = loadInstances(xmlSchema, getClass().getClassLoader().getResource("testdata/arokfnp/ikg.shp").toURI())

		assertNotNull(instances)
		List<Instance> list = instances.toList()

		// test count
		assertThat(list).hasSize(14)

		// instance validation
		validateArokFnpIkg(list, 'the_geom')
	}

	/**
	 * Test reading a single Shapefile from a folder.
	 */
	@Test
	void testReadFromFolder() {
		Schema xmlSchema = TestUtil.loadSchema(getClass().getClassLoader().getResource("testdata/arokfnp/ikg.shp").toURI())

		File tempDir = Files.createTempDirectory("read-shape").toFile()
		try {
			def ext = ['shp', 'dbf', 'prj', 'shx']
			ext.each {
				IOUtils.copy(getClass().getClassLoader().getResource("testdata/arokfnp/ikg.$it"), new File(tempDir, "ikg.$it"))
			}

			InstanceCollection instances = loadInstances(xmlSchema, tempDir.toURI())

			assertNotNull(instances)
			List<Instance> list = instances.toList()

			// test count
			assertThat(list).hasSize(14)

			// instance validation
			validateArokFnpIkg(list, 'the_geom')
		} finally {
			tempDir.deleteDir()
		}
	}

	/**
	 * Test reading Shapefile instances using an XML schema.
	 */
	@Test
	void testReadXsdInstances() {
		Schema xmlSchema = TestUtil.loadSchema(getClass().getClassLoader().getResource("testdata/arokfnp/arok-fnp.xsd").toURI())

		InstanceCollection instances = loadInstances(xmlSchema, getClass().getClassLoader().getResource("testdata/arokfnp/ikg.shp").toURI())

		assertNotNull(instances)
		List<Instance> list = instances.toList()

		// test count
		assertThat(list).hasSize(14)

		// instance validation
		validateArokFnpIkg(list, 'geometrie')
	}

	/**
	 * Test reading Shapefile instances using an XML schema.
	 *
	 */
	@Test
	void testShapefileInstanceFromFile() {
		Schema xmlSchema = TestUtil.loadSchema(getClass().getClassLoader().getResource("testdata/bplshp/BPL_631019_0104_003_000.shp").toURI());
		InstanceCollection instances = loadInstances(xmlSchema, getClass().getClassLoader().getResource("testdata/bplshp/BPL_631019_0104_003_000.shp").toURI())
		assertNotNull(instances)
		List<Instance> list = instances.toList()
		assertThat(list).hasSize(1)
	}



	/**
	 * Test reading Shapefile instances using an XML schema, type detection based on file name, but name has result_ prefix.
	 */
	@Test
	void testReadXsdInstancesResultPrefix1() {
		Schema xmlSchema = TestUtil.loadSchema(getClass().getClassLoader().getResource("testdata/arokfnp/arok-fnp.xsd").toURI())

		InstanceCollection instances = loadInstances(xmlSchema, getClass().getClassLoader().getResource("testdata/arokfnp/alt_name/result_ikg.shp").toURI())

		assertNotNull(instances)
		List<Instance> list = instances.toList()

		// test count
		assertThat(list).hasSize(14)

		// instance validation
		validateArokFnpIkg(list, 'geometrie')
	}

	/**
	 * Test reading Shapefile instances using an XML schema, type detection based on file name, but name has result_ prefix.
	 */
	@CompileStatic(TypeCheckingMode.SKIP) // due to strange Groovy compile error on assertThat
	@Test
	void testReadXsdInstancesResultPrefix2() {
		Schema xmlSchema = TestUtil.loadSchema(getClass().getClassLoader().getResource("testdata/arokfnp/arok-fnp.xsd").toURI())

		InstanceCollection instances = loadInstances(xmlSchema, getClass().getClassLoader().getResource("testdata/arokfnp/alt_name/result_fnpgelt.shp").toURI())

		assertNotNull(instances)
		List<Instance> list = instances.toList()

		// test count
		assertThat(list).hasSize(14)

		// type validation
		Set<String> types = list.collect { it.definition.displayName }.toSet()
		assertThat(types)
				.hasSize(1)
				.containsExactly('fnpgelt')
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	private void validateArokFnpIkg(List<Instance> instances, String geometryPropertyName) {
		Map<String, List<Instance>> instancesByType = [:]

		instances.iterator().with {
			while(it.hasNext()) {
				Instance instance = it.next()

				String typeName = instance.getDefinition().displayName
				instancesByType.computeIfAbsent(typeName) { []}
				instancesByType[typeName] << instance
			}
		}

		// check count of feature types
		assertThat(instancesByType)
				.hasSize(1)
				.containsOnlyKeys('ikg')

		// check counts per type

		assertEquals 14, instancesByType.ikg.size()

		// check ikg
		def ikg =  { new InstanceAccessor(instancesByType.ikg) }
		assertThat(ikg().aktennr.values().findAll())
				.hasSize(2)

		assertThat(ikg().bezeich.values())
				.containsExactlyInAnyOrder('Dußlingen-Gomaringen-Nehren, Musburg-Hönisch', 'Engstingen-Hohenstein-Trochtelfingen, Haidt', 'FNP Meersburg (GVV)', 'IKG Berg', 'Immenstaad-Friedrichshafen, Steigwiesen', 'Kirchberg-Weihungstal (Staig), Gassenäcker', 'Laichinger Alb', 'Meßkirch, Industriepark Nördlicher Bodensee', 'Munderkingen', 'Ostrach, Königsegg', 'Reutlingen-Kirchentellinsfurt, Mahden', 'Reutlingen-Kusterdingen, Mark West', 'Wangen-Amtzell, Geiselharz-Schauwies', 'Winterlingen-Straßberg, Vogelherd/Längenfeld')

		assertThat(ikg()."$geometryPropertyName".values().findAll())
				.as('ikg geometries')
				.hasSize(14)
				.allSatisfy({ value ->
					assertThat(value)
							.isInstanceOf(GeometryProperty)
					assertThat(value.geometry)
							.isNotNull()
							.isInstanceOf(Geometry)
					assertThat(value.CRSDefinition)
							.isNotNull()
					assertThat(value.CRSDefinition.CRS)
							.isNotNull()
				} as Consumer)
	}

	/**
	 * Test reading Shapefile instances using an XML schema.
	 *
	 * Special is that an element "name" is defined in the XSD, which should be used, but there is also the "name" attribute defined in the GML base type.
	 * Original behavior was that neither property is populated because no unique property could be identified.
	 */
	@Test
	@Link(value = "ING-4543", type = "JIRA")
	@CompileStatic(TypeCheckingMode.SKIP)
	void testReadXsdInstancesName() {
		Schema xmlSchema = TestUtil.loadSchema(getClass().getClassLoader().getResource("testdata/shape-xsd-name/shape.xsd").toURI())

		InstanceCollection instances = loadInstances(xmlSchema, getClass().getClassLoader().getResource("testdata/shape-xsd-name/shape.shp").toURI())

		assertNotNull(instances)
		List<Instance> list = instances.toList()

		// test count
		assertThat(list).hasSize(3)

		// check instances
		def all =  { new InstanceAccessor(list) }

		def expectedNames = [
			'Allgäubahn',
			'Bodensee-Gürtelbahn',
			'Donaubahn'
		].toArray()

		// verify that names are present
		assertThat(all().name.values())
				.containsExactlyInAnyOrder(expectedNames)

		// verify that correct name property is used
		def nameProperty = new QName('https://www.geoportal-raumordnung-bw.de/planAtlas-rp', 'name')
		def names = list.collect { it.getProperty(nameProperty)[0] }.toList()
		assertThat(names)
				.containsExactlyInAnyOrder(expectedNames)

		// also check for id element (which is present in the schema in addition to the GML id attribute)
		def idProperty = new QName('https://www.geoportal-raumordnung-bw.de/planAtlas-rp', 'id')
		def ids = list.collect { it.getProperty(idProperty)[0] }.toList()
		assertThat(ids)
				.containsExactlyInAnyOrder('ln_8164_843_1', 'ln_8164_843_2', 'ln_8164_843_3')

		// GML id attribute should be empty
		def gmlIdProperty = new QName('http://www.opengis.net/gml/3.2', 'id')
		def gmlIds = list.collect {
			def prop = it.getProperty(gmlIdProperty)
			if (prop) {
				prop[0]
			}
			else {
				null
			}
		}.findAll()
		assertThat(gmlIds)
				.isEmpty()
	}

	/**
	 * Test reading Shapefile instance from URL provided by nginx container.
	 * If Shapefile is provided via URL and contains FIX file then ShappeInstanceReader fails to read the instances.
	 */
	@Test
	@CompileStatic(TypeCheckingMode.SKIP)
	@TypeChecked
	void testShapefileInstanceFromNginxUrl() {

		def mapOfFile = new HashMap();
		mapOfFile.put("BPL_631019_0104_003_000.dbf", "testdata/bplshp/BPL_631019_0104_003_000.dbf");
		mapOfFile.put("BPL_631019_0104_003_000.prj", "testdata/bplshp/BPL_631019_0104_003_000.prj");
		mapOfFile.put("BPL_631019_0104_003_000.shp", "testdata/bplshp/BPL_631019_0104_003_000.shp");
		mapOfFile.put("BPL_631019_0104_003_000.shx", "testdata/bplshp/BPL_631019_0104_003_000.shx");
		mapOfFile.put("BPL_631019_0104_003_000.fix", "testdata/bplshp/BPL_631019_0104_003_000.fix");

		def nginxContainer = createNginxContainerToProvideFileUrls(mapOfFile)

		def host = nginxContainer.getHost()
		def exposedPort = nginxContainer.getMappedPort(80)
		def shapeFileUrl = "http://$host:$exposedPort/BPL_631019_0104_003_000.shp"
		Schema xmlSchema = TestUtil.loadSchema(URI.create(shapeFileUrl));
		InstanceCollection instances = loadInstances(xmlSchema, URI.create(shapeFileUrl))
		assertNotNull(instances)
		assertThat(instances.toList()).hasSize(1)
	}

	/**
	 * Test reading Shapefile instance from URL provided by nginx container.
	 * If Shapefile is provided via URL and contains FIX file then ShappeInstanceReader fails to read the instances.
	 * This test does not provide the FIX file, so it should also work.
	 */
	@Test
	@CompileStatic(TypeCheckingMode.SKIP)
	@TypeChecked
	void testShapefileInstanceFromNginxUrlWithoutFixFile() {
		def mapOfFile = new HashMap();
		mapOfFile.put("BPL_631019_0104_003_000.dbf", "testdata/bplshp/BPL_631019_0104_003_000.dbf");
		mapOfFile.put("BPL_631019_0104_003_000.prj", "testdata/bplshp/BPL_631019_0104_003_000.prj");
		mapOfFile.put("BPL_631019_0104_003_000.shp", "testdata/bplshp/BPL_631019_0104_003_000.shp");
		mapOfFile.put("BPL_631019_0104_003_000.shx", "testdata/bplshp/BPL_631019_0104_003_000.shx");

		def nginxContainer = createNginxContainerToProvideFileUrls(mapOfFile)

		def host = nginxContainer.getHost()
		def exposedPort = nginxContainer.getMappedPort(80)
		def shapeFileUrl = "http://$host:$exposedPort/BPL_631019_0104_003_000.shp"
		Schema xmlSchema = TestUtil.loadSchema(URI.create(shapeFileUrl));
		InstanceCollection instances = loadInstances(xmlSchema, URI.create(shapeFileUrl))
		assertNotNull(instances)
		assertThat(instances.toList()).hasSize(1)
	}

	/**
	 * Test reading the base name of a shapefile URL.
	 */
	@CompileStatic(TypeCheckingMode.SKIP)
	@Test
	void testBaseNameFromURL(){
		def mapOfFile = new HashMap();
		mapOfFile.put("BPL_631019_0104_003_000.shp", "testdata/bplshp/BPL_631019_0104_003_000.shp");
		def nginxContainer = createNginxContainerToProvideFileUrls(mapOfFile)
		def host = nginxContainer.getHost()
		def exposedPort = nginxContainer.getMappedPort(80)
		def shapeFileUrl = "http://$host:$exposedPort/BPL_631019_0104_003_000.shp"
		def url = URI.create(shapeFileUrl).toURL()
		def reader = new ShapeInstanceReader()
		def actual =reader.getBaseName(url).toString()
		def expected = "http://$host:$exposedPort/BPL_631019_0104_003_000".toString()
		assertEquals(expected, actual)
	}

	/**
	 * Test reading the base name of a shapefile file.
	 */
	@CompileStatic(TypeCheckingMode.SKIP)
	@Test
	void testBaseNameFromFile(){
		def loc = getClass().getClassLoader().getResource("testdata/bplshp/BPL_631019_0104_003_000.shp").toURI()
		def file = new File(loc)
		def reader = new ShapeInstanceReader()
		def actual =reader.getBaseName(file)
		def filePath = file.getAbsoluteFile().toString()
		def expected = filePath.substring(0,filePath.lastIndexOf("."))
		assertEquals(expected, actual)
	}

	// helpers

	/**
	 * Load an instance collection from a Shapefile.
	 *
	 * @param schema the schema to use
	 * @param resource the file to load
	 * @return the loaded instance collection
	 */
	static InstanceCollection loadInstances(Schema schema, URI resource) {
		ShapeInstanceReader reader = new ShapeInstanceReader()

		reader.setSource(new DefaultInputSupplier(resource))
		reader.setSourceSchema(schema)

		reader.setCharset(StandardCharsets.UTF_8)

		IOReport report = reader.execute(new LogProgressIndicator())

		assertTrue(report.isSuccess())
		assertTrue(report.getErrors().isEmpty())

		return reader.getInstances();
	}

	/**
	 * Create a Testcontainers Nginx container to provide file URLs.
	 * @param network
	 * @param mapOfFile
	 * @return
	 */
	@CompileStatic(TypeCheckingMode.SKIP)
	@TypeChecked
	static GenericContainer createNginxContainerToProvideFileUrls(HashMap mapOfFile) {
		def network = Network.newNetwork();
		def nginxContainer = new GenericContainer<>(DockerImageName.parse("nginx:latest"))
				.withNetwork(network)
				.withExposedPorts(80)
				.waitingFor(new HttpWaitStrategy())
		// add all files
		for (Map.Entry<String, String> entry : mapOfFile.entrySet()) {
			nginxContainer.withCopyFileToContainer(
					MountableFile.forClasspathResource(entry.getValue()),
					"/usr/share/nginx/html/" + entry.getKey())
		}
		nginxContainer.start();
		nginxContainer
	}
}
