
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
package eu.esdihumboldt.hale.io.wfs.test.capabilities

import static org.junit.Assert.*

import groovy.transform.CompileStatic

import javax.xml.namespace.QName

import org.junit.Test

import eu.esdihumboldt.hale.io.wfs.WFSVersion
import eu.esdihumboldt.hale.io.wfs.capabilities.CapabilitiesHelper
import eu.esdihumboldt.hale.io.wfs.capabilities.WFSCapabilities
import eu.esdihumboldt.util.test.AbstractPlatformTest

@CompileStatic
class CapabilitiesHelperTest extends AbstractPlatformTest {

	private static final String PLUGIN_NAME = 'eu.esdihumboldt.hale.io.wfs.test'

	private static final URL WFS_CAPABILITIES_2 = CapabilitiesHelperTest.classLoader.getResource('resources/wfsCapabilities2.xml')
	private static final URL WFS_CAPABILITIES_11 = CapabilitiesHelperTest.classLoader.getResource('resources/wfsCapabilities11.xml')

	private static final QName FEATURE_TYPE_NAME = new QName('urn:x-inspire:specification:gmlas:Addresses:3.0', 'Address')

	private static final String POST_URL  = 'http://localhost:8070/services/wfs'

	@Test
	void testLoadWFS2Capabilities() {
		WFSCapabilities caps
		WFS_CAPABILITIES_2.withInputStream {
			caps = CapabilitiesHelper.loadCapabilities(it)
		}

		assertNotNull(caps)

		// check version
		assertEquals(WFSVersion.V2_0_0, caps.version)

		// check transaction URL
		assertNotNull('Transaction operation missing', caps.transactionOp)
		assertEquals('Wrong transaction POST URL', POST_URL, caps.transactionOp.httpPostUrl)

		// check feature types
		assertEquals(1, caps.featureTypes.size())
		QName ft = caps.featureTypes.keySet().iterator().next()
		assertEquals(FEATURE_TYPE_NAME, ft)

		def ftInfo = caps.featureTypes[ft]
		assertNotNull(ftInfo)
		assertEquals('urn:ogc:def:crs:EPSG::4258', ftInfo.defaultCrs)
		assertNotNull(ftInfo.wgs84BBox)
	}

	@Test
	void testLoadWFS11Capabilities() {
		WFSCapabilities caps
		WFS_CAPABILITIES_11.withInputStream {
			caps = CapabilitiesHelper.loadCapabilities(it)
		}

		assertNotNull(caps)

		// check version
		assertEquals(WFSVersion.V1_1_0, caps.version)

		// check transaction URL
		assertNotNull('Transaction operation missing', caps.transactionOp)
		assertEquals('Wrong transaction POST URL', POST_URL, caps.transactionOp.httpPostUrl)

		// check feature types
		assertEquals(1, caps.featureTypes.size())
		QName ft = caps.featureTypes.keySet().iterator().next()
		assertEquals(FEATURE_TYPE_NAME, ft)

		def ftInfo = caps.featureTypes[ft]
		assertNotNull(ftInfo)
		assertEquals('urn:ogc:def:crs:EPSG::4258', ftInfo.defaultCrs)
		assertNotNull(ftInfo.wgs84BBox)
	}
}
