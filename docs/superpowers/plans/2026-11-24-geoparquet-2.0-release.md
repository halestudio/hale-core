# GeoParquet 2.0 Release Follow-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the GeoParquet writer plugin's references, documentation and validator versions with the final GeoParquet 2.0.0 release, without changing writer behaviour.

**Architecture:** The final specification is identical to the state the writer implements, so there are no code changes. Two text changes (schema README, parameter description) and an optional bump of the external validators pinned in `mise.toml`, verified by the existing test suite.

**Tech Stack:** Gradle, Eclipse `plugin.xml`, mise (pipx tools `geoparquet-io`, `portolan-cli`), Groovy/JUnit tests.

**Spec:** `docs/superpowers/specs/2026-11-24-geoparquet-2.0-release-design.md`

## Global Constraints

- No change to the behaviour of the writer; no changes under `src/`.
- 2.0.0 stays the default version; 1.0.0 and 1.1.0 stay supported.
- `resources/test/geoparquet-schema/2.0.0.json` content stays unchanged.
- The GDAL CI image (`GDAL_CI_IMAGE` in `ExternalValidators.groovy`) stays as pinned.
- `docs/superpowers/specs/2026-10-28-geoparquet-writer-design.md` is not edited.
- New tool versions must be the latest releases.
- Conventional Commits, no `Co-authored-by` footer, no JIRA reference (none identifiable from the branch).
- Run `./gradlew spotlessApply` before each commit.
- Plugin directory (below: `$P`): `io/plugins/eu.esdihumboldt.hale.io.geoparquet`.

## Review Focus

- A newer validator rejects files the writer produces today: expected is to keep the old pin and report the finding, not to change the writer (Task 2, step 4).
- A newer Portolan fixes the 3D bbox reading: the `PORTOLAN_3D_BBOX_RULE` exemption and the texts naming "Portolan 0.8.0" would then be stale (Task 2, step 5).
- External validator tests are skipped silently when a tool is not on the PATH of the Gradle daemon: a green run must actually have executed them (Task 2, step 3).
- `plugin.xml` must stay well-formed after the description edit (Task 1, step 3).

---

### Task 1: Reference the final 2.0.0 release

**Files:**
- Modify: `$P/resources/test/geoparquet-schema/README` (the `2.0.0.json` entry)
- Modify: `$P/resources/main/plugin.xml:39` (description of parameter `geoparquet.version`)

- [ ] **Step 1: Update the README entry for `2.0.0.json`**

Replace the three-line entry (`main at commit 1edd56fb…` including the PR #302 remarks) by:

```
2.0.0.json  tag v2.0.0
```

- [ ] **Step 2: Extend the description of the `geoparquet.version` parameter**

New value of the `description` attribute:

```
The version of the GeoParquet specification to write. Version 2.0.0 uses the native Parquet GEOMETRY type, versions 1.0.0 and 1.1.0 write plain binary columns with the geo metadata. Use version 1.1.0 for readers that do not support GeoParquet 2.0 yet (e.g. GDAL before 3.14 and applications based on it). Note that Portolan does not accept version 1.0.0.
```

- [ ] **Step 3: Verify**

Run: `./gradlew spotlessApply :io:plugins:eu.esdihumboldt.hale.io.geoparquet:check`
Expected: BUILD SUCCESSFUL (the writer tests load the provider from `plugin.xml`; the compliance tests read the schema resources).

- [ ] **Step 4: Commit**

```bash
git add io/plugins/eu.esdihumboldt.hale.io.geoparquet/resources
git commit -m "docs(geoparquet): reference the final GeoParquet 2.0.0 release"
```

### Task 2: Update the external validators

**Files:**
- Modify: `mise.toml:10-11` (`pipx:geoparquet-io`, currently `1.7.0`; `pipx:portolan-cli`, currently `0.8.0`)
- Possibly modify: `$P/test/eu/esdihumboldt/hale/io/geoparquet/ExternalValidators.groovy`, `$P/resources/main/plugin.xml` (only per step 5)

- [ ] **Step 1: Determine the latest releases**

Run: `mise latest pipx:geoparquet-io; mise latest pipx:portolan-cli`
If both equal the pinned versions: the task ends here without a commit; report that the pins are current.

- [ ] **Step 2: Bump the versions in `mise.toml` and install**

Run: `mise install`, then `./gradlew --stop` (the Gradle daemon keeps the old PATH).

- [ ] **Step 3: Run the validator tests and make sure they were executed**

Run: `mise x -- ./gradlew :io:plugins:eu.esdihumboldt.hale.io.geoparquet:test --tests '*ExternalValidatorsTest' --tests '*GeoParquetRealDataTest' --tests '*GeoParquetGmlTest' --rerun-tasks`
Expected: BUILD SUCCESSFUL, and in `$P/build/test-results/test/*.xml` the `ExternalValidatorsTest` cases are not reported as `skipped`.

- [ ] **Step 4: If a new version fails**

Read the failure. If it is caused by the tool itself or would need a change of the writer: revert that tool's pin to the previous version, repeat step 3, and report the failure output in the hand-back. Do not change anything under `src/`.

- [ ] **Step 5: Check the Portolan 3D bbox exemption (only if `portolan-cli` was bumped)**

Temporarily remove `PORTOLAN_3D_BBOX_RULE` from the ignored rules in `ExternalValidators.check` and rerun step 3.
- Still fails with `PTL-DAT-005`: restore the exemption; replace "Portolan 0.8.0" by the new version in the comment at `ExternalValidators.groovy:100-108` and in the provider description and `bbox2d` parameter description in `plugin.xml`.
- Passes: keep the exemption removed, delete the constant with its comment, and in `plugin.xml` change the texts to say that Portolan versions up to 0.8.0 do not read the 6-value bbox correctly.

- [ ] **Step 6: Full check and commit**

Run: `./gradlew spotlessApply && mise x -- ./gradlew :io:plugins:eu.esdihumboldt.hale.io.geoparquet:check`
Expected: BUILD SUCCESSFUL

```bash
git add mise.toml io/plugins/eu.esdihumboldt.hale.io.geoparquet
git commit -m "test(geoparquet): update external GeoParquet validators"
```
