# GeoParquet Instance Writer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A hale-core instance writer that writes GeoParquet 2.0 (and 1.1/1.0) files that pass the GeoParquet spec validators and Portolan's `portolan check` without re-conversion.

**Architecture:** New OSGi bundle `io/plugins/eu.esdihumboldt.hale.io.geoparquet`. Uses parquet-java (`parquet-hadoop`) directly, shaded into the bundle, with a pure-Java compression codec factory (no Hadoop/native classes at runtime). One Parquet file per hale type. hale types map to Parquet schemas (`ParquetSchemaMapper`), instances are written through a custom `WriteSupport`, `geo` metadata is produced at file finalization, and rows are spatially sorted (Hilbert) in a two-pass write over `InstanceReference`s.

**Tech Stack:** Java 17 (main), Groovy 2.5 + JUnit 4 (tests), Gradle (hale conventions, `hale.shadow-dependencies`, bnd), parquet-java, aircompressor, JTS, GeoTools referencing, mise (uv + pipx tools for validators).

**Spec:** `docs/superpowers/specs/2026-10-28-geoparquet-writer-design.md` — read it first; this plan argues from it. Spike code for orientation (throwaway, not in repo): codec factory and nested-column writing shown in the spec §9.

## Global Constraints

- Java 17; no Hadoop, Arrow, JNI/native code at runtime; no network access at runtime.
- New dependencies at their **latest release** at implementation time (check Maven Central / PyPI); add them to `gradle/libs.versions.toml` / `gradle/test-libs.versions.toml`, never inline coordinates in `build.gradle`.
- `parquet-hadoop` must exclude `org.xerial.snappy:snappy-java` and `com.github.luben:zstd-jni` (main/shadow scope). `hadoop-common` only `compileOnly`, non-transitive.
- GeoParquet `version` strings exactly `"2.0.0"`, `"1.1.0"`, `"1.0.0"`. Default version 2.0.
- Default compression ZSTD; row groups at most **150,000** rows by default.
- Geometry encoding `WKB` only.
- bbox covering DOUBLE fields must use `ColumnOrder.typeDefined()` explicitly.
- `geo` metadata always contains the column-level `bbox` for every geometry column with at least one geometry.
- Never omit `crs` for a non-CRS84 CRS; unknown CRS → `"crs": null` + report warning.
- Commits: Conventional Commits (`feat:`, `test:`, `build:`, `docs:` …), no `Co-authored-by`. Use `git commit --fixup` for corrections to earlier commits of this branch.
- Code style: match hale-core (tabs, Javadoc on public members, LGPL header as in `GeopackageInstanceWriter.java`); run `./gradlew spotlessApply` before each commit.

## Review Focus

1. **Lat/lon-first geographic CRS other than 4326** (e.g. EPSG:4258 ETRS89 from INSPIRE GML): coordinates must be written lon/lat (x/y). Tests in Task 2 (`testLatitudeFirstCrsIsSwapped`) and Task 10 (`testInspireAddressLike`, end-to-end).
2. **Empty geometries / features with null primary geometry** in a type that has geometries: bbox column null for that row (bbox struct `optional`), not NaN; excluded from extent and statistics; sorted to the end. Tests in Task 5 (`testNullAndEmptyGeometry`) and Task 8 (`testFeaturesWithoutGeometrySortedLast`).
3. **Type with zero instances** and **an export with no instances at all**: no file written for that type; with nothing written at all, report a warning and succeed (as the Shapefile writer does). Test in Task 6 (`testNoInstances`).
4. **Property names that are not unique** after flattening groups/choices or across namespaces (`gml-conflict.gpkg` case, or a choice member with the same local name as a sibling): deterministic, unique column names. Test in Task 4 (`testNameCollisionsAreSuffixed`).
5. **Values that do not convert** to the column type (e.g. a non-numeric string in an Integer property): reported as error in the IOReport, value written as null, the export continues. Test in Task 5 (`testConversionErrorIsReported`).

---

## File Structure

All new code lives in `io/plugins/eu.esdihumboldt.hale.io.geoparquet/` (picked up automatically by `settings.gradle`).

| Path (relative to plugin) | Responsibility |
|---|---|
| `build.gradle` | dependencies, shading, bnd imports, `hadoopFreeTest` task, manifest verification |
| `resources/main/plugin.xml` | content type `.parquet`, writer provider + parameters |
| `resources/main/eu/esdihumboldt/hale/io/geoparquet/crs/epsg-projjson.tsv.gz` | generated PROJJSON for all non-deprecated EPSG CRS (`<code>\t<json>` per line) |
| `resources/main/eu/esdihumboldt/hale/io/geoparquet/crs/epsg-projjson.version` | PROJ / EPSG database versions used for generation |
| `scripts/generate-projjson.py` | generator for the two files above |
| `src/eu/esdihumboldt/hale/io/geoparquet/GeoParquetInstanceWriter.java` | the writer (parameters, execute, streaming and sorted paths, output naming) |
| `src/eu/esdihumboldt/hale/io/geoparquet/GeoParquetVersion.java` | version enum |
| `src/.../internal/PureJavaCodecFactory.java` | compression without Hadoop |
| `src/.../internal/crs/ProjJsonRegistry.java` | lookup of bundled PROJJSON |
| `src/.../internal/crs/GeoParquetCrs.java` | resolved CRS representation for `geo` and the Parquet logical type |
| `src/.../internal/schema/ParquetSchemaMapper.java` | hale `TypeDefinition` → `TypeMapping` |
| `src/.../internal/schema/TypeMapping.java`, `ColumnMapping.java`, `ValueKind.java` | mapping model |
| `src/.../internal/write/InstanceWriteSupport.java` | parquet `WriteSupport<Instance>`; writes records, collects stats, emits `geo` on finalize |
| `src/.../internal/write/ValueWriter.java` | conversion of single values to Parquet primitives |
| `src/.../internal/write/GeometryConverter.java` | interface implemented by the writer for CRS conversion |
| `src/.../internal/write/TypeFileWriter.java` | one open Parquet file for one type |
| `src/.../internal/meta/GeoColumnStats.java`, `GeoMetadataBuilder.java` | geometry types/extent tracking, `geo` JSON |
| `src/.../internal/sort/HilbertCurve.java`, `SortIndex.java` | sort keys and per-type reference ordering |
| `test/eu/esdihumboldt/hale/io/geoparquet/...` | tests (Groovy unless noted) |
| `test/.../GeoParquetTestUtil.groovy` | write helper, read-back helpers |
| `test/.../GeoParquetCompliance.groovy` | in-Java compliance checks (spec §6.2) |
| `test/.../ExternalValidators.groovy` | gpio / Portolan / GDAL invocation |
| `resources/test/geoparquet-schema/{1.0.0,1.1.0,2.0.0}.json` | vendored official `geo` JSON schemas |
| repo root `mise.toml` | `uv`, `pipx:geoparquet-io`, `pipx:portolan-cli` |

