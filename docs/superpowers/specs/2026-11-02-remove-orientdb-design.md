# Remove OrientDB and unused dependencies — design

- Related issue: ING-4137 (hale: Replace OrientDB as temporary database), part 5 of its decomposition
- Date: 2026-11-02
- Predecessor: `2026-10-08-instance-store-design.md` (SQLite instance store)

## 1. Intent

The SQLite based instance store (`common.instance.store.sqlite`) replaced OrientDB as the temporary instance store. OrientDB is now only a low-priority fallback (`orient` store, priority 0). The OrientDB code, the old OrientDB 1.5.1 libraries and everything that only exists because of them should go. After that, the dependency catalog and module dependency declarations are swept for anything else that is no longer used.

Why now: hale studio does not consume hale-core modules yet (it has its own Tycho copies), so the original precondition "after UI integration and a transition period" from the predecessor spec does not apply to hale-core. The OrientDB modules were always internal; downstream projects at most declare them as dependencies.

## 2. Goals

1. Remove all OrientDB code, modules, tests, and libraries from hale-core.
2. Replace the OrientDB-specific TinkerPop build (`2.5.0-orientDB151`) with the official TinkerPop 2 release 2.6.0.
3. Sweep the whole dependency catalog and all module dependency declarations for unused entries, report the findings to the user, and remove what the user approves.
4. Make the removal visible to downstream projects as a breaking change.

## 3. Non-goals

- Migrating `common.instance.graph` (`ReferenceGraph`) to TinkerPop 3 or another graph library.
- Touching the unbuilt directories that use TinkerPop 3 (`common/plugins/eu.esdihumboldt.hale.common.align.tgraph`, `...align.tgraph.test`, `ext/xslt/*` — none has a `build.gradle`). Removing them would be a separate decision.
- Changes to hale studio.
- Changes to the instance store API beyond what the removal requires.

## 4. Anti-goals

- Breaking the headless transformation / CLI: after the removal the SQLite store must be found and used everywhere the orient store was a possible fallback.
- Removing a dependency that is only needed at runtime (e.g. via OSGi `plugin.xml` extensions, service loaders, reflection, Groovy scripts, or JDBC drivers loaded by name) because it looks unused at compile time.
- Silently removing sweep findings without the user's approval.

## 5. Part A — OrientDB removal

### Modules to delete

| Module | Notes |
| --- | --- |
| `common/plugins/eu.esdihumboldt.hale.common.instance.orient` | OrientDB instance model and storage |
| `common/plugins/eu.esdihumboldt.hale.common.headless.orient` | `orient` store factory (`plugin.xml` registration), `OrientInstanceStoreContractTest` |
| `common/features/eu.esdihumboldt.hale.common.feature.orient` | only wraps `instance.orient` |
| `util/plugins/eu.esdihumboldt.util.orient.embedded.test` | test-only OrientDB bundle |
| `util/plugins/eu.esdihumboldt.util.blueprints.entities` | needs `blueprints-orient-graph`; no known consumers (no module depends on it) |

Modules are discovered automatically by `settings.gradle` and included in the BOM automatically, so deleting the directories is enough there.

### References to update

