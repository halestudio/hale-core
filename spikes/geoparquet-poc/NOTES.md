# GeoParquet write PoC — notes (ING-5399)

Standalone spike, not integrated into a hale plugin. See ticket ING-5399 for full scope
(pure-Java, non-Arrow GeoParquet 2.0 writer investigation).

## How to run this PoC

Requires JDK 17 and Maven; run from this directory (`spikes/geoparquet-poc/`).

**PowerShell:**

```powershell
mvn compile
mvn dependency:build-classpath "-Dmdep.outputFile=cp.txt" "-DincludeScope=runtime"
Remove-Item -ErrorAction SilentlyContinue geoparquet-from-gpkg.parquet
java -cp "target/classes;$(cat cp.txt)" GeoPackageToGeoParquet
```

**Git Bash:**

```bash
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt -DincludeScope=runtime
rm -f geoparquet-from-gpkg.parquet
java -cp "target/classes;$(cat cp.txt)" GeoPackageToGeoParquet
```

The `-DincludeScope=runtime` on the classpath step is load-bearing, not optional: Maven's
`dependency:build-classpath` includes `provided`-scope jars by default, which would put
`hadoop-common.jar` itself back on the classpath and silently defeat the PoC's whole
"zero Hadoop classes touched at runtime" claim (see the "OSGi / dependency-footprint
check" section below for the full story on why this matters).

`cp.txt` is a generated file, not checked in — regenerate it with the `dependency:
build-classpath` command above any time you re-open a shell to run this, the same way
you'd expect for a build artifact.

`GeoPackageToGeoParquet` takes four optional positional args (all default if omitted):
`<gpkg-path> <table-name> <output-path> <codec>` — defaults are `data/013_Grindelwald.gpkg`,
`Bedrock_PLG`, `geoparquet-from-gpkg.parquet`, `UNCOMPRESSED`. `<codec>` is one of
`UNCOMPRESSED`, `ZSTD`, `SNAPPY`, `LZ4_RAW`, `GZIP` — all Hadoop-free, see "Hadoop-free
compression via aircompressor" below. The writer refuses to overwrite an
existing output file (`FileAlreadyExistsException`), so **delete the previous output
first if you're re-running** (`Remove-Item geoparquet-from-gpkg.parquet` /
`rm -f geoparquet-from-gpkg.parquet`, as above), or pass a different output path as the
third arg instead.

For the synthetic 3-feature demo instead of the real GeoPackage data, swap the main
class (optional arg is an EPSG code, default 4326):

```powershell
java -cp "target/classes;$(cat cp.txt)" GeoParquetPoc
```

### Validating the output