Packages: `eu.esdihumboldt.hale.io.geoparquet` (public), `…geoparquet.internal.*` (private in bnd).

---

### Task 1: Bundle scaffold, dependencies, pure-Java compression, Hadoop-free guard

**Files:**
- Create: `io/plugins/eu.esdihumboldt.hale.io.geoparquet/build.gradle`
- Create: `…/resources/main/plugin.xml` (content type only for now)
- Create: `…/src/eu/esdihumboldt/hale/io/geoparquet/internal/PureJavaCodecFactory.java`
- Modify: `gradle/libs.versions.toml`, `gradle/test-libs.versions.toml`
- Test: `…/test/eu/esdihumboldt/hale/io/geoparquet/internal/PureJavaCodecFactoryTest.groovy`, `…/test/eu/esdihumboldt/hale/io/geoparquet/HadoopFreeTest.java`

**Interfaces:**
- Produces: `public class PureJavaCodecFactory implements org.apache.parquet.compression.CompressionCodecFactory` with no-arg constructor; `getCompressor(CompressionCodecName)` supports `UNCOMPRESSED`, `ZSTD`, `SNAPPY`, `GZIP` and throws `UnsupportedOperationException` for others; `getDecompressor` returns a decompressor that throws `UnsupportedOperationException` (write-only).
- Produces: Gradle task `hadoopFreeTest` (runs test classes matching `*HadoopFreeTest*` with all jars named `hadoop-*`, `zstd-jni-*`, `snappy-java-*` removed from the classpath), wired into `check`.

- [ ] **Step 1: Catalog entries.** Add to `libs.versions.toml`: `parquet` version, `parquet-hadoop` (`org.apache.parquet:parquet-hadoop`), `aircompressor` (`io.airlift:aircompressor`, version as resolved transitively by parquet-hadoop), `hadoop-common` (`org.apache.hadoop:hadoop-common`, latest 3.4.x). Add to `test-libs.versions.toml`: `hadoop-common`, `hadoop-mapreduce-client-core` (same version), `zstd-jni`, `snappy-java`, `json-schema-validator` (`com.networknt:json-schema-validator`).
- [ ] **Step 2: `build.gradle`.**

```groovy
plugins {
  id 'hale.migrated-groovy'
  id 'hale.shadow-dependencies'
}

hale {
  bundleName = 'hale GeoParquet Support'
  // dead code paths in parquet-hadoop reference Hadoop and native codecs; parquet relocates its own deps to shaded.parquet.*
  importPackage 'org.apache.hadoop.*;resolution:=optional,org.xerial.snappy;resolution:=optional,com.github.luben.zstd;resolution:=optional,!shaded.parquet.*,*'
  privatePackage 'eu.esdihumboldt.hale.io.geoparquet.internal.*,shaded.parquet.*'
}
```

Dependencies: `shadowImplementation(libs.parquet.hadoop) { exclude group: 'org.xerial.snappy'; exclude group: 'com.github.luben' }`, `shadowImplementation libs.aircompressor`, `compileOnly(libs.hadoop.common) { transitive = false }`, plus the `implementation` set of the GeoPackage plugin (`slf4jplus.api`, `guava`, `eclipse.core.runtime`, `jts`, `geotools.opengis/referencing/main`, hale `schema.groovy`, `instance.groovy`, `convert`, `util`) and the GeoPackage plugin's test dependencies. Test-only: `testRuntimeOnly` hadoop-common, hadoop-mapreduce-client-core, zstd-jni, snappy-java (needed only for read-back in tests; verified in planning). Register `hadoopFreeTest` (`Test` task, `testClassesDirs = sourceSets.test.output.classesDirs`, classpath filtered as above, `filter { includeTestsMatching '*HadoopFreeTest*' }`), exclude `*HadoopFreeTest*` from the normal `test` task, `check.dependsOn hadoopFreeTest`. Add a `verifyBundleManifest` task (dependsOn `jar`/`shadowJar` output used for publishing) that fails if `Import-Package` contains `org.apache.hadoop`, `org.xerial.snappy` or `com.github.luben.zstd` without `resolution:=optional`, or contains any `shaded.parquet` package; `check.dependsOn verifyBundleManifest`.

- [ ] **Step 3: Write failing tests.**

`PureJavaCodecFactoryTest` (extends nothing, plain JUnit4):
- `testRoundTrip` — parameterized over `ZSTD`, `SNAPPY`, `GZIP`, `UNCOMPRESSED`: compress 64 KiB of repetitive bytes via `getCompressor(codec).compress(BytesInput.from(data))`; decompress with `io.airlift.compress.zstd.ZstdDecompressor` / `SnappyDecompressor` / `GZIPInputStream` / identity; `assert decompressed == data`; for non-UNCOMPRESSED `assert compressed.size() < data.length`; `getCodecName() == codec`.
- `testUnsupportedCodec` — `LZ4_RAW` → `UnsupportedOperationException`.

`HadoopFreeTest` (Java): writes a 3-row file with `ExampleParquetWriter.builder(new LocalOutputFile(tmp)).withConf(new PlainParquetConfiguration()).withCodecFactory(new PureJavaCodecFactory())` for each of the four codecs; then asserts `Class.forName("org.apache.hadoop.conf.Configuration")` throws `ClassNotFoundException`. (Later tasks extend this class to run the real writer — Task 7.)

- [ ] **Step 4: Run, expect FAIL** — `./gradlew :io:plugins:eu.esdihumboldt.hale.io.geoparquet:test :io:plugins:eu.esdihumboldt.hale.io.geoparquet:hadoopFreeTest` → compilation failure (`PureJavaCodecFactory` missing).
- [ ] **Step 5: Implement `PureJavaCodecFactory`** — ZSTD/Snappy via aircompressor `Compressor.compress(byte[],int,int,byte[],int,int)` into a buffer of `maxCompressedLength`; GZIP via `GZIPOutputStream`; Snappy is the raw (unframed) format.
- [ ] **Step 6: Run** both test tasks and `./gradlew :io:plugins:eu.esdihumboldt.hale.io.geoparquet:check` → PASS (incl. `verifyBundleManifest`).
- [ ] **Step 7: Commit** — `build: add GeoParquet bundle with pure-Java compression`

---

### Task 2: Bundled PROJJSON and CRS resolution

**Files:**
- Create: `…/scripts/generate-projjson.py`, `…/resources/main/eu/esdihumboldt/hale/io/geoparquet/crs/epsg-projjson.tsv.gz`, `…/crs/epsg-projjson.version`
- Create: `…/src/…/internal/crs/ProjJsonRegistry.java`, `…/internal/crs/GeoParquetCrs.java`
- Test: `…/test/…/internal/crs/GeoParquetCrsTest.groovy`