- `app/features/eu.esdihumboldt.hale.app.feature.cli/build.gradle`: drop the `common.feature.orient` and `common.headless.orient` entries.
- `cst/features/eu.esdihumboldt.cst.feature/build.gradle`: drop the `common.feature.orient` entry.
- `app/plugins/eu.esdihumboldt.hale.app.transform/build.gradle`: drop the `testRuntimeOnly` entry for `headless.orient`; delete `ExecuteOrientStoreTest.groovy` and `StorePathTransformationOrientTest.groovy`.
- `gradle/libs.versions.toml`: drop `orientdb`, `orient-common` versions and the `orientdb-core`, `orient-common`, `tinkerpop-blueprints-orient` libraries.
- `.trivyignore`: drop `CVE-2017-11467` and its OrientDB comment (the file may become empty apart from the header link; keeping or deleting the file is the builder's call).

### Code that stays, with cleanup

- `InstanceStoreExtension` keeps its generic priority/fallback mechanism (decision: generic, costs nothing). SQLite is then the only registered store. `InstanceStoreExtensionTest` uses `orient` as an example id — replace with neutral ids (e.g. `low` / `high`).
- `StoreInstancesJob.TASK_TYPE` keeps the legacy value `eu.esdihumboldt.hale.instance.orient.store` for compatibility (decision); its comment can stay as it explains the name. `StoreInstancesJobTest` stays.
- Comments mentioning OrientDB that are now stale (e.g. `MetaFilter.java`, `TreePropertyTransformer.java` "OrientInstanceSink") are updated or removed. Mentions in historic specs/plans under `docs/superpowers` are left as they are.

### Transitive dependencies

`instance.orient` exposes `eclipse-collections-api` as `api`, and the orient feature is included by the CLI and CST features. Modules that relied on OrientDB-related libraries or on these transitively must declare what they actually use. The build and the full test suite must pass after the removal.

## 6. Part B — TinkerPop 2.6.0

- The only built module using TinkerPop after Part A is `common.instance.graph` (`ReferenceGraph`: Blueprints `TinkerGraph`, Pipes, Gremlin-Java), used by the GML writer (`StreamGmlWriter`, `XPlanGmlInstanceWriter`) for partitioning output while keeping referenced features together.
- Change the `tinkerpop` version in `gradle/libs.versions.toml` from `2.5.0-orientDB151` to `2.6.0` (artifacts `blueprints-core`, `pipes`, `gremlin-java`). 2.6.0 is the last official TinkerPop 2 release; a newer major would mean a rewrite (non-goal).
- Check what 2.6.0 pulls in transitively (e.g. Gremlin-Java's dependencies) and exclude what is not needed, if anything changes compared to the current build.
- Existing GML writer / partitioning tests must pass.

## 7. Part C — Broader sweep

Scope: every entry in all three version catalogs — `gradle/libs.versions.toml`, `gradle/test-libs.versions.toml` and `gradle/resources.versions.toml` (versions, libraries, bundles, plugins) — and every dependency declared in each module's `build.gradle` (my call: the broader sweep covers all catalogs).

BOM: `bom-deps` publishes every library alias of the `libs` catalog as a version constraint. The BOM is meant to reflect what hale-core uses (user decision), not to manage versions for downstream projects. A `libs` entry that no hale-core module uses is therefore unused even though it disappears from the published BOM; removing it is not a breaking change. Entries in `resources.versions.toml` may be used by name at runtime rather than referenced in code, so they get the runtime-use check of step 2 with particular care.

Process:

1. Find candidates: catalog entries referenced by no build file, and declared module dependencies that the module's code (main and test, Java and Groovy) does not use. Known example: `io.wfs` declares `common.instance.graph` but does not use it.
2. Check each candidate against runtime-only uses before calling it unused: OSGi `plugin.xml` extensions and `MANIFEST`-related config, `META-INF/services`, class names in strings/reflection, Groovy scripts and resources, JDBC drivers, logging backends, and dependencies that only exist to pin the version of a transitive dependency (e.g. to fix a security issue).
3. Report the findings to the user as a list, grouped into "clearly unused" and "unsure, needs a decision", with the evidence for each.
4. Remove only what the user approves.

Tooling to find candidates is the builder's choice (e.g. a Gradle dependency analysis plugin used only locally, or grep-based scripts); nothing new is added to the build permanently unless the user agrees.

## 8. Commits and downstream notice

- Conventional Commits. The OrientDB removal commit is a breaking change (`!` and/or a `BREAKING CHANGE:` footer) that lists the removed modules, so semantic-release puts them in the generated `CHANGELOG.md` (do not edit `CHANGELOG.md` by hand). Footer references `ING-4137`.
- The TinkerPop change and each group of sweep removals are separate commits (e.g. `fix(deps): ...` / `chore(deps): ...` / `build: ...`, the builder picks the type that fits).
- Work happens on the existing branch `stempler/remove-orientdb`.

## 9. Verification

- `./gradlew build` (compile, all tests) passes after each part.
- CLI transformation tests in `app.transform` (now only with the SQLite store) pass.
- `git grep -i orient` returns only legitimate hits (orientation-related words, `TASK_TYPE`, historic docs).

## 10. Left to the builder

- Order of the steps within each part, and how to split the sweep removals into commits.
- Tooling for the sweep.
- Exact wording of updated comments and test ids.
