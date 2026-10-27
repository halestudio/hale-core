# GeoParquet instance writer — design

- Related: ING-5399 (investigation spike, see `spike/spike-ING-5399-geoparquet-poc/NOTES.md`)
- Date: 2026-10-28
- Status: design, approved for planning once reviewed

## 1. Intent

hale-core gets an instance writer that produces GeoParquet files.

- **Primary goal:** files that can be added to a [Portolan](https://www.portolan-sdi.org) catalog **as written**, i.e. pass `portolan check` (its validator `rashid`) without being re-converted. Portolan publishes `.parquet` files as-is unless the user explicitly forces a reconversion, so every property Portolan checks must come from hale.
- **Secondary goal:** general, standards-compliant GeoParquet support usable with GDAL/QGIS, DuckDB, Arrow etc.
- hale only produces the file(s). Creating or publishing to a Portolan catalog is out of scope.

Input data covers both flat (GeoPackage-like tables) and complex models (INSPIRE, XPlanGML: nested properties, repeated values, multiple feature types). Datasets with millions of features occur, though they are not the norm.

## 2. Goals, non-goals, anti-goals

Goals

- GeoParquet **2.0** (default), plus selectable **1.1** and **1.0**.
- Output passes the GeoParquet spec validators *and* Portolan's validator.
- Flat and nested data; multiple feature types per export.
- Compression (ZSTD default) without Hadoop or native libraries at runtime.
- Unit tests with `SchemaBuilder`/`InstanceBuilder` (like `GeopackageInstanceWriterTest`), real-world data tests, and automated compliance verification — locally and in CI with the same tooling.

Non-goals

- Reading GeoParquet (no instance/schema reader in this scope).
- Portolan catalog management (STAC metadata, providers, README, push).
- Partitioning a single type into multiple files.
- GeoArrow native encodings (1.1 allows them, 2.0 does not); WKB only.
- A "flat" output mode (complex values as JSON) — possible later if a consumer struggles with nested columns.

Anti-goals (would be a failure even if it technically works)

- Files that only pass the JSON-schema check of the `geo` metadata but are rejected or degraded by Portolan or pyarrow-based tools (both happened in the spike — see §9).
- Any network access at runtime (the spike fetched PROJJSON from spatialreference.org).
- Hadoop, Arrow or JNI/native code in the runtime footprint, or an OSGi bundle that does not resolve.
- Silently wrong CRS information (e.g. omitting `crs`, which readers interpret as OGC:CRS84).

## 3. Constraints

- Java 17, Gradle, OSGi bundles (bnd), hale-core conventions (`hale.migrated-java`/`-groovy`, `hale.shadow-dependencies`).
- Library: **parquet-java (`parquet-hadoop`) used directly**, latest release (1.18.1 at time of writing; 1.19.0 in RC). Re-verified alternatives: Carpet still requires hadoop-common at compile scope; tileverse parquetry (1.0-RC5) requires JDK 25 + preview and writes flat columns only; GeoTools GeoParquet is read-only (via DuckDB, native); GDAL/Sedona not embeddable. All new dependencies at their latest release.
- GeoParquet 2.0 is still `v2.0.0-rc.1` (2026-07-19). `main` has since re-added the optional bbox `covering` (#302). The `version` value in the schema is the constant `"2.0.0"`.
- Portolan requirements (from `portolan-spec/specs/portolan/formats.md` and rashid source):
  - GeoParquet 1.1 or 2.x (`1.0` is rejected).
  - Spatial statistics (MUST): a `bbox` covering column with Parquet min/max statistics, or native `GeospatialStatistics` (2.x). Covering column RECOMMENDED even for 2.x (warning otherwise).
  - Rows spatially ordered (MUST); Hilbert is what Portolan's CLI uses.
  - At most 150,000 rows per row group (MUST).
  - Compression SHOULD, ZSTD RECOMMENDED.
  - portolan-cli derives the dataset extent from the column-level `bbox` in the `geo` metadata — without it the file is treated as "no geometry".

## 4. Decisions made with the stakeholder

1. **Default version 2.0**; 1.1 and 1.0 selectable (1.0 is not Portolan-compatible; that is acceptable for general use).
2. **Nested properties are written as native Parquet structures** (struct for single complex values, LIST for repeated values) — not flattened, not JSON — chosen for QGIS/GDAL compatibility. Verified in the spike with GDAL 3.14: structs appear as `address.street`/`address.number`, lists of primitives as OGR list fields, lists of structs as JSON string fields; DuckDB/Arrow keep full structure.
3. **One file per feature type**, following the Shapefile writer: if a single type is written, the file has exactly the target name; with multiple types, `<target-base>_<TypeName>.parquet` next to the target, reported via `MultiLocationOutputSupplier`. Types without geometry are written as plain Parquet files (no `geo` metadata) rather than skipped.
4. **Spatial sorting via two passes over `InstanceReference`s** (stakeholder's proposal): the writer is *not passthrough* by default, so headless transformation stores results in a reiterable sink (`StoreTransformationSink`) where references are cheap to resolve. Pass 1 collects per type a sort key + reference; pass 2 resolves references in sorted order and writes. A parameter disables sorting, making the writer passthrough (single streaming pass). Where efficient writing of data from a collection with expensive reference resolution is needed (e.g. `GmlInstanceCollection#getInstance` re-parses from the start), an instance store can be used as intermediate storage (stakeholder notes hale-cli data rewrite already does this; see also `docs/superpowers/specs/2026-10-08-instance-store-design.md`). **The writer does not copy data into a store itself** — this is the caller's responsibility; the writer documents the limitation (parameter description, Javadoc) and the `sort` parameter is the escape hatch.
5. **External validators integrated into the tests, provided via mise**, so local runs and CI behave the same. *(Correction during planning)* The shared workflow `wetransform/gha-workflows` `gradle-library.yml` (already at the pinned v4.6.2) runs `jdx/mise-action` whenever `mise.toml` exists, so tools added to `mise.toml` are installed in CI automatically — **no shared-workflow change is needed**.
6. **Real-world test data: reuse what is already committed** in hale-core (GeoPackage plugin test data). The spike's `013_Grindelwald.gpkg` (7 MB, licence unrecorded) is not committed.

## 5. Design

### 5.1 Plugin

New bundle `io/plugins/eu.esdihumboldt.hale.io.geoparquet` (name is the builder's call within hale conventions), registered in `plugin.xml` like the GeoPackage writer (content type `.parquet`, `application/vnd.apache.parquet`, provider parameters). Writer class extends `AbstractGeoInstanceWriter` (target CRS handling, geometry conversion helpers as used by the GeoPackage writer).

### 5.2 Dependencies and OSGi

- `parquet-hadoop` + `parquet-column` etc. shaded into the bundle via `hale.shadow-dependencies`, excluding `org.xerial.snappy:snappy-java` and `com.github.luben:zstd-jni`. `hadoop-common` only at compile time (`compileOnly`, non-transitive) — needed for builder signatures, never loaded at runtime.
- Writer setup: `ExampleParquetWriter`-style builder (or a custom `WriteSupport` — builder's call) with `withConf(new PlainParquetConfiguration())`, `LocalOutputFile`/custom `OutputFile`, and **`withCodecFactory(<pure-Java factory>)`**.
- bnd manifest fixes from the spike: `org.apache.hadoop.*`, `org.xerial.snappy`, `com.github.luben.zstd` imports `resolution:=optional`; parquet's internally relocated packages (`shaded.parquet.*` — Jackson, and check thrift/fastutil) must be private, not imported. Verify the bundle resolves (e.g. an OSGi resolution check if hale-core has one, otherwise manifest inspection in a test).
- `aircompressor` (already a transitive of parquet-hadoop) provides pure-Java ZSTD and Snappy.

### 5.3 Compression

Custom `org.apache.parquet.compression.CompressionCodecFactory` (write side only needs compressors): `UNCOMPRESSED`, `ZSTD` (aircompressor `ZstdCompressor`), `SNAPPY` (aircompressor `SnappyCompressor`, raw format), `GZIP` (`java.util.zip.GZIPOutputStream`). Proven in the spike: all four written with no Hadoop class on the classpath; GDAL reads and validates all; on 10k features ZSTD 83 KB vs. 778 KB uncompressed. Default **ZSTD**; parameter to choose. Compression level parameter is optional (builder's call).

### 5.4 Schema mapping (hale type → Parquet message)

- One Parquet schema per hale type. Columns from the type's properties in definition order.
- **Simple properties** → typed primitive columns. Mapping is the builder's call but must cover at least: String, Boolean, Integer/Long/Short/Byte, Float/Double, BigInteger, BigDecimal (Parquet `DECIMAL` where precision/scale known, otherwise a lossless fallback such as string — builder's call, documented and tested), date (`DATE`), date-time/Instant (`TIMESTAMP` with UTC adjustment), time, byte[] (`BINARY`), URI/other → string via `ConversionUtil`. Conversion errors are reported, not thrown (as in the GeoPackage writer).
- **Cardinality:** max occurrence > 1 → Parquet `LIST` (3-level standard structure). Nullable/min 0 → `optional`.
- **Complex properties** → Parquet group (struct). hale **groups and choices** are flattened into their parent (no extra nesting level). Choice members become optional fields.
- **Depth limit / recursion:** beyond a configurable depth (and always on recursive type cycles) the value is written as a JSON string column. Default depth is the builder's call.
- **XML attributes / mixed content / special hale constructs** (e.g. `@` attributes, `nilReason`, value of a complex type with simple content): builder decides a consistent naming convention; document it.
- **Geometry properties:**
  - **Primary geometry (stakeholder decision):** the type's default geometry as detected by hale (`DefaultGeometry` constraint / geometry detection as hale does elsewhere) becomes the primary top-level geometry column `geometry` — **even if it is nested** (e.g. INSPIRE Address `position/GeographicPosition/geometry`). It is **moved**, not copied: the value is omitted at its original nested location, so it is not stored twice.
  - Other top-level geometry properties → additional geometry columns (WKB in `BINARY`, annotated with the Parquet `GEOMETRY` logical type for 2.0).
  - Other nested geometries (inside structs/lists) → WKT string, since GeoParquet forbids geometry columns inside structs/lists.
  - *(My call)* A geometry property with max occurrence > 1 that becomes a geometry column: multiple values are combined into the matching Multi* geometry or a GeometryCollection; report a warning if that changes the geometry type.
  - All geometries of one column are converted to one CRS (configured target CRS, otherwise the first encountered).
- **`bbox` covering column** (struct `xmin, ymin, xmax, ymax`, DOUBLE, required for non-empty geometries) for the primary geometry column. **Must set `ColumnOrder.typeDefined()` explicitly on the four DOUBLE fields** — parquet-java 1.18.1 otherwise writes a column order that pyarrow 25 doesn't recognize, so pyarrow (and thus rashid) ignores the min/max statistics (found in the spike).
- **Column naming:** property local names; collisions (same local name in different namespaces, see GeoPackage `gml-conflict` test data) resolved by the same preference as the GeoPackage writer (non-GML namespace first) plus a deterministic suffix.

### 5.5 File metadata (`geo`)

Per version:

| | 2.0 (default) | 1.1 | 1.0 |
|---|---|---|---|
| `version` | `2.0.0` | `1.1.0` | `1.0.0` |
| Parquet `GEOMETRY` logical type | yes | no (plain BINARY) | no |
| `covering.bbox` | yes (re-added on 2.0 `main`; if final 2.0 drops it, follow the final spec) | yes | no (bbox column may still be written but is not declared) |

Common: `primary_column`, per column `encoding: "WKB"`, `geometry_types` (collected while writing), **column-level `bbox`** (overall extent — required by portolan-cli), `crs` (see 5.6). `edges`/`orientation` omitted (planar default) unless the builder finds a reason.

Validate the generated `geo` JSON against the matching official JSON schema in tests (schemas vendored as test resources).

### 5.6 CRS

- **Bundled PROJJSON for all non-deprecated EPSG CRS** as one compressed resource (measured: 7,216 CRS, 0.7 MB gzip, 0.3 MB xz), generated by a script using pyproj (run via mise/uv), committed together with the PROJ/EPSG version used. No network access at runtime.
- EPSG code determined from hale's `CRSDefinition` (`CodeDefinition`, or GeoTools `CRS.lookupEpsgCode`) as in the GeoPackage writer.
- `geo` `crs`: PROJJSON (including `id.authority`/`id.code` — portolan-cli derives `proj:epsg` from it). Parquet logical-type `crs`: `"EPSG:<code>"` (`authority:code` form, valid per the Parquet spec); must be consistent with `geo` per GeoParquet 2.0.
- **EPSG:4326 / CRS84:** GeoParquet stores x/y = lon/lat. Ensure coordinates are lon/lat and write OGC:CRS84 (omit `crs` in `geo` and the logical type, which defaults to CRS84) — builder confirms the exact representation against GDAL's validator.
- **CRS without EPSG code** (WKT-only): write `"crs": null` (spec: unknown CRS) and report a warning. Never omit `crs` in that case. *(My call)* For 2.0 the Parquet logical-type `crs` must express "unknown" consistently as well (research indicates `srid:0`; omitting it would mean CRS84) — the builder confirms the exact value against GDAL's `validate_geoparquet.py`. No WKT→PROJJSON conversion in Java.

### 5.7 Sorting and writing

- Pass 1 (per instance): determine type, primary geometry, its envelope center (after CRS conversion), extend per-type extent, store `(center x, center y, InstanceReference)` per type. Memory budget ~100 bytes/feature is acceptable (≈200 MB for 2M features); the builder may compact (primitive arrays + reference array, sort indices).
- Compute Hilbert keys from the per-type extent, sort, then pass 2: write each type's file sequentially (one Parquet writer open at a time), resolving references via `InstanceCollection#getInstance`.
- Features without geometry: sorted to the end (or kept in input order — builder's call).
- `sort` parameter (default `true`); `false` → `isPassthrough() == true`, single pass, one writer per type open concurrently.
- Row groups: row-count limit 150,000 (`withRowGroupRowCountLimit`), configurable; byte-size limit left at default unless tests show otherwise.
- Cancellation and progress reporting as in other writers.
- `overwriteTargetFile` behavior consistent with the GeoPackage writer; multiple output files reported to hale.

### 5.8 Parameters (provider parameters in `plugin.xml`)

`geoparquet.version` (2.0/1.1/1.0), `compression` (zstd/snappy/gzip/none), `sort` (bool), `rowGroupRowLimit` (int), `nestingDepth` (int), plus inherited target-CRS parameter. Exact names are the builder's call following hale conventions.

## 6. Testing and compliance verification

### 6.1 Unit tests (Groovy, `SchemaBuilder`/`InstanceBuilder`, like `GeopackageInstanceWriterTest`)

At least: each simple data type incl. nulls, BigInteger/BigDecimal edge cases, dates/timestamps; multiple types → multiple files and naming; type without geometry; multiple geometry properties; mixed geometry types; target CRS conversion; EPSG:4326 axis order; CRS without EPSG code; nested struct, list of primitives, list of structs, groups and choices, recursion/depth limit → JSON; each version; each codec; sorting on/off (order verified); row-group limit; overwrite behavior.

Read-back for assertions uses parquet-java (spike found `ParquetFileReader` on the read side touches Hadoop classes — acceptable in tests, `hadoop-common` as `testRuntimeOnly`; or read via DuckDB JDBC — builder's call).

### 6.2 In-Java compliance checks (always run)

Helper used by every test: `geo` metadata against the vendored JSON schema; logical type / `crs` consistency; `bbox` column statistics present with type-defined column order; column-level `bbox` matches data; row-group row limit; geometry_types match data; native geospatial statistics present (2.0).

### 6.3 External validators (via mise)

Added to `mise.toml` (latest versions at implementation time):

- `pipx:rashid` / `pipx:portolan-cli` — Portolan's checks. rashid validates catalogs, not files: the test helper creates a temporary catalog (`portolan init --auto`, `portolan add <file>`, `portolan check`) and fails on errors relating to the data file (catalog-metadata findings like missing providers/README are ignored). Verified working in the spike.
- `pipx:geoparquet-io` — `gpio check spec` / `gpio check all`. **Note:** needs the DuckDB `spatial` extension, downloaded at runtime from `extensions.duckdb.org`; CI needs access to that host (blocked in the dev sandbox) or a cached extension.
- GDAL `validate_geoparquet.py --check-data` (GDAL ≥ 3.13 with Arrow/Parquet driver): not available via mise; optional via Docker image `ghcr.io/osgeo/gdal:ubuntu-full-*` if the builder finds a clean way, otherwise a manual verification step. Passed for all spike outputs.

Behavior: validators are invoked from a test helper on each written file. Locally, a missing tool skips (JUnit `Assume`); with `CI` set, a missing tool fails. CI gets the tools through the existing mise step of the shared workflow (see §4.5). GitHub-hosted runners have internet access, so gpio's DuckDB extension download works there (only the dev sandbox blocks it).

### 6.4 Real-world data tests

Read `MajorAirports-StrategicNoiseMaps-AT-LOWW.gpkg` (1 MB) and `util_wastewater_discharge.gpkg` (3 MB) from the GeoPackage plugin's test resources with `GeopackageInstanceReader`, write GeoParquet, run all checks, compare feature counts/values. Complex data via `InstanceBuilder` (INSPIRE-like address with locators, repeated names, groups/choices, recursive type); optionally the 60 KB deegree `features.gml` in `io.gml` test data.

## 7. Left to the builder

Plugin/class names, parameter names, Java vs. Groovy for main code, data-type mapping details within §5.4, naming for XML attributes and simple-content values, default nesting depth, Hilbert implementation, memory layout of pass 1, read-back approach in tests, whether GDAL validation runs in CI, compression level option, test file reuse mechanics (test dependency on the GeoPackage plugin vs. copying resources).

## 8. Risks and open points

- GeoParquet 2.0 not final; bbox covering re-added on `main`. Track the final release and adjust `geo` metadata.
- parquet-java column-order behavior for DOUBLE may change between releases; covered by the in-Java check (§6.2).
- Expensive reference resolution for some source collections when sorting (§4.4); documented, mitigated by the `sort` parameter or an intermediate instance store.
- Nested columns and Portolan: no spec rules found. The spike file contained a struct, a list of strings and a list of structs and passed `portolan add` + `portolan check`. Portolan's `table:columns` generation and PMTiles conversion with nested columns were not examined; if they break, that is reported upstream or handled as a follow-up (a "flat" mode stays a non-goal for now — my call).
- DuckDB extension download for gpio in CI (§6.3).

## 9. Spike findings (2026-10-28, throwaway, not committed)

Verified with parquet-java 1.18.1, GDAL 3.14.0dev, geoparquet-io 1.7.0, portolan-cli 0.8.0 / rashid 0.3.0:

- Pure-Java codec factory: ZSTD/Snappy/GZIP/uncompressed written, no Hadoop class on the runtime classpath.
- Nested columns read by GDAL as described in §4.2.
- GDAL `validate_geoparquet.py --check-data` passes for all codecs.
- `gpio check spec` (geoparquet-io 1.7.0): 40 passed, 0 warnings, 0 failed (once the DuckDB extension host was reachable).
- parquet-java writes native `GeospatialStatistics` for the `GEOMETRY` column automatically.
- portolan-cli rejected the file ("no geometry") until the column-level `bbox` was added to `geo`.
- pyarrow/rashid ignored bbox column statistics until `ColumnOrder.typeDefined()` was set explicitly; afterwards `portolan check` reported no data-file findings.
- Corrections to the original spike notes: compression is possible without Hadoop (`withCodecFactory`); Parquet logical-type `crs` content is now defined (PROJJSON or `authority:code`).
