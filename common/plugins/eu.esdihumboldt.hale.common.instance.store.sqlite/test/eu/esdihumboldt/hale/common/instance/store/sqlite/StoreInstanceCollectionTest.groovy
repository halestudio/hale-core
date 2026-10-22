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
import eu.esdihumboldt.hale.common.instance.model.ext.InstanceIterator
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance
import eu.esdihumboldt.hale.common.instance.model.impl.FilteredInstanceCollection
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.common.test.TestUtil
import eu.esdihumboldt.util.test.AbstractPlatformTest

class StoreInstanceCollectionTest extends AbstractPlatformTest {

	Path dir
	StoreContext ctx
	Schema schema
	TypeDefinition itemType
	TypeDefinition personType

	@Before
	void setUp() {
		TestUtil.startConversionService()
		dir = Files.createTempDirectory('sqlite-collection-test')
		ctx = new StoreContext(DataSet.SOURCE)
		ctx.database = new SqliteDatabase(dir)
		schema = new SchemaBuilder().schema {
			ItemType {
				id(Long)
				name(String)
			}
			PersonType { name(String) }
		}
		itemType = schema.getType(new QName('ItemType'))
		personType = schema.getType(new QName('PersonType'))
	}

	@After
	void tearDown() {
		ctx.database.close()
		dir.toFile().deleteDir()
	}

	Instance item(long id) {
		Instance instance = new InstanceBuilder(types: schema).ItemType {
			delegate.id(id)
			name("item$id")
		}
		DefaultInstance result = new DefaultInstance(instance)
		result.setMetaData('ID', "i$id" as String)
		result
	}

	Instance person(String name) {
		new InstanceBuilder(types: schema).PersonType { delegate.name(name) }
	}

