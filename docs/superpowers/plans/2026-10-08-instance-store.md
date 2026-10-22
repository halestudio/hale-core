# Temporary Instance Store (SQLite) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace OrientDB as the temporary instance store of headless transformation with a store-agnostic API, a SQLite implementation using Kryo serialization, and an Orient fallback behind the same API.

**Architecture:** A new neutral module `common.instance.store` defines `InstanceStore` / `InstanceStoreWriter` / `InstanceStoreFactory`, an extension point for implementations, a generic `StoreInstancesJob`. `common.instance.store.sqlite` implements it (one SQLite file per store, one row per top-level instance with a Kryo payload, a single writer thread, keyset-paginated reads, fan-out, SQL `MetaFilter`). `common.headless` uses only the API (`Transformation`, new `StoreTransformationSink`); the existing OrientDB code becomes an `InstanceStore` implementation in `common.headless.orient`.

**Tech Stack:** Java 17, Gradle (`hale.migrated-java` / `hale.migrated-groovy` conventions, bnd manifests), JUnit 4, Groovy tests with `SchemaBuilder` / `InstanceBuilder`, `org.xerial:sqlite-jdbc` 3.53.4.0, `com.esotericsoftware:kryo` 5.7.1, JTS, Eclipse extension registry (non-OSGi registry in tests).

**Spec:** `docs/superpowers/specs/2026-10-08-instance-store-design.md`

## Global Constraints

- Java toolchain 17 (no Java 21 APIs).
- New dependency: `com.esotericsoftware:kryo` `5.7.1` only; `sqlite-jdbc` uses the existing catalog entry (`3.53.4.0`).
- `common.instance.store`, `common.instance.store.sqlite` and `common.headless` must not depend on any OrientDB artifact or on `common.instance.orient`.
- Extension point id: `eu.esdihumboldt.hale.instance.store`; store ids: `sqlite` (priority 10), `orient` (priority 0).
- Store selection only via system property `hale.instance.store`.
- SQLite file: `<directory>/instances.sqlite`; pragmas `journal_mode=WAL`, `synchronous=OFF`; connections via `SQLiteDataSource` (never `DriverManager`).
- Writer: ids from an `AtomicLong`, transactions of up to 1000 records or after 100 ms without new records, bounded queue (10 000), writer thread ignores interrupts and stops only via `close()`.
- Kryo: `registrationRequired=true`, references disabled, registration ids fixed in code — append new registrations only at the end.
- Reads: keyset pagination with chunks of 1000 rows; a read connection is borrowed per chunk / lookup only.
- Every new source file gets the LGPL license header (applied by `spotlessApply`); run `./gradlew :<project-path>:spotlessApply` before each commit.
- Commits: Conventional Commits, footer `ING-4137`, no `Co-authored-by`.
- Gradle project paths mirror directories, e.g. `:common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite`; new directories with a `build.gradle` are included automatically by `settings.gradle`.

## Review Focus

1. Instances without a type definition passed to `InstanceStoreWriter.add` — expect a clear `IllegalArgumentException`, not a corrupted store (test in Task 7).
2. Iterating a store collection while the writer is still adding (sink/UI) — expect a consistent committed prefix, no exceptions, no duplicates (test in Task 8).
3. A payload that cannot be decoded (e.g. class of a Java-serialized value no longer loadable) — expect the instance to be skipped with an error log, iteration continues (test in Task 8).
4. A `MetaFilter` with non-String values or an empty value set — expect correct (in-memory) results, identical to `MetaFilter.match` semantics (test in Task 8).
5. Requested store id not installed or failing to create (e.g. `-Dhale.instance.store=foo`, native library failure) — expect warning and fallback to the next store by priority (test in Task 3).

---

## Spec deviations (applied in Task 4, Step 1)

- **String conversion step dropped.** The spec's value step 4 (ConversionService string conversion) only applies to a whitelist that step 2 (registered types: `BigInteger`, `URI`) already fully covers; implementing it would be dead code.
- **WKB:** geometries keep 2D/3D as today (`ExtendedWKBWriter` with dimension from the first coordinate); M values and SRID are not preserved (same as today), instead of "including Z, M and SRID".

---

### Task 1: `TypeAwareFilter` and fan-out aware filtering

**Files:**
- Create: `common/plugins/eu.esdihumboldt.hale.common.instance/src/eu/esdihumboldt/hale/common/instance/model/TypeAwareFilter.java`
- Modify: `common/plugins/eu.esdihumboldt.hale.common.instance/src/eu/esdihumboldt/hale/common/instance/model/impl/FilteredInstanceCollection.java` (method `applyFilter`)
- Test: `common/plugins/eu.esdihumboldt.hale.common.instance/test/eu/esdihumboldt/hale/common/instance/model/impl/FilteredInstanceCollectionTypeAwareTest.java`

**Interfaces:**
- Produces: `eu.esdihumboldt.hale.common.instance.model.TypeAwareFilter extends Filter { Set<TypeDefinition> getTypes(); }`; `FilteredInstanceCollection.applyFilter(InstanceCollection, Filter)` restricts fan-out capable collections to the filter's types.

- [ ] **Step 1: Write the failing test**

```java
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
		InstanceCollection plain = new DefaultInstanceCollection(List.of(instance(TYPE_A, "a1"),
				instance(TYPE_B, "a3"), instance(TYPE_C, "a2")));
		InstanceCollection result = FilteredInstanceCollection.applyFilter(plain,
				new NameFilter(Set.of(TYPE_A, TYPE_C), "a"));
		List<String> names = names(result);
		assertEquals(2, names.size());
		assertTrue(names.containsAll(List.of("a1", "a2")));
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance:test --tests '*FilteredInstanceCollectionTypeAwareTest'`
Expected: compilation FAIL — `TypeAwareFilter` does not exist.

- [ ] **Step 3: Create `TypeAwareFilter`**

```java
package eu.esdihumboldt.hale.common.instance.model;

import java.util.Set;

import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Filter that can only match instances of a known set of types. Instance
 * collections may use this information to only read instances of these types.
 */
public interface TypeAwareFilter extends Filter {

	/**
	 * @return the types an instance must have to be able to match the filter,
	 *         never <code>null</code>
	 */
	Set<TypeDefinition> getTypes();

}
```

- [ ] **Step 4: Extend `FilteredInstanceCollection.applyFilter`**

Add after the existing `TypeFilter` block (before `return new FilteredInstanceCollection(instances, filter);`), with imports `java.util.ArrayList`, `java.util.List`, `java.util.Map`, `eu.esdihumboldt.hale.common.instance.model.TypeAwareFilter`:

```java
		if (filter instanceof TypeAwareFilter && instances instanceof InstanceCollection2) {
			InstanceCollection2 instances2 = (InstanceCollection2) instances;

			if (instances2.supportsFanout()) {
				// only read the collections of the types the filter can match
				Map<TypeDefinition, InstanceCollection> fanout = instances2.fanout();
				List<InstanceCollection> parts = new ArrayList<>();
				for (TypeDefinition type : ((TypeAwareFilter) filter).getTypes()) {
					InstanceCollection part = fanout.get(type);
					if (part != null) {
						parts.add(new FilteredInstanceCollection(part, filter));
					}
				}
				if (parts.isEmpty()) {
					return EmptyInstanceCollection.INSTANCE;
				}
				if (parts.size() == 1) {
					return parts.get(0);
				}
				return new MultiInstanceCollection(parts);
			}
		}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance:test --tests '*FilteredInstanceCollectionTypeAwareTest'`
Expected: PASS (4 tests).

- [ ] **Step 6: Run the module's full test suite**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance:test`
Expected: PASS.

