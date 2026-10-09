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
package eu.esdihumboldt.hale.common.instance.store.sqlite.codec

import static org.junit.Assert.*

import javax.xml.namespace.QName

import org.junit.Before
import org.junit.Test

import eu.esdihumboldt.hale.common.core.report.SimpleLog
import eu.esdihumboldt.hale.common.core.report.SimpleLogContext
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.instance.model.DataSet
import eu.esdihumboldt.hale.common.instance.model.Group
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.test.TestUtil
import eu.esdihumboldt.util.test.AbstractPlatformTest

class InstanceCodecTest extends AbstractPlatformTest {

	static final UUID KEY = UUID.randomUUID()

	Schema schema
	InstanceCodec codec = new InstanceCodec()

	@Before
	void setUp() {
		TestUtil.startConversionService()
		schema = new SchemaBuilder().schema {
			def itemType = ItemType {
				id(Long)
				name(String)
			}
			OtherType { code(String) }
			OrderType {
				item(itemType)
				quantity(Integer)
			}
		}
	}

	Instance order() {
		new InstanceBuilder(types: schema).OrderType {
			item {
				id(12)
				name('item12')
			}
			quantity(3)
		}
	}

	StoredInstance roundTrip(Instance instance, boolean withTypes = true) {
		byte[] data = codec.encode(instance)
		codec.decode(data, instance.definition, DataSet.SOURCE, KEY, 7L, withTypes ? schema : null)
	}

	@Test
	void testNestedInstance() {
		StoredInstance result = roundTrip(order())

		assertEquals schema.getType(new QName('OrderType')), result.definition
		assertEquals DataSet.SOURCE, result.dataSet
		assertEquals 7L, result.id
		assertEquals KEY, result.storeKey
		assertEquals 3, result.getProperty(new QName('quantity'))[0]
		Instance item = (Instance) result.getProperty(new QName('item'))[0]
		assertEquals schema.getType(new QName('ItemType')), item.definition
		assertEquals 12L, item.getProperty(new QName('id'))[0]
		assertEquals 'item12', item.getProperty(new QName('name'))[0]
	}

	@Test
	void testValueAndMetadata() {
		DefaultInstance instance = new DefaultInstance(order())
		instance.value = 'the value'
		instance.setMetaData('ID', 'a1')
		instance.setMetaData('SourceID', 's1', 's2')

		StoredInstance result = roundTrip(instance)

		assertEquals 'the value', result.value
		assertEquals(['a1'], result.getMetaData('ID'))
		assertEquals(['s1', 's2'], result.getMetaData('SourceID'))
	}

	@Test
	void testDifferentNestedTypeResolvedViaTypeIndex() {
		def otherType = schema.getType(new QName('OtherType'))
		DefaultInstance other = new DefaultInstance(otherType, null)
		other.addProperty(new QName('code'), 'x')
		DefaultInstance instance = new DefaultInstance(schema.getType(new QName('OrderType')), null)
		instance.addProperty(new QName('item'), other)

		Instance withTypes = (Instance) roundTrip(instance).getProperty(new QName('item'))[0]
		assertEquals otherType, withTypes.definition

		Instance withoutTypes = (Instance) roundTrip(instance, false).getProperty(new QName('item'))[0]
		assertEquals schema.getType(new QName('ItemType')), withoutTypes.definition
		assertEquals 'x', withoutTypes.getProperty(new QName('code'))[0]
	}

	@Test
	void testSchemaLessGroups() {
		Instance instance = new InstanceBuilder().instance {
			name('test')
			type { href('http://example.com/some-location') }
		}

		StoredInstance result = roundTrip(instance)

		assertNull result.definition
		assertEquals 'test', result.getProperty(new QName('name'))[0]
		Group type = (Group) result.getProperty(new QName('type'))[0]
		assertFalse type instanceof Instance
		assertEquals 'http://example.com/some-location', type.getProperty(new QName('href'))[0]
	}

	@Test
	void testDroppedValue() {
		DefaultInstance instance = new DefaultInstance(order())
		instance.addProperty(new QName('quantity'), new Object())
		List<String> warnings = []
		SimpleLog log = [warn: { String msg, Throwable e -> warnings << msg },
			error: { String msg, Throwable e -> warnings << msg },
			info: { String msg, Throwable e -> }] as SimpleLog

		StoredInstance result = SimpleLogContext.withLog(log, { roundTrip(instance) } as java.util.function.Supplier)

		assertEquals([3], result.getProperty(new QName('quantity')) as List)
		assertEquals 1, warnings.size()
	}
}