Both outputs are `.parquet` files, git-ignored (see `.gitignore`), so nothing you
generate here gets committed. Validate independently with `validate.py` (requires
`duckdb` + `jsonschema`, both already available in this environment's Python):

**PowerShell** (sets UTF-8 output first, so non-ASCII attribute text like "Aalénien"
prints correctly instead of crashing partway through - see gotcha below):

```powershell
$env:PYTHONIOENCODING="utf-8"
python validate.py geoparquet-from-gpkg.parquet
```

**Git Bash:**

```bash
PYTHONIOENCODING=utf-8 python validate.py geoparquet-from-gpkg.parquet
```

Two environment gotchas hit while testing this, neither a bug in the script itself:

- If it fails on `import jsonschema` with `TypeError: TypeVar.__init__() got an
unexpected keyword argument 'default'`, the `typing_extensions` package is missing
  (a transitive dependency of `jsonschema`'s `referencing` package) — fix with
  `pip install typing_extensions`.
- If it crashes with `UnicodeEncodeError: 'charmap' codec can't encode characters...`
  partway through (after the schema check already printed `VALID`), that's PowerShell's
  console defaulting to the `cp1252` codepage, which can't print that non-ASCII
  attribute text - the `PYTHONIOENCODING=utf-8` above avoids it; without that env var,
  `python validate.py geoparquet-from-gpkg.parquet` alone will hit this.

Or open the output directly in QGIS/ogr2ogr instead of via `validate.py`.

### Browsing the rows

`validate.py` only prints the first 5 rows. To see more, use `show_rows.py` (requires
`duckdb`; loads DuckDB's `spatial` extension and reads the geometry column name from the
file's `"geo"` metadata, printing geometries as WKT):

```powershell
$env:PYTHONIOENCODING="utf-8"
python show_rows.py geoparquet-from-gpkg.parquet        # all rows
python show_rows.py geoparquet-from-gpkg.parquet 50     # first 50 rows
python show_rows.py geoparquet-from-gpkg.parquet csv    # writes geoparquet-from-gpkg.csv
```

The same `PYTHONIOENCODING=utf-8` gotcha as above applies when printing to the console.
The `csv` option is the easiest way to browse all 1,097 rows (e.g. in Excel), since the
polygon WKT makes console output very wide. Unlike the `.parquet` outputs, the `.csv` is
**not** git-ignored — delete it afterwards.

## Status

- Write path proven: `parquet-hadoop` + `ExampleParquetWriter.withConf(new
PlainParquetConfiguration())` writes Point/LineString/Polygon (WKB via JTS) with
  GeoParquet 2.0.0 file-level `"geo"` metadata (real PROJJSON `crs`, cached under
  `crs-cache/`), with no `org.apache.hadoop.*` class touched at runtime.
- **Corrected (2026-09-15): the geometry column now also carries Parquet's native
  `GEOMETRY` logical type annotation** (`LogicalTypeAnnotation.geometryType(crsId)`),
  not just plain `BINARY`. See "GeoParquet 2.0 spec correction" below — this was a real
  gap in the PoC's compliance, not just a nice-to-have.
- Output independently validated with `validate.py` (DuckDB): JSON-Schema-validated
  against the official published GeoParquet 2.0.0-rc.1 schema, geometries readable back
  as WKT, for both the EPSG:4326 and EPSG:25832 outputs.
- Reading was _not_ provable Hadoop-free in-process — even `ParquetReadOptions` in
  parquet-hadoop 1.17.1 transitively touches `org.apache.hadoop.mapreduce.lib.input.
FileInputFormat`. Not a blocker for the write-only priority, but a risk to carry into
  the follow-up ticket if reading is ever needed from the same dependency.
- **Validated against real data (2026-09-15)**: see "Real test data" section below.

## Real test data (2026-09-15)

Real test data (`data/013_Grindelwald.gpkg`, a GeoPackage from the Swiss geological
survey covering the Grindelwald area, CRS EPSG:2056 / CH1903+ LV95, 18 feature layers)
was added to this directory. Closes the "synthetic sample data" gap.

`GeoPackageToGeoParquet.java` (new) reads a GeoPackage layer via plain JDBC
(`org.xerial:sqlite-jdbc` — native/JNI, but only used to load this PoC's test input, not
part of the write-path dependency footprint being evaluated) plus a small hand-rolled
GeoPackageBinary (GPB) header parser (magic/version/flags/srs_id/envelope, per the OGC
GeoPackage spec) that strips down to the underlying WKB, which is then written the same
way as `GeoParquetPoc.java`'s synthetic path.

Run against `Bedrock_PLG` (1097 real polygon features, 14 text attribute columns
including UUIDs and non-ASCII text like "Aalénien"): wrote successfully, 2.77 MB
uncompressed. `validate.py` (updated to read the primary geometry column name generically
from the `"geo"` metadata instead of assuming the synthetic sample's column names)
confirms: passes the official GeoParquet 2.0.0-rc.1 JSON-Schema check, all 1097 rows
readable back via DuckDB with correct WKT geometries and correctly preserved non-ASCII
attribute text, real EPSG:2056 PROJJSON in the `crs` field.

**Second independent validator attempted (2026-09-15), inconclusive for environment
reasons, not a finding about the file**: tried validating via GDAL/OGR (`st_read`)
through two routes — DuckDB's own `spatial` extension, and Python's `pyogrio` (GDAL
3.12.4) — as a check independent of DuckDB's native Parquet reader (which is what all
validation above actually went through). Both failed to open _any_ Parquet file at all,
including a plain non-geo one used as a sanity check, with no Parquet driver registered
in either GDAL build. This is a known, common GDAL packaging gap: the Parquet/GeoParquet
driver needs an Arrow-C++-enabled GDAL build, which most redistributed binaries (pip
wheels, DuckDB's extension) omit for size reasons — a real GDAL install (e.g. via
conda-forge, which typically does enable it) would very likely work, but wasn't feasible
to set up in this environment. DuckDB's native reader remains the one working
independent validator used here; it satisfies the DoD's "e.g. DuckDB, GDAL/ogr2ogr, QGIS"
wording as stated, but a genuine second tool is still worth running if a GDAL/ogr2ogr or
QGIS install is available elsewhere, since DuckDB's own GeoParquet recognition may be
more permissive than a stricter reference implementation.

**Confirmed with a concrete number (2026-09-23)**: `validate.py` now auto-loads the
`spatial` extension before attempting `st_read` (previously it just tried `st_read` cold
and got DuckDB's generic "not in the catalog, try installing/loading it" message, which
misleadingly implies loading the extension would fix it). With `spatial` actually
loaded, `st_read` still fails the same way — `IO Error: Could not open GDAL dataset` —
and querying `st_drivers()` directly shows why: this build's bundled GDAL has **54
drivers total, 0 of them Parquet-capable**. So this isn't a fluke or a missing step;
it's a real, confirmed gap in this specific GDAL build, exactly as already documented
above. (Getting `spatial` to load at all also needed a workaround: DuckDB's own
extension auto-downloader got a plain-`http` `403` in this environment and failed
differently over `https` too, while a manual `curl` download of the same `.gz` over
`https` worked fine — so the `.duckdb_extension` was downloaded manually and dropped
into `~/.duckdb/extensions/v1.5.5/windows_amd64/`, where DuckDB picks it up like a
normal local install.)

## Compression codec correction (2026-09-15) — important

Earlier note below claimed `CompressionCodecName.GZIP` was a safe pure-Java,
Hadoop-free fallback if compression is wanted without native codecs. **This is wrong.**
Tested it directly (`GeoPackageToGeoParquet.java` originally used GZIP): it throws
`NoClassDefFoundError: com/ctc/wstx/io/InputBootstrapper` at runtme. Root cause:
`parquet-hadoop`'s `CodecFactory.getCompressor()` constructs a real
`org.apache.hadoop.conf.Configuration` for _any_ non-`UNCOMPRESSED` codec (it looks up
the actual codec implementation class through Hadoop's own compression-codec
configuration mechanism) — and `Configuration`'s own XML resource parsing needs
Woodstox, which isn't on the classpath since `hadoop-common` is `provided`-scope/
excluded. So it's not just Snappy/Zstd that need native/Hadoop classes — **any**
compression codec through `parquet-hadoop`'s stock `CodecFactory` does, only
`UNCOMPRESSED` is genuinely Hadoop-free. Switched `GeoPackageToGeoParquet.java` back to
`UNCOMPRESSED` to match what's actually proven.

This also caught a methodology flaw in the "OSGi / dependency-footprint check" below:
`mvn dependency:build-classpath` includes `provided`-scope jars **by default** (needs
`-DincludeScope=runtime` to exclude them), so the classpath used to "confirm" the
Hadoop-free write path had `hadoop-common-3.3.0.jar` itself silently present the whole
time — only its transitive dependencies (via the wildcard `<exclusions>` in `pom.xml`)
were actually absent. Re-tested `GeoParquetPoc.java` (`UNCOMPRESSED`) against the
correctly-scoped runtime classpath (`hadoop-common.jar` itself absent, not just its
transitives) — **the core claim holds**: it still writes and validates successfully with
zero Hadoop classes reachable at all. But this means: for the follow-up implementation,
compressed GeoParquet output through this library is an open problem, not a solved one
with a known pure-Java answer.

## Hadoop-free compression via aircompressor (2026-09-29) — resolves the codec issue

Follow-up to the "Compression codec correction" above: the Hadoop coupling is in
`parquet-hadoop`'s stock `CodecFactory`, not in the compression algorithms. parquet-java
lets a writer plug in its own factory (`ParquetWriter.Builder.withCodecFactory(
CompressionCodecFactory)`, interface in `parquet-common`), so `PureJavaCodecFactory.java`
implements that interface and calls the compressors directly:

- `ZSTD`, `SNAPPY`, `LZ4_RAW`: pure-Java `io.airlift:aircompressor`
  (`ZstdCompressor`, `SnappyCompressor`, `Lz4Compressor`)
- `GZIP`: the JDK's `java.util.zip`

**Effort / footprint:** one new class (~210 lines incl. comments, ~150 of code; about
half is the decompressor side, which the writer doesn't use but the interface requires)
plus ~15 changed lines in `GeoPackageToGeoParquet.java`. **No new library**:
aircompressor (255 KB, no runtime dependencies of its own) was already a transitive
`compile` dependency of `parquet-hadoop` (2.0.2); `pom.xml` now declares it explicitly at
2.0.3, the version hale-core already pins in `gradle/libs.versions.toml` (CVE fix).
snappy-java / zstd-jni stay excluded.

**Tested** on `Bedrock_PLG` (1,097 polygons, 14 attributes) with the runtime-scoped
classpath (`-DincludeScope=runtime`, `hadoop-common` absent), and with `-verbose:class`:
**0 `org.apache.hadoop` classes loaded** for a ZSTD write. Every output read back by
DuckDB 1.5.5 with identical content to the uncompressed file (`EXCEPT` both ways = 0 rows,
same total `ST_Area`), native `GeometryType(crs=EPSG:2056)` and GeospatialStatistics
(bbox + types) present, and the ZSTD file passes `validate.py`'s schema check:

| Codec        | Size        | vs. uncompressed |
| ------------ | ----------- | ---------------- |
| UNCOMPRESSED | 2,770,648 B | 100%             |
| SNAPPY       | 2,173,767 B | 78%              |
| LZ4_RAW      | 1,919,406 B | 69%              |
| ZSTD         | 1,404,839 B | 51%              |
| GZIP         | 1,397,420 B | 50%              |

(Default compression levels; writing speed not measured. ZSTD is the usual choice for
Parquet — GZIP compresses similarly here but is typically much slower to read/write.)

Still to check for a real plugin: the OSGi bundle must include/import aircompressor
(2.0.x uses `sun.misc.Unsafe` internally — check under OSGi and newer JDKs); no reader-
side use of the decompressors was tested (parquet-java reading isn't Hadoop-free anyway,
see "Status"); and a larger multi-row-group file.

## GeoParquet 2.0 spec correction (2026-09-15) — important

Checked the actual current spec text (opengeospatial/geoparquet main branch), not just
the JSON metadata schema. It states plainly:

> "Geometry columns MUST be encoded as either `GEOMETRY` or `GEOGRAPHY` logical types
> in Parquet... Producing these native geospatial Parquet types is the foundation of
> GeoParquet 2.0 and is what every writer should do; the GeoParquet `geo` metadata
> layers additional, more explicit information on top."

This PoC's original version (plain `BINARY` column + file-level `"geo"` metadata only —
the GeoParquet 1.x approach) is **not actually 2.0-compliant**, even though it passed
`validate.py`'s JSON-Schema check — that check only validates the shape of the `"geo"`
metadata document, not whether the column itself carries the required native logical
type. That's a real gap this PoC had; not a compliance-nuance to gloss over.

The native `GEOMETRY`/`GEOGRAPHY` logical type annotations were added to the Parquet
_format_ spec in 2.11.0 (March 2025) and are present in `parquet-java`'s schema API
(`org.apache.parquet.schema.LogicalTypeAnnotation.GeometryLogicalTypeAnnotation`,
`.geometryType(String crs)`) — confirmed present in the classfiles of both the
`1.17.1` release already used here and the current latest `1.18.1`. So no library
upgrade is needed; the PoC schema now uses it (see `GeoParquetPoc.java`), tested
building/writing/validating successfully.

Open question this raises: the Parquet format spec leaves the `crs` string's _content_
undefined — "an optional string value," defaulting to `"OGC:CRS84"` if unset, with no
guidance on whether it should be a PROJJSON string, an authority code, or a URI. This
PoC currently passes a plain `"EPSG:<code>"` identifier there (full PROJJSON stays in
the `"geo"` file metadata, as the spec text implies is the intended split). Not yet
verified against a reference writer's actual output — worth checking before the
follow-up implementation locks in a convention.

## OSGi / dependency-footprint check (2026-09-15)

`parquet-hadoop:1.17.1`'s own POM declares `org.xerial.snappy:snappy-java:1.1.10.7` and
`com.github.luben:zstd-jni:1.5.7-3` as plain `compile`-scope (not `optional`), so both
are pulled in transitively by default. Both are native/JNI-backed compression codecs
bundling per-platform binaries — directly conflicting with the ticket's "no native/JNI
bindings" preference:

- `zstd-jni-1.5.7-3.jar`: 7.4 MB
- `snappy-java-1.1.10.7.jar`: 2.3 MB

(for comparison, `parquet-hadoop` itself is 1.6 MB, `parquet-column` 3.3 MB)

**Tested and confirmed unnecessary for `UNCOMPRESSED` output**: excluding both from the
`parquet-hadoop` dependency (see `pom.xml`) and rebuilding still produces a file that
validates as GeoParquet-2.0.0-rc.1-compliant via `validate.py`, using the `UNCOMPRESSED`
codec, on a correctly-scoped Hadoop-free runtime classpath (see "Compression codec
correction" below). **Correction**: any _other_ codec — including GZIP, previously
assumed to be a safe pure-Java fallback here — turns out to need real Hadoop classes at
runtime via `parquet-hadoop`'s own `CodecFactory`, regardless of snappy-java/zstd-jni.
See "Compression codec correction" for the detail; this changes the exclusion's payoff
from "removes ~9.7MB of native jars, compression still available via GZIP" to "removes
~9.7MB of native jars, but compression of any kind is now an open problem."

Remaining runtime dependency set with the exclusion applied: `parquet-hadoop`,
`parquet-column`, `parquet-encoding`, `parquet-common`, `parquet-format-structures`,
`parquet-jackson` (shaded Jackson — avoids clashing with hale's own Jackson version),
`javax.annotation-api`, `aircompressor` (pure-Java GZIP/LZ4 codecs), `commons-pool`,
`slf4j-api`, plus `jts-core` (already a hale dependency). No Hadoop, no Arrow, no native
code.

### OSGi bundling — actually tested against hale's real tooling (2026-09-15)

None of these are published as OSGi bundles, so they'd need wrapping. hale-core already
has a proven mechanism for exactly this — the `hale.shadow-dependencies` + bnd
(`biz.aQute.bnd.builder`, via `hale.library-conventions`) convention, already used in
production by `eu.esdihumboldt.hale.io.codelist.skos` to shade a non-OSGi third-party
library into its own bundle. Rather than guess whether it'd work here, built a standalone
Gradle project applying the exact same plugin versions hale-core pins (bnd 7.3.0, Shadow
9.6.1) and shaded this dependency set the same way.

**It builds and produces a bundle**, but bnd's bytecode-analysis-based manifest
generation surfaced a real problem: `Import-Package` came back with hard (non-optional)
requirements on `org.apache.hadoop.classification`, `.conf`, `.fs`, `.io`, `.io.compress`,
`.mapred`, `.mapreduce` (+ sub-packages), `.util`, plus `org.xerial.snappy` and
`com.github.luben.zstd` — none of which hale-core provides or ships. These come from
_dead code paths_ physically compiled into `parquet-hadoop`/`parquet-column` (MapReduce
`InputFormat`/`OutputFormat` integration, and codec classes for the compression libs we
deliberately excluded) that this PoC never calls, but bnd's static analysis doesn't know
that — it declares every package any class in the jar references, live code or not. **In
a real Equinox/OSGi runtime, this bundle would fail to resolve/start at all**, not just
fail at the point of use, since unresolvable hard imports block bundle resolution
entirely — a much worse failure mode than the "throws if you touch that code path" risk
already documented for compression.

**Fix, tested and confirmed working**: adding explicit bnd `Import-Package` overrides
marking those specific packages `resolution:=optional` (`org.apache.hadoop.*;
resolution:=optional,org.xerial.snappy;resolution:=optional,com.github.luben.zstd;
resolution:=optional,*`) produces a manifest where all of them carry
`resolution:=optional` — the bundle would resolve and activate fine without those
packages present, consistent with the runtime behavior already proven (those code paths
are simply never invoked by the `UNCOMPRESSED` write path).

**One more loose end found, not yet resolved**: `parquet-jackson`'s internally-relocated
Jackson classes (packaged under `shaded.parquet.com.fasterxml.jackson.*` by the upstream
parquet project itself) are physically present in the shaded jar (confirmed: 1173 class
files) but still show up in `Import-Package` as an _external_ requirement rather than
being recognized as bundled/private content — because the bnd `Export-Package` pattern
used here (`org.apache.parquet.*`) doesn't cover that sibling package tree, and no
`Private-Package` was declared for it either. A real implementation would need
`Private-Package` to explicitly claim `shaded.parquet.*` (and similarly check the other
shaded sub-trees — thrift, fastutil, etc.) so the bundle doesn't self-import packages it
already contains. Small, mechanical fix, but needs doing — flagged as follow-up, not
chased to completion here.

**Bottom line**: OSGi bundling of this dependency set through hale's existing
shadow+bnd convention is _achievable_, not blocked, but not a rubber-stamp either — it
needs the same "declare compression/Hadoop-touching packages optional" treatment plus a
`Private-Package` fix for parquet's own internal shading, before it would actually
resolve cleanly in hale's runtime. Both are known, bounded fixes, not open unknowns.

## Library survey (2026-09-15)

| Option                                            | License    | Verdict                           | Why                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| ------------------------------------------------- | ---------- | --------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`parquet-java` (parquet-mr) direct** — this PoC | Apache-2.0 | **Recommended candidate**         | Pure Java, no Arrow. Native/JNI compression codecs excludable and confirmed non-load-bearing (see above). Actively maintained (Apache project; 1.18.1 released within the last two weeks of this check). Already supports the native `GEOMETRY`/`GEOGRAPHY` logical types required by 2.0. Only real cost: hand-roll the `"geo"` file metadata and schema-building yourself — no GeoParquet-specific convenience layer.                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `com.jerolba:carpet-record` (Carpet)              | Apache-2.0 | **Not recommended**               | Friendlier Java-records-based API over `parquet-hadoop`, but checked its actual dependency tree (`mvn dependency:tree`, not just its docs) and found it pulls in `org.apache.hadoop:hadoop-common:3.4.1` as a direct **`compile`-scope** dependency (trimmed of hadoop-common's own heavy transitives, but hadoop-common itself is present and loadable) — despite being marketed as "Hadoop-free." That's a materially different, weaker claim than this PoC's approach (hadoop-common `provided`-scope, zero Hadoop classes touched at runtime, verified). Also brings its own `snappy-java`/`zstd-jni` by default (same as raw parquet-hadoop) and has **no GeoParquet-specific support** — no WKB/geometry logical type helpers, no `"geo"` metadata convenience. Adds a dependency and an API layer without reducing the actual GeoParquet-specific work. |
| `tileverse-io/parquetry`                          | Apache-2.0 | **Promising, but not usable now** | The only library found with purpose-built GeoParquet 2.0 support (typed CRS/PROJJSON models, native Geometry/Geography logical types, JTS materializer) and explicitly no Hadoop/no parquet-java dependency. Disqualifying right now: **requires JDK 25 with `--enable-preview`** (hale-core targets JDK 17 — see `hale.java-conventions.gradle`), version `1.0-SNAPSHOT` with no stable release, only 2 GitHub stars, and its own docs say the write path is incomplete (flat columns only; nested/repeated "next"). Worth re-checking in a future follow-up if it matures and hale's JDK baseline ever moves.                                                                                                                                                                                                                                                |
| GeoTools GeoParquet `DataStore`                   | LGPL-2.1   | Disqualified (unchanged)          | Re-confirmed against the current (35.x) docs: still explicitly read-only.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| GDAL/OGR                                          | MIT-style  | Disqualified (unchanged)          | Native/JNI dependency hale-core doesn't carry today.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| Apache Sedona                                     | Apache-2.0 | Disqualified (new finding)        | GeoParquet support exists only as part of its Spark/Flink/Snowflake cluster-computing runtime — no standalone embeddable Java library mode. Wrong deployment model entirely for a hale plugin.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |

License note: all candidates here are permissively licensed (Apache-2.0 or MIT-style)
except GeoTools (LGPL-2.1, already how hale-core consumes GeoTools elsewhere, so not a
new constraint) — license was not a differentiator in this survey, fit and maturity were.

**Recommendation stands as the ticket's own hypothesis**: `parquet-java` direct, writing
WKB via JTS into a column annotated with the native `GEOMETRY` logical type, with
`"geo"` file metadata layered on top. No pure-Java, non-Arrow alternative found that's
actually usable today; Carpet doesn't help for this use case; Parquetry is the one to
watch for later.

## Open questions / risks (carry into follow-up ticket)

- No bbox covering column in the `geo` metadata (spec-optional but commonly expected by
  consumers for row-group pruning) — out of scope for this PoC.
- PROJJSON is currently fetched from spatialreference.org at runtime and disk-cached;
  not acceptable for a shipped plugin. Needs bundling/pre-generating PROJJSON for the
  CRSes hale actually needs (e.g. common INSPIRE/XPlanGML EPSG codes), or vendoring
  PROJ's own EPSG-to-PROJJSON data.
- No defined path yet for a hale `CRSDefinition` backed by a custom/WKT-only CRS with no
  EPSG identifier.
- OSGi bundling is now verified achievable via hale's existing shadow+bnd convention
  (see "OSGi bundling — actually tested" above), but needs two known, bounded fixes
  before a real plugin build would resolve cleanly: `resolution:=optional` on the
  Hadoop/snappy/zstd imports (tested, works), and a `Private-Package` declaration for
  parquet's own internally-shaded `shaded.parquet.*` classes (identified, not yet fixed).
- The native `GEOMETRY` logical type's `crs` string content/convention is unverified
  against a reference writer (see spec-correction section above).
- ~~Compressed GeoParquet output has no known pure-Java, Hadoop-free path~~ —
  **resolved in the PoC (2026-09-29)**: a custom `CompressionCodecFactory` using
  aircompressor writes ZSTD/SNAPPY/LZ4_RAW (and GZIP via the JDK) with zero Hadoop classes
  loaded; see "Hadoop-free compression via aircompressor". Remaining for the follow-up:
  pick the default codec (ZSTD suggested), OSGi check of aircompressor, and tests.
- This write-up needs to actually reach the ticket/team (comment on ING-5399, PR, or doc
  link) before Monday's Refinement; right now it only exists in this local, uncommitted
  directory.