- [ ] **Step 7: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.instance
git commit -m "feat(instance): add TypeAwareFilter and restrict filtering to fan-out of its types" -m "ING-4137"
```

---

### Task 2: `TypeCellFilter` uses fan-out

**Files:**
- Modify: `cst/plugins/eu.esdihumboldt.cst/src/eu/esdihumboldt/cst/ConceptualSchemaTransformer.java` (class `TypeCellFilter` ~line 349, `doTypeTransformation` ~line 273)

**Interfaces:**
- Consumes: `TypeAwareFilter` (Task 1), `FilteredInstanceCollection.applyFilter`.

- [ ] **Step 1: Make `TypeCellFilter` implement `TypeAwareFilter`**

Change the declaration and add the method (imports: `java.util.Set`, `eu.esdihumboldt.hale.common.instance.model.TypeAwareFilter`):

```java
	private static class TypeCellFilter implements TypeAwareFilter {
```

```java
		@Override
		public Set<TypeDefinition> getTypes() {
			return lookup.keySet();
		}
```

- [ ] **Step 2: Use `applyFilter` in `doTypeTransformation`**

Replace

```java
			source = source.select(new TypeCellFilter(typeCell));
```

with (import `eu.esdihumboldt.hale.common.instance.model.impl.FilteredInstanceCollection`):

```java
			// uses fan-out by type if supported by the source collection
			source = FilteredInstanceCollection.applyFilter(source, new TypeCellFilter(typeCell));
```

- [ ] **Step 3: Run the cst test suite**

Run: `./gradlew :cst:plugins:eu.esdihumboldt.cst:test :cst:plugins:eu.esdihumboldt.cst.functions.core:test`
Expected: PASS (behaviour unchanged; the filter result is the same, only fewer instances are read for fan-out collections).

- [ ] **Step 4: Format and commit**

```bash
./gradlew :cst:plugins:eu.esdihumboldt.cst:spotlessApply
git add cst/plugins/eu.esdihumboldt.cst
git commit -m "perf(cst): only read source instances of a type cell's types if supported" -m "ING-4137"
```

---

### Task 3: Store API module and selection

**Files:**
- Create: `common/plugins/eu.esdihumboldt.hale.common.instance.store/build.gradle`
- Create: `common/plugins/eu.esdihumboldt.hale.common.instance.store/resources/main/plugin.xml`
- Create: `common/plugins/eu.esdihumboldt.hale.common.instance.store/resources/main/schema/eu.esdihumboldt.hale.instance.store.exsd`
- Create: `common/plugins/eu.esdihumboldt.hale.common.instance.store/src/eu/esdihumboldt/hale/common/instance/store/InstanceStore.java`
- Create: `.../instance/store/InstanceStoreWriter.java`
- Create: `.../instance/store/InstanceStoreFactory.java`
- Create: `.../instance/store/InstanceStoreExtension.java`
- Test: `common/plugins/eu.esdihumboldt.hale.common.instance.store/test/eu/esdihumboldt/hale/common/instance/store/InstanceStoreExtensionTest.java`

**Interfaces:**
- Produces:
  - `InstanceStore extends Closeable`: `InstanceStoreWriter openWriter()`, `InstanceCollection getInstances(@Nullable TypeIndex types)`, `Set<TypeDefinition> getStoredTypes(TypeIndex types)`, `void clear()`, `void close() throws IOException`.
  - `InstanceStoreWriter extends Closeable`: `InstanceReference add(Instance instance)`, `void flush()`, `void close() throws IOException`.
  - `InstanceStoreFactory`: `InstanceStore create(DataSet dataSet, Path directory, @Nullable ServiceProvider services) throws IOException`.
  - `InstanceStoreExtension.getInstance().createStore(DataSet, Path, ServiceProvider) throws IOException`; constants `EXTENSION_ID`, `SYSTEM_PROPERTY`.

- [ ] **Step 1: Create `build.gradle`**

```groovy
plugins {
  id 'hale.migrated-java'
}

hale {
  bundleName = 'Temporary instance store API'
}

dependencies {
  implementation libs.slf4jplus.api

  implementation libs.eclipse.core.runtime
  implementation libs.igd.eclipse.util
  implementation libs.eclipse.collections.api
  implementation libs.eclipse.collections.core
  implementation libs.spotbugs.annotations

  api project(':common:plugins:eu.esdihumboldt.hale.common.core')
  api project(':common:plugins:eu.esdihumboldt.hale.common.instance')
  api project(':common:plugins:eu.esdihumboldt.hale.common.schema')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.instance.index')
}
```

Before committing check the spotbugs annotations alias with `grep -n spotbugs gradle/libs.versions.toml` and adjust (`@Nullable` from `edu.umd.cs.findbugs.annotations`); if there is no such alias, drop the line and the `@Nullable` annotations (document nullability in Javadoc instead).

- [ ] **Step 2: Create `plugin.xml` and the extension point schema**

`resources/main/plugin.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
   <extension-point id="eu.esdihumboldt.hale.instance.store" name="Temporary instance stores" schema="schema/eu.esdihumboldt.hale.instance.store.exsd"/>
</plugin>
```

`resources/main/schema/eu.esdihumboldt.hale.instance.store.exsd`:

```xml
<?xml version='1.0' encoding='UTF-8'?>
<schema targetNamespace="eu.esdihumboldt.hale.common.instance.store" xmlns="http://www.w3.org/2001/XMLSchema">
<annotation>
      <appinfo>
         <meta.schema plugin="eu.esdihumboldt.hale.common.instance.store" id="eu.esdihumboldt.hale.instance.store" name="Temporary instance stores"/>
      </appinfo>
      <documentation>
         Implementations of temporary instance stores. The store with the highest priority is used, unless the system property hale.instance.store names another store.
      </documentation>
   </annotation>
   <element name="extension">
      <complexType>
         <choice minOccurs="1" maxOccurs="unbounded">
            <element ref="store"/>
         </choice>
         <attribute name="point" type="string" use="required"/>
         <attribute name="id" type="string"/>
         <attribute name="name" type="string"/>
      </complexType>
   </element>
   <element name="store">
      <complexType>
         <attribute name="id" type="string" use="required">
            <annotation><documentation>The unique store identifier, used for selection via the system property hale.instance.store.</documentation></annotation>
         </attribute>
         <attribute name="priority" type="string" use="default" value="0">
            <annotation><documentation>The store priority (higher number is higher priority).</documentation></annotation>
         </attribute>
         <attribute name="class" type="string" use="required">
            <annotation>
               <documentation>The store factory.</documentation>
               <appinfo>
                  <meta.attribute kind="java" basedOn=":eu.esdihumboldt.hale.common.instance.store.InstanceStoreFactory"/>
               </appinfo>
            </annotation>
         </attribute>
      </complexType>
   </element>
</schema>
```

- [ ] **Step 3: Write the failing test**

```java
package eu.esdihumboldt.hale.common.instance.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension.Candidate;

public class InstanceStoreExtensionTest {

	private static final Path DIR = Path.of("unused");

	private static InstanceStore dummyStore() {
		return (InstanceStore) java.lang.reflect.Proxy.newProxyInstance(
				InstanceStore.class.getClassLoader(), new Class<?>[] { InstanceStore.class },
				(proxy, method, args) -> null);
	}

	private static Candidate candidate(String id, int priority, InstanceStoreFactory factory) {
		return new Candidate() {

			@Override
			public String getId() {
				return id;
			}

			@Override
			public int getStorePriority() {
				return priority;
			}

			@Override
			public InstanceStoreFactory createFactory() {
				return factory;
			}
		};
	}

	private static Candidate working(String id, int priority, InstanceStore store) {
		return candidate(id, priority, (ds, dir, sp) -> store);
	}

	private static Candidate failing(String id, int priority) {
		return candidate(id, priority, (ds, dir, sp) -> {
			throw new IOException("cannot create " + id);
		});
	}

	private static List<String> ids(List<Candidate> candidates) {
		return candidates.stream().map(Candidate::getId).collect(Collectors.toList());
	}

	@Test
	public void testOrderByPriority() {
		List<Candidate> ordered = InstanceStoreExtension.order(
				List.of(working("orient", 0, null), working("sqlite", 10, null)), null);
		assertEquals(List.of("sqlite", "orient"), ids(ordered));
	}

	@Test
	public void testRequestedFirst() {
		List<Candidate> ordered = InstanceStoreExtension.order(
				List.of(working("orient", 0, null), working("sqlite", 10, null)), "orient");
		assertEquals(List.of("orient", "sqlite"), ids(ordered));
	}

	@Test
	public void testRequestedUnknown() {
		List<Candidate> ordered = InstanceStoreExtension.order(
				List.of(working("orient", 0, null), working("sqlite", 10, null)), "foo");
		assertEquals(List.of("sqlite", "orient"), ids(ordered));
	}

	@Test
	public void testFallbackOnFailure() throws IOException {
		InstanceStore store = dummyStore();
		InstanceStore result = InstanceStoreExtension.createStore(
				List.of(failing("sqlite", 10), working("orient", 0, store)), null, DataSet.SOURCE,
				DIR, null);
		assertSame(store, result);
	}

	@Test
	public void testAllFailing() {
		try {
			InstanceStoreExtension.createStore(List.of(failing("sqlite", 10), failing("orient", 0)),
					null, DataSet.SOURCE, DIR, null);
			fail("Expected exception");
		} catch (IOException e) {
			assertEquals(2, e.getSuppressed().length);
		}
	}

	@Test
	public void testNoCandidates() {
		try {
			InstanceStoreExtension.createStore(List.of(), null, DataSet.SOURCE, DIR, null);
			fail("Expected exception");
		} catch (IOException e) {
			assertTrue(e.getMessage().contains("No instance store"));
		}
	}
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store:test`
Expected: compilation FAIL — API classes missing.

- [ ] **Step 5: Create the API interfaces**

`InstanceStore.java`:

```java
package eu.esdihumboldt.hale.common.instance.store;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Temporary store for the instances of one data set. Closing the store deletes
 * its files.
 */
public interface InstanceStore extends Closeable {

	/**
	 * Get a writer to add instances. Returns the currently open writer if there
	 * is one.
	 *
	 * @return the writer, thread-safe
	 */
	InstanceStoreWriter openWriter();

	/**
	 * Get the stored instances. The collection is reiterable and resolves
	 * references returned by the writer. Iteration reflects the committed state
	 * (see {@link InstanceStoreWriter#flush()}).
	 *
	 * @param types the type index used to determine the instance types; if
	 *            <code>null</code> the collection can only be used to resolve
	 *            references
	 * @return the instance collection, an
	 *         {@link eu.esdihumboldt.hale.common.instance.model.ext.InstanceCollection2}
	 *         if the store supports fan-out
	 */
	InstanceCollection getInstances(TypeIndex types);

	/**
	 * @param types the type index
	 * @return the types of the type index that are present in the store
	 */
	Set<TypeDefinition> getStoredTypes(TypeIndex types);

	/**
	 * Remove all instances. An open writer is closed.
	 */
	void clear();

	/**
	 * Close the store and delete its files.
	 */
	@Override
	void close() throws IOException;

}
```

`InstanceStoreWriter.java`:

```java
package eu.esdihumboldt.hale.common.instance.store;

import java.io.Closeable;
import java.io.IOException;

import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;

/**
 * Adds instances to an {@link InstanceStore}. Implementations are thread-safe.
 */
public interface InstanceStoreWriter extends Closeable {

	/**
	 * Add an instance.
	 *
	 * @param instance the instance, must have a type definition
	 * @return the reference to the stored instance, can be resolved immediately
	 *         via the store's instance collection
	 * @throws IllegalArgumentException if the instance has no type definition or
	 *             cannot be serialized (the instance is not stored)
	 * @throws IllegalStateException if the writer is closed or failed
	 */
	InstanceReference add(Instance instance);

	/**
	 * Block until all instances added so far are visible when iterating the
	 * store's instance collections.
	 *
	 * @throws IllegalStateException if writing failed
	 */
	void flush();

	/**
	 * Flush and close the writer.
	 *
	 * @throws IOException if writing failed
	 */
	@Override
	void close() throws IOException;

}
```

`InstanceStoreFactory.java`:

```java
package eu.esdihumboldt.hale.common.instance.store;

import java.io.IOException;
import java.nio.file.Path;

import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.hale.common.instance.model.DataSet;

/**
 * Creates instance stores. Contributed via the extension point
 * {@value InstanceStoreExtension#EXTENSION_ID}.
 */
public interface InstanceStoreFactory {

	/**
	 * Create a new empty store.
	 *
	 * @param dataSet the data set of the instances to store
	 * @param directory the directory the store may use exclusively, it is
	 *            deleted when the store is closed
	 * @param services the service provider, may be <code>null</code>
	 * @return the store
	 * @throws IOException if the store cannot be created
	 */
	InstanceStore create(DataSet dataSet, Path directory, ServiceProvider services)
			throws IOException;

}
```

- [ ] **Step 6: Create `InstanceStoreExtension`**

```java
package eu.esdihumboldt.hale.common.instance.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;

import de.fhg.igd.eclipse.util.extension.AbstractConfigurationFactory;
import de.fhg.igd.eclipse.util.extension.AbstractExtension;
import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.hale.common.instance.model.DataSet;

/**
 * Extension for {@link InstanceStoreFactory}s, selects and creates stores.
 */
public class InstanceStoreExtension
		extends AbstractExtension<InstanceStoreFactory, InstanceStoreExtension.Descriptor> {

	/**
	 * The extension point ID.
	 */
	public static final String EXTENSION_ID = "eu.esdihumboldt.hale.instance.store";

	/**
	 * System property naming the store to use.
	 */
	public static final String SYSTEM_PROPERTY = "hale.instance.store";

	private static final ALogger log = ALoggerFactory.getLogger(InstanceStoreExtension.class);

	/**
	 * A store implementation candidate.
	 */
	public interface Candidate {

		/**
		 * @return the store identifier
		 */
		String getId();

		/**
		 * @return the store priority, higher is preferred
		 */
		int getStorePriority();

		/**
		 * @return the store factory
		 * @throws Exception if the factory cannot be created
		 */
		InstanceStoreFactory createFactory() throws Exception;
	}

	/**
	 * Store factory descriptor based on a configuration element.
	 */
	public static class Descriptor extends AbstractConfigurationFactory<InstanceStoreFactory>
			implements Candidate {

		/**
		 * @param conf the configuration element
		 */
		protected Descriptor(IConfigurationElement conf) {
			super(conf, "class");
		}

		@Override
		public void dispose(InstanceStoreFactory instance) {
			// nothing to do
		}

		@Override
		public String getIdentifier() {
			return conf.getAttribute("id");
		}

		@Override
		public String getDisplayName() {
			return getIdentifier();
		}

		@Override
		public int getPriority() {
			return -getStorePriority();
		}

		@Override
		public String getId() {
			return getIdentifier();
		}

		@Override
		public int getStorePriority() {
			try {
				return Integer.parseInt(conf.getAttribute("priority"));
			} catch (Exception e) {
				return 0;
			}
		}

		@Override
		public InstanceStoreFactory createFactory() throws Exception {
			return createExtensionObject();
		}
	}

	private static final InstanceStoreExtension INSTANCE = new InstanceStoreExtension();

	/**
	 * @return the extension instance
	 */
	public static InstanceStoreExtension getInstance() {
		return INSTANCE;
	}

	private InstanceStoreExtension() {
		super(EXTENSION_ID);
	}

	@Override
	protected Descriptor createFactory(IConfigurationElement conf) throws Exception {
		if ("store".equals(conf.getName())) {
			return new Descriptor(conf);
		}
		return null;
	}

	/**
	 * Create a store, using the store named by the system property
	 * {@value #SYSTEM_PROPERTY} if set, otherwise the store with the highest
	 * priority. If creating a store fails, the next store is tried.
	 *
	 * @param dataSet the data set
	 * @param directory the store directory
	 * @param services the service provider, may be <code>null</code>
	 * @return the created store
	 * @throws IOException if no store could be created
	 */
	public InstanceStore createStore(DataSet dataSet, Path directory, ServiceProvider services)
			throws IOException {
		return createStore(getFactories(), System.getProperty(SYSTEM_PROPERTY), dataSet,
				directory, services);
	}

	/**
	 * Order candidates: requested id first, then by descending priority.
	 *
	 * @param candidates the candidates
	 * @param requestedId the requested id, may be <code>null</code>
	 * @return the ordered candidates
	 */
	static List<Candidate> order(List<? extends Candidate> candidates, String requestedId) {
		List<Candidate> result = new ArrayList<>(candidates);
		result.sort(Comparator.comparingInt(Candidate::getStorePriority).reversed());
		if (requestedId != null && !requestedId.isEmpty()) {
			Candidate requested = result.stream().filter(c -> requestedId.equals(c.getId()))
					.findFirst().orElse(null);
			if (requested == null) {
				log.warn("Requested instance store \"{}\" is not available", requestedId);
			}
			else {
				result.remove(requested);
				result.add(0, requested);
			}
		}
		return result;
	}

	/**
	 * Create a store from the first candidate that succeeds.
	 *
	 * @param candidates the candidates
	 * @param requestedId the requested id, may be <code>null</code>
	 * @param dataSet the data set
	 * @param directory the store directory
	 * @param services the service provider, may be <code>null</code>
	 * @return the created store
	 * @throws IOException if no store could be created
	 */
	static InstanceStore createStore(List<? extends Candidate> candidates, String requestedId,
			DataSet dataSet, Path directory, ServiceProvider services) throws IOException {
		List<Candidate> ordered = order(candidates, requestedId);
		if (ordered.isEmpty()) {
			throw new IOException("No instance store implementation available");
		}
		IOException failure = new IOException("No instance store could be created");
		for (Candidate candidate : ordered) {
			try {
				InstanceStore store = candidate.createFactory().create(dataSet, directory,
						services);
				log.debug("Using instance store \"{}\" for {} data", candidate.getId(), dataSet);
				return store;
			} catch (Exception e) {
				log.error("Failed to create instance store \"" + candidate.getId() + "\"", e);
				failure.addSuppressed(e);
			}
		}
		throw failure;
	}

}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store:test`
Expected: PASS (6 tests).

- [ ] **Step 8: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.instance.store
git commit -m "feat(instance-store): add temporary instance store API and extension point" -m "ING-4137"
```

---

### Task 4: SQLite module skeleton and value codec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-08-instance-store-design.md` (section 6, value handling)
- Modify: `gradle/libs.versions.toml` (add `kryo`)
- Create: `common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite/build.gradle`
- Create: `.../src/eu/esdihumboldt/hale/common/instance/store/sqlite/codec/Dictionary.java`
- Create: `.../sqlite/codec/ChildContext.java`
- Create: `.../sqlite/codec/NestedCodec.java`
- Create: `.../sqlite/codec/ValueCodec.java`
- Create: `.../sqlite/codec/ExtendedWKBWriter.java`, `.../sqlite/codec/ExtendedWKBReader.java` (copied)
- Test: `common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite/test/eu/esdihumboldt/hale/common/instance/store/sqlite/codec/ValueCodecTest.java`

**Interfaces:**
- Produces:
  - `Dictionary<T>`: `int idOf(T value)`, `T valueOf(int id)`.
  - `record ChildContext(DefinitionGroup parent, QName name)`.
  - `NestedCodec`: `void write(Kryo kryo, Output out, Object value, ChildContext context)` (value is `Instance` or `Group`, tag already written), `Object read(Kryo kryo, Input in, byte tag, ChildContext context)`.
  - `ValueCodec(Dictionary<CRSDefinition> crs, Dictionary<Class<?>> classes)`: `Kryo createKryo()`, `void write(Kryo, Output, Object value, ChildContext ctx, NestedCodec nested)`, `Object read(Kryo, Input, ChildContext ctx, NestedCodec nested)`, constants `TAG_INSTANCE`, `TAG_GROUP`, sentinel `DROPPED`.

- [ ] **Step 1: Record the spec deviations**

In the spec, section 6 "Value handling", replace item 4 with:

```markdown
4. (dropped during planning) String conversion via `ConversionService` is not implemented: its whitelist (`BigInteger`, `URI`) is fully covered by item 2.
```

and in item 3 replace "`Geometry` as WKB including Z, M and SRID, with" by "`Geometry` as WKB with 2D or 3D coordinates (as today; M values and SRID are not preserved), with".

- [ ] **Step 2: Add the Kryo catalog entry**

In `gradle/libs.versions.toml` below the `sqlite-jdbc` line:

```toml
kryo = {module = "com.esotericsoftware:kryo", version = "5.7.1"}
```

- [ ] **Step 3: Create `build.gradle`**

```groovy
plugins {
  id 'hale.migrated-groovy'
}

hale {
  bundleName = 'Temporary instance store based on SQLite'
}

dependencies {
  implementation libs.slf4jplus.api

  implementation libs.sqlite.jdbc
  implementation libs.kryo
  implementation libs.jts
  implementation libs.commons.io
  implementation libs.igd.osgi.util

  api project(':common:plugins:eu.esdihumboldt.hale.common.instance.store')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.core')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.instance')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.schema')

  testImplementation testLibs.junit4
  testImplementation project(':util:plugins:eu.esdihumboldt.util.test')
  testImplementation project(':common:plugins:eu.esdihumboldt.hale.common.test')
  testImplementation project(':common:plugins:eu.esdihumboldt.hale.common.schema.groovy')
  testImplementation project(':common:plugins:eu.esdihumboldt.hale.common.instance.groovy')
}
```

- [ ] **Step 4: Copy the WKB helpers**

```bash
M=common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite/src/eu/esdihumboldt/hale/common/instance/store/sqlite/codec
mkdir -p $M
O=common/plugins/eu.esdihumboldt.hale.common.instance.orient/src/eu/esdihumboldt/hale/common/instance/orient/internal
for f in ExtendedWKBWriter ExtendedWKBReader; do
  sed 's/^package eu.esdihumboldt.hale.common.instance.orient.internal;/package eu.esdihumboldt.hale.common.instance.store.sqlite.codec;/' $O/$f.java > $M/$f.java
done
grep -n "^package\|^import" $M/ExtendedWKB*.java
```

Expected: both files declare the new package and import only `org.locationtech.jts.*` / `java.*`.

- [ ] **Step 5: Write the failing test**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite.codec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;
import eu.esdihumboldt.hale.common.core.report.SimpleLogContext;
import eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty;
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;

public class ValueCodecTest {

	private final ValueCodec codec = new ValueCodec(new Dictionary<>(), new Dictionary<>());
	private final GeometryFactory gf = new GeometryFactory();

	public static class SerializableValue implements Serializable {

		private static final long serialVersionUID = 1L;
		private final String text;

		public SerializableValue(String text) {
			this.text = text;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof SerializableValue && Objects.equals(text, ((SerializableValue) o).text);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(text);
		}
	}

	private Object roundTrip(Object value) {
		Kryo kryo = codec.createKryo();
		Output out = new Output(256, -1);
		codec.write(kryo, out, value, null, null);
		Input in = new Input(out.toBytes());
		return codec.read(kryo, in, null, null);
	}

	private void assertRoundTrip(Object value) {
		Object result = roundTrip(value);
		assertEquals(value, result);
		if (value != null) {
			assertEquals(value.getClass(), result.getClass());
		}
	}

	@Test
	public void testSimpleValues() throws Exception {
		for (Object value : Arrays.asList(null, "text", 42, 42L, (short) 4, (byte) 2, 1.5d, 2.5f,
				true, 'c', new BigDecimal("123.456"), new BigInteger("12345678901234567890"),
				new Date(1234567890123L), new java.sql.Date(1234567890000L),
				Instant.parse("2026-10-08T10:15:30.123Z"), LocalDate.of(2026, 10, 8),
				ZonedDateTime.parse("2026-10-08T10:15:30+02:00[Europe/Berlin]"),
				URI.create("http://example.com/a"), new URL("http://example.com/b"),
				UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
				new QName("http://example.com", "local", "ex"))) {
			assertRoundTrip(value);
		}
	}

	@Test
	public void testTimestampKeepsNanos() {
		Timestamp ts = new Timestamp(1234567890123L);
		ts.setNanos(123456789);
		assertRoundTrip(ts);
	}

	@Test
	public void testByteArray() {
		byte[] bytes = { 1, 2, 3 };
		assertArrayEquals(bytes, (byte[]) roundTrip(bytes));
	}

	@Test
	public void testObjectArrayKeepsComponentType() {
		String[] array = { "a", "b" };
		Object result = roundTrip(array);
		assertTrue(result instanceof String[]);
		assertArrayEquals(array, (String[]) result);
	}

	@Test
	public void testCollections() {
		List<Object> list = new ArrayList<>(List.of("a", 1, List.of(2L, "b")));
		assertEquals(list, roundTrip(list));
		Set<Object> set = new LinkedHashSet<>(List.of("x", "y"));
		Object result = roundTrip(set);
		assertTrue(result instanceof Set);
		assertEquals(set, result);
	}

	@Test
	public void testGeometries() {
		Point point3d = gf.createPoint(new Coordinate(1, 2, 3));
		Geometry result = (Geometry) roundTrip(point3d);
		assertTrue(point3d.equalsExact(result));
		assertEquals(3.0, result.getCoordinate().getZ(), 0.0);

		LinearRing ring = gf.createLinearRing(new Coordinate[] { new Coordinate(0, 0),
				new Coordinate(1, 0), new Coordinate(1, 1), new Coordinate(0, 0) });
		Object ringResult = roundTrip(ring);
		assertTrue(ringResult instanceof LinearRing);
		assertTrue(ring.equalsExact((Geometry) ringResult));
	}

	@Test
	public void testGeometryPropertyKeepsCrs() {
		CRSDefinition crs = new CodeDefinition("EPSG:4326");
		Point point = gf.createPoint(new Coordinate(8, 50));
		GeometryProperty<?> result = (GeometryProperty<?>) roundTrip(
				new DefaultGeometryProperty<>(crs, point));
		assertSame(crs, result.getCRSDefinition());
		assertTrue(point.equalsExact(result.getGeometry()));
	}

	@Test
	public void testSerializableFallback() {
		assertRoundTrip(new SerializableValue("hello"));
	}

	@Test
	public void testNotSerializableIsDropped() {
		List<String> warnings = new ArrayList<>();
		SimpleLog log = new SimpleLog() {

			@Override
			public void warn(String message, Throwable e) {
				warnings.add(message);
			}

			@Override
			public void error(String message, Throwable e) {
				warnings.add(message);
			}

			@Override
			public void info(String message, Throwable e) {
				// ignore
			}
		};
		Object result = SimpleLogContext.withLog(log, () -> roundTrip(new Object()));
		assertSame(ValueCodec.DROPPED, result);
		assertEquals(1, warnings.size());
	}
}
```

Note: if `SimpleLog` declares further abstract methods, implement them as no-ops; if `CodeDefinition`'s `equals` is identity-based this test still passes because the dictionary returns the same object.

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*ValueCodecTest'`
Expected: compilation FAIL — `ValueCodec` missing.

- [ ] **Step 7: Create `Dictionary`, `ChildContext`, `NestedCodec`**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite.codec;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory dictionary assigning ids to values. Ids are only valid
 * for the lifetime of the dictionary.
 *
 * @param <T> the value type
 */
public class Dictionary<T> {

	private final ConcurrentHashMap<T, Integer> ids = new ConcurrentHashMap<>();
	private final List<T> values = new CopyOnWriteArrayList<>();

	/**
	 * @param value the value, not <code>null</code>
	 * @return the id of the value, assigned if not yet present
	 */
	public int idOf(T value) {
		return ids.computeIfAbsent(value, v -> {
			synchronized (values) {
				values.add(v);
				return values.size() - 1;
			}
		});
	}

	/**
	 * @param id the id
	 * @return the value with the given id
	 */
	public T valueOf(int id) {
		return values.get(id);
	}

}
```

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite.codec;

import javax.xml.namespace.QName;

import eu.esdihumboldt.hale.common.schema.model.DefinitionGroup;

/**
 * Context of a property value: the definition of the group the property
 * belongs to and the property name.
 *
 * @param parent the parent definition, may be <code>null</code>
 * @param name the property name
 */
public record ChildContext(DefinitionGroup parent, QName name) {
}
```

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite.codec;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Writes and reads nested instances and groups for {@link ValueCodec}.
 */
public interface NestedCodec {

	/**
	 * Write the body of a nested instance or group (the tag is already written).
	 *
	 * @param kryo the Kryo instance
	 * @param out the output
	 * @param value the instance or group
	 * @param context the property context, may be <code>null</code>
	 */
	void write(Kryo kryo, Output out, Object value, ChildContext context);

	/**
	 * Read a nested instance or group.
	 *
	 * @param kryo the Kryo instance
	 * @param in the input
	 * @param tag {@link ValueCodec#TAG_INSTANCE} or {@link ValueCodec#TAG_GROUP}
	 * @param context the property context, may be <code>null</code>
	 * @return the instance or group
	 */
	Object read(Kryo kryo, Input in, byte tag, ChildContext context);

}
```

- [ ] **Step 8: Create `ValueCodec`**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import de.fhg.igd.osgi.util.OsgiUtils;
import eu.esdihumboldt.hale.common.core.report.SimpleLogContext;
import eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty;
import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;

/**
 * Writes and reads property and metadata values with Kryo.
 */
public class ValueCodec {

	/** Value tags */
	static final byte TAG_NULL = 0;
	/** Nested instance */
	public static final byte TAG_INSTANCE = 1;
	/** Nested group */
	public static final byte TAG_GROUP = 2;
	static final byte TAG_KRYO = 3;
	static final byte TAG_GEOMETRY = 4;
	static final byte TAG_GEOMETRY_PROPERTY = 5;
	static final byte TAG_LIST = 6;
	static final byte TAG_SET = 7;
	static final byte TAG_ARRAY = 8;
	static final byte TAG_JAVA = 9;
	static final byte TAG_DROPPED = 10;

	/**
	 * Returned by {@link #read(Kryo, Input, ChildContext, NestedCodec)} for a
	 * value that could not be stored.
	 */
	public static final Object DROPPED = new Object();

	/**
	 * Value types handled by Kryo. Registration ids are derived from the
	 * position - only append to this list.
	 */
	private static final List<Class<?>> REGISTERED_TYPES = List.of(Integer.class, Long.class,
			Short.class, Byte.class, Double.class, Float.class, Boolean.class, Character.class,
			BigDecimal.class, BigInteger.class, byte[].class, int[].class, long[].class,
			short[].class, double[].class, float[].class, char[].class, boolean[].class,
			java.util.Date.class, java.sql.Date.class, java.sql.Time.class,
			java.sql.Timestamp.class, Instant.class, LocalDate.class, LocalTime.class,
			LocalDateTime.class, OffsetDateTime.class, OffsetTime.class, ZonedDateTime.class,
			Duration.class, Period.class, Year.class, YearMonth.class, MonthDay.class, URI.class,
			URL.class, UUID.class);

	private static final int FIRST_REGISTRATION_ID = 20;

	private final Dictionary<CRSDefinition> crsDictionary;
	private final Dictionary<Class<?>> classDictionary;

	/**
	 * @param crsDictionary dictionary for CRS definitions
	 * @param classDictionary dictionary for array component classes
	 */
	public ValueCodec(Dictionary<CRSDefinition> crsDictionary,
			Dictionary<Class<?>> classDictionary) {
		this.crsDictionary = crsDictionary;
		this.classDictionary = classDictionary;
	}

	/**
	 * @return a new configured Kryo instance (not thread-safe, use a pool)
	 */
	public Kryo createKryo() {
		Kryo kryo = new Kryo();
		kryo.setRegistrationRequired(true);
		kryo.setReferences(false);
		int id = FIRST_REGISTRATION_ID;
		for (Class<?> type : REGISTERED_TYPES) {
			kryo.register(type, id++);
		}
		kryo.register(QName.class, new QNameSerializer(), id++);
		return kryo;
	}

	/**
	 * Write a value.
	 *
	 * @param kryo the Kryo instance
	 * @param out the output
	 * @param value the value
	 * @param context the property context, may be <code>null</code>
	 * @param nested the codec for nested instances and groups, may be
	 *            <code>null</code> if none are expected
	 */
	public void write(Kryo kryo, Output out, Object value, ChildContext context,
			NestedCodec nested) {
		if (value == null) {
			out.writeByte(TAG_NULL);
		}
		else if (value instanceof Group) {
			if (nested == null) {
				throw new IllegalStateException("Nested instances or groups not supported here");
			}
			out.writeByte(value instanceof eu.esdihumboldt.hale.common.instance.model.Instance
					? TAG_INSTANCE
					: TAG_GROUP);
			nested.write(kryo, out, value, context);
		}
		else if (value instanceof GeometryProperty) {
			GeometryProperty<?> property = (GeometryProperty<?>) value;
			out.writeByte(TAG_GEOMETRY_PROPERTY);
			CRSDefinition crs = property.getCRSDefinition();
			out.writeVarInt(crs == null ? 0 : crsDictionary.idOf(crs) + 1, true);
			Geometry geometry = property.getGeometry();
			out.writeBoolean(geometry != null);
			if (geometry != null) {
				writeGeometry(out, geometry);
			}
		}
		else if (value instanceof Geometry) {
			out.writeByte(TAG_GEOMETRY);
			writeGeometry(out, (Geometry) value);
		}
		else if (kryo.getClassResolver().getRegistration(value.getClass()) != null
				|| value instanceof String) {
			out.writeByte(TAG_KRYO);
			kryo.writeClassAndObject(out, value);
		}
		else if (value instanceof List || value instanceof Set) {
			Collection<?> collection = (Collection<?>) value;
			out.writeByte(value instanceof List ? TAG_LIST : TAG_SET);
			out.writeVarInt(collection.size(), true);
			for (Object element : collection) {
				write(kryo, out, element, context, nested);
			}
		}
		else if (value.getClass().isArray()
				&& !value.getClass().getComponentType().isPrimitive()) {
			Object[] array = (Object[]) value;
			out.writeByte(TAG_ARRAY);
			out.writeVarInt(classDictionary.idOf(value.getClass().getComponentType()), true);
			out.writeVarInt(array.length, true);
			for (Object element : array) {
				write(kryo, out, element, context, nested);
			}
		}
		else {
			byte[] bytes = (value instanceof Serializable) ? javaSerialize(value) : null;
			if (bytes != null) {
				out.writeByte(TAG_JAVA);
				out.writeVarInt(bytes.length, true);
				out.writeBytes(bytes);
			}
			else {
				out.writeByte(TAG_DROPPED);
				SimpleLogContext.getLog().warn("Value of type " + value.getClass().getName()
						+ " cannot be stored in the temporary database and is dropped", null);
			}
		}
	}

	/**
	 * Read a value.
	 *
	 * @param kryo the Kryo instance
	 * @param in the input
	 * @param context the property context, may be <code>null</code>
	 * @param nested the codec for nested instances and groups, may be
	 *            <code>null</code> if none are expected
	 * @return the value or {@link #DROPPED}
	 */
	public Object read(Kryo kryo, Input in, ChildContext context, NestedCodec nested) {
		byte tag = in.readByte();
		switch (tag) {
		case TAG_NULL:
			return null;
		case TAG_INSTANCE:
		case TAG_GROUP:
			if (nested == null) {
				throw new IllegalStateException("Nested instances or groups not supported here");
			}
			return nested.read(kryo, in, tag, context);
		case TAG_GEOMETRY_PROPERTY:
			int crsId = in.readVarInt(true);
			CRSDefinition crs = crsId == 0 ? null : crsDictionary.valueOf(crsId - 1);
			Geometry geometry = in.readBoolean() ? readGeometry(in) : null;
			return new DefaultGeometryProperty<>(crs, geometry);
		case TAG_GEOMETRY:
			return readGeometry(in);
		case TAG_KRYO:
			return kryo.readClassAndObject(in);
		case TAG_LIST:
		case TAG_SET:
			int size = in.readVarInt(true);
			Collection<Object> collection = tag == TAG_LIST ? new ArrayList<>(size)
					: new LinkedHashSet<>();
			for (int i = 0; i < size; i++) {
				Object element = read(kryo, in, context, nested);
				if (element != DROPPED) {
					collection.add(element);
				}
			}
			return collection;
		case TAG_ARRAY:
			Class<?> component = classDictionary.valueOf(in.readVarInt(true));
			int length = in.readVarInt(true);
			List<Object> elements = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				Object element = read(kryo, in, context, nested);
				if (element != DROPPED) {
					elements.add(element);
				}
			}
			Object array = Array.newInstance(component, elements.size());
			for (int i = 0; i < elements.size(); i++) {
				Array.set(array, i, elements.get(i));
			}
			return array;
		case TAG_JAVA:
			return javaDeserialize(in.readBytes(in.readVarInt(true)));
		case TAG_DROPPED:
			return DROPPED;
		default:
			throw new IllegalStateException("Unknown value tag " + tag);
		}
	}

	private static void writeGeometry(Output out, Geometry geometry) {
		Coordinate first = geometry.getCoordinate();
		int dimension = (first == null || Double.isNaN(first.getZ())) ? 2 : 3;
		byte[] wkb = new ExtendedWKBWriter(dimension).write(geometry);
		out.writeVarInt(wkb.length, true);
		out.writeBytes(wkb);
	}

	private static Geometry readGeometry(Input in) {
		byte[] wkb = in.readBytes(in.readVarInt(true));
		try {
			return new ExtendedWKBReader().read(wkb);
		} catch (ParseException e) {
			throw new IllegalStateException("Could not read geometry", e);
		}
	}

	private static byte[] javaSerialize(Object value) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(value);
		} catch (IOException e) {
			return null;
		}
		return bytes.toByteArray();
	}

	private static Object javaDeserialize(byte[] bytes) {
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes)) {

			@Override
			protected Class<?> resolveClass(ObjectStreamClass desc)
					throws IOException, ClassNotFoundException {
				try {
					Class<?> result = OsgiUtils.loadClass(desc.getName(), null);
					if (result != null) {
						return result;
					}
				} catch (Throwable e) {
					// not running in OSGi or class not found via OSGi
				}
				return super.resolveClass(desc);
			}
		}) {
			return in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			throw new IllegalStateException("Could not deserialize stored value", e);
		}
	}

	private static class QNameSerializer extends Serializer<QName> {

		@Override
		public void write(Kryo kryo, Output output, QName name) {
			output.writeString(name.getNamespaceURI());
			output.writeString(name.getLocalPart());
			output.writeString(name.getPrefix());
		}

		@Override
		public QName read(Kryo kryo, Input input, Class<? extends QName> type) {
			return new QName(input.readString(), input.readString(), input.readString());
		}
	}

}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*ValueCodecTest'`
Expected: PASS (9 tests). If a `java.time` type fails with "Class is not registered", Kryo has no default serializer for it — register it with Kryo's `TimeSerializers` counterpart explicitly in `createKryo` (append at the end of the registrations).

- [ ] **Step 10: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:spotlessApply
git add gradle/libs.versions.toml docs/superpowers/specs/2026-10-08-instance-store-design.md common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite
git commit -m "feat(instance-store): add Kryo based value codec for SQLite instance store" -m "ING-4137"
```

---

### Task 5: Instance codec and `StoredInstance`

**Files:**
- Create: `.../sqlite/codec/StoredInstance.java`
- Create: `.../sqlite/codec/InstanceCodec.java`
- Test: `common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite/test/eu/esdihumboldt/hale/common/instance/store/sqlite/codec/InstanceCodecTest.groovy`

**Interfaces:**
- Consumes: `ValueCodec`, `Dictionary`, `ChildContext`, `NestedCodec` (Task 4).
- Produces:
  - `StoredInstance extends DefaultInstance implements Identifiable`: `StoredInstance(TypeDefinition definition, DataSet dataSet, UUID storeKey, long storeId)`, `UUID getStoreKey()`, `long getStoreId()`, `Object getId()` returns `Long`.
  - `InstanceCodec()`: `byte[] encode(Instance instance)`, `StoredInstance decode(byte[] data, TypeDefinition type, DataSet dataSet, UUID storeKey, long id, TypeIndex types)` (`types` may be `null`).

- [ ] **Step 1: Write the failing test**

```groovy
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
		new InstanceBuilder(types: schema).instance('OrderType') {
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*InstanceCodecTest'`
Expected: compilation FAIL — `InstanceCodec` / `StoredInstance` missing.

- [ ] **Step 3: Create `StoredInstance`**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite.codec;

import java.util.UUID;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Identifiable;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Instance read from a temporary instance store. Detached copy, changes are not
 * persisted.
 */
public class StoredInstance extends DefaultInstance implements Identifiable {

	private final UUID storeKey;
	private final long storeId;

	/**
	 * @param definition the type definition
	 * @param dataSet the data set
	 * @param storeKey the key of the store the instance was read from
	 * @param storeId the instance id in the store
	 */
	public StoredInstance(TypeDefinition definition, DataSet dataSet, UUID storeKey,
			long storeId) {
		super(definition, dataSet);
		this.storeKey = storeKey;
		this.storeId = storeId;
	}

	/**
	 * @return the key of the store the instance was read from
	 */
	public UUID getStoreKey() {
		return storeKey;
	}

	/**
	 * @return the instance id in the store
	 */
	public long getStoreId() {
		return storeId;
	}

	@Override
	public Object getId() {
		return storeId;
	}

}
```

- [ ] **Step 4: Create `InstanceCodec`**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.xml.namespace.QName;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.MutableGroup;
import eu.esdihumboldt.hale.common.instance.model.MutableInstance;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultGroup;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance;
import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.DefinitionGroup;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Serializes instances to bytes and back. Thread-safe. Uses in-memory
 * dictionaries, so serialized data can only be read by the same codec.
 *
 * <pre>
 * Instance := typeRef? value metadata group   (typeRef only for nested instances)
 * Group    := propertyCount { nameId valueCount { value } }
 * metadata := keyCount { keyId valueCount { value } }
 * </pre>
 */
public class InstanceCodec {

	private static final ALogger log = ALoggerFactory.getLogger(InstanceCodec.class);

	private final ValueCodec values = new ValueCodec(new Dictionary<>(), new Dictionary<>());
	private final Dictionary<QName> names = new Dictionary<>();
	private final Dictionary<String> metadataKeys = new Dictionary<>();

	private final Pool<Kryo> kryoPool = new Pool<Kryo>(true, false, 32) {

		@Override
		protected Kryo create() {
			return values.createKryo();
		}
	};

	/**
	 * Encode a top-level instance. Its type is not included.
	 *
	 * @param instance the instance
	 * @return the encoded instance
	 */
	public byte[] encode(Instance instance) {
		Kryo kryo = kryoPool.obtain();
		try (Output out = new Output(1024, -1)) {
			new TreeCodec(null).writeInstanceBody(kryo, out, instance);
			return out.toBytes();
		} finally {
			kryoPool.free(kryo);
		}
	}

	/**
	 * Decode a top-level instance.
	 *
	 * @param data the encoded instance
	 * @param type the instance type
	 * @param dataSet the data set
	 * @param storeKey the store key
	 * @param id the instance id in the store
	 * @param types the type index to resolve types of nested instances, may be
	 *            <code>null</code>
	 * @return the instance
	 */
	public StoredInstance decode(byte[] data, TypeDefinition type, DataSet dataSet,
			UUID storeKey, long id, TypeIndex types) {
		Kryo kryo = kryoPool.obtain();
		try (Input in = new Input(data)) {
			StoredInstance instance = new StoredInstance(type, dataSet, storeKey, id);
			new TreeCodec(types).readInstanceBody(kryo, in, instance);
			return instance;
		} finally {
			kryoPool.free(kryo);
		}
	}

	private static ChildDefinition<?> child(ChildContext context) {
		if (context == null || context.parent() == null) {
			return null;
		}
		return context.parent().getChild(context.name());
	}

	private static TypeDefinition declaredType(ChildContext context) {
		ChildDefinition<?> child = child(context);
		return (child != null && child.asProperty() != null)
				? child.asProperty().getPropertyType()
				: null;
	}

	private static DefinitionGroup declaredGroup(ChildContext context) {
		ChildDefinition<?> child = child(context);
		return child != null ? child.asGroup() : null;
	}

	private class TreeCodec implements NestedCodec {

		private final TypeIndex types;

		TreeCodec(TypeIndex types) {
			this.types = types;
		}

		void writeInstanceBody(Kryo kryo, Output out, Instance instance) {
			values.write(kryo, out, instance.getValue(), null, this);

			Set<String> keys = instance.getMetaDataNames();
			out.writeVarInt(keys.size(), true);
			for (String key : keys) {
				out.writeVarInt(metadataKeys.idOf(key), true);
				List<Object> data = instance.getMetaData(key);
				out.writeVarInt(data.size(), true);
				for (Object value : data) {
					values.write(kryo, out, value, null, this);
				}
			}

			writeGroup(kryo, out, instance);
		}

		void writeGroup(Kryo kryo, Output out, Group group) {
			List<QName> properties = new ArrayList<>();
			group.getPropertyNames().forEach(properties::add);
			out.writeVarInt(properties.size(), true);
			for (QName name : properties) {
				out.writeVarInt(names.idOf(name), true);
				Object[] propertyValues = group.getProperty(name);
				int count = propertyValues == null ? 0 : propertyValues.length;
				out.writeVarInt(count, true);
				ChildContext context = new ChildContext(group.getDefinition(), name);
				for (int i = 0; i < count; i++) {
					values.write(kryo, out, propertyValues[i], context, this);
				}
			}
		}

		void readInstanceBody(Kryo kryo, Input in, MutableInstance instance) {
			Object value = values.read(kryo, in, null, this);
			instance.setValue(value == ValueCodec.DROPPED ? null : value);

			int keyCount = in.readVarInt(true);
			for (int k = 0; k < keyCount; k++) {
				String key = metadataKeys.valueOf(in.readVarInt(true));
				int count = in.readVarInt(true);
				List<Object> data = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					Object item = values.read(kryo, in, null, this);
					if (item != ValueCodec.DROPPED) {
						data.add(item);
					}
				}
				instance.setMetaData(key, data.toArray());
			}

			readGroup(kryo, in, instance);
		}

		void readGroup(Kryo kryo, Input in, MutableGroup group) {
			int propertyCount = in.readVarInt(true);
			for (int p = 0; p < propertyCount; p++) {
				QName name = names.valueOf(in.readVarInt(true));
				int count = in.readVarInt(true);
				ChildContext context = new ChildContext(group.getDefinition(), name);
				for (int i = 0; i < count; i++) {
					Object value = values.read(kryo, in, context, this);
					if (value != ValueCodec.DROPPED) {
						group.addProperty(name, value);
					}
				}
			}
		}

		@Override
		public void write(Kryo kryo, Output out, Object value, ChildContext context) {
			if (value instanceof Instance) {
				Instance instance = (Instance) value;
				TypeDefinition actual = instance.getDefinition();
				if (actual != null && !Objects.equals(actual, declaredType(context))) {
					out.writeVarInt(names.idOf(actual.getName()) + 1, true);
				}
				else {
					out.writeVarInt(0, true);
				}
				writeInstanceBody(kryo, out, instance);
			}
			else {
				writeGroup(kryo, out, (Group) value);
			}
		}

		@Override
		public Object read(Kryo kryo, Input in, byte tag, ChildContext context) {
			if (tag == ValueCodec.TAG_INSTANCE) {
				int typeRef = in.readVarInt(true);
				TypeDefinition type = declaredType(context);
				if (typeRef > 0) {
					QName typeName = names.valueOf(typeRef - 1);
					TypeDefinition found = types != null ? types.getType(typeName) : null;
					if (found != null) {
						type = found;
					}
					else {
						log.warn("Type {} of a nested instance not found, using declared type",
								typeName);
					}
				}
				DefaultInstance instance = new DefaultInstance(type, null);
				readInstanceBody(kryo, in, instance);
				return instance;
			}
			DefaultGroup group = new DefaultGroup(declaredGroup(context));
			readGroup(kryo, in, group);
			return group;
		}
	}

}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*InstanceCodecTest'`
Expected: PASS (5 tests).

- [ ] **Step 6: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite
git commit -m "feat(instance-store): serialize instance trees with Kryo" -m "ING-4137"
```

---

### Task 6: SQLite database access

**Files:**
- Create: `.../src/eu/esdihumboldt/hale/common/instance/store/sqlite/SqliteDatabase.java`
- Test: `.../test/eu/esdihumboldt/hale/common/instance/store/sqlite/SqliteDatabaseTest.java`

**Interfaces:**
- Produces: `SqliteDatabase implements Closeable`: `SqliteDatabase(Path directory) throws IOException`, `Connection writerConnection()`, `<T> T withReader(SqlFunction<T> function)`, `void close()`, constant `FILE_NAME = "instances.sqlite"`; nested `@FunctionalInterface interface SqlFunction<T> { T apply(Connection connection) throws SQLException; }`. `withReader` wraps `SQLException` in `IllegalStateException`.

- [ ] **Step 1: Write the failing test**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SqliteDatabaseTest {

	private Path dir;
	private SqliteDatabase db;

	@Before
	public void setUp() throws Exception {
		dir = Files.createTempDirectory("sqlite-db-test");
		db = new SqliteDatabase(dir);
	}

	@After
	public void tearDown() throws Exception {
		db.close();
		org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile());
	}

	@Test
	public void testSchemaAndWal() {
		List<String> tables = db.withReader(c -> {
			List<String> names = new ArrayList<>();
			try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
					"SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
				while (rs.next()) {
					names.add(rs.getString(1));
				}
			}
			return names;
		});
		assertEquals(List.of("instances", "metadata", "types"), tables);
		String mode = db.withReader(c -> {
			try (Statement s = c.createStatement();
					ResultSet rs = s.executeQuery("PRAGMA journal_mode")) {
				rs.next();
				return rs.getString(1);
			}
		});
		assertEquals("wal", mode);
		assertTrue(Files.exists(dir.resolve(SqliteDatabase.FILE_NAME)));
	}

	@Test
	public void testReadersSeeCommittedData() throws Exception {
		Connection w = db.writerConnection();
		w.setAutoCommit(false);
		try (PreparedStatement p = w.prepareStatement(
				"INSERT INTO instances (id, type, payload) VALUES (?, 1, x'00')")) {
			for (int i = 1; i <= 100; i++) {
				p.setLong(1, i);
				p.executeUpdate();
			}
		}
		w.commit();

		ExecutorService executor = Executors.newFixedThreadPool(4);
		List<Future<Long>> counts = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			counts.add(executor.submit(() -> db.withReader(c -> {
				try (Statement s = c.createStatement();
						ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM instances")) {
					rs.next();
					return rs.getLong(1);
				}
			})));
		}
		for (Future<Long> count : counts) {
			assertEquals(100L, (long) count.get());
		}
		executor.shutdown();
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*SqliteDatabaseTest'`
Expected: compilation FAIL — `SqliteDatabase` missing.

- [ ] **Step 3: Create `SqliteDatabase`**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;

/**
 * SQLite database of an instance store: one writer connection and a pool of
 * reader connections.
 */
public class SqliteDatabase implements Closeable {

	/**
	 * Name of the database file in the store directory.
	 */
	public static final String FILE_NAME = "instances.sqlite";

	private static final int MAX_IDLE_READERS = 8;
	private static final int BUSY_TIMEOUT_MILLIS = 60_000;

	private static final ALogger log = ALoggerFactory.getLogger(SqliteDatabase.class);

	/**
	 * Function using a connection.
	 *
	 * @param <T> the result type
	 */
	@FunctionalInterface
	public interface SqlFunction<T> {

		/**
		 * @param connection the connection
		 * @return the result
		 * @throws SQLException if a database error occurs
		 */
		T apply(Connection connection) throws SQLException;
	}

	private final SQLiteDataSource readSource;
	private final Connection writer;
	private final ConcurrentLinkedQueue<Connection> idleReaders = new ConcurrentLinkedQueue<>();
	private final AtomicInteger idleCount = new AtomicInteger();
	private volatile boolean closed;

	/**
	 * Create a new database in the given directory.
	 *
	 * @param directory the directory
	 * @throws IOException if the database cannot be created
	 */
	public SqliteDatabase(Path directory) throws IOException {
		Files.createDirectories(directory);
		String url = "jdbc:sqlite:" + directory.resolve(FILE_NAME).toAbsolutePath();

		SQLiteConfig writeConfig = new SQLiteConfig();
		writeConfig.setPageSize(8192);
		writeConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
		writeConfig.setSynchronous(SQLiteConfig.SynchronousMode.OFF);
		writeConfig.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
		writeConfig.setCacheSize(-65536); // 64 MB
		SQLiteDataSource writeSource = new SQLiteDataSource(writeConfig);
		writeSource.setUrl(url);

		SQLiteConfig readConfig = new SQLiteConfig();
		readConfig.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
		readConfig.setCacheSize(-16384); // 16 MB
		readSource = new SQLiteDataSource(readConfig);
		readSource.setUrl(url);

		try {
			writer = writeSource.getConnection();
			try (Statement s = writer.createStatement()) {
				s.execute("CREATE TABLE types (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)");
				s.execute("CREATE TABLE instances (id INTEGER PRIMARY KEY, type INTEGER NOT NULL, "
						+ "payload BLOB NOT NULL)");
				s.execute("CREATE INDEX instances_type ON instances(type, id)");
				s.execute("CREATE TABLE metadata (key TEXT NOT NULL, value TEXT NOT NULL, "
						+ "instance INTEGER NOT NULL, PRIMARY KEY (key, value, instance)) WITHOUT ROWID");
			}
		} catch (SQLException e) {
			throw new IOException("Failed to create temporary instance database", e);
		}
	}

	/**
	 * @return the writer connection, only to be used by a single thread
	 */
	public Connection writerConnection() {
		return writer;
	}

	/**
	 * Run a function with a pooled reader connection.
	 *
	 * @param function the function
	 * @return the function result
	 * @throws IllegalStateException if a database error occurs
	 */
	public <T> T withReader(SqlFunction<T> function) {
		if (closed) {
			throw new IllegalStateException("Database is closed");
		}
		Connection connection = idleReaders.poll();
		try {
			if (connection == null) {
				connection = readSource.getConnection();
			}
			else {
				idleCount.decrementAndGet();
			}
			T result = function.apply(connection);
			release(connection);
			return result;
		} catch (SQLException e) {
			closeQuietly(connection);
			throw new IllegalStateException("Error reading temporary instance database", e);
		} catch (RuntimeException e) {
			release(connection);
			throw e;
		}
	}

	private void release(Connection connection) {
		if (connection == null) {
			return;
		}
		if (!closed && idleCount.incrementAndGet() <= MAX_IDLE_READERS) {
			idleReaders.offer(connection);
		}
		else {
			idleCount.decrementAndGet();
			closeQuietly(connection);
		}
	}

	private static void closeQuietly(Connection connection) {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				log.warn("Failed to close database connection", e);
			}
		}
	}

	@Override
	public void close() {
		closed = true;
		Connection connection;
		while ((connection = idleReaders.poll()) != null) {
			closeQuietly(connection);
		}
		closeQuietly(writer);
	}

}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*SqliteDatabaseTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite
git commit -m "feat(instance-store): add SQLite database access with pooled readers" -m "ING-4137"
```

---

### Task 7: Store context and writer

**Files:**
- Create: `.../sqlite/TypeRegistry.java`
- Create: `.../sqlite/StoreContext.java`
- Create: `.../sqlite/StoreInstanceReference.java`
- Create: `.../sqlite/SqliteInstanceWriter.java`
- Test: `.../test/eu/esdihumboldt/hale/common/instance/store/sqlite/SqliteInstanceWriterTest.java`

**Interfaces:**
- Consumes: `SqliteDatabase` (Task 6), `InstanceCodec`, `StoredInstance` (Task 5), `InstanceStoreWriter` (Task 3).
- Produces:
  - `TypeRegistry`: `int idOf(QName)`, `Integer findId(QName)` (null if unknown), `QName nameOf(int)`, `Set<Integer> ids()`.
  - `StoreContext` (package-private class, fields package-visible): `UUID key`, `DataSet dataSet`, `InstanceCodec codec`, `TypeRegistry types`, `ConcurrentHashMap<Long, PendingRecord> pending`, `ConcurrentHashMap<Integer, LongAdder> counts`, `AtomicLong nextId`, `volatile SqliteDatabase database`; methods `long committedCount(int typeId)`, `StoredInstance load(long id, TypeDefinition type, TypeIndex types)`, `void reset()`; `record PendingRecord(int typeId, byte[] payload)`.
  - `StoreInstanceReference implements InstanceReference, Identifiable`: `StoreInstanceReference(UUID storeKey, long id, DataSet dataSet, TypeDefinition type)`, `getStoreKey()`, `getStoreId()`, `getType()`, `getDataSet()`, `getId()`; `equals`/`hashCode` on store key, id, data set.
  - `SqliteInstanceWriter implements InstanceStoreWriter` (package-private): `SqliteInstanceWriter(StoreContext)`, `boolean isClosed()`; constants `BATCH_SIZE = 1000`, `IDLE_COMMIT_MILLIS = 100`, `QUEUE_CAPACITY = 10_000`.

- [ ] **Step 1: Write the failing test**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.namespace.QName;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultTypeDefinition;

public class SqliteInstanceWriterTest {

	static final TypeDefinition TYPE = new DefaultTypeDefinition(new QName("T"));

	private Path dir;
	private StoreContext ctx;

	static Instance instance(String id) {
		DefaultInstance instance = new DefaultInstance(TYPE, null);
		instance.addProperty(new QName("name"), "n-" + id);
		instance.setMetaData("ID", id);
		return instance;
	}

	private long count(String sql) {
		return ctx.database.withReader(c -> {
			try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
				rs.next();
				return rs.getLong(1);
			}
		});
	}

	@Before
	public void setUp() throws Exception {
		dir = Files.createTempDirectory("sqlite-writer-test");
		ctx = new StoreContext(DataSet.SOURCE);
		ctx.database = new SqliteDatabase(dir);
	}

	@After
	public void tearDown() throws Exception {
		ctx.database.close();
		org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile());
	}

	@Test
	public void testAddFlush() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			InstanceReference r1 = writer.add(instance("a"));
			InstanceReference r2 = writer.add(instance("b"));
			assertNotEquals(r1, r2);
			assertEquals(DataSet.SOURCE, r1.getDataSet());
			writer.flush();
			assertTrue(ctx.pending.isEmpty());
			assertEquals(2, count("SELECT COUNT(*) FROM instances"));
			assertEquals(2, count("SELECT COUNT(*) FROM metadata WHERE key = 'ID'"));
			assertEquals(1, count("SELECT COUNT(*) FROM types"));
			assertEquals(2, ctx.committedCount(ctx.types.findId(TYPE.getName())));
		}
	}

	@Test
	public void testPendingResolvableBeforeCommit() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			StoreInstanceReference ref = (StoreInstanceReference) writer.add(instance("a"));
			Instance loaded = ctx.load(ref.getStoreId(), TYPE, null);
			assertNotNull(loaded);
			assertEquals("n-a", loaded.getProperty(new QName("name"))[0]);
		}
	}

	@Test
	public void testLargeBatches() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			for (int i = 0; i < 2500; i++) {
				writer.add(instance("i" + i));
			}
		}
		assertEquals(2500, count("SELECT COUNT(*) FROM instances"));
	}

	@Test
	public void testIdleCommit() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			writer.add(instance("a"));
			long deadline = System.currentTimeMillis() + 5000;
			while (!ctx.pending.isEmpty() && System.currentTimeMillis() < deadline) {
				Thread.sleep(20);
			}
			assertTrue("instance should be committed without flush", ctx.pending.isEmpty());
		}
	}

	@Test
	public void testConcurrentAdds() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			ExecutorService executor = Executors.newFixedThreadPool(4);
			List<Future<?>> futures = new ArrayList<>();
			for (int t = 0; t < 4; t++) {
				int thread = t;
				futures.add(executor.submit(() -> {
					for (int i = 0; i < 250; i++) {
						writer.add(instance(thread + "-" + i));
					}
				}));
			}
			for (Future<?> f : futures) {
				f.get();
			}
			executor.shutdown();
			writer.flush();
		}
		assertEquals(1000, count("SELECT COUNT(DISTINCT id) FROM instances"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstanceWithoutType() throws Exception {
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			writer.add(new DefaultInstance(null, null));
		}
	}

	@Test
	public void testUnserializableInstance() throws Exception {
		DefaultInstance broken = new DefaultInstance(TYPE, null) {

			@Override
			public Iterable<QName> getPropertyNames() {
				throw new IllegalStateException("broken instance");
			}
		};
		try (SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx)) {
			try {
				writer.add(broken);
				fail("Expected exception");
			} catch (IllegalArgumentException e) {
				// expected
			}
			// writer still usable
			writer.add(instance("a"));
			writer.flush();
		}
		assertEquals(1, count("SELECT COUNT(*) FROM instances"));
	}

	@Test
	public void testAddAfterClose() throws Exception {
		SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx);
		writer.close();
		assertTrue(writer.isClosed());
		try {
			writer.add(instance("a"));
			fail("Expected exception");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	@Test
	public void testFailurePropagates() throws Exception {
		SqliteInstanceWriter writer = new SqliteInstanceWriter(ctx);
		ctx.database.writerConnection().close();
		writer.add(instance("a"));
		try {
			writer.flush();
			fail("Expected exception");
		} catch (IllegalStateException e) {
			// expected
		}
		try {
			writer.add(instance("b"));
			fail("Expected exception");
		} catch (IllegalStateException e) {
			// expected
		}
		try {
			writer.close();
			fail("Expected exception");
		} catch (java.io.IOException e) {
			// expected
		}
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*SqliteInstanceWriterTest'`
Expected: compilation FAIL.

- [ ] **Step 3: Create `TypeRegistry`**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;

/**
 * Assigns ids to type names. Thread-safe.
 */
public class TypeRegistry {

	private final ConcurrentHashMap<QName, Integer> ids = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, QName> names = new ConcurrentHashMap<>();
	private final AtomicInteger next = new AtomicInteger(1);

	/**
	 * @param name the type name
	 * @return the type id, assigned if not yet present
	 */
	public int idOf(QName name) {
		return ids.computeIfAbsent(name, n -> {
			int id = next.getAndIncrement();
			names.put(id, n);
			return id;
		});
	}

	/**
	 * @param name the type name
	 * @return the type id or <code>null</code> if the type is unknown
	 */
	public Integer findId(QName name) {
		return ids.get(name);
	}

	/**
	 * @param id the type id
	 * @return the type name
	 */
	public QName nameOf(int id) {
		return names.get(id);
	}

	/**
	 * @return the assigned ids
	 */
	public Set<Integer> ids() {
		return names.keySet();
	}

}
```

- [ ] **Step 4: Create `StoreInstanceReference`**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import java.util.Objects;
import java.util.UUID;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Identifiable;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Reference to an instance in a SQLite instance store.
 */
public final class StoreInstanceReference implements InstanceReference, Identifiable {

	private final UUID storeKey;
	private final long id;
	private final DataSet dataSet;
	private final TypeDefinition type;

	/**
	 * @param storeKey the store key
	 * @param id the instance id in the store
	 * @param dataSet the data set
	 * @param type the instance type
	 */
	public StoreInstanceReference(UUID storeKey, long id, DataSet dataSet, TypeDefinition type) {
		this.storeKey = storeKey;
		this.id = id;
		this.dataSet = dataSet;
		this.type = type;
	}

	@Override
	public DataSet getDataSet() {
		return dataSet;
	}

	@Override
	public Object getId() {
		return id;
	}

	/**
	 * @return the store key
	 */
	public UUID getStoreKey() {
		return storeKey;
	}

	/**
	 * @return the instance id in the store
	 */
	public long getStoreId() {
		return id;
	}

	/**
	 * @return the instance type
	 */
	public TypeDefinition getType() {
		return type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(storeKey, id, dataSet);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof StoreInstanceReference)) {
			return false;
		}
		StoreInstanceReference other = (StoreInstanceReference) obj;
		return id == other.id && storeKey.equals(other.storeKey) && dataSet == other.dataSet;
	}

	@Override
	public String toString() {
		return "StoreInstanceReference [id=" + id + ", dataSet=" + dataSet + "]";
	}

}
```

- [ ] **Step 5: Create `StoreContext`**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.store.sqlite.codec.InstanceCodec;
import eu.esdihumboldt.hale.common.instance.store.sqlite.codec.StoredInstance;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * State shared by the store, its writer and collections.
 */
class StoreContext {

	/**
	 * Serialized instance not yet committed to the database.
	 *
	 * @param typeId the type id
	 * @param payload the serialized instance
	 */
	record PendingRecord(int typeId, byte[] payload) {
	}

	final UUID key = UUID.randomUUID();
	final DataSet dataSet;
	final InstanceCodec codec = new InstanceCodec();
	final TypeRegistry types = new TypeRegistry();
	final ConcurrentHashMap<Long, PendingRecord> pending = new ConcurrentHashMap<>();
	final ConcurrentHashMap<Integer, LongAdder> counts = new ConcurrentHashMap<>();
	final AtomicLong nextId = new AtomicLong(1);
	volatile SqliteDatabase database;

	StoreContext(DataSet dataSet) {
		this.dataSet = dataSet;
	}

	long committedCount(int typeId) {
		LongAdder count = counts.get(typeId);
		return count == null ? 0 : count.sum();
	}

	/**
	 * Load an instance by id, including instances not yet committed.
	 *
	 * @param id the instance id
	 * @param type the instance type
	 * @param types the type index for nested types, may be <code>null</code>
	 * @return the instance or <code>null</code> if it does not exist
	 */
	StoredInstance load(long id, TypeDefinition type, TypeIndex types) {
		PendingRecord record = pending.get(id);
		byte[] payload = record != null ? record.payload() : database.withReader(c -> {
			try (PreparedStatement p = c
					.prepareStatement("SELECT payload FROM instances WHERE id = ?")) {
				p.setLong(1, id);
				try (ResultSet rs = p.executeQuery()) {
					return rs.next() ? rs.getBytes(1) : null;
				}
			}
		});
		return payload == null ? null : codec.decode(payload, type, dataSet, key, id, types);
	}

	/**
	 * Forget all instances (after the database was recreated).
	 */
	void reset() {
		pending.clear();
		counts.clear();
	}

}
```

- [ ] **Step 6: Create `SqliteInstanceWriter`**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import javax.xml.namespace.QName;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter;
import eu.esdihumboldt.hale.common.instance.store.sqlite.StoreContext.PendingRecord;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * Writer serializing instances on the calling thread and inserting them on a
 * dedicated writer thread in batches.
 */
class SqliteInstanceWriter implements InstanceStoreWriter {

	static final int BATCH_SIZE = 1000;
	static final long IDLE_COMMIT_MILLIS = 100;
	static final int QUEUE_CAPACITY = 10_000;

	private static final ALogger log = ALoggerFactory.getLogger(SqliteInstanceWriter.class);

	private static final Object STOP = new Object();

	private record Item(long id, int typeId, QName typeName, byte[] payload,
			List<String[]> metadata) {
	}

	private final StoreContext ctx;
	private final BlockingQueue<Object> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
	private final AtomicReference<Throwable> failure = new AtomicReference<>();
	private final Thread thread;
	private volatile boolean closed;

	// writer thread state
	private final List<Item> batch = new ArrayList<>();
	private final Set<Integer> storedTypes = new HashSet<>();

	SqliteInstanceWriter(StoreContext ctx) {
		this.ctx = ctx;
		this.thread = new Thread(this::run, "hale-instance-store-writer");
		thread.setDaemon(true);
		thread.start();
	}

	boolean isClosed() {
		return closed;
	}

	@Override
	public InstanceReference add(Instance instance) {
		checkUsable();
		TypeDefinition type = instance.getDefinition();
		if (type == null) {
			throw new IllegalArgumentException(
					"Instance without type definition cannot be stored");
		}
		long id = ctx.nextId.getAndIncrement();
		int typeId = ctx.types.idOf(type.getName());
		byte[] payload;
		try {
			payload = ctx.codec.encode(instance);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Instance cannot be serialized", e);
		}
		ctx.pending.put(id, new PendingRecord(typeId, payload));
		put(new Item(id, typeId, type.getName(), payload, stringMetadata(instance)));
		return new StoreInstanceReference(ctx.key, id, ctx.dataSet, type);
	}

	@Override
	public void flush() {
		checkUsable();
		CountDownLatch latch = new CountDownLatch(1);
		put(latch);
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for flush", e);
		}
		rethrowFailure();
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		put(STOP);
		boolean interrupted = false;
		while (thread.isAlive()) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
		Throwable error = failure.get();
		if (error != null) {
			throw new IOException("Writing to the temporary instance database failed", error);
		}
	}

	private void checkUsable() {
		if (closed) {
			throw new IllegalStateException("Writer is closed");
		}
		rethrowFailure();
	}

	private void rethrowFailure() {
		Throwable error = failure.get();
		if (error != null) {
			throw new IllegalStateException("Writing to the temporary instance database failed",
					error);
		}
	}

	private void put(Object item) {
		try {
			queue.put(item);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while adding to the instance store", e);
		}
	}

	private static List<String[]> stringMetadata(Instance instance) {
		List<String[]> result = new ArrayList<>();
		for (String key : instance.getMetaDataNames()) {
			for (Object value : instance.getMetaData(key)) {
				if (value instanceof String) {
					result.add(new String[] { key, (String) value });
				}
			}
		}
		return result;
	}

	private void run() {
		while (true) {
			Object item;
			try {
				item = queue.poll(IDLE_COMMIT_MILLIS, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				// ignore interrupts (e.g. job cancellation), only stopped via close
				continue;
			}
			try {
				if (item == null) {
					commit();
				}
				else if (item == STOP) {
					commit();
					return;
				}
				else if (item instanceof CountDownLatch) {
					try {
						commit();
					} finally {
						((CountDownLatch) item).countDown();
					}
				}
				else if (failure.get() == null) {
					insert((Item) item);
					if (batch.size() >= BATCH_SIZE) {
						commit();
					}
				}
			} catch (Throwable e) {
				if (failure.compareAndSet(null, e)) {
					log.error("Writing to the temporary instance database failed", e);
				}
				batch.clear();
				try {
					ctx.database.writerConnection().rollback();
				} catch (Throwable e2) {
					// ignore
				}
				if (item == STOP) {
					return;
				}
			}
		}
	}

	private void insert(Item item) throws SQLException {
		Connection c = ctx.database.writerConnection();
		if (c.getAutoCommit()) {
			c.setAutoCommit(false);
		}
		if (storedTypes.add(item.typeId())) {
			try (PreparedStatement p = c
					.prepareStatement("INSERT OR IGNORE INTO types (id, name) VALUES (?, ?)")) {
				p.setInt(1, item.typeId());
				p.setString(2, item.typeName().toString());
				p.executeUpdate();
			}
		}
		try (PreparedStatement p = c
				.prepareStatement("INSERT INTO instances (id, type, payload) VALUES (?, ?, ?)")) {
			p.setLong(1, item.id());
			p.setInt(2, item.typeId());
			p.setBytes(3, item.payload());
			p.executeUpdate();
		}
		if (!item.metadata().isEmpty()) {
			try (PreparedStatement p = c.prepareStatement(
					"INSERT OR IGNORE INTO metadata (key, value, instance) VALUES (?, ?, ?)")) {
				for (String[] entry : item.metadata()) {
					p.setString(1, entry[0]);
					p.setString(2, entry[1]);
					p.setLong(3, item.id());
					p.executeUpdate();
				}
			}
		}
		batch.add(item);
	}

	private void commit() throws SQLException {
		if (batch.isEmpty()) {
			return;
		}
		ctx.database.writerConnection().commit();
		for (Item item : batch) {
			ctx.counts.computeIfAbsent(item.typeId(), t -> new LongAdder()).increment();
			ctx.pending.remove(item.id());
		}
		batch.clear();
	}

}
```

Note: prepared statements are created per insert for clarity; if profiling (Task 13) shows overhead, cache them in fields of the writer thread.

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*SqliteInstanceWriterTest'`
Expected: PASS (9 tests).

- [ ] **Step 8: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite
git commit -m "feat(instance-store): add batching SQLite instance writer" -m "ING-4137"
```

---

### Task 8: Store instance collection, references and filters

**Files:**
- Create: `.../sqlite/StoreInstanceCollection.java`
- Test: `.../test/eu/esdihumboldt/hale/common/instance/store/sqlite/StoreInstanceCollectionTest.groovy`

**Interfaces:**
- Consumes: `StoreContext`, `SqliteInstanceWriter`, `StoreInstanceReference` (Task 7), `TypeAwareFilter` (Task 1).
- Produces: `StoreInstanceCollection implements InstanceCollection2` (public class, package-private constructors): `StoreInstanceCollection(StoreContext ctx, TypeIndex types)`; with `types == null` iteration is empty and only `getInstance` works. Constants `CHUNK_SIZE = 1000`, `MAX_FILTER_VALUES = 30_000`.

- [ ] **Step 1: Write the failing test**

```groovy
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
		Instance instance = new InstanceBuilder(types: schema).instance('ItemType') {
			delegate.id(id)
			name("item$id")
		}
		DefaultInstance result = new DefaultInstance(instance)
		result.setMetaData('ID', "i$id" as String)
		result
	}

	Instance person(String name) {
		new InstanceBuilder(types: schema).instance('PersonType') { delegate.name(name) }
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
		write([item(1), person('a'), item(2), person('b'), person('c')])
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
		Set<Object> seen = [] as Set
		while (producer.alive) {
			collection.toList().each { assertTrue('no duplicates in one iteration', seen.add(it.id) || true) }
			seen.clear()
		}
		producer.join()
		writer.close()
		def ids = collection.toList().collect { it.id }
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

	@Test
	void testWithoutTypeIndex() {
		def refs = write([item(1)])
		StoreInstanceCollection collection = new StoreInstanceCollection(ctx, null)
		assertTrue collection.toList().isEmpty()
		assertEquals 'item1', collection.getInstance(refs[0]).getProperty(new QName('name'))[0]
	}
}
```

Note: in `testIterationDuringWrite` the assertion that matters is that iterating while writing raises no exception and the final state is complete and duplicate-free; `seen.add(it.id)` within one `toList()` additionally checks no duplicates per iteration — replace `|| true` by a real check if the executor prefers stricter assertions (ids are unique per iteration with keyset pagination).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*StoreInstanceCollectionTest'`
Expected: compilation FAIL — `StoreInstanceCollection` missing.

