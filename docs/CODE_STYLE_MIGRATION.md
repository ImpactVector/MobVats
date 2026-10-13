# Repository-wide Java formatting validation — October 13, 2026

Formatting adoption and full build/preview validation are complete. Production
Java changes remain mechanical. The user separately authorized removing
Java-source-text assertions from one CPU test; runtime checks remain.

## Baseline and scope

Repository: `ImpactVector/MobVats`, local `C:/Projects/MobVats/src`.
Pre-format branch: `main`. Pre-format commit:
`d91425eb02b5be5cf9d7fe1acb4177b9e96d8c88`. The initial working tree was clean.
The outer workspace is not a Git repository. The sibling portable workspace
checkout/instructions remain unavailable; repository/user testing restrictions
were retained. No existing uncommitted work needed preservation.

Spotless 8.10.4 / Palantir Java Format 2.102.0, `PALANTIR` style, Java 21,
UTF-8/LF and disabled Javadoc reformatting are unchanged. Gradle 9.2.1,
Minecraft/NeoForge/ZeroCore and all other dependency versions are unchanged.
The seven-file historical manifest remains at `gradle/spotless-trial.txt`; it no
longer controls Gradle. Six trial files retain their original hashes. The seventh,
`ConduitArtGeometryTest`, has the separately authorized test-only deletion.

**315 Java files were formatted; one additional trial CPU test was adjusted
with explicit user authorization. All 322 authored Java files are enrolled.** The
tracked-Java inventory exactly matches the configured positive targets. Main
contains 237 production and 71 development/GameTest files; test contains seven
CPU files; the independent benchmark main contains six; the build-only exporter
adds one. `manualClient` has no authored Java. The changed counts are 232
production, 70 main development/GameTest, seven CPU tests (six formatted and
one test-only adjustment), six benchmarks and one exporter. No permanent exceptions were added.

## Batches and checkpoints

Exact paths are in [CODE_STYLE_BATCHES.txt](CODE_STYLE_BATCHES.txt). Each batch
expanded the temporary manifest before applying the pinned formatter. Checks
and compilation ran after apply in a separate Gradle invocation so they saw
the formatted source. Each batch passed lexical review and `git diff --check`.

| Batch | Newly formatted files | Post-apply checks |
| --- | ---: | --- |
| 1: shared types, registrations, fluid/item and conduit/resource infrastructure | 62 | Spotless, main compile and conduit/art geometry CPU checks passed |
| 2: Vat/Mixer process engines and definitions | 52 | Spotless and main compile passed |
| 3: controllers, entities, parts and common machine infrastructure | 90 | Spotless, main compile, buffer ordering, shaft and conduit geometry CPU checks passed |
| 4: clients, rendering, presentation and Jade integration | 28 | Spotless, main compile, buffer ordering and shaft CPU checks passed; conduit CPU test failed on an exact source-whitespace assertion |
| 5: GameTests, acceptance builders, CPU tests, benchmarks and exporter | 83 | Spotless and main compile passed; same conduit CPU assertion blocked the aggregate checkpoint. Independent benchmark compilation subsequently passed |

Some concurrent CPU processes logged Windows log-rotation/file-lock warnings;
their buffer/shaft assertions passed. Serial validation reproduced the source
assertion failure, so it is independent of those log warnings. Existing compiler
deprecation warnings remain; no API changes were made.

## Mechanical equivalence

A lexical comparison against the recorded commit checked all 322 Java files.
After accounting explicitly for the authorized test-only deletion below,
it preserved every non-import code token and every string, character, numeric
and text-block literal, plus ordered comment text allowing formatter whitespace
and wrapping of adjacent line comments. This covers annotations, signatures,
registrations, serialized/NBT identifiers, resource locations, lambda captures,
switch/record syntax, operators and evaluation order. No production code changes or
unexplained token differences remain.

Palantir inherently reordered imports and removed 28 unused/redundant imports.
It added no imports and expanded no wildcards. These are the only non-layout
source-token adjustments. Representative diff review included resource routing
and storage, Mixer planning, Vat scheduling, controller lifecycle, renderer
caches, GameTest discovery and the compiled geometry exporter. No methods,
constants, schemas, fixtures or responsibilities were refactored.

## Full enforcement and idempotence

The temporary scope/property/apply guard was removed. `gradle/spotless.gradle`
now targets all authored Java with `enforceCheck = true` and an explicit
`check -> spotlessCheck` dependency. Build/check task graphs contain the check
and no apply task. A deliberate whitespace violation in `MobVats.java` made
both `spotlessCheck` and actual Gradle `check --continue` fail through
`spotlessJavaCheck`, with its path and formatting diff. The exact original bytes
were restored, and `spotlessCheck` passed again.

A forced `spotlessApply --rerun-tasks --offline` executed the formatter and
changed **zero hashes across all 322 Java files**. No build task rewrites Java.
Generated directories within Java roots are excluded; downloaded references,
external dependencies, build outputs, caches and generated resources remain
outside the positive roots. See [CODE_STYLE.md](CODE_STYLE.md) for commands and
future source-root/benchmark policy.