**Interfaces:**
- Produces: `ProjJsonRegistry.getInstance()`; `Optional<String> ProjJsonRegistry#getProjJson(int epsgCode)` — scans the gz resource once per requested code, caches results (do not load all 7k entries into memory).
- Produces: `public final class GeoParquetCrs` with
  - `static GeoParquetCrs resolve(CRSDefinition crs, SimpleLog log)` (null `crs` → `unknown()` + warning)
  - `static GeoParquetCrs crs84()`, `static GeoParquetCrs unknown()`
  - `boolean isCrs84()`, `boolean isUnknown()`, `Integer getEpsgCode()` (null if none)
  - `String getProjJson()` — PROJJSON for `geo` `crs`; null for CRS84 (omit key) and unknown (write JSON `null`)
  - `String getParquetCrs()` — value for `LogicalTypeAnnotation.geometryType(String)`: `null` for CRS84, `"EPSG:<code>"` for EPSG, and the unknown-CRS value for unknown (start with `"srid:0"`, confirm against GDAL validator in Task 10 and adjust)
  - `CoordinateReferenceSystem getTargetCrs()` — the CRS geometries are converted to before writing, as decoded from the definition (for `CodeDefinition('EPSG:4326')` that is lat/lon axis order)
  - `boolean isSwapAxes()` — `CRS.getAxisOrder(getTargetCrs()) == CRS.AxisOrder.NORTH_EAST`; the writer swaps x/y after conversion when true, so output is always lon/lat (x/y)

- [ ] **Step 1: Generator script.** `scripts/generate-projjson.py`: for all non-deprecated EPSG CRS from `pyproj.database.query_crs_info(auth_name="EPSG")`, write `<code>\t<minified PROJJSON>` lines sorted by numeric code, gzip level 9, plus the version file (`proj=<pyproj.proj_version_str>`, `epsg=<EPSG db version from pyproj.database.get_database_metadata("EPSG.VERSION")>`, generation date). Document invocation in the script header: `mise x uv -- uv run --with pyproj==<latest> python -I scripts/generate-projjson.py resources/main/eu/esdihumboldt/hale/io/geoparquet/crs`. Run it and commit the outputs (expect ~0.7 MB, ~7,200 lines).
- [ ] **Step 2: Write failing tests** (`GeoParquetCrsTest`, extends `AbstractPlatformTest`):
  - `testEpsgProjJson` — `resolve(new CodeDefinition('EPSG:2056'))`: `epsgCode == 2056`; parsed `projJson.id.authority == 'EPSG'`, `projJson.id.code == 2056`, `projJson.type == 'ProjectedCRS'`; `parquetCrs == 'EPSG:2056'`; `!swapAxes`.
  - `testWgs84IsCrs84` — `'EPSG:4326'` (lat/lon as decoded by `CodeDefinition`): `isCrs84()`, `projJson == null`, `parquetCrs == null`, `swapAxes`.
  - `testLatitudeFirstCrsIsSwapped` — `'EPSG:4258'`: not CRS84, `epsgCode == 4258`, `swapAxes`.
  - `testWktWithoutCodeIsUnknown` — `new WKTDefinition(<WKT of a custom LCC projection without AUTHORITY>, null)`: `isUnknown()`, log received one warning.
  - `testRegistryMissingCode` — `getProjJson(999999).isEmpty()`.
- [ ] **Step 3: Run, expect FAIL** (classes missing).
- [ ] **Step 4: Implement.** EPSG code via `CRSDefinitionUtil.getCode` / `CodeDefinition.extractEPSGCode`, else `CRS.lookupEpsgCode(crs, true)` (as `AbstractGeoInstanceWriter#extractCode`). Code present but not in registry → unknown + warning.
- [ ] **Step 5: Run tests → PASS.**
- [ ] **Step 6: Commit** — `feat(geoparquet): bundle EPSG PROJJSON and resolve CRS`

---

### Task 3: Schema mapping — simple properties, geometries, bbox, versions

**Files:**
- Create: `…/src/…/internal/schema/ParquetSchemaMapper.java`, `TypeMapping.java`, `ColumnMapping.java`, `ValueKind.java`
- Create: `…/src/eu/esdihumboldt/hale/io/geoparquet/GeoParquetVersion.java`
- Test: `…/test/…/internal/schema/ParquetSchemaMapperTest.groovy`

**Interfaces:**
- Produces: `public enum GeoParquetVersion { V1_0("1.0.0"), V1_1("1.1.0"), V2_0("2.0.0"); String getVersionString(); boolean usesNativeGeometryType(); /* V2_0 */ boolean declaresCovering(); /* V1_1, V2_0 */ static GeoParquetVersion fromParameter(String) /* accepts "1.0","1.1","2.0" and the full strings; null → V2_0 */ }`
- Produces: `enum ValueKind { STRING, BOOLEAN, INT32, INT64, FLOAT, DOUBLE, DATE, TIMESTAMP_MILLIS, TIME_MILLIS, BINARY, DECIMAL_AS_STRING, DECIMAL, WKB, WKT, JSON }` — `DECIMAL`: `FIXED_LEN_BYTE_ARRAY(16)` with `LogicalTypeAnnotation.decimalType(scale, 38)`
- Produces: `public enum BigNumberEncoding { STRING, DOUBLE, DECIMAL; static BigNumberEncoding fromParameter(String) /* "string","double","decimal", case-insensitive; null → STRING */ }` in package `eu.esdihumboldt.hale.io.geoparquet`
- Produces: `public record MappingOptions(GeoParquetVersion version, int nestingDepth, BigNumberEncoding bigNumberEncoding, int decimalScale)` in `…internal.schema`, with `static MappingOptions defaults()` = `(V2_0, 5, STRING, 9)`
- Produces: `final class ColumnMapping` — `String getColumnName()`; `List<QName> getPropertyPath()` (path from the parent group/instance, including flattened group/choice names); `boolean isRepeated()`; `boolean isOptional()`; `ValueKind getValueKind()` (null for struct); `List<ColumnMapping> getChildren()` (non-empty for struct); `boolean isStruct()`.
- Produces: `final class TypeMapping` — `TypeDefinition getType()`; `MessageType getSchema()`; `List<ColumnMapping> getColumns()` (in schema order, excluding bbox); `List<String> getGeometryColumns()` (top-level WKB columns, primary first); `String getPrimaryGeometryColumn()` (null if the type has no geometry); `List<QName> getPrimaryGeometryPath()`; `boolean hasBboxColumn()`; `static final String BBOX_COLUMN = "bbox"`; `static final String PRIMARY_GEOMETRY_COLUMN = "geometry"`.
- Produces: `ParquetSchemaMapper(MappingOptions options)`; `TypeMapping map(TypeDefinition type, Map<String, GeoParquetCrs> geometryCrs, SimpleLog log)` — `geometryCrs` keyed by geometry column name (needed for the logical-type `crs`); a second method `List<String> geometryColumnNames(TypeDefinition type)` lets the caller determine CRS per geometry column before calling `map`.