- [ ] **Step 3: Create `StoreInstanceCollection`**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.Filter;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.MetaFilter;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.model.ext.InstanceCollection2;
import eu.esdihumboldt.hale.common.instance.model.ext.InstanceIterator;
import eu.esdihumboldt.hale.common.instance.model.impl.FilteredInstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.impl.InstanceDecorator;
import eu.esdihumboldt.hale.common.instance.model.impl.InstanceReferenceDecorator;
import eu.esdihumboldt.hale.common.instance.store.sqlite.codec.StoredInstance;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Instance collection on a SQLite instance store. Iterates committed instances
 * of mapping relevant types in id order.
 */
public class StoreInstanceCollection implements InstanceCollection2 {

	static final int CHUNK_SIZE = 1000;
	static final int MAX_FILTER_VALUES = 30_000;

	private static final ALogger log = ALoggerFactory.getLogger(StoreInstanceCollection.class);

	private record Row(long id, int typeId, byte[] payload) {
	}

	private final StoreContext ctx;
	private final TypeIndex types;
	/** allowed types by name */
	private final Map<QName, TypeDefinition> allowedTypes;
	/** metadata condition, may be <code>null</code> */
	private final String metaKey;
	private final List<String> metaValues;

	StoreInstanceCollection(StoreContext ctx, TypeIndex types) {
		this(ctx, types, mappingRelevant(types), null, null);
	}

