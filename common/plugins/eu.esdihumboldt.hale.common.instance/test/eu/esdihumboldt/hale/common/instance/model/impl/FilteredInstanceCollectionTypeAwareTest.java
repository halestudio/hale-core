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
package eu.esdihumboldt.hale.common.instance.model.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.junit.Test;

import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.model.TypeAwareFilter;
import eu.esdihumboldt.hale.common.instance.model.ext.impl.PerTypeInstanceCollection;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultTypeDefinition;

public class FilteredInstanceCollectionTypeAwareTest {

	private static final QName NAME = new QName("name");
	private static final TypeDefinition TYPE_A = new DefaultTypeDefinition(new QName("A"));
	private static final TypeDefinition TYPE_B = new DefaultTypeDefinition(new QName("B"));
	private static final TypeDefinition TYPE_C = new DefaultTypeDefinition(new QName("C"));

	private static Instance instance(TypeDefinition type, String name) {
		DefaultInstance instance = new DefaultInstance(type, null);
		instance.addProperty(NAME, name);
		return instance;
	}

	private static InstanceCollection failOnIterate() {
		return new DefaultInstanceCollection() {

			@Override
			public ResourceIterator<Instance> iterator() {
				throw new AssertionError("collection must not be iterated");
			}
		};
	}

	private static class NameFilter implements TypeAwareFilter {

		private final Set<TypeDefinition> types;
		private final String prefix;

		NameFilter(Set<TypeDefinition> types, String prefix) {
			this.types = types;
			this.prefix = prefix;
		}

		@Override
		public boolean match(Instance instance) {
			return types.contains(instance.getDefinition())
					&& ((String) instance.getProperty(NAME)[0]).startsWith(prefix);
		}

		@Override
		public Set<TypeDefinition> getTypes() {
			return types;
		}
	}

	private static List<String> names(InstanceCollection collection) {
		return collection.toList().stream().map(i -> (String) i.getProperty(NAME)[0])
				.collect(Collectors.toList());
	}

	private static PerTypeInstanceCollection perType() {
		Map<TypeDefinition, InstanceCollection> map = new LinkedHashMap<>();
		map.put(TYPE_A, new DefaultInstanceCollection(
				List.of(instance(TYPE_A, "a1"), instance(TYPE_A, "x1"))));
		map.put(TYPE_B, failOnIterate());
		map.put(TYPE_C, new DefaultInstanceCollection(List.of(instance(TYPE_C, "a2"))));
		return new PerTypeInstanceCollection(map);
	}

	@Test
	public void testOnlyCollectionsOfFilterTypesAreRead() {
		InstanceCollection result = FilteredInstanceCollection.applyFilter(perType(),
				new NameFilter(Set.of(TYPE_A), "a"));
		assertEquals(List.of("a1"), names(result));
	}

	@Test
	public void testMultipleTypes() {
		InstanceCollection result = FilteredInstanceCollection.applyFilter(perType(),
				new NameFilter(Set.of(TYPE_A, TYPE_C), "a"));
		List<String> names = names(result);
		assertEquals(2, names.size());
		assertTrue(names.containsAll(List.of("a1", "a2")));
	}

	@Test
	public void testTypeNotPresent() {
		TypeDefinition other = new DefaultTypeDefinition(new QName("Other"));
		InstanceCollection result = FilteredInstanceCollection.applyFilter(perType(),
				new NameFilter(Set.of(other), "a"));
		assertTrue(result.toList().isEmpty());
	}

	@Test
	public void testCollectionWithoutFanout() {
		InstanceCollection plain = new DefaultInstanceCollection(
				List.of(instance(TYPE_A, "a1"), instance(TYPE_B, "a3"), instance(TYPE_C, "a2")));
		InstanceCollection result = FilteredInstanceCollection.applyFilter(plain,
				new NameFilter(Set.of(TYPE_A, TYPE_C), "a"));
		List<String> names = names(result);
		assertEquals(2, names.size());
		assertTrue(names.containsAll(List.of("a1", "a2")));
	}
}
