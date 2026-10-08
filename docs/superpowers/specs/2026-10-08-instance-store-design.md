# Replace OrientDB as temporary instance store — design

- Issue: ING-4137 (hale: Replace OrientDB as temporary database)
- Date: 2026-10-08
- Scope of this spec: sub-projects 2 and 3 of the decomposition below (store API, SQLite store with Kryo serialization, headless integration). UI integration and OrientDB removal are separate follow-ups.

## 1. Background

hale uses an embedded OrientDB 1.5.1 database as temporary store for source instances (headless transformation, hale studio) and transformed instances (reiterable transformation sink, hale studio). Problems:

- OrientDB 1.5 is very old; upgrading to the current 3.2.x maintenance line would be a near-complete API rewrite.
- Multi-threaded property transformation was disabled (`TreePropertyTransformer.forkedTransformation = false`) because of issues with OrientDB.
- No effective filtering: OrientDB instances are live views with nested records stored as separate records of their own type, so every collection has to be wrapped with an in-memory "was inserted" filter (`Transformation.java`, `OrientInstanceService`). This disables the existing `MetaFilter`-to-SQL optimization and forces a full scan of all instances per type cell.

### Requirements (from the issue)

- Effective lookup of instances by `InstanceReference`.
- Serialize and deserialize all expected kinds of data; the format need not be stable (temporary data).
- Effectively list the types present and the size of an unfiltered collection.
- Improvements: concurrent read and write; effective `TypeFilter` / fan-out by type; effective `MetaFilter` (`id:` / `source:` in the data view); effective `TypeCellFilter` in type transformation.

### Decomposition of ING-4137

1. Store evaluation (done, summary in section 2).
2. Store abstraction and binary instance serialization (this spec).
3. SQLite store implementation and headless integration (this spec).
4. UI integration in hale studio (replaces `OrientInstanceService`) — separate spec. Depends on how hale studio consumes hale-core modules (the `hale` repo still contains its own Tycho copies of the common modules).
5. Removal of OrientDB (delete `instance.orient`, `headless.orient`, catalog entries) — after 4 and a transition period. The OrientDB/Blueprints dependency of `util.blueprints.entities` (no known consumers) and `util.orient.embedded.test` is handled independently.

Out of scope for this spec: re-enabling multi-threaded property transformation (follow-up ticket; this design only removes the store as a blocker).

## 2. Store evaluation summary

Candidates were evaluated via desk research (maintenance, license, embedding, OSGi, concurrency, JSON support) and a throwaway benchmark (500k synthetic hale-like instances, ~1.2 GB payload, 4-core Linux, single run per configuration, defaults for H2).

|                                   | SQLite               | H2 SQL                | H2 MVStore            | DuckDB          |
| --------------------------------- | -------------------- | --------------------- | --------------------- | --------------- |
| load rows/s (JSON → Kryo payload) | 39k → 97k (1 writer) | 26k → 33k (4 writers) | 29k → 30k (4 writers) | 46k (4 writers) |
| lookup by id ops/s                | 366k                 | 93k                   | 226k                  | 15k             |
| lookup by source id ops/s         | 297k                 | 167k                  | 450k                  | 14k             |
| reads while writing ops/s         | 337k                 | 27k                   | 32k                   | 8k              |
| disk size / payload               | 1.55×                | 5.6×                  | 7.2×                  | 1.56×           |

Decision: **SQLite via `org.xerial:sqlite-jdbc`**.

- Already a hale dependency (`io.jdbc`, `io.jdbc.spatialite`, 3.53.4.0) — native loading in hale's OSGi runtime is proven; ships natives for Windows/Linux (glibc + musl)/macOS on x64 and arm64.
- Extremely stable file format and project, driver released with every SQLite release; public domain / Apache-2.0.
- WAL mode: one writer, non-blocking concurrent readers.

Rejected:

- **DuckDB** (MIT): analytics-oriented; point lookups ~25× slower than SQLite, reads during writes 8k/s; ART indexes must fit in memory and hamper bulk load; JSON paths cannot be indexed; spatial extension not bundled (runtime download); 85 MB jar; v2.0 imminent.
- **H2 2.5** (MPL/EPL, pure Java): viable fallback, but large disk growth untuned, recurring MVStore corruption fixes, docs warn that thread interrupts (Eclipse job cancellation) can corrupt the database.
- **Apache Derby**: retired (2025-10). **HSQLDB**: no release in ~2 years, table locks by default. **Xodus**: sunset. **Berkeley DB JE**: no release since 2018. **MapDB**: maintenance only. **Nitrite**: breaking changes in almost every 2026 release. **ArcadeDB**: Java 21, heavy dependencies, no OSGi manifest. **OrientDB 3.2**: near-complete rewrite on a maintenance branch. **LMDB / RocksDB**: manual indexes, no Windows arm64; RocksDB 87 MB jar. **Lucene 10**: Java 21.

Payload format: **Kryo 5** (BSD-3, OSGi manifest, registered classes written as numeric ids). Benchmark (20k instances): JSON 2126 B / decode 209k/s; CBOR 1605 B / 241k/s; Kryo 1592 B / 373k/s; Fory 1650 B / 350k/s (no OSGi manifest, runtime code generation). JSON brings no benefit because no suitable store offers indexable JSON queries and all filtered fields are stored as columns anyway.

## 3. Modules

| Module                         | Status  | Contents                                                                                                                                                                                       | Dependencies                                                                 |
| ------------------------------ | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `common.instance.store`        | new     | Store API, extension point `eu.esdihumboldt.hale.instance.store`, `InstanceStoreExtension`, generic `StoreInstancesJob`                                                                        | `common.instance`, `common.schema`, `common.instance.index`; no database     |
| `common.instance.store.sqlite` | new     | SQLite store, Kryo codec, extended WKB reader/writer; registered with priority 10                                                                                                              | + `sqlite-jdbc` (catalog), `kryo` (new catalog entry, 5.7.1), `jts` |
| `common.headless.orient`       | changed | Orient implementation of the store API built from code moved out of `Transformation` / `OrientTransformationSink`; registered with priority 0; its transformation sink registration is removed | `instance.orient` (unchanged)                                                |
| `common.headless`              | changed | `Transformation` uses the store API; generic reiterable `StoreTransformationSink`; **no OrientDB dependency**                                                                                  | `common.instance.store`                                                      |
| `common.instance`              | changed | new `TypeAwareFilter`; `FilteredInstanceCollection.applyFilter` optimization                                                                                                                   | —                                                                            |
| `cst`                          | changed | `TypeCellFilter` implements `TypeAwareFilter`; `doTypeTransformation` uses `FilteredInstanceCollection.applyFilter`                                                                            | —                                                                            |

Features: the CLI and core features include `instance.store` and `instance.store.sqlite`; the orient feature keeps `headless.orient`.

After this work, all OrientDB code is in `instance.orient` and `headless.orient` and only used if those bundles are deployed. Part 5 deletes them without touching `common.headless`.

## 4. Store API (`common.instance.store`)

```java
/** Temporary store for the instances of one data set. Closing deletes its files. */
public interface InstanceStore extends Closeable {
  InstanceStoreWriter openWriter();                       // thread-safe
  InstanceCollection getInstances(TypeIndex types);       // reiterable, resolver for its references;
                                                          // InstanceCollection2 if fan-out is supported
  Set<TypeDefinition> getStoredTypes(TypeIndex types);    // types actually present
  void clear();
}

public interface InstanceStoreWriter extends Closeable {
  InstanceReference add(Instance instance);               // reference usable immediately
  void flush();                                           // everything added is visible to readers
}

public interface InstanceStoreFactory {                   // contributed via extension point
  InstanceStore create(DataSet dataSet, Path directory, ServiceProvider services);
}
```

### Selection

- Extension point `eu.esdihumboldt.hale.instance.store` with attributes `id`, `class` (factory), `priority`, following the existing `eu.esdihumboldt.hale.headless.sink` pattern (`AbstractExtension`).
- `InstanceStoreExtension.createStore(dataSet, directory, services)`:
    1. If the system property `hale.instance.store` is set, try the factory with that id first; if it is not installed, log a warning.
    2. Then try factories by descending priority.
    3. If creating a store fails (e.g. native library cannot be loaded), log the error and try the next factory. Fail only if no factory succeeds.