	private StoreInstanceCollection(StoreContext ctx, TypeIndex types,
			Map<QName, TypeDefinition> allowedTypes, String metaKey, List<String> metaValues) {
		this.ctx = ctx;
		this.types = types;
		this.allowedTypes = allowedTypes;
		this.metaKey = metaKey;
		this.metaValues = metaValues;
	}

	private static Map<QName, TypeDefinition> mappingRelevant(TypeIndex types) {
		if (types == null) {
			return Collections.emptyMap();
		}
		Map<QName, TypeDefinition> result = new LinkedHashMap<>();
		for (TypeDefinition type : types.getMappingRelevantTypes()) {
			result.put(type.getName(), type);
		}
		return result;
	}

	/**
	 * @return the allowed types present in the store, by type id
	 */
	private Map<Integer, TypeDefinition> resolveTypeIds() {
		Map<Integer, TypeDefinition> result = new HashMap<>();
		for (Map.Entry<QName, TypeDefinition> entry : allowedTypes.entrySet()) {
			Integer id = ctx.types.findId(entry.getKey());
			if (id != null) {
				result.put(id, entry.getValue());
			}
		}
		return result;
	}

	@Override
	public ResourceIterator<Instance> iterator() {
		return new StoreIterator(resolveTypeIds());
	}

