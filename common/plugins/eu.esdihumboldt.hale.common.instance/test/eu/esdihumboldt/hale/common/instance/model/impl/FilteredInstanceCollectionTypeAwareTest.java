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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.junit.Test;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.model.TypeAwareFilter;
import eu.esdihumboldt.hale.common.instance.model.ext.InstanceCollection2;
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

	/**
	 * Reference of a {@link ResolvingFanoutCollection}.
	 */
	private static class NameReference implements InstanceReference {

		private final String name;

		NameReference(String name) {
			this.name = name;
		}

		@Override
		public DataSet getDataSet() {
			return null;
		}
	}

	/**
	 * Fan-out collection that is no {@link MultiInstanceCollection} and resolves
	 * references of any of its instances itself (like a store collection).
	 */
	private static class ResolvingFanoutCollection extends DefaultInstanceCollection
			implements InstanceCollection2 {

		private final Map<TypeDefinition, InstanceCollection> fanout = new LinkedHashMap<>();

		ResolvingFanoutCollection(List<Instance> instances) {
			super(instances);
			for (Instance instance : instances) {
				InstanceCollection part = fanout.computeIfAbsent(instance.getDefinition(),
						t -> new DefaultInstanceCollection());
				((DefaultInstanceCollection) part).add(instance);
			}
		}

		@Override
		public boolean supportsFanout() {
			return true;
		}

		@Override
		public Map<TypeDefinition, InstanceCollection> fanout() {
			return fanout;
		}

		@Override
		public InstanceReference getReference(Instance instance) {
			return new NameReference(
					(String) InstanceDecorator.getRoot(instance).getProperty(NAME)[0]);
		}

		@Override
		public Instance getInstance(InstanceReference reference) {
			String name = ((NameReference) reference).name;
			return toList().stream().filter(i -> name.equals(i.getProperty(NAME)[0])).findFirst()
					.orElse(null);
		}
	}

	/**
	 * References of root instances (as used by the index join handler) must be
	 * resolvable on the result of a fan-out based type aware selection.
	 */
	private static void assertRootReferencesResolvable(InstanceCollection source) {
		InstanceCollection result = FilteredInstanceCollection.applyFilter(source,
				new NameFilter(Set.of(TYPE_A, TYPE_C), "a"));
		List<String> resolved = new ArrayList<>();
		try (ResourceIterator<Instance> it = result.iterator()) {
			while (it.hasNext()) {
				Instance instance = it.next();

				// reference of the instance as returned by the iterator
				InstanceReference ref = result.getReference(instance);
				assertNotNull(ref);
				assertEquals(instance.getProperty(NAME)[0],
						result.getInstance(ref).getProperty(NAME)[0]);

				// reference of the root instance
				Instance root = InstanceDecorator.getRoot(instance);
				InstanceReference rootRef = result.getReference(root);
				assertNotNull(rootRef);
				Instance rootResolved = result.getInstance(rootRef);
				assertNotNull(rootResolved);
				assertSame(root.getDefinition(), rootResolved.getDefinition());
				resolved.add((String) rootResolved.getProperty(NAME)[0]);
			}
		}
		assertEquals(2, resolved.size());
		assertTrue(resolved.containsAll(List.of("a1", "a2")));
	}

	@Test
	public void testRootReferencesPerTypeCollection() {
		assertRootReferencesResolvable(perType());
	}

	@Test
	public void testRootReferencesResolvingFanoutCollection() {
		assertRootReferencesResolvable(new ResolvingFanoutCollection(List.of(instance(TYPE_A, "a1"),
				instance(TYPE_A, "x1"), instance(TYPE_B, "a3"), instance(TYPE_C, "a2"))));
	}

	@Test
	public void testReferencesDelegateToOriginal() {
		InstanceCollection result = FilteredInstanceCollection.applyFilter(
				new ResolvingFanoutCollection(
						List.of(instance(TYPE_A, "a1"), instance(TYPE_C, "a2"))),
				new NameFilter(Set.of(TYPE_A, TYPE_C), "a"));
		for (Instance instance : result.toList()) {
			assertTrue(result
					.getReference(InstanceDecorator.getRoot(instance)) instanceof NameReference);
		}
	}
}
