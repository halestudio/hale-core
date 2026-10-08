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
package eu.esdihumboldt.hale.common.headless.transform

import static org.junit.Assert.*

import javax.xml.namespace.QName

import org.junit.Test

import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.instance.model.InstanceReference
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.common.schema.model.constraint.type.MappableFlag
import eu.esdihumboldt.hale.common.schema.model.constraint.type.MappingRelevantFlag
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchema
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultTypeDefinition
import eu.esdihumboldt.util.test.AbstractPlatformTest

class StoreTransformationSinkTest extends AbstractPlatformTest {

	static Instance instance(TypeDefinition type, String name) {
		DefaultInstance instance = new DefaultInstance(type, null)
		instance.addProperty(new QName('name'), name)
		instance
	}

	@Test
	void testLimboThenStore() {
		DefaultTypeDefinition type = new DefaultTypeDefinition(new QName('T'))
		type.setConstraint(MappingRelevantFlag.ENABLED)
		type.setConstraint(MappableFlag.ENABLED)
		DefaultSchema schema = new DefaultSchema('', null)
		schema.addType(type)

		StoreTransformationSink sink = new StoreTransformationSink()
		try {
			sink.setTypes(schema)
			sink.addInstance(instance(type, 'a'))
			sink.addInstance(instance(type, 'b'))
			sink.addInstance(instance(type, 'c'))
			sink.done(false)

			InstanceCollection collection = sink.instanceCollection
			// first iteration served from limbo sink
			List<Instance> first = collection.toList()
			assertEquals(['a', 'b', 'c'], first.collect { it.getProperty(new QName('name'))[0] })
			InstanceReference ref = collection.getReference(first[1])
			assertEquals 'b', collection.getInstance(ref).getProperty(new QName('name'))[0]

			// second iteration served from the store
			List<Instance> second = collection.toList()
			assertEquals(['a', 'b', 'c'], second.collect { it.getProperty(new QName('name'))[0] })
			assertEquals ref, collection.getReference(second[1])
		} finally {
			sink.dispose()
		}
	}
}