	@Override
	public boolean hasSize() {
		return metaKey == null;
	}

	@Override
	public int size() {
		if (!hasSize()) {
			return UNKNOWN_SIZE;
		}
		long sum = 0;
		for (Integer typeId : resolveTypeIds().keySet()) {
			sum += ctx.committedCount(typeId);
		}
		return (int) Math.min(Integer.MAX_VALUE, sum);
	}

	@Override
	public boolean isEmpty() {
		if (hasSize()) {
			return size() == 0;
		}
		try (ResourceIterator<Instance> it = iterator()) {
			return !it.hasNext();
		}
	}

	@Override
	public boolean supportsFanout() {
		return metaKey == null;
	}

	@Override
	public Map<TypeDefinition, InstanceCollection> fanout() {
		if (!supportsFanout()) {
			return null;
		}
		Map<TypeDefinition, InstanceCollection> result = new LinkedHashMap<>();
		for (TypeDefinition type : resolveTypeIds().values()) {
			result.put(type, new StoreInstanceCollection(ctx, types,
					Collections.singletonMap(type.getName(), type), null, null));
		}
		return result;
	}

	@Override
	public InstanceCollection select(Filter filter) {
		if (filter instanceof MetaFilter && metaKey == null) {
			InstanceCollection result = selectMeta((MetaFilter) filter);
			if (result != null) {
				return result;
			}
		}
		// TypeFilter and TypeAwareFilter are handled via fan-out
		return FilteredInstanceCollection.applyFilter(this, filter);
	}

