# Documentation consolidation — October 12, 2026

## Scope and preservation

Documentation-only consolidation of the existing source revision. No gameplay, registrations, geometry, routing, procedural outputs, lifecycle or benchmark measurement code is changed. Original reports remain intact except historical banners and reference rebasing. Samples, generated references and the supplementary raster are moved without changing bytes. This report records the inventory and date evidence; the same-day filename describes this substantial cleanup, not a fabricated earlier report.

## Before / after

```text
Before
  README.txt, ASSET_PROVENANCE.txt, AI_PLACEHOLDER_ASSETS.md
  root implementation notes, operating guides and configuration samples
  docs/ mixed current specifications, generated charts and reports
  benchmarks/ operating README and dated results

After
  README.md, LICENSE, ATTRIBUTION.md, AGENTS.md
  docs/README.md
  docs/01-ARCHITECTURE.md ... 07-TESTING.md
  docs/design/FUTURE-FEATURES.md
  docs/examples/README.md + five JSON samples
  docs/generated/MIXER_CLEARANCE_GENERATED.md + MIXER_REFERENCE.svg/.png
  docs/history/README.md + 51 original records + this report
  benchmarks/README.md + unchanged dated result artifacts
```

## Original inventory, disposition and date evidence

The original inventory contains 59 tracked Markdown/text/SVG files and five root configuration samples and one supplementary PNG (65 documentation artifacts). Root and docs inventories, benchmark text/results and the retained tool/provenance README are included. There were no additional tracked tooling READMEs, standalone unimplemented proposals or duplicates requiring destructive deletion. Mixed historical design questions are retained and current unresolved work is classified separately.

