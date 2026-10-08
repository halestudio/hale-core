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
package eu.esdihumboldt.hale.common.headless.transform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.junit.Test;

import eu.esdihumboldt.hale.common.core.report.Report;
import eu.esdihumboldt.hale.common.core.report.ReportHandler;
import eu.esdihumboldt.hale.common.core.report.Statistics;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.model.TypeFilter;
import eu.esdihumboldt.hale.common.instance.model.ext.impl.PerTypeInstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.impl.FilteredInstanceCollection;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultTypeDefinition;

/**
 * Tests for {@link StatsCountInstanceCollection}.
 */
public class StatsCountInstanceCollectionTest {

	/**
	 * Fan-out of a type filtered source must still report the loaded instances of
	 * the selected type.
	 */
	@Test
	public void testFanoutKeepsStatistics() {
		TypeDefinition typeA = new DefaultTypeDefinition(new QName("typeA"));
		TypeDefinition typeB = new DefaultTypeDefinition(new QName("typeB"));

		Map<TypeDefinition, InstanceCollection> parts = new HashMap<>();
		parts.put(typeA, new DefaultInstanceCollection(List.of(instance(typeA), instance(typeA))));
		parts.put(typeB, new DefaultInstanceCollection(List.of(instance(typeB))));
		PerTypeInstanceCollection perType = new PerTypeInstanceCollection(parts);

		List<Report<?>> reports = new ArrayList<>();
		ReportHandler handler = reports::add;

		InstanceCollection source = new StatsCountInstanceCollection(perType, handler);
		InstanceCollection filtered = FilteredInstanceCollection.applyFilter(source,
				new TypeFilter(typeA));
		assertNotNull(filtered);

		int count = 0;
		ResourceIterator<Instance> it = filtered.iterator();
		try {
			while (it.hasNext()) {
				assertEquals(typeA, it.next().getDefinition());
				count++;
			}
		} finally {
			it.close();
		}

		assertEquals(2, count);
		assertEquals(1, reports.size());

		Report<?> report = reports.get(0);
		assertTrue(report instanceof Statistics);
		Object loaded = ((Statistics) report).stats().at("loadedPerType").at("typeA").value();
		assertEquals(2L, ((Number) loaded).longValue());
	}

	private static Instance instance(TypeDefinition type) {
		return new DefaultInstance(type, DataSet.SOURCE);
	}
}
