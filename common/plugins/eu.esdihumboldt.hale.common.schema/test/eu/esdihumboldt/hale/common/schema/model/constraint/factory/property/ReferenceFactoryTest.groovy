/*
 * Copyright (c) 2014 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.schema.model.constraint.factory.property

import javax.xml.namespace.QName

import org.junit.Test

import eu.esdihumboldt.hale.common.core.io.Value
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.common.schema.model.constraint.factory.AbstractPropertiesCompareConstraintFactoryTest
import eu.esdihumboldt.hale.common.schema.model.constraint.property.Reference
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultTypeDefinition


/**
 * Tests for {@link ReferenceFactory}.
 *
 * @author Simon Templer
 */
class ReferenceFactoryTest extends AbstractPropertiesCompareConstraintFactoryTest<Reference> {

	/**
	 * No reference.
	 */
	@Test
	void testNoRef() {
		storeRestoreTest(new Reference())
	}

	/**
	 * A reference but unknown types.
	 */
	@Test
	void testRef() {
		storeRestoreTest(new Reference(true))
	}

	/**
	 * A reference with known referenced types.
	 */
	@Test
	void testRefTypes() {
		TypeDefinition type = new DefaultTypeDefinition(new QName('ReferencedType'))
		Reference ref = new Reference(type)

		def typeIndex = [(type): Value.of('someid')]

		storeRestoreTest(ref, typeIndex, null)
	}

	@Override
	protected List<String> getPropertiesToCompare() {
		[
			'referencedTypes',
			'reference'
		]
	}
}