| Original | Classification | Destination | Disposition / date evidence |
|---|---|---|---|
| `AGENTS.md` | Build-critical policy or provenance | [AGENTS.md](../../AGENTS.md) | Retained in place. Not moved. |
| `AI_PLACEHOLDER_ASSETS.md` | Build-critical policy or provenance | [docs/history/2026-10-12-ai-placeholder-retirement.md](2026-10-12-ai-placeholder-retirement.md) | Archived with current policy in docs/05-PROCEDURAL-ART.md. Explicit retirement date in ledger: October 12, 2026. |
| `ASSET_PROVENANCE.txt` | Build-critical policy or provenance | [docs/history/2026-10-03-asset-provenance.md](2026-10-03-asset-provenance.md) | Archived with current policy in docs/05-PROCEDURAL-ART.md. Earliest explicit report date; later appended findings retain their own dates. |
| `ATTRIBUTION.md` | Build-critical policy or provenance | [ATTRIBUTION.md](../../ATTRIBUTION.md) | Retained in place. Not moved. |
| `CASING_HARNESS_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-03-casing-harness-notes.md](2026-10-03-casing-harness-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `COW_PROCESS_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-03-cow-process-notes.md](2026-10-03-cow-process-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `CREATIVE_TEST_BLOCKS.txt` | Historical report or implementation record | [docs/history/2026-10-09-creative-test-blocks.md](2026-10-09-creative-test-blocks.md) | Archived; unique content retained. Git introduction author date 2026-10-09T14:37:09-05:00; report event date is not independently established. |
| `HANDOFF1_2_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-07-handoff1-2-notes.md](2026-10-07-handoff1-2-notes.md) | Archived; unique content retained. Git introduction author date 2026-10-07T13:55:29-05:00; report event date is not independently established. |
| `JADE_INTEGRATION.md` | Current authoritative or operational reference | [docs/history/2026-10-10-jade-integration.md](2026-10-10-jade-integration.md) | Consolidated into docs/01-ARCHITECTURE.md; original preserved in history. Git introduction author date 2026-10-10T14:39:21-05:00; report event date is not independently established. |
| `KILL_OUTPUT_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-04-kill-output-notes.md](2026-10-04-kill-output-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `MANUAL_TEST_WORLD.md` | Current authoritative or operational reference | [docs/history/2026-10-10-manual-test-world.md](2026-10-10-manual-test-world.md) | Consolidated into docs/07-TESTING.md; original preserved in history. Earliest explicit report date; later appended findings retain their own dates. |
| `MIXER_FE_DRIVE_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-09-mixer-fe-drive-notes.md](2026-10-09-mixer-fe-drive-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `MIXER_RECIPES_V2.md` | Current authoritative or operational reference | [docs/history/2026-10-10-mixer-recipes-v2.md](2026-10-10-mixer-recipes-v2.md) | Consolidated into docs/04-PROCESSING.md; original preserved in history. Git introduction author date 2026-10-10T14:04:59-05:00; report event date is not independently established. |
| `MIXER_VESSEL_RENDERING.md` | Historical report or implementation record | [docs/history/2026-10-10-mixer-vessel-rendering.md](2026-10-10-mixer-vessel-rendering.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `PORTS_TOPOLOGY_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-03-ports-topology-notes.md](2026-10-03-ports-topology-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `PRESENTATION_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-03-presentation-notes.md](2026-10-03-presentation-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `PROCEDURAL_CONDUIT_ART_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-11-procedural-conduit-art-notes.md](2026-10-11-procedural-conduit-art-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `PROCEDURAL_FLUID_BUFFER_ART_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-11-procedural-fluid-buffer-art-notes.md](2026-10-11-procedural-fluid-buffer-art-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `PROCEDURAL_FLUID_BUFFER_CORRECTION_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-11-procedural-fluid-buffer-correction-notes.md](2026-10-11-procedural-fluid-buffer-correction-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `README.txt` | Historical report or implementation record | [docs/history/2026-10-03-initial-readme.md](2026-10-03-initial-readme.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `RENDERING_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-03-rendering-notes.md](2026-10-03-rendering-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `RESOURCE_ROLES.md` | Current authoritative or operational reference | [docs/history/2026-10-09-resource-roles.md](2026-10-09-resource-roles.md) | Consolidated into docs/03-RESOURCE-ROUTING.md; original preserved in history. Git introduction author date 2026-10-09T16:52:26-05:00; report event date is not independently established. |
| `SLICE10_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-05-slice10-notes.md](2026-10-05-slice10-notes.md) | Archived; unique content retained. Git introduction author date 2026-10-05T07:58:19-05:00; report event date is not independently established. |
| `SLICE11_0_1_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-05-slice11-0-1-notes.md](2026-10-05-slice11-0-1-notes.md) | Archived; unique content retained. Git introduction author date 2026-10-05T12:27:20-05:00; report event date is not independently established. |
| `SLICE11_1_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-05-slice11-1-notes.md](2026-10-05-slice11-1-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `SLICE11_2_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-05-slice11-2-notes.md](2026-10-05-slice11-2-notes.md) | Archived; unique content retained. Git introduction author date 2026-10-05T13:18:58-05:00; report event date is not independently established. |
| `SLICE11_3_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-05-slice11-3-notes.md](2026-10-05-slice11-3-notes.md) | Archived; unique content retained. Git introduction author date 2026-10-05T15:00:31-05:00; report event date is not independently established. |
| `SLICE12_1_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-05-slice12-1-notes.md](2026-10-05-slice12-1-notes.md) | Archived; unique content retained. Git introduction author date 2026-10-05T22:11:46-05:00; report event date is not independently established. |
| `SLICE12_2_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-05-slice12-2-notes.md](2026-10-05-slice12-2-notes.md) | Archived; unique content retained. Git introduction author date 2026-10-05T23:39:07-05:00; report event date is not independently established. |
| `SLICE12_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-05-slice12-notes.md](2026-10-05-slice12-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `SLICE4_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-04-slice4-notes.md](2026-10-04-slice4-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `SLICE5_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-04-slice5-notes.md](2026-10-04-slice5-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `SLICE6A_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-04-slice6a-notes.md](2026-10-04-slice6a-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `SLICE6B_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-04-slice6b-notes.md](2026-10-04-slice6b-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `SLICE6C_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-04-slice6c-notes.md](2026-10-04-slice6c-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `SLICE7A_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-04-slice7a-notes.md](2026-10-04-slice7a-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `SLICE7B_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-04-slice7b-notes.md](2026-10-04-slice7b-notes.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `SLICE7C_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-04-slice7c-notes.md](2026-10-04-slice7c-notes.md) | Archived; unique content retained. Git introduction author date 2026-10-04T23:06:09-05:00; report event date is not independently established. |
| `SLICE8_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-05-slice8-notes.md](2026-10-05-slice8-notes.md) | Archived; unique content retained. Git introduction author date 2026-10-05T06:10:21-05:00; report event date is not independently established. |
| `SLICE9_NOTES.txt` | Historical report or implementation record | [docs/history/2026-10-05-slice9-notes.md](2026-10-05-slice9-notes.md) | Archived; unique content retained. Git introduction author date 2026-10-05T06:50:21-05:00; report event date is not independently established. |
| `TOOL_INTERACTIONS.txt` | Current authoritative or operational reference | [docs/history/2026-10-10-tool-interactions.md](2026-10-10-tool-interactions.md) | Consolidated into docs/04-PROCESSING.md; original preserved in history. Git introduction author date 2026-10-10T12:39:15-05:00; report event date is not independently established. |
| `UNIFIED_CLADDING_VALIDATION.md` | Historical report or implementation record | [docs/history/2026-10-12-unified-cladding-validation.md](2026-10-12-unified-cladding-validation.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `UNIFIED_MECHANICAL_CLADDING.md` | Current authoritative or operational reference | [docs/history/2026-10-12-unified-mechanical-cladding.md](2026-10-12-unified-mechanical-cladding.md) | Consolidated into docs/02-MULTIBLOCKS.md; original preserved in history. Git introduction author date 2026-10-12T11:54:42-05:00; report event date is not independently established. |
| `benchmarks/README.md` | Tool-specific operational documentation | [benchmarks/README.md](../../benchmarks/README.md) | Retained near benchmark runner. Not moved. |
| `benchmarks/results/2026-10-11/SUMMARY.md` | Historical report or implementation record | [benchmarks/results/2026-10-11/SUMMARY.md](../../benchmarks/results/2026-10-11/SUMMARY.md) | Retained benchmark evidence in place. Not moved. |
| `benchmarks/results/2026-10-11/accepted-runs.txt` | Historical report or implementation record | [benchmarks/results/2026-10-11/accepted-runs.txt](../../benchmarks/results/2026-10-11/accepted-runs.txt) | Retained benchmark evidence in place. Not moved. |
| `dev/ai-placeholder-art/AI_PLACEHOLDER_ASSETS_README.txt` | Build-critical policy or provenance | [dev/ai-placeholder-art/AI_PLACEHOLDER_ASSETS_README.txt](../../dev/ai-placeholder-art/AI_PLACEHOLDER_ASSETS_README.txt) | Retained in place. Not moved. |
| `docs/CREATIVE_ACCEPTANCE.md` | Historical report or implementation record | [docs/history/2026-10-11-creative-acceptance.md](2026-10-11-creative-acceptance.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `docs/MEDIUM_LIQUID_ACCEPTANCE.md` | Historical report or implementation record | [docs/history/2026-10-11-medium-liquid-acceptance.md](2026-10-11-medium-liquid-acceptance.md) | Archived; unique content retained. Git introduction author date 2026-10-11T15:15:15-05:00; report event date is not independently established. |
| `docs/MIXER_CAPACITY_VALIDATION.md` | Historical report or implementation record | [docs/history/2026-10-12-mixer-capacity-validation.md](2026-10-12-mixer-capacity-validation.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `docs/MIXER_CLEARANCE_GENERATED.md` | Current generated reference | [docs/generated/MIXER_CLEARANCE_GENERATED.md](../generated/MIXER_CLEARANCE_GENERATED.md) | Relocated unchanged; generator output filenames stay stable. Not moved. |
| `docs/MIXER_GEOMETRY.md` | Current authoritative or operational reference | [docs/history/2026-10-11-mixer-geometry.md](2026-10-11-mixer-geometry.md) | Consolidated into docs/02-MULTIBLOCKS.md; original preserved in history. Git introduction author date 2026-10-11T06:54:12-05:00; report event date is not independently established. |
| `docs/MIXER_REFERENCE.svg` | Current generated reference | [docs/generated/MIXER_REFERENCE.svg](../generated/MIXER_REFERENCE.svg) | Relocated unchanged; generator output filenames stay stable. Not moved. |
| `docs/PROCEDURAL_ART.md` | Historical report or implementation record | [docs/history/2026-10-11-procedural-art.md](2026-10-11-procedural-art.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `docs/PROCEDURAL_ART_VALIDATION.md` | Historical report or implementation record | [docs/history/2026-10-11-procedural-art-validation.md](2026-10-11-procedural-art-validation.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `docs/PROCEDURAL_FIXTURES.md` | Historical report or implementation record | [docs/history/2026-10-11-procedural-fixtures.md](2026-10-11-procedural-fixtures.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `docs/PROCEDURAL_MACHINERY.md` | Historical report or implementation record | [docs/history/2026-10-11-procedural-machinery.md](2026-10-11-procedural-machinery.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `docs/XR2_PERFORMANCE_BASELINE.md` | Historical report or implementation record | [docs/history/2026-10-11-xr2-performance-baseline.md](2026-10-11-xr2-performance-baseline.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `docs/ZEROCORE_LIFECYCLE_PARITY.md` | Historical report or implementation record | [docs/history/2026-10-11-zerocore-lifecycle-parity.md](2026-10-11-zerocore-lifecycle-parity.md) | Archived; unique content retained. Earliest explicit report date; later appended findings retain their own dates. |
| `CULTURE_PROFILES.example.json` | Current configuration example | [docs/examples/CULTURE_PROFILES.example.json](../examples/CULTURE_PROFILES.example.json) | Moved unchanged to documentation examples. Not a historical report. |
| `MIXER_RECIPES.example.json` | Current configuration example | [docs/examples/MIXER_RECIPES.example.json](../examples/MIXER_RECIPES.example.json) | Moved unchanged to documentation examples. Not a historical report. |
| `PASSIVE_DEFAULTS.example.json` | Current configuration example | [docs/examples/PASSIVE_DEFAULTS.example.json](../examples/PASSIVE_DEFAULTS.example.json) | Moved unchanged to documentation examples. Not a historical report. |
| `RESOURCE_PROFILES.example.json` | Current configuration example | [docs/examples/RESOURCE_PROFILES.example.json](../examples/RESOURCE_PROFILES.example.json) | Moved unchanged to documentation examples. Not a historical report. |
| `VANILLA_DEFAULTS.example.json` | Current configuration example | [docs/examples/VANILLA_DEFAULTS.example.json](../examples/VANILLA_DEFAULTS.example.json) | Moved unchanged to documentation examples. Not a historical report. |
| `docs/MIXER_REFERENCE.png` | Supplementary documentation image | [docs/generated/MIXER_REFERENCE.png](../generated/MIXER_REFERENCE.png) | Moved unchanged; existing raster preserved, SVG geometry remains canonical. |

## Consolidated current contracts and corrected claims

- Current numbered references replace the old root/current guidance. Detailed resource-role, recipe and Tool executor contracts are merged; original versions remain historical. No unique report is deleted as a duplicate.
- Mixer slots use floor((effectiveDiameter - 1)^2 * shaftLength / 8), not pixel/voxel deductions. Polygon clearance and six-pixel Shaft remain separate physical contracts; rectangular odd footprints use their smaller axis for nominal diameter.
- Mixer Drives are same-height cardinal neighbors of the Impeller. Creative, combustion and FE operation/startup are implemented; old above-Impeller/no-motor notes are historical.
- Multiple Head specimens, culture exposure/happiness and clean/dirty fouling are implemented. One-specimen limits and paid-culture-credit instructions are superseded. Exact XP fluid handling uses millibuckets, preserving tails.
- Universal functions and explicitly applied T1 materials are implemented. Removed mechanical/direction-only registrations are not current IDs; family adapter entity types remain. T2/T3 gameplay is proposed, though palettes exist.
- Current art is production-bound authored generation; prototype-only instructions, old resource counts and remaining-AI warnings are historical. Environmental Goat/Sniffer/Armadillo adapters are implemented.
- Manual client classpath prose now reflects manualClient and compile-only Jade. Build setup is rooted in the checkout and actual Gradle task names.

## Reference and provenance repairs

All three ledger task/public-action manifest inputs and the missing-ledger diagnostic now reference docs/history/2026-10-12-ai-placeholder-retirement.md. The parser sentinel, table, original eight SHA-256 fingerprints and rejection behavior are preserved. AGENTS.md points to the relocated ledger and current README. ATTRIBUTION.md points to routing; its upstream copyright/license notice is unchanged. A root LICENSE records existing All Rights Reserved metadata without granting new rights.

Document links, generated chart/SVG references, historical cross-references and sample paths are rebased. Generated Java chart producers keep their output names; Testing documents the copy destinations. No stable compatibility copies are introduced. Source comments reference only retained ATTRIBUTION.md; no gameplay source path repair was needed. Benchmark operating README drops its obsolete workstation path, while raw measurements/runners remain unchanged. Historical absolute paths and filenames identifying removed assets/logs are evidence, not current runnable instructions.

## Validation

Final checks completed October 12, 2026. Gradle commands ran from the mod repository using the included Windows wrapper. Expanded execution permissions were needed because the sandbox denied Java loopback connections and the default user-cache lock. No repository build behavior or guard was disabled. Preliminary attempts with a writable workspace cache encountered daemon/local socket restrictions and JVM configuration-cache access; the final ordinary wrapper commands succeeded without those temporary workaround flags.

| Command / check | Result |
|---|---|
| .\gradlew.bat build | PASS, 21 seconds; 17 tasks (11 executed, 6 up-to-date) |
| .\gradlew.bat testAiPlaceholderGuard | PASS; standalone task up-to-date from the fresh successful build regression |
| .\gradlew.bat verifyNoAiPlaceholderArt publicReleaseValidation | PASS; real ledger/resources/artifacts validated and public-action revalidation completed, no publication |
| git diff --check | PASS |
| Saved documentation consistency checker | PASS; 249 local links, 2 explicit heading anchors, 54 numbered reference sections, all 65 inventory destinations, whitespace and five JSON samples |
| Sample/generated document preservation | PASS; all five sample Git-filtered blob hashes match their original root blobs; generated clearance Markdown/SVG and the supplementary PNG retain their original content |
| Guard relocation / fingerprint preservation | PASS; all three manifest/public-action paths relocated, required sentinel intact, all eight original hashes retained |
| Scope comparison | PASS; no diff in gameplay source, source resources, generators, benchmark runners or benchmark results |

Build verification executed guard rejection fixtures for active rows, retained source PNG, renamed fingerprint, dangling reference, malformed manifest and clean historical ledger. The relocated file is the task input used by the regression and both real/public-action validators. Missing-ledger rejection remains in unchanged validator logic (the diagnostic path alone changed). No active warning copy remains at the root.

Existing verifyProceduralArtResources verified **228 generated PNGs and provenance** in the JAR; verifyAiPlaceholderDevResources verified machinery/blockstate/inventory/formed-rotor bindings, placeholder absence and unchanged base PNGs; verifyRenderResources verified model/atlas references and seven protected hashes; verifyDevelopmentHooksExcluded verified development classes remain excluded. Existing testProceduralArt was up-to-date for unchanged art sources. CPU buffer ordering, Mixer Shaft and conduit/fixture/model regressions passed. The ordinary test task has no test sources; CPU checks are explicit tasks attached to check.

Nonfatal Log4j terminal/rotating-log diagnostics appeared during CPU rendering tasks; their assertions and build passed. No GameTest server, acceptance-world generation, client, publication or commit was run. Ignored historical log URLs were converted to annotated artifact names so absent local logs do not produce broken repository links.

Local execution evidence is saved under sibling workspace/.cache/doc-cleanup/: build.log, guard-regression.log, release-validation.log, tasks.log, check-docs.cjs and doc-check-results.json. These are ignored local artifacts, not required repository files. The final documentation task-name audit used Gradle tasks --all as well as the actual build/verification tasks.

## Remaining uncertainties

Git introduction dates identify when a report entered the repository, not an independently proven event time. Appended reports may span several dates; no time ordering was invented. Historical logs/saves can be absent or ignored. The existing raster reference has no separately documented generation command; canonical SVG/Java geometry takes precedence. Client visual acceptance is manual and was not run. Existing configuration samples illustrate schemas and historical defaults, so current bundled values/parser validation take precedence. Recovered drawing authorship is not independently proved by hash identity. Public distribution is not authorized by validation.
