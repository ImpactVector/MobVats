# Spotless installation validation — October 13, 2026

## Starting state and workspace inventory

Repository: `C:/Projects/MobVats/src`, remote
`https://github.com/ImpactVector/MobVats.git`, branch `main`, HEAD
`6bc79442f489637689e7eee57d57c52bd1f1f472`. Initial working tree was clean;
there was no outstanding uncommitted source work. The outer
`C:/Projects/MobVats` directory is not a Git repository. No commit, push, tag,
publication, deployment, repository clone or relocation was performed.

| Workspace item | Current observation |
| --- | --- |
| MobVats source | Present in `src`; JSON art refactor, immutable pre-extraction hashes and compiled geometry exporter confirmed |
| ZeroCore2 source | 27 selected Java references plus metadata under `../.codex/references/zerocore`; reference metadata is 2.4.17, not a complete exact-runtime checkout |
| Extreme Reactors 2 source | 15 selected Java references plus metadata under `../.codex/references/reactors`; Minecraft 1.21.1 / XR2 2.4.27 references, not a complete checkout |
| Portable workspace instructions | Sibling `../workspace/AGENTS.md` and checkout absent; README and current repository/user testing restrictions retained |
| Local dependency artifacts | Published ZeroCore2 1.21.1-2.4.18 JAR cached; NeoForge/Minecraft artifacts and source JARs cached; current local MobVats JAR in `build/libs`; no source composite dependency builds declared |
| Artwork inputs/tools | `tools/procedural-art/*.json`, `GeometryExport.java` and `gradle/procedural-art.gradle` present |
| Benchmarks/reports | Separate `benchmarks` project, six authored Java files, runners/scenarios and dated results including accepted runs, raw data and JFR summaries present; existing procedural-art reports/logs preserved |
| Worlds/generation artifacts | `run/saves/MobVatsDev`, `MobVatsTest`, `New World`; `run-world-generator`, reports and untouched-revision archive in `build/tmp/acceptance-baseline` present; presence does not certify successful acceptance |
| Development tools/cache | Wrapper Gradle 9.2.1 and cache at `../.codex/gradle`; cached Temurin 21.0.12.1+1; PATH launcher Java 17; source toolchain explicitly Java 21; no global Gradle required |

This inventory establishes local availability only; it does not claim a complete
Codex-server workspace migration. Missing complete XR2/ZeroCore source did not
block the normal dependency-based build.

## Tooling and trial

Pinned Spotless 8.10.4 and Palantir Java Format 2.102.0 were resolved and tested
with the actual Gradle 9.2.1 / Java 21 / ModDev 2.0.148 project. Final checks used
the cached Java 21 installation for the Gradle runtime. A temporary standalone
project under `build/tmp/spotless-java21-probe` additionally formatted and compiled
record patterns, a `when`-guarded switch, `case null` and a compact record
constructor. Its initial isolated repository/cache setup errors were corrected;
the final probe passed. The probe is ignored build output and never packaged.

Exactly seven Java files from [spotless-trial.txt](../gradle/spotless-trial.txt)
were formatted. No source outside that enrollment changed. The patch was reviewed
for expanded statements/control flow, compact constructors, switch expressions,
generic types, annotations, capability declarations, lambdas/method references,
comments and Javadocs. Long API expressions now wrap consistently. Missing braces
were not added or behavior refactored.

A lexical comparison against HEAD preserved all non-import code tokens, including
strings/operators and evaluation order, in all seven files. All comment text
survived, allowing only whitespace changes. Palantir reordered imports and
removed one redundant same-package wildcard import from `FluidBufferEntity`;
there were no added imports or wildcard expansions. UTF-8 decoding was checked
strictly, and all seven files use LF without a BOM.

The second `spotlessApply --rerun-tasks --offline` actually executed both
formatter tasks. SHA-256 comparisons with the first pass found **zero further
changes in all seven files**. This was not an up-to-date-only idempotence claim.

## Current validation

All commands below ran from the source repository with the existing local cache.
Baseline validation completed before any Java formatting or plugin changes.