	List<InstanceReference> write(List<Instance> instances) {
		SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)
		def refs = instances.collect { writer.add(it) }
		writer.close()
		refs
	}

	@Test
	void testIterationAcrossChunks() {
		write((1..2500).collect { item(it) })
		StoreInstanceCollection collection = new StoreInstanceCollection(ctx, schema)

		def ids = collection.toList().collect { it.getProperty(new QName('id'))[0] }
		assertEquals((1L..2500L).toList(), ids)
		assertTrue collection.hasSize()
		assertEquals 2500, collection.size()
		assertFalse collection.isEmpty()
	}

	@Test
	void testFanoutAndTypeFilter() {
		write([
			item(1),
			person('a'),
			item(2),
			person('b'),
			person('c')
		])
		StoreInstanceCollection collection = new StoreInstanceCollection(ctx, schema)

		assertTrue collection.supportsFanout()
		Map<TypeDefinition, InstanceCollection> fanout = collection.fanout()
		assertEquals 2, fanout[itemType].size()
		assertEquals 3, fanout[personType].size()
		assertEquals 3, fanout[personType].toList().size()

		InstanceCollection persons = FilteredInstanceCollection.applyFilter(collection, new TypeFilter(personType))
		assertEquals(['a', 'b', 'c'], persons.toList().collect { it.getProperty(new QName('name'))[0] })
	}

	@Test
	void testTypePeekAndSkip() {
		write([item(1), person('a'), item(2)])
		InstanceIterator it = (InstanceIterator) new StoreInstanceCollection(ctx, schema).iterator()
		List<String> seen = []
		while (it.hasNext()) {
			if (it.typePeek() == personType) {
				it.skip()
			}
			else {
				seen << it.next().getProperty(new QName('name'))[0]
			}
		}
		assertEquals(['item1', 'item2'], seen)
	}

	@Test
	void testMetaFilterSql() {
		write((1..10).collect { item(it) })
		StoreInstanceCollection collection = new StoreInstanceCollection(ctx, schema)

		InstanceCollection selected = collection.select(new MetaFilter(null, 'ID', ['i3', 'i7']))
		assertEquals(['item3', 'item7'], selected.toList().collect { it.getProperty(new QName('name'))[0] })

		InstanceCollection typed = collection.select(new MetaFilter(personType, 'ID', ['i3']))
		assertTrue typed.toList().isEmpty()
	}

	@Test
	void testMetaFilterFallbacks() {
		write((1..3).collect { item(it) })
		StoreInstanceCollection collection = new StoreInstanceCollection(ctx, schema)

		// empty value set matches all instances (MetaFilter semantics)
		assertEquals 3, collection.select(new MetaFilter(null, 'ID', [])).toList().size()
		// non-String values are filtered in memory
		assertTrue collection.select(new MetaFilter(null, 'ID', [3L])).toList().isEmpty()
	}

	@Test
	void testReferences() {
		SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)
		InstanceReference ref = writer.add(item(1))
		StoreInstanceCollection collection = new StoreInstanceCollection(ctx, schema)

		// resolvable before commit
		assertEquals 'item1', collection.getInstance(ref).getProperty(new QName('name'))[0]
		writer.close()

		Instance read = collection.toList()[0]
		assertEquals ref, collection.getReference(read)
		assertEquals ref.hashCode(), collection.getReference(read).hashCode()
		assertEquals 'item1', collection.getInstance(ref).getProperty(new QName('name'))[0]

		// reference of another store
		StoreInstanceReference foreign = new StoreInstanceReference(UUID.randomUUID(), 1L, DataSet.SOURCE, itemType)
		assertNull collection.getInstance(foreign)
		assertNull collection.getReference(item(5))
	}

	@Test
	void testIterationDuringWrite() {
		SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)
		StoreInstanceCollection collection = new StoreInstanceCollection(ctx, schema)
		Thread producer = Thread.start {
			(1..3000).each { writer.add(item(it)) }
		}
		boolean running = true
		while (running) {
			running = producer.alive
			def ids = collection.toList().collect { it.getProperty(new QName('id'))[0] }
			assertEquals('no duplicates in one iteration', ids.size(), (ids as Set).size())
		}
		producer.join()
		writer.close()
		def ids = collection.toList().collect { it.getProperty(new QName('id'))[0] }
		assertEquals 3000, ids.size()
		assertEquals 3000, (ids as Set).size()
	}

	@Test
	void testCorruptPayloadIsSkipped() {
		write([item(1), item(2)])
		def c = ctx.database.writerConnection()
		c.createStatement().withCloseable {
			it.executeUpdate("UPDATE instances SET payload = x'FF' WHERE id = 1")
		}
		c.commit()

		def names = new StoreInstanceCollection(ctx, schema).toList().collect { it.getProperty(new QName('name'))[0] }
		assertEquals(['item2'], names)
	}

	private void corrupt(String where) {
		def c = ctx.database.writerConnection()
		c.createStatement().withCloseable {
			it.executeUpdate("UPDATE instances SET payload = x'FF' WHERE $where")
		}
		c.commit()
	}

	@Test
	void testSkippedRowsAreNotDecoded() {
		write([
			item(1),
			person('a'),
			item(2),
			person('b')
		])
		corrupt("type = ${ctx.types.idOf(personType.name)}")

		InstanceIterator it = (InstanceIterator) new StoreInstanceCollection(ctx, schema).iterator()
		List<String> seen = []
		while (it.hasNext()) {
			if (it.typePeek() == personType) {
				it.skip()
			}
			else {
				seen << it.next().getProperty(new QName('name'))[0]
			}
		}
		assertEquals(['item1', 'item2'], seen)
	}

	@Test
	void testCorruptLastRow() {
		write([item(1), item(2)])
		corrupt('id = 2')
		def it = new StoreInstanceCollection(ctx, schema).iterator()
		assertTrue it.hasNext()
		assertEquals 'item1', it.next().getProperty(new QName('name'))[0]
		assertTrue it.hasNext()
		try {
			it.next()
			fail('expected NoSuchElementException')
		} catch (NoSuchElementException e) {
			// expected
		}
	}

	@Test
	void testWithoutTypeIndex() {
		def refs = write([item(1)])
		StoreInstanceCollection collection = new StoreInstanceCollection(ctx, null)
		assertTrue collection.toList().isEmpty()
		assertEquals 'item1', collection.getInstance(refs[0]).getProperty(new QName('name'))[0]
	}
}
