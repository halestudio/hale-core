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
package eu.esdihumboldt.hale.common.instance.store.test

import static org.junit.Assert.*
import static org.junit.Assume.assumeTrue

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

import javax.xml.namespace.QName

import org.junit.After
import org.junit.Before
import org.junit.Test

import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.instance.model.DataSet
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.instance.model.InstanceReference
import eu.esdihumboldt.hale.common.instance.model.MetaFilter
import eu.esdihumboldt.hale.common.instance.model.TypeFilter
import eu.esdihumboldt.hale.common.instance.model.ext.InstanceCollection2
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance
import eu.esdihumboldt.hale.common.instance.model.impl.FilteredInstanceCollection
import eu.esdihumboldt.hale.common.instance.store.InstanceStore
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.common.test.TestUtil
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Contract every {@link InstanceStore} implementation must fulfill. Optional
 * capabilities (fan-out, size) are only checked if supported.
 */
abstract class AbstractInstanceStoreContractTest extends AbstractPlatformTest {

	protected Path dir
	protected InstanceStore store
	protected Schema schema
	protected TypeDefinition orderType
	protected TypeDefinition itemType

	protected abstract InstanceStore createStore(DataSet dataSet, Path directory)

	@Before
	void setUpStore() {
		TestUtil.startConversionService()
		schema = new SchemaBuilder().schema {
			def itemDef = ItemType {
				id(Long)
				name(String)
			}
			OrderType {
				item(itemDef)
				quantity(Integer)
			}
		}
		orderType = schema.getType(new QName('OrderType'))
		itemType = schema.getType(new QName('ItemType'))
		dir = Files.createTempDirectory('instance-store-contract')
		store = createStore(DataSet.SOURCE, dir)
	}

	@After
	void tearDownStore() {
		store?.close()
		dir?.toFile()?.deleteDir()
	}

	protected Instance order(int n) {
		Instance instance = new InstanceBuilder(types: schema).OrderType {
			item {
				delegate.id(n)
				name("item$n")
			}
			quantity(n)
		}
		DefaultInstance result = new DefaultInstance(instance)
		result.dataSet = DataSet.SOURCE
		result.setMetaData('ID', "o$n" as String)
		result
	}

	protected Instance item(int n) {
		Instance instance = new InstanceBuilder(types: schema).ItemType {
			delegate.id(n)
			name("item$n")
		}
		DefaultInstance result = new DefaultInstance(instance)
		result.dataSet = DataSet.SOURCE
		result
	}

	protected static Object quantity(Instance order) {
		order.getProperty(new QName('quantity'))[0]
	}

	protected List<InstanceReference> addAll(List<Instance> instances) {
		InstanceStoreWriter writer = store.openWriter()
		def refs = instances.collect { writer.add(it) }
		writer.close()
		refs
	}

	@Test
	void testResolveBeforeAndAfterFlush() {
		InstanceStoreWriter writer = store.openWriter()
		InstanceReference ref = writer.add(order(1))
		InstanceCollection collection = store.getInstances(schema)
		assertEquals 1, quantity(collection.getInstance(ref))
		writer.flush()
		assertEquals 1, quantity(collection.getInstance(ref))
		writer.close()
	}

	@Test
	void testIterateNested() {
		addAll((1..20).collect { order(it) })
		List<Instance> instances = store.getInstances(schema).toList()
		assertEquals((1..20).toList(), instances.collect { quantity(it) }.sort())
		Instance first = instances.find { quantity(it) == 1 }
		assertEquals orderType, first.definition
		Instance nested = (Instance) first.getProperty(new QName('item'))[0]
		assertEquals 'item1', nested.getProperty(new QName('name'))[0]
	}

	@Test
	void testReferencesStable() {
		def refs = addAll([order(1), order(2)])
		InstanceCollection collection = store.getInstances(schema)
		Set<InstanceReference> fromIteration = collection.toList().collect { collection.getReference(it) } as Set
		assertEquals(refs as Set, fromIteration)
		refs.each { assertEquals DataSet.SOURCE, it.dataSet }
	}

	@Test
	void testStoredTypes() {
		addAll([order(1), order(2)])
		assertEquals([orderType] as Set, store.getStoredTypes(schema))
	}

	@Test
	void testSizeIfSupported() {
		addAll((1..5).collect { order(it) } + [item(9)])
		InstanceCollection collection = store.getInstances(schema)
		assumeTrue(collection.hasSize())
		assertEquals 6, collection.size()
		assertFalse collection.isEmpty()
	}

	@Test
	void testFanoutIfSupported() {
		addAll([order(1), item(2), order(3)])
		InstanceCollection collection = store.getInstances(schema)
		assumeTrue(collection instanceof InstanceCollection2 && ((InstanceCollection2) collection).supportsFanout())
		Map<TypeDefinition, InstanceCollection> fanout = ((InstanceCollection2) collection).fanout()
		assertEquals 2, fanout[orderType].toList().size()
		assertEquals 1, fanout[itemType].toList().size()
	}

	@Test
	void testTypeFilter() {
		addAll([order(1), item(2), order(3)])
		InstanceCollection orders = FilteredInstanceCollection.applyFilter(store.getInstances(schema), new TypeFilter(orderType))
		assertEquals([1, 3], orders.toList().collect { quantity(it) }.sort())
	}

	@Test
	void testMetaFilter() {
		addAll((1..10).collect { order(it) })
		InstanceCollection selected = store.getInstances(schema).select(new MetaFilter(null, 'ID', ['o2', 'o5']))
		assertEquals([2, 5], selected.toList().collect { quantity(it) }.sort())
	}

	@Test
	void testClear() {
		addAll([order(1)])
		store.clear()
		assertTrue store.getInstances(schema).toList().isEmpty()
		addAll([order(2)])
		assertEquals([2], store.getInstances(schema).toList().collect { quantity(it) })
	}

	@Test
	void testCloseDeletesFiles() {
		addAll([order(1)])
		store.close()
		store = null
		assertFalse Files.exists(dir)
	}

	@Test
	void testConcurrentWritersAndReaders() {
		InstanceStoreWriter writer = store.openWriter()
		InstanceCollection collection = store.getInstances(schema)
		def executor = Executors.newFixedThreadPool(5)
		def writers = (0..3).collect { t ->
			executor.submit({
				(1..250).collect { i ->
					InstanceReference ref = writer.add(order(t * 1000 + i))
					assertEquals(t * 1000 + i, quantity(collection.getInstance(ref)))
					ref
				}
			} as java.util.concurrent.Callable)
		}
		def refs = writers.collectMany { it.get() }
		executor.shutdown()
		writer.close()
		assertEquals 1000, (refs as Set).size()
		assertEquals 1000, collection.toList().size()
	}
}