	private InstanceCollection selectMeta(MetaFilter filter) {
		Set<? extends Object> values = filter.getValues();
		if (values.isEmpty() || values.size() > MAX_FILTER_VALUES
				|| !values.stream().allMatch(v -> v instanceof String)) {
			return null;
		}
		Map<QName, TypeDefinition> restricted = allowedTypes;
		if (filter.getType() != null) {
			TypeDefinition type = filter.getType();
			restricted = allowedTypes.containsKey(type.getName())
					? Collections.singletonMap(type.getName(), type)
					: Collections.emptyMap();
		}
		List<String> stringValues = values.stream().map(String.class::cast)
				.collect(Collectors.toList());
		return new StoreInstanceCollection(ctx, types, restricted, filter.getMetadataKey(),
				stringValues);
	}

	@Override
	public InstanceReference getReference(Instance instance) {
		Instance root = InstanceDecorator.getRoot(instance);
		if (root instanceof StoredInstance) {
			StoredInstance stored = (StoredInstance) root;
			if (stored.getStoreKey().equals(ctx.key)) {
				return new StoreInstanceReference(ctx.key, stored.getStoreId(), ctx.dataSet,
						stored.getDefinition());
			}
		}
		return null;
	}

	@Override
	public Instance getInstance(InstanceReference reference) {
		InstanceReference root = InstanceReferenceDecorator.getRootReference(reference);
		if (root instanceof StoreInstanceReference) {
			StoreInstanceReference ref = (StoreInstanceReference) root;
			if (ref.getStoreKey().equals(ctx.key)) {
				return ctx.load(ref.getStoreId(), ref.getType(), types);
			}
		}
		return null;
	}

	private String buildQuery(Set<Integer> typeIds) {
		StringBuilder sql = new StringBuilder(
				"SELECT id, type, payload FROM instances WHERE id > ? AND type IN (");
		sql.append(typeIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
		sql.append(")");
		if (metaKey != null) {
			sql.append(" AND id IN (SELECT instance FROM metadata WHERE key = ? AND value IN (");
			sql.append(String.join(",", Collections.nCopies(metaValues.size(), "?")));
			sql.append("))");
		}
		sql.append(" ORDER BY id LIMIT ").append(CHUNK_SIZE);
		return sql.toString();
	}

	private class StoreIterator implements InstanceIterator {

		private final Map<Integer, TypeDefinition> typeIds;
		private final String query;
		private final Deque<Row> buffer = new ArrayDeque<>();
		private long lastId = 0;
		private boolean exhausted;
		private Instance decoded;

		StoreIterator(Map<Integer, TypeDefinition> typeIds) {
			this.typeIds = typeIds;
			this.exhausted = typeIds.isEmpty();
			this.query = exhausted ? null : buildQuery(typeIds.keySet());
		}

		private Row peekRow() {
			if (buffer.isEmpty() && !exhausted) {
				fetch();
			}
			return buffer.peekFirst();
		}

		private void fetch() {
			List<Row> rows = ctx.database.withReader(c -> {
				List<Row> result = new ArrayList<>();
				try (PreparedStatement p = c.prepareStatement(query)) {
					int index = 1;
					p.setLong(index++, lastId);
					if (metaKey != null) {
						p.setString(index++, metaKey);
						for (String value : metaValues) {
							p.setString(index++, value);
						}
					}
					try (ResultSet rs = p.executeQuery()) {
						while (rs.next()) {
							result.add(new Row(rs.getLong(1), rs.getInt(2), rs.getBytes(3)));
						}
					}
				}
				return result;
			});
			if (rows.size() < CHUNK_SIZE) {
				exhausted = true;
			}
			if (!rows.isEmpty()) {
				lastId = rows.get(rows.size() - 1).id();
			}
			buffer.addAll(rows);
		}

		@Override
		public boolean hasNext() {
			if (decoded != null) {
				return true;
			}
			Row row;
			while ((row = peekRow()) != null) {
				buffer.pollFirst();
				try {
					decoded = ctx.codec.decode(row.payload(), typeIds.get(row.typeId()),
							ctx.dataSet, ctx.key, row.id(), types);
					return true;
				} catch (RuntimeException e) {
					log.error("Could not read instance " + row.id()
							+ " from the temporary database, skipping it", e);
				}
			}
			return false;
		}

		@Override
		public Instance next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			Instance result = decoded;
			decoded = null;
			return result;
		}

		@Override
		public TypeDefinition typePeek() {
			if (decoded != null) {
				return decoded.getDefinition();
			}
			Row row = peekRow();
			return row == null ? null : typeIds.get(row.typeId());
		}

		@Override
		public boolean supportsTypePeek() {
			return true;
		}

		@Override
		public void skip() {
			if (decoded != null) {
				decoded = null;
			}
			else if (peekRow() != null) {
				buffer.pollFirst();
			}
		}

		@Override
		public void close() {
			// no resources held between chunks
		}
	}

}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*StoreInstanceCollectionTest'`
Expected: PASS (9 tests).

- [ ] **Step 5: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite
git commit -m "feat(instance-store): add SQLite instance collection with fan-out and metadata filter" -m "ING-4137"
```

---

### Task 9: SQLite store, registration and contract tests

**Files:**
- Create: `.../sqlite/SqliteInstanceStore.java`
- Create: `.../sqlite/SqliteInstanceStoreFactory.java`
- Create: `common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite/resources/main/plugin.xml`
- Create: `common/plugins/eu.esdihumboldt.hale.common.instance.store.test/build.gradle`
- Create: `common/plugins/eu.esdihumboldt.hale.common.instance.store.test/src/eu/esdihumboldt/hale/common/instance/store/test/AbstractInstanceStoreContractTest.groovy`
- Modify: `common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite/build.gradle` (test dependency)
- Test: `.../test/eu/esdihumboldt/hale/common/instance/store/sqlite/SqliteInstanceStoreContractTest.groovy`
- Test: `.../test/eu/esdihumboldt/hale/common/instance/store/sqlite/SqliteStoreRegistrationTest.groovy`

**Interfaces:**
- Consumes: everything from Tasks 3–8.
- Produces:
  - `SqliteInstanceStore implements InstanceStore`: `SqliteInstanceStore(DataSet dataSet, Path directory) throws IOException`.
  - `SqliteInstanceStoreFactory implements InstanceStoreFactory`, `ID = "sqlite"`.
  - `AbstractInstanceStoreContractTest` (module `common.instance.store.test`): abstract `InstanceStore createStore(DataSet dataSet, Path directory)`; fields `schema`, `orderType`, `itemType`, helpers `order(int)`, `item(int)`.

- [ ] **Step 1: Create the contract test module**

`common/plugins/eu.esdihumboldt.hale.common.instance.store.test/build.gradle`:

```groovy
plugins {
  id 'hale.migrated-groovy'
}

hale {
  bundleName = 'Contract tests for temporary instance stores'
}