## Final validation results

| Check | Result |
| --- | --- |
| Full `spotlessCheck` | Passed |
| `compileJava` | Passed after every batch |
| Independent benchmark `compileJava` | Passed; no benchmark server launched |
| `build` | Passed after the authorized source-text assertion removal; full CPU/art/packaging suite passed |
| `proceduralArtPreview --offline` | Passed after the test-only adjustment |
| `runGameTestServer --offline` | Passed: all 766 required tests discovered and passed |
| `compareProceduralArtBaseline` | Passed: 233/233 PNGs and 453/453 model/resource files byte-identical; identical output path set |
| `testProceduralArt` | Passed: deterministic generation, geometry/color/resource and JSON-input contracts |
| `verifyRenderResources` | Passed: packaged casing materials, atlas/model references and preserved glass hashes |
| `verifyDevelopmentHooksExcluded` | Passed: development GameTest, acceptance and smoke hooks absent from player JAR |
| `verifyProceduralArtResources` | Passed: 233 procedural PNGs and provenance verified in the development JAR |
| Forced `testAiPlaceholderGuard` | Passed |
| `verifyNoAiPlaceholderArt publicReleaseValidation` | Passed: no active placeholder art, shipped references or fingerprints; validation only |
| `git diff --check` | Passed |

Art regeneration ran using compiled gameplay classes and the formatted Java
exporter. An independent pre/post SHA-256 snapshot covered 923 generated
outputs, production resources, inputs and geometry-descriptor files. The only
changed file is the ignored generated provenance manifest. Its sole changed
field is `geometryExporterSha256`:

- Before: `1a65a344e9b14765f1fa6674ce5dbc2f5060a0f18f451476f6a90cd61fbfd50b`
- After: `94a384092351d6bd1cb045a6a33556121960672aae43987908d9c97f19144c5f`

Substituting that field's old value in memory reproduces the entire original
manifest SHA-256, proving all other provenance bytes and resource bindings are
identical. Exported geometry, all art inputs, historical provenance, production
resources and golden baselines remain unchanged. No generated PNG/model JSON
was manually edited and no golden hash was updated. The request for an entirely
byte-identical provenance manifest cannot hold while its exporter source hash
truthfully records the authorized formatting change; this metadata difference
is reported explicitly rather than hidden.

## Resolved test compatibility issue and excluded work

`src/test/java/net/impactvector/mobvats/render/ConduitArtGeometryTest.java:249`
checks five snippets read directly from `VatMechanismRenderer.java` against
literal whitespace. Formatter spacing/wrapping makes the assertion fail with
`Mechanical item transform changed`. The renderer's actual code-token sequence
matches the baseline. The test was already one of the seven formatted trial files.

The user explicitly authorized fixing the test and stated that tests should
not check exact Java code. The source-file read and both source-snippet assertions
were therefore removed entirely, rather than made whitespace-tolerant. The
substantive runtime geometry, vise mounting, immutable-scale, restored assignment,
mesh and model-rendering checks remain. This is the sole authorized non-mechanical
Java adjustment, isolated to the CPU test; no production workaround, formatter
exclusion or fixture change was introduced. The subsequent full build and preview
passed. Single-submit/item-transform coverage is no longer inferred from source
text; a future renderer-specific behavior test belongs in a separate slice.

Historical acceptance-world failures remain unresolved and separate from the
passing 766-GameTest suite. No acceptance generation or client launch occurred.
Structural maintainability findings remain open. No additional analyzer,
dependency upgrade, gameplay change, art change, commit, push, publication,
release or deployment was performed.

## Pre-commit working-tree snapshot and evidence

At the end of validation, HEAD and branch remained the recorded `main` revision.
All changes were unstaged and uncommitted. The source/configuration/documentation diff contained the 315
formatted Java files and the one authorized CPU-test adjustment, `AGENTS.md`, `gradle/spotless.gradle`, `CODE_STYLE.md` and
`CODE_MAINTAINABILITY_AUDIT.md`; this report and the exact batch inventory are
new untracked documentation files. Exact status: 320 modified tracked files,
two untracked documentation files, zero staged files; 322 working-tree entries.
No tracked resource or golden changes exist.
Commit/merge guidance is in `CODE_STYLE.md`. The user subsequently authorized
committing and pushing the completed migration to `main`.

Ignored local evidence is under `build/reports/format-migration/`: `baseline.json`,
exact batch lists, per-batch apply/check logs and equivalence records,
`equivalence.json`, `first-pass.json`, `idempotence.log`, formatter violation and
task-graph logs, full build/preview logs, `benchmark-compile.log`,
`art-independent.log`, `art-comparison.json`, `safeguards.log`, and `gametest.log`.
The follow-up validation is recorded in `test-fix-apply.log`,
`test-fix-validation.log` and `test-fix-idempotence.log`. GameTest results remain
from the preceding run against the same final production source; only the CPU
test changed afterward. Temporary validation scripts remain under ignored `build/tmp`; they are not
packaged or part of the repository diff.
