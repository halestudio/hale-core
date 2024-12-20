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
package eu.esdihumboldt.hale.common.schema.model.constraint.factory.type

import org.junit.Test

import eu.esdihumboldt.hale.common.schema.model.constraint.factory.AbstractPropertiesCompareConstraintFactoryTest
import eu.esdihumboldt.hale.common.schema.model.constraint.factory.ClassResolver
import eu.esdihumboldt.hale.common.schema.model.constraint.factory.OsgiClassResolver
import eu.esdihumboldt.hale.common.schema.model.constraint.type.GeometryType


/**
 * Tests for {@link GeometryTypeFactory}.
 *
 * @author Simon Templer
 */
class GeometryTypeFactoryTest extends AbstractPropertiesCompareConstraintFactoryTest<GeometryType> {

	@Test
	void testNoGeom() {
		storeRestoreTest(new GeometryType())
	}

	@Test
	void testGeom() {
		ClassResolver classResolver = new OsgiClassResolver();
		storeRestoreTest(GeometryType.get(classResolver.loadClass("org.locationtech.jts.geom.Geometry")))
	}

	@Override
	protected List<String> getPropertiesToCompare() {
		['binding', 'geometry']
	}
}