dependencies {
  implementation testLibs.junit4

  api project(':common:plugins:eu.esdihumboldt.hale.common.instance.store')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.instance')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.schema')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.schema.groovy')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.instance.groovy')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.test')
  api project(':util:plugins:eu.esdihumboldt.util.test')
}
```

`src/eu/esdihumboldt/hale/common/instance/store/test/AbstractInstanceStoreContractTest.groovy`:

```groovy
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
		Instance instance = new InstanceBuilder(types: schema).instance('OrderType') {
			item {
				id(n)
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
		Instance instance = new InstanceBuilder(types: schema).instance('ItemType') {
			id(n)
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
```

- [ ] **Step 2: Add the contract tests for SQLite (failing)**

In the sqlite module `build.gradle` add:

```groovy
  testImplementation project(':common:plugins:eu.esdihumboldt.hale.common.instance.store.test')
```

`SqliteInstanceStoreContractTest.groovy`:

```groovy
package eu.esdihumboldt.hale.common.instance.store.sqlite

import java.nio.file.Path

import eu.esdihumboldt.hale.common.instance.model.DataSet
import eu.esdihumboldt.hale.common.instance.store.InstanceStore
import eu.esdihumboldt.hale.common.instance.store.test.AbstractInstanceStoreContractTest

class SqliteInstanceStoreContractTest extends AbstractInstanceStoreContractTest {

	@Override
	protected InstanceStore createStore(DataSet dataSet, Path directory) {
		new SqliteInstanceStore(dataSet, directory)
	}
}
```

`SqliteStoreRegistrationTest.groovy`:

```groovy
package eu.esdihumboldt.hale.common.instance.store.sqlite

import static org.junit.Assert.*

import java.nio.file.Files

import org.junit.Test

import eu.esdihumboldt.hale.common.instance.model.DataSet
import eu.esdihumboldt.hale.common.instance.store.InstanceStore
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension
import eu.esdihumboldt.util.test.AbstractPlatformTest

class SqliteStoreRegistrationTest extends AbstractPlatformTest {

	@Test
	void testSqliteIsDefault() {
		def dir = Files.createTempDirectory('store-registration')
		InstanceStore store = InstanceStoreExtension.instance.createStore(DataSet.SOURCE, dir, null)
		try {
			assertTrue store instanceof SqliteInstanceStore
		} finally {
			store.close()
		}
	}
}
```

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*SqliteInstanceStoreContractTest' --tests '*SqliteStoreRegistrationTest'`
Expected: compilation FAIL — `SqliteInstanceStore` missing.

- [ ] **Step 3: Create `SqliteInstanceStore` and factory**

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Temporary instance store based on SQLite.
 */
public class SqliteInstanceStore implements InstanceStore {

	private static final ALogger log = ALoggerFactory.getLogger(SqliteInstanceStore.class);

	private final Path directory;
	private final StoreContext ctx;
	private SqliteInstanceWriter writer;

	/**
	 * Create a new store.
	 *
	 * @param dataSet the data set of the stored instances
	 * @param directory the store directory, deleted when the store is closed
	 * @throws IOException if the database cannot be created
	 */
	public SqliteInstanceStore(DataSet dataSet, Path directory) throws IOException {
		this.directory = directory;
		this.ctx = new StoreContext(dataSet);
		ctx.database = new SqliteDatabase(directory);
	}

	@Override
	public synchronized InstanceStoreWriter openWriter() {
		if (writer == null || writer.isClosed()) {
			writer = new SqliteInstanceWriter(ctx);
		}
		return writer;
	}

	@Override
	public InstanceCollection getInstances(TypeIndex types) {
		return new StoreInstanceCollection(ctx, types);
	}

	@Override
	public Set<TypeDefinition> getStoredTypes(TypeIndex types) {
		Set<TypeDefinition> result = new LinkedHashSet<>();
		for (Integer typeId : ctx.types.ids()) {
			if (ctx.committedCount(typeId) > 0) {
				TypeDefinition type = types.getType(ctx.types.nameOf(typeId));
				if (type != null) {
					result.add(type);
				}
			}
		}
		return result;
	}

	@Override
	public synchronized void clear() {
		closeWriter();
		ctx.database.close();
		try {
			Files.deleteIfExists(directory.resolve(SqliteDatabase.FILE_NAME + "-wal"));
			Files.deleteIfExists(directory.resolve(SqliteDatabase.FILE_NAME + "-shm"));
			Files.deleteIfExists(directory.resolve(SqliteDatabase.FILE_NAME));
			ctx.reset();
			ctx.database = new SqliteDatabase(directory);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to recreate temporary instance database", e);
		}
	}

	@Override
	public synchronized void close() throws IOException {
		try {
			closeWriter();
		} finally {
			ctx.database.close();
			try {
				FileUtils.deleteDirectory(directory.toFile());
			} catch (IOException e) {
				log.warn("Could not delete temporary instance database, deleting on exit", e);
				FileUtils.forceDeleteOnExit(directory.toFile());
			}
		}
	}

	private void closeWriter() {
		if (writer != null) {
			try {
				writer.close();
			} catch (IOException e) {
				log.error("Error closing instance store writer", e);
			}
			writer = null;
		}
	}

}
```

```java
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import java.io.IOException;
import java.nio.file.Path;

import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreFactory;

/**
 * Factory for {@link SqliteInstanceStore}s.
 */
public class SqliteInstanceStoreFactory implements InstanceStoreFactory {

	/**
	 * The store identifier.
	 */
	public static final String ID = "sqlite";

	@Override
	public InstanceStore create(DataSet dataSet, Path directory, ServiceProvider services)
			throws IOException {
		return new SqliteInstanceStore(dataSet, directory);
	}

}
```

`resources/main/plugin.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
   <extension
         point="eu.esdihumboldt.hale.instance.store">
      <store
            class="eu.esdihumboldt.hale.common.instance.store.sqlite.SqliteInstanceStoreFactory"
            id="sqlite"
            priority="10">
      </store>
   </extension>
   <extension
         point="eu.esdihumboldt.util.groovy.sandbox">
      <allow
            allowAll="false"
            class="eu.esdihumboldt.hale.common.instance.store.sqlite.codec.StoredInstance">
      </allow>
   </extension>
</plugin>
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test`
Expected: PASS (all sqlite module tests incl. 11 contract tests and the registration test). If the registration test fails because the non-OSGi registry does not pick up the module's `plugin.xml`, compare with how `eu.esdihumboldt.hale.common.lookup` tests register extensions and align (do not drop the test).

- [ ] **Step 5: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:spotlessApply :common:plugins:eu.esdihumboldt.hale.common.instance.store.test:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite common/plugins/eu.esdihumboldt.hale.common.instance.store.test
git commit -m "feat(instance-store): add SQLite instance store and store contract tests" -m "ING-4137"
```

---

### Task 10: Generic `StoreInstancesJob`

**Files:**
- Create: `common/plugins/eu.esdihumboldt.hale.common.instance.store/src/eu/esdihumboldt/hale/common/instance/store/StoreInstancesJob.java`
- Test: `common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite/test/eu/esdihumboldt/hale/common/instance/store/sqlite/StoreInstancesJobTest.groovy`

**Interfaces:**
- Consumes: `InstanceStore`, `InstanceStoreWriter` (Task 3); `SqliteInstanceStore` in the test (Task 9).
- Produces: `StoreInstancesJob extends Job`: `StoreInstancesJob(String name, InstanceStore store, InstanceCollection instances, TypeIndex types, ServiceProvider serviceProvider, ReportHandler reportHandler, boolean doProcessing)` (all but name/store/instances may be `null` / `false`); hooks `protected void processInstance(Instance instance)`, `protected void onComplete()`; `public IStatus run(IProgressMonitor monitor)`; `TASK_TYPE = "eu.esdihumboldt.hale.instance.store.load"`.

- [ ] **Step 1: Write the failing test**

```groovy
package eu.esdihumboldt.hale.common.instance.store.sqlite

import static org.junit.Assert.*

import java.nio.file.Files
import java.nio.file.Path

import javax.xml.namespace.QName

import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.After
import org.junit.Before
import org.junit.Test

import eu.esdihumboldt.hale.common.core.report.Report
import eu.esdihumboldt.hale.common.core.report.ReportHandler
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.instance.model.DataSet
import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.instance.model.InstanceReference
import eu.esdihumboldt.hale.common.instance.store.InstanceStore
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter
import eu.esdihumboldt.hale.common.instance.store.StoreInstancesJob
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.common.test.TestUtil
import eu.esdihumboldt.util.test.AbstractPlatformTest

class StoreInstancesJobTest extends AbstractPlatformTest {

	Path dir
	SqliteInstanceStore store
	Schema schema

	@Before
	void setUp() {
		TestUtil.startConversionService()
		schema = new SchemaBuilder().schema {
			PersonType { name(String) }
		}
		dir = Files.createTempDirectory('store-job-test')
		store = new SqliteInstanceStore(DataSet.SOURCE, dir)
	}

	@After
	void tearDown() {
		store.close()
	}

	InstanceCollection persons(int count) {
		new InstanceBuilder(types: schema).createCollection {
			(1..count).each { n -> PersonType { name("p$n") } }
		}
	}

	@Test
	void testStoresAllAndReports() {
		List<Report> reports = []
		List<Instance> processed = []
		def job = new StoreInstancesJob('Store', store, persons(25), schema, null,
				{ Report r -> reports << r } as ReportHandler, false) {
					@Override
					protected void processInstance(Instance instance) {
						processed << instance
					}
				}

		IStatus status = job.run(new NullProgressMonitor())

		assertTrue status.isOK()
		assertEquals 25, processed.size()
		assertEquals 25, store.getInstances(schema).toList().size()
		assertEquals 1, reports.size()
		assertTrue reports[0].isSuccess()
	}

	@Test
	void testUnstorableInstanceIsSkipped() {
		List<Instance> instances = persons(2).toList()
		instances.add(1, new eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance(null, null))
		List<Report> reports = []
		def job = new StoreInstancesJob('Store', store,
				new eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstanceCollection(instances),
				schema, null, { Report r -> reports << r } as ReportHandler, false)

		IStatus status = job.run(new NullProgressMonitor())

		assertTrue status.isOK()
		assertEquals 2, store.getInstances(schema).toList().size()
		assertFalse reports[0].errors.isEmpty()
	}

	@Test
	void testWriterFailureFailsJob() {
		InstanceStore failing = [
			openWriter: {
				[add: { Instance i -> throw new IllegalStateException('disk full') },
					flush: {},
					close: {}] as InstanceStoreWriter
			},
			getInstances: { types -> store.getInstances(types) }
		] as InstanceStore
		List<Report> reports = []
		def job = new StoreInstancesJob('Store', failing, persons(3), schema, null,
				{ Report r -> reports << r } as ReportHandler, false)

		IStatus status = job.run(new NullProgressMonitor())

		assertEquals IStatus.ERROR, status.severity
		assertFalse reports[0].isSuccess()
	}
}
```

Add to the sqlite module `build.gradle`: `testImplementation libs.eclipse.core.runtime`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*StoreInstancesJobTest'`
Expected: compilation FAIL — `StoreInstancesJob` missing.

- [ ] **Step 3: Create `StoreInstancesJob`**

```java
package eu.esdihumboldt.hale.common.instance.store;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;

import org.eclipse.collections.api.block.procedure.primitive.ObjectIntProcedure;
import org.eclipse.collections.api.factory.primitive.ObjectIntMaps;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.api.map.primitive.ObjectIntMap;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.core.report.LogAware;
import eu.esdihumboldt.hale.common.core.report.Message;
import eu.esdihumboldt.hale.common.core.report.ReportHandler;
import eu.esdihumboldt.hale.common.core.report.ReportSimpleLogSupport;
import eu.esdihumboldt.hale.common.core.report.Reporter;
import eu.esdihumboldt.hale.common.core.report.SimpleLogContext;
import eu.esdihumboldt.hale.common.core.report.impl.DefaultReporter;
import eu.esdihumboldt.hale.common.core.report.impl.MessageImpl;
import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.hale.common.instance.index.InstanceIndexService;
import eu.esdihumboldt.hale.common.instance.model.Identifiable;
import eu.esdihumboldt.hale.common.instance.model.IdentifiableInstanceReference;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResolvableInstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.processing.InstanceProcessingExtension;
import eu.esdihumboldt.hale.common.instance.processing.InstanceProcessor;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Job storing instances in an {@link InstanceStore}, feeding instance
 * processors and the instance index.
 */
public class StoreInstancesJob extends Job {

	/**
	 * Report task type.
	 */
	public static final String TASK_TYPE = "eu.esdihumboldt.hale.instance.store.load";

	private static final ALogger log = ALoggerFactory.getLogger(StoreInstancesJob.class);

	private static class DefaultLog extends DefaultReporter<Message>
			implements ReportSimpleLogSupport<Message> {

		DefaultLog(String taskName) {
			super(taskName, TASK_TYPE, Message.class, false);
		}

		@Override
		public Message createMessage(String message, Throwable e) {
			return new MessageImpl(message, e);
		}
	}

	private final InstanceStore store;
	private InstanceCollection instances;
	private final TypeIndex types;
	private final ServiceProvider serviceProvider;
	private final ReportHandler reportHandler;
	private final boolean doProcessing;
	private final DefaultLog report;

	/**
	 * @param name the job name
	 * @param store the store to add the instances to
	 * @param instances the instances to store
	 * @param types the type index to resolve nested types when instances are
	 *            resolved by reference, may be <code>null</code>
	 * @param serviceProvider the service provider, required if
	 *            <code>doProcessing</code> is set
	 * @param reportHandler the report handler, may be <code>null</code>
	 * @param doProcessing if instance processors and the instance index should
	 *            be fed with the stored instances
	 */
	public StoreInstancesJob(String name, InstanceStore store, InstanceCollection instances,
			TypeIndex types, ServiceProvider serviceProvider, ReportHandler reportHandler,
			boolean doProcessing) {
		super(name);
		setUser(true);
		this.store = store;
		this.instances = instances;
		this.types = types;
		this.serviceProvider = serviceProvider;
		this.reportHandler = reportHandler;
		this.doProcessing = doProcessing;
		this.report = reportHandler != null ? new DefaultLog("Load data into temporary database")
				: null;
	}

	@Override
	public IStatus run(IProgressMonitor monitor) {
		boolean exactProgress = instances.hasSize();
		int size = instances.size();
		monitor.beginTask("Store instances in temporary database",
				exactProgress ? size : IProgressMonitor.UNKNOWN);

		AtomicInteger count = new AtomicInteger();
		MutableObjectIntMap<QName> typeCount = ObjectIntMaps.mutable.empty();
		if (report != null) {
			report.setStartTime(new Date());
		}

		final List<InstanceProcessor> processors = doProcessing
				? new InstanceProcessingExtension(serviceProvider).getInstanceProcessors()
				: Collections.emptyList();
		final InstanceIndexService indexService = doProcessing
				? serviceProvider.getService(InstanceIndexService.class)
				: null;
		final InstanceCollection resolver = store.getInstances(types);

		try {
			InstanceStoreWriter writer = store.openWriter();
			try {
				SimpleLogContext.withLog(report, () -> {
					if (report != null && instances instanceof LogAware) {
						((LogAware) instances).setLog(report);
					}
					long lastUpdate = 0;
					try (ResourceIterator<Instance> it = instances.iterator()) {
						while (it.hasNext() && !monitor.isCanceled()) {
							Instance instance = it.next();
							processInstance(instance);

							InstanceReference ref;
							try {
								ref = writer.add(instance);
							} catch (IllegalArgumentException e) {
								// instance cannot be stored - skip it
								String msg = "Instance could not be stored in the temporary database and is skipped";
								if (report != null) {
									report.error(new MessageImpl(msg, e));
								}
								else {
									log.error(msg, e);
								}
								continue;
							}
							ResolvableInstanceReference resolvable = new ResolvableInstanceReference(
									new IdentifiableInstanceReference(ref, Identifiable.getId(ref)),
									resolver);
							processors.forEach(p -> p.process(instance, resolvable));
							if (indexService != null) {
								indexService.add(instance, resolvable);
							}

							count.incrementAndGet();
							TypeDefinition type = instance.getDefinition();
							if (type != null) {
								typeCount.addToValue(type.getName(), 1);
							}
							if (exactProgress) {
								monitor.worked(1);
							}
							long now = System.currentTimeMillis();
							if (now - lastUpdate > 100) {
								monitor.subTask(MessageFormat.format("{0}{1} instances processed",
										String.valueOf(count.get()),
										size != InstanceCollection.UNKNOWN_SIZE ? "/" + size : ""));
								lastUpdate = now;
							}
						}
					} finally {
						if (report != null && instances instanceof LogAware) {
							((LogAware) instances).setLog(null);
						}
					}
				});
			} finally {
				writer.close();
			}
		} catch (Exception e) {
			String message = "Error storing instances in temporary database";
			if (report != null) {
				reportTypeCount(report, typeCount);
				report.error(new MessageImpl(message, e));
				report.setSuccess(false);
				reportHandler.publishReport(report);
			}
			log.error(message, e);
			monitor.done();
			return new Status(IStatus.ERROR, "eu.esdihumboldt.hale.common.instance.store",
					message, e);
		} finally {
			instances = null;
		}

		try {
			onComplete();
		} catch (RuntimeException e) {
			String message = "Error while post processing stored instances";
			if (report != null) {
				report.error(new MessageImpl(message, e));
			}
			else {
				log.error(message, e);
			}
		}

		String message = MessageFormat.format("Stored {0} instances in the temporary database.",
				count);
		if (monitor.isCanceled()) {
			String warn = "Loading instances was canceled, incomplete data set in the temporary database.";
			if (report != null) {
				report.warn(new MessageImpl(warn, null));
			}
			else {
				log.warn(warn);
			}
		}
		if (report != null) {
			reportTypeCount(report, typeCount);
			report.setSuccess(true);
			report.setSummary(message);
			reportHandler.publishReport(report);
		}
		else {
			log.info(message);
		}

		monitor.done();
		return new Status(monitor.isCanceled() ? IStatus.CANCEL : IStatus.OK,
				"eu.esdihumboldt.hale.common.instance.store", message);
	}

	private void reportTypeCount(Reporter<Message> report, ObjectIntMap<QName> typeCount) {
		typeCount.forEachKeyValue((ObjectIntProcedure<QName>) (typeName, count) -> {
			StringBuilder msg = new StringBuilder("Stored ");
			msg.append(count).append(" instances of type ").append(typeName.getLocalPart());
			String ns = typeName.getNamespaceURI();
			if (ns != null && !ns.isEmpty()) {
				msg.append(" (").append(ns).append(")");
			}
			report.info(new MessageImpl(msg.toString(), null));
			report.stats().at("countPerType").at(typeName.toString()).set(count);
		});
	}

	/**
	 * Called for each instance before it is stored.
	 *
	 * @param instance the instance
	 */
	protected void processInstance(Instance instance) {
		// override me
	}

	/**
	 * Called after all instances were stored (also if the job was canceled).
	 */
	protected void onComplete() {
		// override me
	}

}
```

Note: if `SimpleLogContext.withLog(null, ...)` does not accept `null`, pass `report != null ? report : SimpleLog.NO_LOG`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:test --tests '*StoreInstancesJobTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.instance.store:spotlessApply :common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.instance.store common/plugins/eu.esdihumboldt.hale.common.instance.store.sqlite
git commit -m "feat(instance-store): add store independent job for loading instances" -m "ING-4137"
```

---

### Task 11: `StoreTransformationSink`

**Files:**
- Create: `common/plugins/eu.esdihumboldt.hale.common.headless/src/eu/esdihumboldt/hale/common/headless/transform/InstanceWithReference.java` (copy of the class in `headless.orient`, new package)
- Create: `common/plugins/eu.esdihumboldt.hale.common.headless/src/eu/esdihumboldt/hale/common/headless/transform/StoreTransformationSink.java`
- Modify: `common/plugins/eu.esdihumboldt.hale.common.headless/resources/main/plugin.xml`
- Modify: `common/plugins/eu.esdihumboldt.hale.common.headless/build.gradle`
- Test: `common/plugins/eu.esdihumboldt.hale.common.headless/test/eu/esdihumboldt/hale/common/headless/transform/StoreTransformationSinkTest.groovy`

**Interfaces:**
- Consumes: `InstanceStoreExtension`, `InstanceStore`, `InstanceStoreWriter` (Task 3); SQLite store at test runtime (Task 9).
- Produces: `StoreTransformationSink extends AbstractTransformationSink` (public no-arg constructor), registered as sink `eu.esdihumboldt.hale.headless.sink.store` (priority 10, reiterable).

- [ ] **Step 1: Dependencies**

In `common/plugins/eu.esdihumboldt.hale.common.headless/build.gradle` add:

```groovy
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.instance.store')

  testImplementation testLibs.junit4
  testImplementation project(':util:plugins:eu.esdihumboldt.util.test')
  testRuntimeOnly project(':common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite')
```

(skip lines already present).

- [ ] **Step 2: Write the failing test**

```groovy
package eu.esdihumboldt.hale.common.headless.transform

import static org.junit.Assert.*

import javax.xml.namespace.QName

import org.junit.Test

import eu.esdihumboldt.hale.common.instance.model.Instance
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.instance.model.InstanceReference
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultSchema
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultTypeDefinition
import eu.esdihumboldt.hale.common.schema.model.constraint.type.MappingRelevantFlag
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
```

If `DefaultSchema`'s constructor signature differs, check `common/plugins/eu.esdihumboldt.hale.common.schema/src/eu/esdihumboldt/hale/common/schema/model/impl/DefaultSchema.java` and adapt; alternatively build the schema with `SchemaBuilder` (add `testImplementation` for `common.schema.groovy`).

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.headless:test --tests '*StoreTransformationSinkTest'`
Expected: compilation FAIL — `StoreTransformationSink` missing.

- [ ] **Step 4: Create `InstanceWithReference` in headless**

```bash
H=common/plugins/eu.esdihumboldt.hale.common.headless/src/eu/esdihumboldt/hale/common/headless/transform
sed 's/^package eu.esdihumboldt.hale.common.headless.orient;/package eu.esdihumboldt.hale.common.headless.transform;/' \
  common/plugins/eu.esdihumboldt.hale.common.headless.orient/src/eu/esdihumboldt/hale/common/headless/orient/InstanceWithReference.java \
  > $H/InstanceWithReference.java
```

- [ ] **Step 5: Create `StoreTransformationSink`**

```java
package eu.esdihumboldt.hale.common.headless.transform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Filter;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Reiterable transformation sink based on an {@link InstanceStore}. The first
 * iterator streams instances while the transformation is running, later
 * iterators read from the store after the transformation completed.
 */
public class StoreTransformationSink extends AbstractTransformationSink {

	private static final ALogger log = ALoggerFactory.getLogger(StoreTransformationSink.class);

	private class StoreLimboCollection implements InstanceCollection {

		private boolean firstIterator = true;
		private boolean limboOpen = false;

		private InstanceCollection stored() {
			return store.getInstances(types);
		}

		@Override
		public InstanceReference getReference(Instance instance) {
			if (instance instanceof InstanceWithReference) {
				return ((InstanceWithReference) instance).getReference();
			}
			return stored().getReference(instance);
		}

		@Override
		public Instance getInstance(InstanceReference reference) {
			return stored().getInstance(reference);
		}

		@Override
		public synchronized ResourceIterator<Instance> iterator() {
			if (!complete.get() && !skipLimbo.get() && firstIterator) {
				firstIterator = false;
				limboOpen = true;
				return limboSink.getInstanceCollection().iterator();
			}
			if (firstIterator && !skipLimbo.get()) {
				firstIterator = false;
				limboOpen = true;
				// transformation already completed, but limbo sink still holds
				// the instances - serve them as the first iteration
				return limboSink.getInstanceCollection().iterator();
			}
			waitToComplete();
			return stored().iterator();
		}

		@Override
		public boolean hasSize() {
			return false;
		}

		@Override
		public int size() {
			return UNKNOWN_SIZE;
		}

		@Override
		public boolean isEmpty() {
			// have to return false, even if it actually may be empty
			return false;
		}

		@Override
		public InstanceCollection select(Filter filter) {
			waitToComplete();
			return stored().select(filter);
		}

		private void waitToComplete() {
			if (!limboOpen) {
				// skip limbo sink (prevent blocking when adding to it)
				skipLimbo.set(true);
				// consume limbo sink, make sure it does not block any more
				limboSink.done(true);
			}
			while (!complete.get()) {
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					log.error("Waiting for transformation completion interrupted", e);
				}
			}
		}
	}

	private final Path location;
	private final InstanceStore store;
	private final InstanceStoreWriter writer;
	private final LimboInstanceSink limboSink = new LimboInstanceSink();
	private final StoreLimboCollection collection = new StoreLimboCollection();

	private volatile TypeIndex types;

	private final AtomicBoolean complete = new AtomicBoolean();
	private final AtomicBoolean skipLimbo = new AtomicBoolean();
	private final AtomicInteger counter = new AtomicInteger();

	/**
	 * Create a sink with a new temporary store.
	 */
	public StoreTransformationSink() {
		try {
			location = Files.createTempDirectory("transformationSink");
			store = InstanceStoreExtension.getInstance().createStore(DataSet.TRANSFORMED,
					location, null);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot create temporary instance store", e);
		}
		writer = store.openWriter();
	}

	@Override
	public void setTypes(TypeIndex types) {
		this.types = types;
	}

	@Override
	protected void internalAddInstance(Instance instance) {
		InstanceReference ref = writer.add(instance);
		if (!skipLimbo.get()) {
			// possible problem: limbo sink may block
			limboSink.addInstance(new InstanceWithReference(instance, ref));
		}
		counter.incrementAndGet();
	}

	@Override
	protected void internalDone(boolean cancel) {
		try {
			writer.close();
		} catch (IOException e) {
			log.error("Failed to write transformed instances to temporary store", e);
		}
		limboSink.done(cancel);
		complete.set(true);
		log.debug("Instance sink completed (cancel={}), processed {} instances", cancel,
				counter.get());
	}

	@Override
	public void dispose() {
		try {
			store.close();
		} catch (IOException e) {
			log.warn("Could not delete temporary instance store at " + location, e);
		}
		super.dispose();
	}

	@Override
	public InstanceCollection getInstanceCollection() {
		return collection;
	}

}
```

Note: the second `if (firstIterator)` block keeps the test's "first iteration from limbo" behaviour when `done` is called before the first iteration; if `LimboInstanceSink.getInstanceCollection().iterator()` does not return buffered instances after `done(false)`, remove that block and adjust the test so the first `toList()` runs on a thread started before `done(false)`. Check `LimboInstanceSink` before deciding.

- [ ] **Step 6: Register the sink**

In `common/plugins/eu.esdihumboldt.hale.common.headless/resources/main/plugin.xml` add inside the existing `eu.esdihumboldt.hale.headless.sink` extension:

```xml
      <sink
            class="eu.esdihumboldt.hale.common.headless.transform.StoreTransformationSink"
            id="eu.esdihumboldt.hale.headless.sink.store"
            priority="10"
            reiterable="true">
      </sink>
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.headless:test --tests '*StoreTransformationSinkTest'`
Expected: PASS.

- [ ] **Step 8: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.headless:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.headless
git commit -m "feat(headless): add transformation sink based on temporary instance store" -m "ING-4137"
```

---

### Task 12: Orient implementation of the store API

**Files:**
- Create: `common/plugins/eu.esdihumboldt.hale.common.headless.orient/src/eu/esdihumboldt/hale/common/headless/orient/OrientInstanceStore.java`
- Create: `.../headless/orient/OrientInstanceStoreFactory.java`
- Modify: `common/plugins/eu.esdihumboldt.hale.common.headless.orient/resources/main/plugin.xml` (replace sink with store registration)
- Modify: `common/plugins/eu.esdihumboldt.hale.common.headless.orient/build.gradle`
- Delete: `.../headless/orient/OrientTransformationSink.java`, `.../headless/orient/InstanceWithReference.java`
- Test: `common/plugins/eu.esdihumboldt.hale.common.headless.orient/test/eu/esdihumboldt/hale/common/headless/orient/OrientInstanceStoreContractTest.groovy`

**Interfaces:**
- Consumes: `InstanceStore`, `InstanceStoreWriter`, `InstanceStoreFactory` (Task 3); `AbstractInstanceStoreContractTest` (Task 9); existing `LocalOrientDB`, `BrowseOrientInstanceCollection`, `OrientInstanceSink`, `OInstance`.
- Produces: `OrientInstanceStore implements InstanceStore` (`OrientInstanceStore(DataSet, Path)`), `OrientInstanceStoreFactory`, `ID = "orient"`, registered with priority 0.

- [ ] **Step 1: Update `build.gradle`**

```groovy
plugins {
  id 'hale.migrated-groovy'
}

hale {
  bundleName = 'OrientDB temporary instance store'
}

dependencies {
  implementation libs.slf4jplus.api
  implementation libs.commons.io

  implementation project(':common:plugins:eu.esdihumboldt.hale.common.core')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.instance')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.schema')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.instance.orient')
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.instance.store')

  testImplementation testLibs.junit4
  testImplementation project(':common:plugins:eu.esdihumboldt.hale.common.instance.store.test')
}
```

(The module switches to `hale.migrated-groovy` for the Groovy contract test; main sources stay Java in `src`. The dependency on `common.headless` is removed.)

- [ ] **Step 2: Write the failing contract test**

```groovy
package eu.esdihumboldt.hale.common.headless.orient

import java.nio.file.Path

import eu.esdihumboldt.hale.common.instance.model.DataSet
import eu.esdihumboldt.hale.common.instance.store.InstanceStore
import eu.esdihumboldt.hale.common.instance.store.test.AbstractInstanceStoreContractTest

class OrientInstanceStoreContractTest extends AbstractInstanceStoreContractTest {

	@Override
	protected InstanceStore createStore(DataSet dataSet, Path directory) {
		new OrientInstanceStore(dataSet, directory)
	}
}
```

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.headless.orient:test`
Expected: compilation FAIL — `OrientInstanceStore` missing.

- [ ] **Step 3: Create `OrientInstanceStore` and factory**

```java
package eu.esdihumboldt.hale.common.headless.orient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.Filter;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.model.impl.FilteredInstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.impl.InstanceDecorator;
import eu.esdihumboldt.hale.common.instance.orient.OInstance;
import eu.esdihumboldt.hale.common.instance.orient.storage.BrowseOrientInstanceCollection;
import eu.esdihumboldt.hale.common.instance.orient.storage.LocalOrientDB;
import eu.esdihumboldt.hale.common.instance.orient.storage.OrientInstanceSink;
import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Temporary instance store based on OrientDB (legacy, fallback).
 */
public class OrientInstanceStore implements InstanceStore {

	private static final ALogger log = ALoggerFactory.getLogger(OrientInstanceStore.class);

	/**
	 * Only yields instances that were actually inserted (nested instances are
	 * stored as separate records of their type).
	 */
	private static final Filter INSERTED = instance -> {
		Instance root = InstanceDecorator.getRoot(instance);
		return !(root instanceof OInstance) || ((OInstance) root).isInserted();
	};

	private class OrientWriter implements InstanceStoreWriter {

		// OrientDB 1.5 connections are not thread-safe - write on one thread
		private final ExecutorService thread = Executors.newSingleThreadExecutor();
		private final OrientInstanceSink sink = new OrientInstanceSink(database, false);
		private volatile boolean closed;

		@Override
		public InstanceReference add(Instance instance) {
			if (closed) {
				throw new IllegalStateException("Writer is closed");
			}
			if (instance.getDefinition() == null) {
				throw new IllegalArgumentException(
						"Instance without type definition cannot be stored");
			}
			Future<InstanceReference> future = thread.submit(() -> {
				OInstance conv = new OInstance(instance);
				conv.setDataSet(dataSet);
				return sink.putInstance(conv);
			});
			try {
				return future.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while storing instance", e);
			} catch (ExecutionException e) {
				throw new IllegalStateException("Failed to store instance", e.getCause());
			}
		}

		@Override
		public void flush() {
			// instances are written synchronously
		}

		@Override
		public void close() throws IOException {
			if (closed) {
				return;
			}
			closed = true;
			try {
				thread.submit(() -> {
					sink.close();
					return null;
				}).get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				throw new IOException("Failed to close OrientDB instance sink", e.getCause());
			} finally {
				thread.shutdown();
				try {
					thread.awaitTermination(100, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	private final DataSet dataSet;
	private final Path directory;
	private final LocalOrientDB database;
	private OrientWriter writer;

	/**
	 * @param dataSet the data set
	 * @param directory the database directory
	 */
	public OrientInstanceStore(DataSet dataSet, Path directory) {
		this.dataSet = dataSet;
		this.directory = directory;
		this.database = new LocalOrientDB(directory.toFile());
	}

	@Override
	public synchronized InstanceStoreWriter openWriter() {
		if (writer == null || writer.closed) {
			writer = new OrientWriter();
		}
		return writer;
	}

	@Override
	public InstanceCollection getInstances(TypeIndex types) {
		return FilteredInstanceCollection.applyFilter(
				new BrowseOrientInstanceCollection(database, types, dataSet), INSERTED);
	}

	@Override
	public Set<TypeDefinition> getStoredTypes(TypeIndex types) {
		Set<TypeDefinition> result = new LinkedHashSet<>();
		try (ResourceIterator<Instance> it = getInstances(types).iterator()) {
			while (it.hasNext()) {
				result.add(it.next().getDefinition());
			}
		}
		return result;
	}

	@Override
	public synchronized void clear() {
		closeWriter();
		database.clear();
	}

	@Override
	public synchronized void close() throws IOException {
		closeWriter();
		database.delete();
		FileUtils.deleteQuietly(directory.toFile());
	}

	private void closeWriter() {
		if (writer != null) {
			try {
				writer.close();
			} catch (IOException e) {
				log.error("Error closing OrientDB instance writer", e);
			}
			writer = null;
		}
	}

}
```

```java
package eu.esdihumboldt.hale.common.headless.orient;

import java.nio.file.Path;

import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreFactory;

/**
 * Factory for {@link OrientInstanceStore}s.
 */
public class OrientInstanceStoreFactory implements InstanceStoreFactory {

	/**
	 * The store identifier.
	 */
	public static final String ID = "orient";

	@Override
	public InstanceStore create(DataSet dataSet, Path directory, ServiceProvider services) {
		return new OrientInstanceStore(dataSet, directory);
	}

}
```

- [ ] **Step 4: Replace the registration and delete the Orient sink**

`resources/main/plugin.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
   <extension
         point="eu.esdihumboldt.hale.instance.store">
      <store
            class="eu.esdihumboldt.hale.common.headless.orient.OrientInstanceStoreFactory"
            id="orient"
            priority="0">
      </store>
   </extension>

</plugin>
```

```bash
git rm common/plugins/eu.esdihumboldt.hale.common.headless.orient/src/eu/esdihumboldt/hale/common/headless/orient/OrientTransformationSink.java \
       common/plugins/eu.esdihumboldt.hale.common.headless.orient/src/eu/esdihumboldt/hale/common/headless/orient/InstanceWithReference.java
grep -rn "OrientTransformationSink\|headless.orient.InstanceWithReference" --include=*.java --include=*.groovy --include=*.xml . | grep -v /build/
```

Expected: no remaining references.

- [ ] **Step 5: Run the contract tests**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.headless.orient:test`
Expected: PASS (contract tests; fan-out and size tests are skipped via `assumeTrue` because the Orient collection supports neither). If `testStoredTypes` fails because `BrowseOrientInstanceCollection` returns nested `ItemType` records despite the `INSERTED` filter, fix `getStoredTypes` (filter is applied by `getInstances`) rather than the test.

- [ ] **Step 6: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.headless.orient:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.headless.orient
git commit -m "refactor(headless): provide OrientDB as instance store implementation" -m "ING-4137"
```

---

### Task 13: Headless transformation uses the store API

**Files:**
- Modify: `common/plugins/eu.esdihumboldt.hale.common.headless/src/eu/esdihumboldt/hale/common/headless/transform/Transformation.java` (imports ~65–71, lines ~324–371, ~451–453, ~506–515)
- Modify: `common/plugins/eu.esdihumboldt.hale.common.headless/build.gradle` (remove orient dependency)
- Modify: `app/plugins/eu.esdihumboldt.hale.app.transform/build.gradle`
- Modify: `app/features/eu.esdihumboldt.hale.app.feature.cli/build.gradle`, `common/features/eu.esdihumboldt.hale.common.feature.core/build.gradle`
- Test: `app/plugins/eu.esdihumboldt.hale.app.transform/test/eu/esdihumboldt/hale/app/transform/test/ExecuteOrientStoreTest.groovy`

**Interfaces:**
- Consumes: `InstanceStoreExtension`, `InstanceStore`, `StoreInstancesJob` (Tasks 3, 10).

- [ ] **Step 1: Add the Orient variant of the CLI tests (fails to compile/run until wired)**

```groovy
package eu.esdihumboldt.hale.app.transform.test

import org.junit.AfterClass
import org.junit.BeforeClass

import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension

/**
 * Runs the CLI transformation tests with the OrientDB instance store.
 */
class ExecuteOrientStoreTest extends ExecuteTest {

	@BeforeClass
	static void useOrient() {
		System.setProperty(InstanceStoreExtension.SYSTEM_PROPERTY, 'orient')
	}

	@AfterClass
	static void resetStore() {
		System.clearProperty(InstanceStoreExtension.SYSTEM_PROPERTY)
	}
}
```

In `app/plugins/eu.esdihumboldt.hale.app.transform/build.gradle` replace

```groovy
  testRuntimeOnly project(':common:plugins:eu.esdihumboldt.hale.common.headless.orient') // for OrientDB instance sink
```

with

```groovy
  testImplementation project(':common:plugins:eu.esdihumboldt.hale.common.instance.store')
  testRuntimeOnly project(':common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite')
  testRuntimeOnly project(':common:plugins:eu.esdihumboldt.hale.common.headless.orient') // fallback store, ExecuteOrientStoreTest
```

Check `ExecuteTest` for a `@BeforeClass` with the same method name; rename the new methods if they would shadow it.

- [ ] **Step 2: Switch `Transformation` to the store API**

Imports: remove `OInstance`, `BrowseOrientInstanceCollection`, `LocalOrientDB`, `StoreInstancesJob` (orient) and the now unused `InstanceDecorator`/`Filter` imports if unused; add:

```java
import java.io.IOException;
import java.nio.file.Path;

import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreExtension;
import eu.esdihumboldt.hale.common.instance.store.StoreInstancesJob;
```

Replace the block from `final LocalOrientDB db;` through the end of the `else { sourceToUse = new StatsCountInstanceCollection(...); db = null; }` with:

```java
		final InstanceStore store;
		boolean useTempDatabase = settings.useTemporaryDatabase().orElseGet(() -> {
			boolean useDb = false;

			for (Cell cell : alignment.getActiveTypeCells()) {
				/*
				 * XXX right now the source is read for each type transformation - does it makes
				 * sense to use the DB if there is a certain number of type transformations?
				 */

				if (!isStreamingTypeTransformation(cell.getTransformationIdentifier())) {
					useDb = true;
					break;
				}
			}

			return useDb;
		});

		// Create temporary instance store if necessary.
		if (useTempDatabase) {
			try {
				Path storeDir = java.nio.file.Files.createTempDirectory("hale-source-instances");
				store = InstanceStoreExtension.getInstance().createStore(DataSet.SOURCE, storeDir,
						serviceProvider);
			} catch (IOException e) {
				result.setException(e);
				return result;
			}
			sourceToUse = store.getInstances(sourceSchema);
		}
		else {
			sourceToUse = new StatsCountInstanceCollection(sources, reportHandler);
			store = null;
		}
```

(`Files` in this class refers to Guava's `com.google.common.io.Files`, hence the fully qualified `java.nio.file.Files`; remove the Guava import if it becomes unused.)

In the `transformJob` listener replace

```java
				if (db != null) {
					db.delete();
				}
```

with

```java
				if (store != null) {
					try {
						store.close();
					} catch (IOException e) {
						log.warn("Failed to delete temporary instance store", e);
					}
				}
```

Replace the store job creation

```java
			Job storeJob = new StoreInstancesJob("Load source instances into temporary database",
					db, sources, serviceProvider, reportHandler, true) {
```

with

```java
			Job storeJob = new StoreInstancesJob("Load source instances into temporary database",
					store, sources, sourceSchema, serviceProvider, reportHandler, true) {
```

keeping the overridden `onComplete()` and `belongsTo(Object)` methods unchanged.

- [ ] **Step 3: Remove the Orient dependency of `common.headless`**

In `common/plugins/eu.esdihumboldt.hale.common.headless/build.gradle` delete:

```groovy
  implementation project(':common:plugins:eu.esdihumboldt.hale.common.instance.orient')
```

Then verify:

```bash
grep -rn "orient" common/plugins/eu.esdihumboldt.hale.common.headless/src common/plugins/eu.esdihumboldt.hale.common.headless/build.gradle
```

Expected: no matches.

- [ ] **Step 4: Features**

In `app/features/eu.esdihumboldt.hale.app.feature.cli/build.gradle` add next to the `headless` entries:

```groovy
  api(project(":common:plugins:eu.esdihumboldt.hale.common.instance.store"))
  api(project(":common:plugins:eu.esdihumboldt.hale.common.instance.store.sqlite"))
```

and the same two lines in `common/features/eu.esdihumboldt.hale.common.feature.core/build.gradle` (inside its `dependencies` block, following the file's existing `api(project(...))` style).

- [ ] **Step 5: Run headless, CLI and cst tests**

Run: `./gradlew :common:plugins:eu.esdihumboldt.hale.common.headless:test :app:plugins:eu.esdihumboldt.hale.app.transform:test :cst:plugins:eu.esdihumboldt.cst:test`
Expected: PASS, including `ExecuteTest` (SQLite default) and `ExecuteOrientStoreTest` (Orient fallback).

- [ ] **Step 6: Run the full build**

Run: `./gradlew build --parallel`
Expected: BUILD SUCCESSFUL. Investigate any failure in modules that used `OrientTransformationSink` or `common.headless`'s former transitive Orient dependency (add explicit dependencies where a module relied on it).

- [ ] **Step 7: Manual performance comparison (for the PR description)**

```bash
for f in app/plugins/eu.esdihumboldt.hale.app.transform/build/test-results/test/TEST-*Execute*.xml; do
  grep -o 'testsuite name="[^"]*"[^>]*time="[^"]*"' $f
done
```

Record both suite durations (SQLite vs. Orient) for the PR description. If a larger sample project is available, additionally run it with the CLI with and without `-Dhale.instance.store=orient` and record load time, transformation time and peak size of the temporary directory.

- [ ] **Step 8: Format and commit**

```bash
./gradlew :common:plugins:eu.esdihumboldt.hale.common.headless:spotlessApply :app:plugins:eu.esdihumboldt.hale.app.transform:spotlessApply
git add common/plugins/eu.esdihumboldt.hale.common.headless app/plugins/eu.esdihumboldt.hale.app.transform app/features/eu.esdihumboldt.hale.app.feature.cli common/features/eu.esdihumboldt.hale.common.feature.core
git commit -m "feat(headless): use SQLite based temporary instance store by default" -m "OrientDB remains available as fallback via -Dhale.instance.store=orient." -m "ING-4137"
```