| Check | Current result |
| --- | --- |
| Baseline `build proceduralArtPreview compareProceduralArtBaseline runGameTestServer --offline` | Passed; all 766 required GameTests passed |
| Baseline `git diff --check` | Passed |
| Trial `spotlessApply`, forced second apply | Passed; all seven source hashes stable on second pass |
| Final scoped `spotlessCheck` | Passed; explicitly executed again with `--rerun-tasks` |
| Final `build` | Passed, including existing CPU geometry/buffer checks, art tests, packaging, resource ownership and development-hook exclusion |
| Final `proceduralArtPreview --offline` | Passed after formatted gameplay compilation and geometry export |
| Final `compareProceduralArtBaseline --offline` | 233/233 PNGs and 453/453 model/resource files byte-identical; exact baseline path set retained |
| Final `runGameTestServer --offline` | All 766 required GameTests discovered and passed; same discovery count as baseline |
| Forced `testAiPlaceholderGuard --rerun-tasks --offline` | Passed: active manifest, retained source, renamed PNG fingerprint, dangling reference, malformed manifest rejected; clean historical ledger accepted |
| `verifyNoAiPlaceholderArt publicReleaseValidation --offline` | Passed on retired-placeholder ledger; validation only, no publication |
| `git diff --check` and final patch review | Passed |
| Full `spotlessCheck -PspotlessScope=all --offline` | Expected failure: all 315 unenrolled authored Java files have formatting violations; no parse/formatter errors or source rewrites |

The final configuration was checked again with `--warning-mode all` after
replacing the deprecated Groovy LF property setter syntax with assignment.
That final build/art/guard/check run passed. Java 21 / Gradle 9.2.1 is certified
by these runs; Gradle 10 and other runtimes have not been validated.

## Art independence and preserved evidence

The generator consumes the compiled geometry exporter, not Java source text.
`ConduitGeometry.DIAMETER = 6.0 / 16` remains a six-model-pixel diameter;
the exported Buffer width remains ten model pixels. CPU geometry tests compare
the descriptor schema/units and dimensions with runtime values.

An independent pre/post SHA-256 snapshot verified **703 unchanged files** across
generated procedural resources, the exported geometry directory, artwork inputs
and existing procedural-art report/log files. The generated output remains
687 files: 233 PNGs, 453 model/resource files and the provenance manifest.
The provenance manifest itself is byte-identical in this formatting slice,
including input hashes, geometry contract and resource bindings. The immutable
pre-extraction baseline and all golden fingerprints remain unchanged.

The two historical acceptance-world failures in
[PROCEDURAL_ART_VALIDATION.md](PROCEDURAL_ART_VALIDATION.md) remain unresolved.
The initial O-09 brush failure, retry culture-consumption failure, logs and
untouched-revision comparison archive were preserved. No acceptance-world run,
fixture change, gameplay change, graphical client, mouse/keyboard interaction or
client smoke/reload task was performed. These failures are not attributed to
Spotless and are not counted as resolved by the passing GameTests.

## Adoption and exact remaining scope

Normal build/check enforcement covers only the seven enrolled trial files.
The eventual positive target roots cover 322 authored Java files: 308 main,
seven CPU test, six benchmark and one build-only geometry exporter. Custom
`manualClient` has no authored Java currently. Generated sources, cache/reference
trees and JSON resources are excluded by the documented positive roots and
generated-directory exclusion.

The **315 remaining files**, all currently violating full formatting, are listed
exactly in [CODE_STYLE_REMAINING.txt](CODE_STYLE_REMAINING.txt). No mass pass was
applied. [CODE_STYLE.md](CODE_STYLE.md) describes enrollment, the six later batch
groups, mechanical-only review and the transition to full mandatory enforcement.
Import expansion and semantic refactoring remain separate future work.

Local ignored evidence: `build/reports/spotless-baseline.log`,
`spotless-apply-first.log`, `spotless-apply-second.log`,
`spotless-first-pass-hashes.json`, `spotless-before-hashes.json`,
`spotless-mechanical-equivalence.json`, `spotless-final-validation.log`,
`spotless-scoped-guard.log`, `spotless-final-config.log`,
`spotless-full-diagnostic.log`, `spotless-full-violations.txt` and
`spotless-java21-probe.log`. These supplement the durable results in this report.