- Selection is only via the system property, so that the source store and the transformation sink (created through the sink extension without access to `TransformationSettings`) are always consistent.

### Generic `StoreInstancesJob`

Store-independent replacement of the Orient `StoreInstancesJob` logic: iterates the source collection, calls `writer.add(instance)`, wraps the reference in `IdentifiableInstanceReference` + `ResolvableInstanceReference` (resolver: the store collection), runs `InstanceProcessor`s and the instance index service, counts instances per type for the report, and calls `flush()` before completing.

### Generic `StoreTransformationSink` (`common.headless`)

Reiterable `TransformationSink` built only on `InstanceStore` (store for `DataSet.TRANSFORMED`), registered in `common.headless`'s `plugin.xml`. It keeps the behaviour of `OrientTransformationSink`: the first iterator streams instances via the limbo sink while the transformation runs (so export can run concurrently); later iterators wait until the transformation is done and iterate the store. `dispose()` closes (deletes) the store.

### Orient implementation (`common.headless.orient`)

- `InstanceStore` wrapping `LocalOrientDB`; `getInstances` returns the "inserted"-filtered `BrowseOrientInstanceCollection` (moved from `Transformation`).
- The writer wraps `OrientInstanceSink.putInstance` and performs all database writes on an internal single thread (as `OrientTransformationSink` does today), since OrientDB 1.5 connections are not thread-safe.
- No new Orient capabilities (no fan-out, sizes or filter optimizations).

## 5. SQLite store (`common.instance.store.sqlite`)

### Files and connections

- One database per store: `<directory>/instances.sqlite` (+ `-wal` / `-shm`).
- `close()` stops the writer, closes all connections and deletes the directory (delete-on-exit as fallback). `clear()` closes, deletes and recreates the database file.
- Connections are created via `SQLiteDataSource` / `SQLiteConfig` directly (no `DriverManager`, avoids OSGi driver discovery).
- Pragmas: `journal_mode=WAL`, `synchronous=OFF` (temporary data), `busy_timeout`; larger `page_size` / `cache_size` for the writer connection.
- One writer connection; a small pool of read connections, borrowed per query or chunk only.

### Schema

```sql
CREATE TABLE types     (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE);   -- QName "{ns}local"
CREATE TABLE instances (id INTEGER PRIMARY KEY, type INTEGER NOT NULL, payload BLOB NOT NULL);
CREATE INDEX instances_type ON instances(type, id);
CREATE TABLE metadata  (key TEXT NOT NULL, value TEXT NOT NULL, instance INTEGER NOT NULL,
                        PRIMARY KEY (key, value, instance)) WITHOUT ROWID;
```

- `payload`: the complete Kryo-serialized instance (nested instances and groups, value, all metadata). One row per top-level instance — no "inserted" filter is needed.
- `metadata`: index-only copy of **string-valued** metadata (e.g. `ID`, `SourceID`) to support `MetaFilter` in SQL. Other metadata is only in the payload.
- `id` is assigned by the writer and is the SQLite rowid: lookups are a single B-tree probe, and rows are stored in id order.

### Writing

- `add(instance)` on the calling thread: assign the id from an `AtomicLong`, serialize with Kryo (so producer threads serialize in parallel), put `id → bytes` into a **pending map**, enqueue the record in a bounded queue (back-pressure), return a `StoreInstanceReference`.
- One **writer thread** drains the queue and inserts in transactions of up to 1000 records (or after 100 ms without new records); after commit it updates per-type counters and removes the committed ids from the pending map.
- **Read-your-writes**: resolving a reference checks the pending map first, so references resolve immediately (join, merge and index service rely on this).
- `flush()` blocks until everything added so far is committed; `close()` flushes and stops the writer thread.
- A failure on the writer thread is kept and rethrown from the next `add` / `flush` / `close`.
- The writer thread ignores interrupts and is only stopped via `close()`.

### Visibility contract

Collections and iterators see committed data. `size()` / `hasSize()` come from the per-type counters (constant time). In transformation, all writing is followed by `flush()` before the collection is iterated (`StoreInstancesJob` before scheduling the transformation; the sink when done).

## 6. Serialization (Kryo)

### Payload structure