Mapping rules for this task (nested structures follow in Task 4 — until then, complex properties without `HasValueFlag` are skipped with a log warning):
- Property binding → ValueKind: `String`, `URI`, other/unknown → STRING; `Boolean` → BOOLEAN; `Byte/Short/Integer` → INT32; `Long` → INT64; `Float` → FLOAT; `Double` → DOUBLE; `BigInteger`, `BigDecimal` → by `options.bigNumberEncoding()`: STRING → DECIMAL_AS_STRING (UTF8, lossless, **default**), DOUBLE → DOUBLE (lossy), DECIMAL → DECIMAL with precision 38 and scale 0 for `BigInteger`, `options.decimalScale()` for `BigDecimal`; `LocalDate`, `java.sql.Date` → DATE; `Instant`, `Date`, `Timestamp`, `Calendar`, `LocalDateTime` → TIMESTAMP_MILLIS (`isAdjustedToUTC=true`); `LocalTime`, `java.sql.Time` → TIME_MILLIS; `byte[]` → BINARY. Use the same `Binding`/`HasValueFlag`/`AugmentedValueFlag` inspection as `GeopackageInstanceWriter#createTableIfNecessary`.
- Primary geometry path: the **shortest** property path (breadth-first over the type's properties, children in definition order, groups/choices transparent) to a property whose type has `GeometryType#isGeometry()`; column name `geometry`; removed from its original position (spec §5.4).
- Other top-level geometry properties → WKB column named after the property. Geometry-typed properties nested deeper → WKT (Task 4).
- WKB columns: `BINARY` with `LogicalTypeAnnotation.geometryType(crs.getParquetCrs())` for V2_0, plain `BINARY` otherwise; `required` never (always `optional`).
- `bbox` (only if a primary geometry exists, all versions): optional group of four `required DOUBLE` fields `xmin, ymin, xmax, ymax`, each with `.columnOrder(ColumnOrder.typeDefined())`; placed directly before `geometry`.
- Cardinality: max occurrence > 1 on a simple property → 3-level LIST (`Types.optionalList().optionalElement(...)`); geometry columns never LIST (Task 5 merges multiple values).

- [ ] **Step 1: Write failing tests** (`ParquetSchemaMapperTest`, schemas via `SchemaBuilder`):
  - `testSimpleTypes` — type with `a(String)`, `b(Double)`, `c(Long)`, `d(Integer)`, `e(Boolean)`, `f(BigInteger)`, `g(BigDecimal)`, `h(LocalDate)`, `i(Instant)`, `j(byte[])`: assert each field's primitive type + logical annotation (string → `stringType()`, h → `dateType()`, i → `timestampType(true, MILLIS)`, f/g → `stringType()`).
  - `testPrimaryGeometryV2` — type `{ name(String); geom(GeometryProperty) }`: field order `name, bbox, geometry`; `geometry` annotation `instanceof GeometryLogicalTypeAnnotation` with crs `EPSG:25832` (pass `['geometry': resolve('EPSG:25832')]`); bbox children are DOUBLE with `getColumnOrder() == ColumnOrder.typeDefined()`.
  - `testPrimaryGeometryV1` — same with `V1_1` and `V1_0`: plain BINARY, no logical annotation; bbox still present.
  - `testNestedPrimaryGeometryIsMovedUp` — INSPIRE-like `Address { position { GeographicPosition { geometry(GeometryProperty); default(Boolean) } } }` (cardinality 1): `primaryGeometryPath == [position, GeographicPosition, geometry]`; top-level `geometry` column exists; the `position` struct (Task 4) — for now: assert no column named after the nested geometry at top level other than `geometry`.
  - `testSecondaryGeometryColumn` — two top-level geometry properties `geom`, `outline`: `geometryColumns == ['geometry', 'outline']`.
  - `testNoGeometry` — `primaryGeometryColumn == null`, `!hasBboxColumn()`.
  - `testRepeatedSimpleProperty` — `names(String, cardinality: '0..n')` → LIST of optional UTF8.
  - `testBigNumberEncodings` — `f(BigInteger)`, `g(BigDecimal)` with each encoding: STRING → UTF8; DOUBLE → DOUBLE; DECIMAL with `decimalScale = 4` → FIXED_LEN_BYTE_ARRAY(16), `DecimalLogicalTypeAnnotation` precision 38, scale 0 (f) / 4 (g).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** the model classes, `BigNumberEncoding`, `MappingOptions` and the mapper.
- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `feat(geoparquet): map hale types to Parquet schemas`

---

### Task 4: Schema mapping — nested structures, groups/choices, depth limit, collisions

**Files:**
- Modify: `…/internal/schema/ParquetSchemaMapper.java`
- Test: `…/test/…/internal/schema/ParquetSchemaMapperNestedTest.groovy`

**Interfaces:**
- Consumes: Task 3 model.
- Produces: `ColumnMapping` structs (`isStruct()`, children) and ValueKinds `WKT`, `JSON`.

Rules:
- Complex property (has child properties) → optional group; if it also has a value (`HasValueFlag`/`AugmentedValueFlag`), the value becomes child field `value` (constant `ColumnMapping.VALUE_FIELD = "value"`).
- XML attributes (`XmlAttributeFlag`) → child fields with their local name (no prefix); if that collides with an element name in the same struct, the attribute gets prefix `attr_` (decision for this plan).
- Group and choice property definitions (`GroupPropertyDefinition`) are transparent: their children are added to the parent; choice members become optional; a repeated group/choice makes each child repeated.
- Repeated complex property → LIST of group.
- Depth: top-level properties have depth 1; a property at depth > `options.nestingDepth()` (default **5**, configurable via writer parameter) or whose type is already on the current path (recursion) → `JSON` string column (UTF8) for the whole subtree.
- Geometry properties below top level (except the primary geometry, which is omitted at its original position) → `WKT` string.
- Name collisions in one struct (incl. top level, incl. reserved `geometry`/`bbox`): first occurrence keeps the local name; among candidates with equal local name prefer a non-GML namespace first (as `GeopackageSchemaBuilder` does for `gml-conflict`), later ones get `_2`, `_3`, … in definition order.

- [ ] **Step 1: Write failing tests:**
  - `testStruct` — `address { street(String); number(Integer) }` → optional group `address` with two optional fields.
  - `testComplexWithValueAndAttribute` — complex property with simple content and an XML attribute `nilReason` → struct with `value` and `nilReason`.
  - `testListOfStructs` — `locators(cardinality: '0..n') { designator(String); type(String) }` → LIST whose element is a group with `designator`, `type`.
  - `testGroupAndChoiceAreTransparent` — type with a sequence group containing `x(String)` and a choice of `p(String)`/`q(Integer)`: top-level fields `x, p, q`, all optional.
  - `testRecursionBecomesJson` — type `Node { name(String); child(Node) }`: `child` is UTF8 with ValueKind `JSON`.
  - `testDepthLimit` — 7 levels nested, `nestingDepth = 5`: level-6 property is a JSON column inside the level-5 struct.
  - `testNestedGeometryIsWkt` — non-primary geometry inside a struct → UTF8 with ValueKind `WKT`.
  - `testNestedPrimaryGeometryRemovedFromStruct` — continue Task 3's INSPIRE case: the `GeographicPosition` struct contains `default` but no `geometry` field.
  - `testNameCollisionsAreSuffixed` — properties `{http://www.opengis.net/gml/3.2}name` and `{urn:x}name` plus a choice member `name`: columns `name` (urn:x), `name_2`, `name_3`; and a property named `bbox` on a type with geometry → `bbox_2`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run (Task 3 + 4 tests) → PASS.**
- [ ] **Step 5: Commit** — `feat(geoparquet): map nested properties to Parquet structs and lists`

---

### Task 5: Writing records — WriteSupport, value conversion, geometries, `geo` metadata

**Files:**
- Create: `…/internal/write/InstanceWriteSupport.java`, `ValueWriter.java`, `GeometryConverter.java`, `TypeFileWriter.java`
- Create: `…/internal/meta/GeoColumnStats.java`, `GeoMetadataBuilder.java`
- Create: `…/resources/test/geoparquet-schema/1.0.0.json`, `1.1.0.json`, `2.0.0.json` (official schemas: tags `v1.0.0`, `v1.1.0`; for 2.0 the `format-specs/schema.json` from `main` at the commit that includes #302 — record the commit SHA in a `README` next to them)
- Create: `…/test/…/GeoParquetTestUtil.groovy` (read-back helpers only in this task)
- Test: `…/test/…/internal/write/TypeFileWriterTest.groovy`, `…/test/…/internal/meta/GeoMetadataBuilderTest.groovy`

**Interfaces:**
- Consumes: `TypeMapping`, `ColumnMapping`, `GeoParquetCrs`, `GeoParquetVersion`, `PureJavaCodecFactory`.
- Produces: `interface GeometryConverter { Geometry convert(Object value, GeoParquetCrs target, SimpleLog log); }` — returns a JTS geometry in the target CRS with x/y = lon/lat for geographic CRS, or null.
- Produces: `GeoColumnStats` — `void add(Geometry g)` (ignores null/empty), `Envelope getExtent()`, `SortedSet<String> getGeometryTypes()` (GeoParquet names, `" Z"` suffix for 3D).
- Produces: `GeoMetadataBuilder.build(GeoParquetVersion version, String primaryColumn, LinkedHashMap<String, GeoColumnStats> stats, Map<String, GeoParquetCrs> crs, boolean primaryHasCovering) → String` (JSON). Keys per spec §5.5 table; `bbox` per column `[xmin, ymin, xmax, ymax]` only when extent not null; `crs` omitted for CRS84, JSON `null` for unknown, PROJJSON object otherwise; `covering` only for `version.declaresCovering()`.
- Produces: `TypeFileWriter(Path file, TypeMapping mapping, GeoParquetVersion version, Map<String, GeoParquetCrs> crs, CompressionCodecName codec, int rowGroupRowLimit, GeometryConverter converter, SimpleLog log)`; `void write(Instance instance)`; `long getRowCount()`; `void close()` (`Closeable`). Builds the parquet writer with `withConf(new PlainParquetConfiguration())`, `withCodecFactory(new PureJavaCodecFactory())`, `withCompressionCodec(codec)`, `withRowGroupRowCountLimit(rowGroupRowLimit)`, `withWriteMode(OVERWRITE)` (the overwrite decision is made by the instance writer).
- Produces: `InstanceWriteSupport extends WriteSupport<Instance>` — `finalizeWrite()` returns `FinalizedWriteContext` whose extra metadata is `{"geo": GeoMetadataBuilder.build(...)}` (types without geometry: no `geo` key).
- Produces (test): `GeoParquetTestUtil.readRows(File) → List<org.apache.parquet.example.data.Group>`, `readFooter(File) → ParquetMetadata`, `geoMetadata(File) → Map` (via `JsonSlurper`), `wkb(Group row, String column) → Geometry`. Read via `ParquetFileReader.open(new LocalInputFile(path), ParquetReadOptions.builder(new PlainParquetConfiguration()).build())` and a `ParquetReader.Builder<Group>` subclass overriding `getReadSupport()` to return `GroupReadSupport` (verified during planning; needs the test-only Hadoop/codec jars from Task 1).

Value rules (`ValueWriter`): value lookup by `ColumnMapping#getPropertyPath` on the `Group`; `Instance#getValue()` used for properties with values; conversion with `ConversionUtil.getAs(value, <java type of ValueKind>)`; conversion failure → `log.error(...)` and write null. JSON columns: serialize with `libs.jackson.databind` (already in the catalog; add as `implementation`, never use parquet's relocated `shaded.parquet` Jackson); JSON shape: object of property local names → value / array (repeated) / nested object, geometries as WKT strings. Geometry columns: multiple values → combine (`GeometryFactory.buildGeometry` → Multi*/GeometryCollection) and `log.warn` if the result type differs from the single-value type; WKB via `WKBWriter(dimension, false)` with 2 or 3 dimensions as the geometry has; bbox from the WKB geometry's envelope; empty/null geometry → null geometry and null bbox.

- [ ] **Step 1: Write failing tests** (`TypeFileWriterTest`, extends `AbstractPlatformTest`, uses a `GeometryConverter` stub that returns the geometry unchanged):
  - `testWriteSimpleValues` — one instance per ValueKind of Task 3's `testSimpleTypes`, incl. `BigDecimal('1098491071975459529.6201509049614540479')` round-tripping exactly as string and `BigInteger(Long.MAX_VALUE + 1)`.
  - `testWriteNested` — struct, list of strings, list of structs, nulls at every level; read back and assert values (e.g. `row.getGroup('locators',0).getGroup('list',1).getGroup('element',0).getString('designator',0) == '3'`).
  - `testJsonAndWktColumns` — recursion case from Task 4 → valid JSON string with the nested child's `name`; nested geometry → `'POINT (1 2)'`.
  - `testGeometryAndBbox` — 3 features (point, line, polygon) in EPSG:25832: WKB round trip equals input; bbox fields equal envelope; footer: geometry column `GeospatialStatistics` present (2.0); bbox DOUBLE column statistics `hasNonNullValue()`.
  - `testNullAndEmptyGeometry` — instances with null and `POINT EMPTY` primary geometry: geometry null, bbox group absent; `geo.columns.geometry.bbox` computed from the others only.
  - `testMultipleGeometryValuesAreCombined` — geometry property with two point values → `MultiPoint`; report has one warning.
  - `testBigNumberValues` — DOUBLE encoding writes `new BigDecimal('1.23')` as `1.23d`; DECIMAL encoding with scale 4: `new BigDecimal('1.23456')` → unscaled `12346` (rounding `HALF_EVEN`, one warning per column on first rounding), `BigInteger` with 39 digits → null + error reported (exceeds precision 38).
  - `testConversionErrorIsReported` — Integer property with value `'abc'` → column null, log has one error, other columns written.
  - `testRowGroupLimit` — limit 10, 25 rows → footer has 3 row groups.
  - `testCodecs` — each of zstd/snappy/gzip/none: footer column `getCodec()` equals the codec; rows readable.
  - `GeoMetadataBuilderTest.testVersions` — for each version: `version` string; `covering` present only for 1.1/2.0; `encoding == 'WKB'`; CRS84 → no `crs` key; unknown → `crs` key with null; EPSG → `crs.id.code`; and **each document validates against the vendored JSON schema of its version** (`com.networknt` validator).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** classes above.
- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `feat(geoparquet): write instances and geo metadata to Parquet files`

---

### Task 6: GeoParquetInstanceWriter — streaming path, parameters, per-type files

**Files:**
- Create: `…/src/eu/esdihumboldt/hale/io/geoparquet/GeoParquetInstanceWriter.java`
- Modify: `…/resources/main/plugin.xml` (provider + parameters), `…/test/…/GeoParquetTestUtil.groovy` (write helper), `…/test/…/HadoopFreeTest.java`
- Test: `…/test/…/GeoParquetInstanceWriterTest.groovy`

**Interfaces:**
- Consumes: Tasks 2–5.
- Produces: `public class GeoParquetInstanceWriter extends AbstractGeoInstanceWriter implements GeometryConverter` with constants and setters:
  - `PARAM_VERSION = "geoparquet.version"` / `setVersion(GeoParquetVersion)`
  - `PARAM_COMPRESSION = "compression"` (values `zstd`, `snappy`, `gzip`, `none`) / `setCompression(String)`
  - `PARAM_SORT = "sort"` (default `true`) / `setSort(boolean)` — `isPassthrough()` returns `!sort`
  - `PARAM_ROW_GROUP_ROW_LIMIT = "rowGroupRowLimit"` (default `150000`) / `setRowGroupRowLimit(int)`
  - `PARAM_NESTING_DEPTH = "nestingDepth"` (default `5`) / `setNestingDepth(int)` — properties nested deeper are written as JSON text
  - `PARAM_BIG_NUMBER_ENCODING = "bigNumberEncoding"` (values `string` (default, lossless), `double` (lossy), `decimal`) / `setBigNumberEncoding(BigNumberEncoding)`
  - `PARAM_DECIMAL_SCALE = "decimalScale"` (default `9`, only used with `decimal` for BigDecimal; precision is always 38) / `setDecimalScale(int)`
  - `PARAM_OVERWRITE_TARGET_FILE = "overwriteTargetFile"` (default `false`, same semantics as GeoPackage writer: existing file and `false` → error, nothing written)
  - inherited target CRS parameter `crs`.
- Produces (test): `GeoParquetTestUtil.writeInstances(File target, Schema schema, InstanceCollection instances, Consumer<GeoParquetInstanceWriter> configurator = null) → IOReport` (asserts success, no errors), `withNewGeoParquet(Schema, InstanceCollection, Consumer<List<File>> handler, Consumer<GeoParquetInstanceWriter> configurator = null)` (temp dir, passes written files sorted by name, deletes afterwards).

Behavior (this task implements `sort=false`; Task 8 adds sorting — until then `execute` always streams):
- Target path from `getTarget().getLocation()`; per type `<base>_<TypeLocalName>.parquet` in the target's directory, type names sanitized to `[A-Za-z0-9_-]`. After writing: exactly one file → rename to the target path; more than one → `setTarget(new MultiLocationOutputSupplier(uris))` and `reporter.info` listing names; none → `reporter.warn("No instances written")`, success.
- Column CRS per geometry column, in order: target CRS parameter → `GeometryMetadata` constraint of the property type → CRS of the first non-null geometry of the first instance of the type → unknown + warning. Resolved via `GeoParquetCrs.resolve`.
- `GeometryConverter#convert`: `extractGeometry`/`convertGeometry`/`unifyGeometry` of `AbstractGeoInstanceWriter` to `crs.getTargetCrs()`, then swap x/y when `crs.isSwapAxes()`, so output is lon/lat for geographic CRS.
- Progress, cancellation (`isCancelable() == true`), errors → `reporter.error`, `setSuccess(false)`.
- `plugin.xml`: content type id `eu.esdihumboldt.hale.io.geoparquet`, `file-extensions="parquet"`, name `GeoParquet`; provider id `eu.esdihumboldt.hale.io.geoparquet.instance.writer`, name `GeoParquet file`; one `providerParameter` per parameter above with label, description, `valueDescriptor` default and default description (copy the `crs` and `overwriteTargetFile` entries from the GeoPackage `plugin.xml`). The `sort` description must state that sorting resolves instances by reference and can be slow for sources where that is expensive (e.g. GML read directly); use an instance store or disable sorting.

- [ ] **Step 1: Write failing tests** (`GeoParquetInstanceWriterTest`, modeled on `GeopackageInstanceWriterTest`; every test uses `setSort(false)` until Task 8):
  - `testWriteAttributes` — the GeoPackage test's `attr1`/`abc` schema: two files `…_attr1.parquet`, `…_abc.parquet`; no `geo` key in either; values and types as in the GeoPackage test.
  - `testSingleTypeUsesTargetName` — one type → exactly the target file exists.
  - `testWriteFeatures` — features with point/polygon in EPSG:4326 (lat/lon input via `createGeometry(wkt, new CodeDefinition('EPSG:4326'))`) → WKB x in [5,10] (longitude), `geo` has no `crs`, logical type crs null.
  - `testWriteFeaturesTargetCRS` — target CRS `code:EPSG:25832` → geometries converted; `geo.columns.geometry.crs.id.code == 25832`; logical type crs `EPSG:25832`.
  - `testVersions` — each version string written as configured.
  - `testParametersReachMapping` — `setNestingDepth(1)` turns a struct property into a JSON column; `setBigNumberEncoding(DECIMAL)` + `setDecimalScale(2)` gives a decimal(38,2) column for a BigDecimal property.
  - `testNoInstances` — empty collection → success, warning in report, no file.
  - `testOverwrite` — existing target with `overwriteTargetFile=false` → report error, file unchanged; with `true` → replaced.
  - `HadoopFreeTest#testWriterWithoutHadoop` (Java) — runs `GeoParquetInstanceWriter` on a small in-memory collection with each codec and both sort settings (sort lands in Task 8; add the `sort=true` case there) and asserts no Hadoop class loadable.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** writer + `plugin.xml`.
- [ ] **Step 4: Run `test` and `hadoopFreeTest` → PASS.**
- [ ] **Step 5: Commit** — `feat(geoparquet): add GeoParquet instance writer`

---

### Task 7: In-Java compliance checks applied to every written file

**Files:**
- Create: `…/test/…/GeoParquetCompliance.groovy`
- Modify: `…/test/…/GeoParquetTestUtil.groovy` (`writeInstances` calls `GeoParquetCompliance.check(file)` for every written file)
- Test: `…/test/…/GeoParquetComplianceTest.groovy`

**Interfaces:**
- Produces: `static void GeoParquetCompliance.check(File file, int rowGroupRowLimit = 150000)` — throws `AssertionError` with a message naming the violated rule.

Checks (spec §6.2), applied when `geo` exists:
1. `geo` validates against the vendored schema for its `version`.
2. Every column in `geo.columns` exists at schema root, is not repeated, is BINARY; for `2.0.0` carries `GeometryLogicalTypeAnnotation`, whose crs is consistent with `geo` crs (null ⇔ key absent; `EPSG:<n>` ⇔ `crs.id.code == n`; unknown value ⇔ `crs == null`).
3. If `covering.bbox` declared: struct `bbox` at root with DOUBLE leaves, each leaf has `ColumnOrder.typeDefined()` and min/max statistics in every row group.
4. Column-level `bbox` equals the extent computed from decoding all WKB values (tolerance 1e-9).
5. `geometry_types` equals the set computed from the data.
6. Every row group ≤ `rowGroupRowLimit` rows.
7. For `2.0.0`: geometry columns have `GeospatialStatistics` in every non-empty row group.

- [ ] **Step 1: Write failing tests** — negative cases build files directly with `ExampleParquetWriter` (+ `PureJavaCodecFactory`, `withExtraMetaData`) via a new helper `GeoParquetTestUtil.writeRaw(File file, MessageType schema, Map<String,String> meta, List<Map<String,Object>> rows)` (top-level primitive and bbox-struct fields only): `testDetectsMissingColumnOrder` (bbox doubles without explicit column order → `check` fails mentioning "column order"), `testDetectsWrongBbox` (`geo` column `bbox` not matching the data → fails), `testAcceptsWriterOutput` (writer output from Task 6 passes).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** and wire into `writeInstances`.
- [ ] **Step 4: Run whole plugin test suite → PASS** (all Task 5/6 outputs now checked).
- [ ] **Step 5: Commit** — `test(geoparquet): verify GeoParquet compliance of written files`

---

### Task 8: Spatial sorting (two-pass, Hilbert)

**Files:**
- Create: `…/internal/sort/HilbertCurve.java`, `…/internal/sort/SortIndex.java`
- Modify: `…/GeoParquetInstanceWriter.java`, `…/test/…/HadoopFreeTest.java`
- Test: `…/test/…/internal/sort/HilbertCurveTest.groovy`, `…/test/…/GeoParquetSortTest.groovy`

**Interfaces:**
- Produces: `HilbertCurve.index(int x, int y) → long` on a 2^16 × 2^16 grid (order 16); `HilbertCurve.index(double x, double y, Envelope extent) → long` (normalizes into the grid, degenerate extent axis → 0).
- Produces: `SortIndex` — `void add(double centerX, double centerY, InstanceReference ref)`, `void addWithoutGeometry(InstanceReference ref)`, `Envelope getExtent()`, `List<InstanceReference> sorted()` (by Hilbert index, ties in insertion order, then all without geometry in insertion order). Store centers in growable `double[]` arrays and refs in an `ArrayList`; sort an index permutation (primitive `long` keys) to keep ~100 bytes/feature or less.

Algorithm for `index(int x, int y)` (standard xy→d, n = 65536):

```java
long d = 0;
for (int s = n / 2; s > 0; s /= 2) {
	int rx = (x & s) > 0 ? 1 : 0;
	int ry = (y & s) > 0 ? 1 : 0;
	d += (long) s * s * ((3 * rx) ^ ry);
	if (ry == 0) {
		if (rx == 1) { x = s - 1 - x; y = s - 1 - y; }
		int t = x; x = y; y = t;
	}
}
return d;
```

Writer, `sort == true` (`isPassthrough() == false`): pass 1 iterates `getInstances()`, per type keeps a `SortIndex` (keyed by `TypeDefinition`), determines column CRS (Task 6 rules) on first encounter, converts the primary geometry with `GeometryConverter` and adds its envelope center (or `addWithoutGeometry`). Pass 2: for each type in first-encounter order, open one `TypeFileWriter`, resolve `getInstances().getInstance(ref)` in `sorted()` order, write, close, release the type's `SortIndex`. Progress: pass 1 and pass 2 as separate progress phases.

- [ ] **Step 1: Write failing tests:**
  - `HilbertCurveTest.testOrder2Grid` — with a test hook for order 2 (n=4) the sequence over `(0,0),(0,1),(1,1),(1,0),(2,0),(3,0),(3,1),(2,1),(2,2),(3,2),(3,3),(2,3),(1,3),(1,2),(0,2),(0,3)` is `0..15`; make the order a constructor parameter (`new HilbertCurve(int order)`, static default instance order 16).
  - `HilbertCurveTest.testLocality` — adjacent grid cells along the curve differ by exactly 1 in index for a sample of 1,000 random `d` values (invert via a test-only `d2xy`, or check `index` of neighbours).
  - `GeoParquetSortTest.testRowsAreSpatiallySorted` — 10,000 points on a shuffled 100×100 grid, `sort=true`, `rowGroupRowLimit=1000`: rows in file are ordered by non-decreasing Hilbert index of their coordinates; consecutive row-group bboxes overlap in < 30% of pairs (Portolan PTL-DAT-006 heuristic).
  - `GeoParquetSortTest.testFeaturesWithoutGeometrySortedLast` — mixed null-geometry instances appear after all others, in input order.
  - `GeoParquetSortTest.testSortWithMultipleTypes` — two types interleaved in input → two files, each sorted.
  - `GeoParquetSortTest.testPassthroughFlag` — default writer `isPassthrough() == false`; `setSort(false)` → `true`.
  - `HadoopFreeTest#testWriterWithoutHadoop` — add `sort=true` case.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** `HilbertCurve`, `SortIndex`, writer sorted path; remove `setSort(false)` from the Task 6 tests that do not need it (keep one streaming-mode variant of `testWriteFeatures`).
- [ ] **Step 4: Run `test` + `hadoopFreeTest` → PASS.**
- [ ] **Step 5: Commit** — `feat(geoparquet): spatially sort features using a Hilbert curve`

---

### Task 9: External validators via mise (gpio, Portolan, GDAL)

**Files:**
- Modify: `mise.toml` (repo root): add `uv` (latest), `"pipx:geoparquet-io"` (latest, ≥ 1.7.0), `"pipx:portolan-cli"` (latest, ≥ 0.8.0)
- Create: `…/test/…/ExternalValidators.groovy`
- Modify: `…/test/…/GeoParquetTestUtil.groovy` (call `ExternalValidators.check(file)` after `GeoParquetCompliance.check`)
- Test: `…/test/…/ExternalValidatorsTest.groovy`
- Modify (if the unknown-CRS value changes): `…/internal/crs/GeoParquetCrs.java`

**Interfaces:**
- Produces: `static void ExternalValidators.check(File file)`; `static boolean ExternalValidators.isAvailable(String tool)`.

Behavior:
- Tool discovery: run `<tool> --version` (via `ProcessBuilder`, inherit `PATH`). Missing tool: `Assume.assumeTrue(...)` when env `CI` is unset; `fail(...)` when `CI` is set.
- gpio: `gpio check spec <file>` → exit code 0 required, output attached to the assertion message.
- Portolan (only for files with `geo` and version 1.1/2.0): temp dir `cat/`, copy file into `cat/data/<name>`, `portolan init <cat> --auto -t Test --license CC0-1.0`, `portolan add data/<name>` (cwd `cat`) must exit 0 and must not print `has no geometry`, `portolan check --format json` (cwd `cat`): fail on any finding with severity error whose code starts with `PTL-DAT` or whose subject is the data asset; ignore `PTL-PRV-*`, `PTL-FIL-*` and other catalog-metadata rules (the JSON report format — inspect it once and pin the filter in code with a comment listing the ignored codes).
- GDAL (optional everywhere, never fails on absence): if `docker` is available and env `GEOPARQUET_GDAL_IMAGE` is set (e.g. `ghcr.io/osgeo/gdal:ubuntu-full-latest`), run `python3 /usr/lib/python3/dist-packages/osgeo_utils/samples/validate_geoparquet.py --check-data /data/<name>` with the file's directory mounted read-only; exit 0 required.
- Validators are slow: `writeInstances` runs them only when the configurator did not disable it — add parameter `boolean externalValidation = true` to `writeInstances`/`withNewGeoParquet`; tests that only check value conversion pass `false`. Every file written by Tasks 6/8/10 end-to-end tests is validated.

- [ ] **Step 1: Add tools to `mise.toml`, run `mise install`**, confirm `gpio --version` and `portolan --version`.
- [ ] **Step 2: Write failing tests** — `ExternalValidatorsTest.testAcceptsWriterOutput` (writer output: polygon features in EPSG:2056 with nested columns, version 2.0 and 1.1), `testRejectsMissingGeoBbox` (file built with Task 7's `writeRaw` whose `geo` lacks the column-level `bbox` → Portolan step fails with a message containing `no geometry`), `testUnknownCrsAccepted` (WKT-only CRS → gpio and GDAL (if available) accept; adjust `GeoParquetCrs#getParquetCrs()` for unknown if `srid:0` is rejected, and record the accepted value in the spec §5.6 via a `docs:` fixup).
- [ ] **Step 3: Run, expect FAIL** (helper missing).
- [ ] **Step 4: Implement** helper and wiring.
- [ ] **Step 5: Run whole plugin tests locally with tools installed → PASS**; run once with `PATH` lacking the tools and `CI` unset → validator-dependent tests reported as skipped, not failed.
- [ ] **Step 6: Commit** — `test(geoparquet): validate output with gpio and Portolan`

---

### Task 10: Real-world and complex end-to-end tests

**Files:**
- Modify: `…/build.gradle` — `testImplementation project(':io:plugins:eu.esdihumboldt.hale.io.geopackage')`; test task `systemProperty 'geoparquet.test.gpkgDir', project(':io:plugins:eu.esdihumboldt.hale.io.geopackage').file('resources/test/testdata').absolutePath`
- Test: `…/test/…/GeoParquetRealDataTest.groovy`, `…/test/…/GeoParquetComplexDataTest.groovy`

**Interfaces:**
- Consumes: `GeopackageSchemaReader`/`GeopackageInstanceReader` (main classes of the GeoPackage plugin; the Groovy test helpers of that plugin are not on the classpath — re-implement the ~15 lines of `loadSchema`/`loadInstances` locally).

- [ ] **Step 1: Write failing tests:**
  - `GeoParquetRealDataTest.testNoiseMaps` — read `MajorAirports-StrategicNoiseMaps-AT-LOWW.gpkg`, write with defaults: one file per feature table; per file row count equals the source table's instance count; for 20 sampled rows (by primary key column) all attribute values equal the source, geometry equals the source after CRS handling (`Geometry#equalsExact` with 1e-9); full compliance + external validation.
  - `GeoParquetRealDataTest.testWastewater` — same for `util_wastewater_discharge.gpkg`, plus ZSTD file smaller than the same written with `none`.
  - `GeoParquetComplexDataTest.testInspireAddressLike` — `InstanceBuilder` collection of 500 `Address` instances: `position/GeographicPosition/geometry` (ETRS89 EPSG:4258, lat/lon input), `locator` (0..n, with `designator` 0..n and `level`), repeated `alternativeIdentifier` strings, a choice, an XML attribute with `nilReason`, and a recursive `parent` reference: top-level `geometry` with lon/lat coordinates; `position` struct without geometry; locators as LIST of structs; recursion as JSON; Portolan validation passes.
- [ ] **Step 2: Run, expect FAIL** (or fix-forward any defect found — each defect gets its own failing unit test in the owning component's test class first).
- [ ] **Step 3: Make them pass.**
- [ ] **Step 4: Run full plugin `check` (incl. `hadoopFreeTest`, `verifyBundleManifest`) → PASS.**
- [ ] **Step 5: Commit** — `test(geoparquet): add real-world and INSPIRE-like end-to-end tests`

---

## Self-review notes

- Spec §5.1–5.8, §6.1–6.4 are covered by Tasks 1–10; §4.4 (no store copy inside the writer) by Task 6's `sort` parameter description; §4.5 corrected to "no shared-workflow change" (mise.toml only, Task 9).
- Decisions made in this plan (left to the builder by the spec): BigInteger/BigDecimal default UTF8 string, configurable (`bigNumberEncoding`, `decimalScale`) as requested by the stakeholder; nesting depth default 5, configurable (`nestingDepth`); attribute naming (`attr_` prefix only on collision); primary geometry = shortest geometry path; unknown-CRS Parquet value starts as `srid:0`, confirmed in Task 9; tests read back via parquet-java with test-only Hadoop jars, guarded by `hadoopFreeTest`.
