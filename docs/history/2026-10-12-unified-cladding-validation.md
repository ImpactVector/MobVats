> Historical record. Original: `UNIFIED_CLADDING_VALIDATION.md`. Current reference: [07-TESTING.md](../07-TESTING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Unified cladding validation — October 12, 2026

Audited clean `main` at `45a2737ca87f8f7486723ed9e742e35035dac83c`, including the initial working tree. Implementation remains uncommitted on `main`. No client was launched, and nothing was published, released or deployed.

The implementation report, registry counts, state contract, interactions, drops, structural changes, rendering, direct registry removal and remaining tier scope are in [2026-10-12-unified-mechanical-cladding.md](2026-10-12-unified-mechanical-cladding.md).

## Command results

Commands ran from the current repository at `D:\backup\dev\git\MobVats\src`, using Java 21 and the available Gradle cache. Offline mode was used successfully for all tasks.

| Validation | Result | Evidence |
|---|---|---|
| `gradlew proceduralArtPreview --offline` | PASS | .cache/build-verified.log (`../../.cache/build-verified.log`, historical ignored artifact) |
| `gradlew build` | PASS; packaged resources, procedural contracts, CPU geometry/render ownership and development-hook exclusion pass | .cache/build-verified.log (`../../.cache/build-verified.log`, historical ignored artifact) |
| `gradlew runGameTestServer` | 765 tests completed; 750 passed, 15 failed; zero failures introduced by consolidation | .cache/gametest-verified.log (`../../.cache/gametest-verified.log`, historical ignored artifact) |
| Untouched HEAD GameTests | 756 completed; 741 passed, the same 15 failed | .cache/baseline-tests.log (`../../.cache/baseline-tests.log`, historical ignored artifact) |
| `gradlew buildTestWorld -PworldName=MobVatsAcceptance` | PASS; regenerated and verified standard, operator, production and creative fixtures | .cache/world-acceptance.log (`../../.cache/world-acceptance.log`, historical ignored artifact) |
| `gradlew buildTestWorld -PworldName=MobVatsMinimumXp -PacceptanceMinimumCowXp=true` | PASS, including extended minimum-XP P-06 production/growth acceptance | .cache/world-minimum-xp.log (`../../.cache/world-minimum-xp.log`, historical ignored artifact) |
| Saved-world material, mode and obsolete-ID audit | PASS for both worlds; 1,440 chunks per world; no obsolete palette or inventory IDs; all five material states present | .cache/world-palettes.json (`../../.cache/world-palettes.json`, historical ignored artifact) |
| Packaged obsolete-resource audit | PASS; no obsolete blockstates, inventory models, recipes, loot or unused fixture aliases | .cache/package-audit.json (`../../.cache/package-audit.json`, historical ignored artifact) |
| `gradlew verifyNoAiPlaceholderArt publicReleaseValidation` | PASS; existing rejection-path safeguard regression also passes; validation performed without distribution | .cache/build-release-guard.log (`../../.cache/build-release-guard.log`, historical ignored artifact) |
| Connected-pair lifecycle benchmark, one pair / one repetition / 20 warmup / 20 measure ticks / no profiler | PASS: assembly, idle, activation, disassembly/repair, actual chunk unload/reload and shutdown | .cache/benchmark-unified.log (`../../.cache/benchmark-unified.log`, historical ignored artifact) |
| `git diff --check` | PASS | Run after final code and documentation changes |

The benchmark fixture uses explicit installed material and unchanged geometry/TAP mode. Its single run validates infrastructure and lifecycle behavior; it is not a statistical performance comparison or a new XR2 timing baseline.

Both generated acceptance reports record conserved resources, independent formed structures, a produced 250 mB-XP culture batch qualifying for enrichment, returned culture reaching the Vat medium, and actual enriched juvenile growth. The minimum-XP scenario used its existing extended settling path, produced/transferred 320 mB XP and produced/returned/applied 1,000 mB culture. Existing fixture geometry was retained.

Runtime registry assertions verified **40 MobVats blocks and 44 items**, versus 60 blocks and 72 items in the audited original registry loops. All nine new applied-cladding GameTests passed. Structural family separation, topology, resource transfer, buffer joining, physical clearance, ownership and chunk lifecycle regressions passed.

## Reproduced baseline failures

These fail both untouched HEAD and the final implementation. Their full diagnostics are in the paired logs above. They concern historical Mixer recipe/XP expectations; the bundled culture recipe currently requires 250 mB XP, while several old tests supply five XP points (100 mB at the default ratio). They were not hidden, disabled or reclassified as optional, and Mixer gameplay/configuration was not changed to make them pass.

- `consolidatedmixerportsacceptrecipeandexportafterdirectionchange`
- `cookcycleconsumesoneeighthcoal`
- `creativedriveprovidesfulldriveonlyonformedimpeller`
- `cultureatomicinputsblockedoutputandpersistence`
- `exhaustionrestartandfullspeedrecipe`
- `formedbreakandrepairswitchclickdiagnostics`
- `formedmixerbucketsbothslurrieswithoutoutputbuffers`
- `mixercadenceandprogresspersistence`
- `mixercultureconsumesexperiencefluid`
- `mixerdisableretainsstateandresumes`
- `mixerfluidmodesneighborsandbreakrepair`
- `mixerformsandwaterbucketxpinteractions`
- `mixerrecipesretainindependentserviceandpersistatowner`
- `recipeprogresssurvivesexhaustionandrestart`
- `standardcapabilitiesfeedmixerbusanddisposeoutputs`

## Remaining concerns

- The full GameTest task remains red because of those 15 reproduced baseline failures. All added cladding tests and all modified structural/resource-preservation tests pass.
- Client visual acceptance remains manual under the workspace instructions. No claim is made that rendered pixels were inspected in Minecraft.
- Only registered T1 materials are implemented. Unsupported T2/T3 gameplay and mixed-tier tests were not invented; future structural tier validation must accompany actual new structural materials.
- Existing worlds using removed IDs are intentionally unsupported. No migration or compatibility infrastructure is present. Both disposable acceptance worlds were regenerated.
- Family-specific functional entity adapters remain. Their identifiers do not represent legacy block/item compatibility entries and do not multiply when structural tiers are added.

Generated worlds, logs, benchmark output, the untouched HEAD test snapshot and intermediate build output are ignored development artifacts. The authored source and documentation belong to the mod repository.