```
Instance := typeRef? value metadata group
Group    := propertyCount { nameId valueCount { value } }
value    := tag-and-object (Kryo, registered classes only)
metadata := keyCount { keyId valueCount { value } }
```

- Definitions are not stored. The root type comes from the `types` table or the reference. Nested definitions are resolved on read via `parent.getDefinition().getChild(name)` (as in the Orient implementation).
- A nested instance whose type differs from the property's declared type (e.g. a subtype) additionally writes its type name; on read it is resolved through the collection's `TypeIndex`, falling back to the declared type with a warning.
- The `DataSet` is not stored (one store per data set).

### Per-store dictionaries

In-memory, thread-safe dictionaries map property and metadata names (QNames), CRS definitions (`CRSDefinition`) and target classes of string-converted values to varint ids. Payloads are therefore only readable in the JVM that wrote them — the same assumption the Orient implementation makes, acceptable for a temporary store.

### Value handling (in order)

1. Nested `Instance` / `Group`: written recursively and embedded.
2. Registered value types (Kryo built-in or small custom serializers): primitives and wrappers, `String`, `BigDecimal`, `BigInteger`, `byte[]`, `java.util.Date`, `java.sql.Date/Time/Timestamp` (time part preserved), `java.time` types (`Instant`, `Local*`, `Offset*`, `Zoned*`), `URI`, `URL`, `UUID`, `QName`, `List` / `Set` (recursive), `Object[]`.
3. Geometries: `Geometry` as WKB including Z, M and SRID, with the extended linear ring handling of today's `ExtendedWKBWriter/Reader` (moved into this module). `GeometryProperty` as CRS dictionary id plus geometry, read back as `DefaultGeometryProperty`. Geometry structure in the instance tree (e.g. GML geometry properties) is retained since geometries are ordinary values.
4. String conversion: values that `ConversionService` can convert to and from `String`, for a class whitelist defined in code (initially today's `BigInteger`, `URI`, which step 2 already covers; extended when needed), stored as target class id plus string.
5. Java serialization for other `Serializable` values; classes resolved on read through hale's OSGi-aware class loading (as today).
6. Otherwise the value is dropped with a warning (as today).

### Kryo configuration

`Pool<Kryo>` per store; `registrationRequired=true` with registration ids fixed in code (no class names written, OSGi-safe); references disabled (instance values form a tree); pooled output buffers. Serialization runs on the producer thread, deserialization on the reader thread.

### Read model

Payloads are deserialized into fully materialized, detached `StoredInstance` objects (`DefaultInstance` + `Identifiable`, id = store id) with `DefaultInstance` / `DefaultGroup` children. No live views, connection handles or thread-local bindings; instances can be passed between threads. Changes to read instances are not persisted (matches how transformation uses source instances). `StoredInstance` is registered with the Groovy sandbox allow-list (as `OInstance` today).

## 7. Reading, references and filtering

### `StoreInstanceCollection` (implements `InstanceCollection2`, resolver for its references)

- Covers the stored types the given `TypeIndex` knows as mapping-relevant (parity with today); type ids are resolved once per collection.
- Iteration via keyset pagination: `SELECT id, type, payload FROM instances WHERE id > ? [AND type IN (…)] ORDER BY id LIMIT 1000`. A read connection is borrowed per chunk only, so iterators never hold connections or long read transactions; unclosed iterators cannot leak connections or block WAL checkpoints.
- The iterator implements `InstanceIterator` (`typePeek()` / `skip()`): skipped instances are never deserialized.
- `hasSize()` is `true`; `size()` / `isEmpty()` from per-type counters.
- `supportsFanout()` is `true`; `fanout()` returns one collection per stored type.

### `select(filter)`

| Filter                               | Handling                                                                                       |
| ------------------------------------ | ---------------------------------------------------------------------------------------------- |
| `TypeFilter`                         | fan-out entry for the type (or empty collection)                                               |
| `MetaFilter` with only String values | SQL via `metadata` join, optionally restricted to the filter's type; `IN` lists chunked; exact |
| `TypeAwareFilter`                    | SQL restricted to the filter's types, filter applied in memory on those                        |
| other                                | `FilteredInstanceCollection` (in memory)                                                       |

### `TypeAwareFilter`

New interface in `common.instance`: `Set<TypeDefinition> getTypes()` — only instances of these types can match.

- `TypeCellFilter` (`ConceptualSchemaTransformer`) implements it.
- `FilteredInstanceCollection.applyFilter`: if the collection supports fan-out, combine the fan-out entries of the filter's types and apply the filter only to those. This also benefits existing fan-out collections (CSV, Shapefile, JDBC, GeoPackage, JSON).
- `ConceptualSchemaTransformer.doTypeTransformation` uses `FilteredInstanceCollection.applyFilter(source, filter)` instead of `source.select(filter)`.

### `StoreInstanceReference`

- Contains a per-store key (random UUID), the `long` id, the `DataSet` and the root `TypeDefinition`; implements `Identifiable` (id = store id).
- `equals` / `hashCode` on store key, id and data set; stable for the lifetime of the store (UI selection, map painter, spatial index).
- `getInstance(ref)`: pending map first, then `SELECT payload FROM instances WHERE id = ?`, deserialized with the reference's type; `null` for unknown ids or references of another store.
- `getReference(instance)`: unwraps decorators to `StoredInstance` and uses its id; `null` for other instances.

## 8. Headless integration

`Transformation.transform`:

- The decision whether a temporary store is needed is unchanged (any non-streaming type cell, or `TransformationSettings.useTemporaryDatabase()`).
- Replaces `LocalOrientDB` and the "inserted" filter:

    ```java
    store = InstanceStoreExtension.getInstance().createStore(DataSet.SOURCE, tempDir, serviceProvider);
    sourceToUse = store.getInstances(sourceSchema);
    storeJob = new StoreInstancesJob("Load source instances…", store, sources, serviceProvider, reportHandler, true);
    ```

- Job chaining unchanged: on successful store job, export and transformation jobs are scheduled; when the transformation job completes, `store.close()` replaces `db.delete()`.
- The reiterable sink is `StoreTransformationSink` (see section 4).

## 9. Error handling

| Situation                            | Behaviour                                                                                              |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------ |
| store cannot be created              | log error, try the next factory by priority; fail only if none works                                   |
| writer thread fails (e.g. disk full) | rethrown from `add` / `flush` / `close`; store job fails with report entry, transformation not started |
| single value not serializable        | warning in report, value dropped                                                                       |
| whole instance not serializable      | error in report, instance skipped, loading continues                                                   |
| payload cannot be deserialized       | error logged, instance skipped, iteration continues                                                    |
| cancel / dispose                     | `close()` stops the writer and deletes the store directory                                             |

Temporary files go to `java.io.tmpdir` (as today); sqlite-jdbc's native library extraction honours `org.sqlite.tmpdir`.

## 10. Testing

- **Codec unit tests**: table-driven round trips for all value kinds, nested instances and groups, subtypes, metadata, geometries (2D / 3D / M, `LinearRing`, `GeometryProperty` with CRS), string conversion, `Serializable` fallback, dropping of non-serializable values with warning.
- **Store contract tests**: abstract `InstanceStoreContractTest`, run against the SQLite and the Orient implementation: add / resolve before and after `flush`, iteration, `typePeek` / `skip`, fan-out, sizes, stored types, `MetaFilter`, `TypeAwareFilter`, `clear`, `close` deletes files, concurrent writers and readers.
- **`FilteredInstanceCollection`**: `TypeAwareFilter` optimization against `PerTypeInstanceCollection`.
- **Integration**: existing headless transformation tests (`app.transform` and others using `Transformation`) run with the SQLite default; one test class additionally with `-Dhale.instance.store=orient`.
- **Performance**: manual comparison of old vs. new store on a larger sample project (load time, transformation time, disk use), documented in the PR.

## 11. Follow-ups

- UI integration in hale studio (part 4): store-based `InstanceService` replacing `OrientInstanceService`, `HaleStoreInstancesJob`, `HaleOrientInstanceSink`; move `ONameUtil` usage of `HALEContextProvider`.
- Re-enable multi-threaded property transformation (`TreePropertyTransformer.forkedTransformation`).
- Remove OrientDB (part 5); separately decide on `util.blueprints.entities` / `util.orient.embedded.test`.
