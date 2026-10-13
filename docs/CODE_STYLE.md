# Java formatting

Repository-wide adoption completed October 13, 2026: all **322 authored Java
files** follow Spotless **8.10.4** with Palantir Java Format **2.102.0**, `PALANTIR`
style and Javadoc reformatting disabled. Versions remain pinned in `build.gradle`
and `gradle/spotless.gradle`. Use Java 21 to run the Gradle 9.2.1 wrapper.
Minecraft 1.21.1, NeoForge 21.1.252, ModDev 2.0.148 and ZeroCore2 2.4.18 are
unchanged. Full build and procedural-preview validation passed after the
user-authorized removal of source-text assertions from one CPU test.

## Commands and enforcement

From the source repository:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
.\gradlew.bat build
.\gradlew.bat proceduralArtPreview compareProceduralArtBaseline --offline
.\gradlew.bat runGameTestServer
git diff --check
```

`spotlessApply` is an explicit developer action. `check` depends on
`spotlessCheck`, so `build` enforces formatting and never applies it. Failures
identify offending paths and show the required formatting diff. A deliberate
whitespace violation was rejected; restoring the original bytes passed. Task
graphs confirm both `check` and `build` include the formatter check, with no
apply task. The former `spotlessScope` property and full-apply guard are removed.

## Coverage and exclusions

The positive target roots are `src/**/*.java`, `benchmarks/src/**/*.java`, and
`tools/procedural-art/GeometryExport.java`. They match all 322 tracked Java files:

| Authored source group | Files |
| --- | ---: |
| Main production, including client and integrations | 237 |
| Main development GameTests and acceptance builders | 71 |
| CPU tests (`src/test/java`) | 7 |
| Independent benchmark main (`benchmarks/src/main/java`) | 6 |
| Custom JavaCompile geometry exporter | 1 |
| `manualClient` | 0 currently |

Future authored source sets under `src` are covered. A new authored source root
outside these positive paths requires a configuration update. The root wrapper
checks benchmark formatting; the independent benchmark build does not add its
own Spotless plugin. Both main and benchmark compilation passed.

Directories named `generated` inside the target roots are excluded. Build output
and generated Java (`build/generated`, `build/tmp`, `build/moddev`), caches
(`.gradle`, `.cache`, `.codex`), downloaded XR2/ZeroCore references, `examples`,
external dependencies, generated resources and JSON artwork are outside the
positive Java targets. There are no legacy-source or file-specific exclusions.
The seven-file trial manifest remains only as historical evidence; Gradle no
longer reads it. `CODE_STYLE_REMAINING.txt` records the completed historical
315-file inventory and is not an exclusion list.

## Mechanical policy

Palantir controls four-space indentation, its 120-column wrapping policy,
statement layout, whitespace, existing braces and lambda formatting. It can
wrap line comments; long indivisible strings/comments can exceed the preferred
width. Do not manually add missing braces or refactor during formatting work.
Sources use UTF-8 and LF. Javadoc reformatting remains disabled.

The pinned Spotless integration inherently sorts imports and removes unused
imports. This migration removed 28 unused/redundant imports, added no imports
and expanded no wildcards. No extra import-order, cleanup, fully qualified type
shortening or static-analysis steps are configured. A broader import policy
requires a separate change.

Preserve literal values, annotations, method signatures, NBT/schema identifiers,
resource locations, exception handling, evaluation order, gameplay and renderer
behavior. For Java edits, apply deliberately, inspect the patch, check, then
compile/test. Never use formatter output as an opportunity for unrelated cleanup.

## Migration evidence and validation

Baseline: `d91425eb02b5be5cf9d7fe1acb4177b9e96d8c88`, clean `main`.
The remaining 315 files were formatted in five package batches: 62 shared/conduit,
52 process, 90 controllers/parts, 28 client/presentation, 83 development/tool files.
See [CODE_STYLE_BATCHES.txt](CODE_STYLE_BATCHES.txt) for exact membership and
[CODE_STYLE_MIGRATION.md](CODE_STYLE_MIGRATION.md) for validation and limitations.
The original installation evidence remains in
[CODE_STYLE_VALIDATION.md](CODE_STYLE_VALIDATION.md).

All non-import code tokens, literals and normalized comment text match the
baseline in 321 files. The remaining CPU test has only the separately authorized
removal of Java-source-text assertions. A forced second apply left all 322 hashes unchanged.
All 766 required GameTests passed. Production resources, art inputs and exported
geometry are unchanged. The generated provenance manifest necessarily updates
only `geometryExporterSha256` to record the formatted exporter source; all
other provenance bytes are identical. Golden hashes were not changed.

The initial build/preview failure came from `ConduitArtGeometryTest` matching
exact renderer-source snippets. The user authorized removing those assertions
entirely: tests should verify behavior rather than Java source text. Runtime
geometry, tool mounting, mesh, persistence and model-rendering assertions remain.
Full `spotlessCheck build proceduralArtPreview compareProceduralArtBaseline`
subsequently passed, including procedural resource/provenance packaging checks.
There are no formatting or build exceptions.

Historical acceptance-world failures remain unresolved. Acceptance generation,
client launches, publication, dependency upgrades and semantic refactoring were
not performed. Formatting does not resolve the audit's architectural findings.

## Reviewing and merging the baseline

Keep formatting changes separate from behavior changes. Compare against the
recorded baseline and review by the documented package batches; whitespace-hidden
diffs help inspect imports but do not replace literal/token verification.
Do not commit or push without authorization. If authorized later, prefer
separate mechanical batch commits and a tooling/documentation commit; the authorized test
compatibility fix should have its own explicit diff/commit. Coordinate rebases
with other branches before merging the large layout change. Add a committed
formatting revision to blame-ignore configuration only after that revision exists.
