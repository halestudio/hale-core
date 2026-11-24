# GeoParquet 2.0 final release: follow-up for the writer

Date: 2026-11-24

## Intent

GeoParquet 2.0.0 was released (tag `v2.0.0`, announced 2026-11-24). The GeoParquet writer
(`io/plugins/eu.esdihumboldt.hale.io.geoparquet`) was built against a pre-release state of the
specification (see `2026-10-28-geoparquet-writer-design.md`). This follow-up aligns the plugin
with the final release.

## Findings

- The specification did not change normatively since the state we implemented: the bundled
  `resources/test/geoparquet-schema/2.0.0.json` is byte-identical to `format-specs/schema.json`
  at tag `v2.0.0`; the only upstream commit since our pinned `main` commit (`1edd56fb`) adds a
  changelog.
- The 2.0 output of the writer matches the final rules (compared by reading the code against the
  spec): root-level optional `GEOMETRY` columns with WKB, `version` `"2.0.0"`, `.parquet` file
  extension, CRS pairing between the Parquet logical type and the `geo` metadata (CRS84: omitted
  in both; EPSG: `EPSG:<code>` + PROJJSON; unknown: `srid:0` + `null`), optional `bbox` covering.
- No released GDAL version fully supports 2.0 yet (announced for 3.14).

## Decisions

- 2.0.0 stays the default version; 1.0.0 and 1.1.0 stay supported (decided by Simon).
- No change to the behaviour of the writer.

## Goals

1. `resources/test/geoparquet-schema/README`: state that `2.0.0.json` is from tag `v2.0.0`
   (file content unchanged).
2. `plugin.xml`, parameter `geoparquet.version`: add a sentence that 1.1.0 is the choice for
   older readers (e.g. released GDAL/QGIS versions that do not fully read 2.0 yet).
3. Check for newer releases of `geoparquet-io` and `portolan-cli`, bump them in `mise.toml` if
   available, run the external validator tests. If a newer version fails for reasons that would
   need writer changes, keep the current pin and report instead of widening the scope.
4. The GDAL CI image stays the pinned 3.14 nightly.

## Non-goals

- The October design document is left as is (historical record).
- Writing `orientation` (needs checking/rewinding all polygon rings; only encouraged by the spec).
- zstd level 15+ as recommended by the distribution guide (the pure-Java compressor has no level
  setting; a native zstd library is deliberately avoided).
- `zmin`/`zmax` in the bbox covering (Portolan up to 0.8.0 mishandled the 3D column bbox; whether
  Portolan 0.9.0 handles a 3D covering was not evaluated).

Each of these can be a separate follow-up.

## Approaches

- **Clean-up only (chosen):** the spec is unchanged, so only references, documentation and
  validator versions are brought up to date. Smallest change, no behaviour risk.
- **Clean-up plus optional 2.0 recommendations** (`orientation`, higher zstd level, 3D covering):
  rejected for now, each is a behaviour or dependency change with a cost of its own and none is
  required for conformance.
- **Change the default to 1.1.0 for the widest reader support:** rejected, the default stays
  2.0.0 by decision.

## Left to the builder

- Exact wording of the README and parameter description.
- Whether validator bumps go in (depends on available versions and test results).
