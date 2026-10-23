# 07 — Testing

## 7.1 Automated checks

```powershell
.\gradlew.bat build
.\gradlew.bat testAiPlaceholderGuard
.\gradlew.bat verifyNoAiPlaceholderArt publicReleaseValidation
git diff --check
```

Build `check` includes art/provenance/resource guards, CPU buffer ordering, Mixer geometry and development-hook exclusion. `testAiPlaceholderGuard` uses isolated fixtures for active ledger rows, retained sources, renamed PNG fingerprints, dangling references, malformed ledgers and a clean historical ledger. The public guard also refuses a missing ledger. See [release-art policy](05-PROCEDURAL-ART.md#54-asset-origin-and-release-policy).

## 7.2 Headless integration and acceptance

```powershell
.\gradlew.bat runGameTestServer
.\gradlew.bat buildTestWorld -PworldName=MobVatsAcceptance -PworldAcceptance=true
.\gradlew.bat buildTestWorld -PworldName=MobVatsMinimumXp -PworldAcceptance=true -PacceptanceMinimumCowXp=true
```

GameTests run in `.cache/gametest`, separated from operator `run/config`, and must perform their setup/interactions/assertions autonomously. Tests cover capture, per-Head processes, transactions, topology, assembly/pause/restore, resources, cladding and presentation contracts. Do not quote an old report's test count as the current suite size.

With `-PworldAcceptance=true`, generation uses actual server processing and writes acceptance reports/completion markers only after assertions and save verification. Default generation prepares a world for manual inspection without production acceptance. Worlds under `run/saves/<worldName>` are disposable outputs; the task resets the named world and refuses a running/locked save. Do not use it on a valuable gameplay save. Documentation-only moves do not require GameTests or regenerated worlds.

## Tier construction acceptance

Run the canonical Java 21 launcher in `format`, then `validate` mode (see [repository instructions](../AGENTS.md)). The latter runs formatting checks, the build/resource/CPU checks and autonomous headless GameTests.

`VatTierGameTests` builds actual Copper/Iron/Blood Iron shells: maximum dimensions; independent axis overflow; rectangular footprints with X/Z exchanged and quarter-turn rotation; minimum chicken/cow; Controller/casing/glass/Tool Port/Harness/Sump/conduit/buffer mismatches; corner/edge/face diagnostic coordinates; repair and installed-cladding replacement with inventory retention; T2/T3 chunk pause/restore with saved process state; and no hostile unlock. All tiers reuse the same Tool Port, Blender and service registrations. Existing all-material `AppliedCladdingGameTests` extend installation, replacement, recovery, Pick Block and serialization checks to the new materials. Existing culture/growth/happiness/harvesting and lifecycle tests remain regression coverage. Resource checks inspect generated blockstates, tier models/materials, transparent glass and deterministic hashes without launching a client. Client visual acceptance remains manual and was not performed by the agent.

The headless world at `.cache/gametest` and generated operator worlds are disposable. Regenerate outdated fixtures; no runtime registry migration is provided. T2/T3 survival recipes, Strange Meat, Blood Iron manufacturing and lightning activation are outside this acceptance slice.

## 7.3 Generated geometry references

The [Mixer clearance tables](generated/MIXER_CLEARANCE_GENERATED.md) and [reference SVG](generated/MIXER_REFERENCE.svg) come from `MixerClearanceCharts` via `MixerVesselGeometryGameTests.generatedClearanceReport`. The GameTest writes `.cache/gametest/mixer-clearance.md` and `mixer-reference.svg`; copy those outputs into their tracked generated-document destinations after an authorized geometry change. Their content is preserved unchanged during documentation reorganization. The existing [raster reference](generated/MIXER_REFERENCE.png) is also preserved beside them; the SVG/Java geometry remains canonical because a separate raster-generation command is not documented.

Tables separate legal placement, canonical clearance, actual intersection and whole-vessel radius reduction. They are independent-device charts, not permission for arbitrary additional horizontal runs/bridges. Acceptance writes actual conflict/reduction evidence separately; a historically dense invalid fixture remains evidence, not a valid construction recipe.

## 7.4 Operator visual acceptance

Minecraft client play and visual acceptance belong to the operator. Headless/CPU tests and HTML previews cannot establish lighting, transparency sorting, occlusion or subjective visual quality. Client smoke/reload tasks require an explicit request; never run them automatically. Development commands/hooks and their test classes are excluded from the player JAR.

Inspect formed/unformed cladding, mechanical models, port direction, specimen/strap presentation, Mixer Drive connections and lifecycle recovery in a user-launched client. The detailed world fixture operating reference follows below; prior dated acceptance outcomes are preserved in [history](history/README.md).

## 7.5 Benchmarks

The separate [benchmark project](../benchmarks/README.md) uses the parent wrapper and isolates MobVats versus published XR2 fixtures. It does not enter the mod source set/JAR. Use its runner for measured lifecycle comparisons and avoid concurrent Minecraft/build/profiler work when collecting accepted baselines. [Original accepted results](../benchmarks/results/2026-10-11/SUMMARY.md) retain measured data and limitations; no performance change is implied by archiving them.

## 7.6 Disposable world fixture operating reference


From the mod repository root, run:

```sh
./gradlew buildTestWorld
./gradlew buildTestWorld -PworldName=MobVatsDev
```

On Windows use `gradlew.bat`. The default name is `MobVatsTest`. The task starts a dedicated headless development server, builds and supplies the fixtures, allows 200 setup ticks at a target 100 TPS, checks base fixture assembly and operator formation, saves, and stops. It does not run automated production acceptance, launch Minecraft's client, or run the GameTest suite. Add `-PworldAcceptance=true` to explicitly run production acceptance before saving. Open the generated world from the normal `runClient` world list yourself.

Output from the mod repository: `run/saves/<worldName>/`. Every invocation resets that named world, including all manual edits. Close it in Minecraft first. The task refuses to overwrite an existing world without its `.mobvats-generated-world` marker or follow linked paths. Names must be simple folder names; spaces and path separators are rejected.

After setup (or opt-in production acceptance), the generator installs **ON = paused / OFF = run** levers on smooth-stone blocks adjacent to primary controllers. The saved world starts with these levers ON; operator enable preferences remain unchanged. Controls cover the base Vat/Mixer fixtures (including repair demonstrations), nine operator Vats, four standalone production Mixers, the linked production Vat, and four standalone Extruders. Creative production rigs keep running. The linked P-05/P-06 service Mixers have no controller levers: use P-VAT to pause the upstream producer. Downstream machines may finish buffered work while that Vat is paused. Operator Vat controllers occupy the west wall to keep powered supports clear of front feed hoppers. Signs identify each control, and `fixtures.md` lists controller and lever coordinates. Generation rejects occupied control locations or power changes at nearby existing block entities, including ports and supply hoppers.

Jade, JEI, Pipez, and Building Gadgets 2 are operator tools available only in the normal `runClient` session, including when you open this generated world. Gradle's non-consumable `manualClientRuntime` configuration feeds that run's additional runtime classpath; the manualClient source set is configured in build.gradle. World generation and GameTest runtimes use MobVats and its required Minecraft/NeoForge/ZeroCore environment without the operator mods. Main compilation also sees the compile-only Jade API. These tools are neither required in MobVats metadata nor included in its published jar or dependency variants. Jade supplies a compile-only inspection API; the other operator tools do not add compile-time APIs.

To resolve the manual client dependencies without launching it, run `./gradlew prepareClientRun`. Client play and visual acceptance remain manual.

The world uses creative mode, cheats, peaceful difficulty, superflat terrain, no generated structures, a clear spawn at `(0, -60, 0)`, fixed noon, and no natural mob spawning. Four rows run east from the spawn: VAT POSITIVE, VAT NEGATIVE, MIXER POSITIVE, MIXER NEGATIVE. Origins are 16 blocks apart within a row and 20 blocks apart between rows. Green/red wool marks the paths. Each row has a supply chest; signs are five blocks north of each fixture, with metadata on the first sign and a description on the second. Both sides carry the same text.

| Sign ID | GameTest method | Fixture |
|---|---|---|
| V-01 | VatGameTests.cowLifecycleAndDrainSump | Minimum 3×4×3 Vat, cow encoded |
| V-02 | VatGameTests.captureEncodeAndLargerVat | Larger 5×4×5 Vat, cow encoded |
| V-03 | VatGameTests.grassInteriorRejectsFormation | Grass block in interior |
| M-01 | MixerCapacityGameTests.minimumCylinderBaseline | Minimum 3×3×3 Mixer, 2-deep shaft |
| M-02 | AcceptanceMixerGameTests.fullHeightAcceptanceMixer | Larger 5×4×5 Mixer, 3-deep shaft, vertical edge storage |
| M-03 | MixerGameTests.missingImpellerRejectsFormation | Missing Impeller |
| M-04 | MixerGameTests.offCenterImpellerRejectsFormation | Off-center Impeller |
| M-05 | MixerGameTests.gapImmediatelyBelowImpellerRejectsFormation | Shaft gap |
| M-06 | MixerGameTests.offAxisShaftRejectsFormation | Off-axis Shaft |
| M-07 | MixerGameTests.disconnectedShaftRejectsFormation | Disconnected Shaft |

`fixtures.md` inside the saved world records the exact GameTest method names, positions, descriptions, and verified assembly statuses. Short sign IDs map to these exact names because the names exceed a sign line's readable width. The world mirrors the construction stage of these tests, not their later capture, processing, or break/repair steps.

`MachineFixtures` supplies the shared Vat/Mixer geometry and Mixer fault mutations. `AcceptanceMixerFixtures` supplies the 5×4×5 manual and production Mixers: the centered shaft reaches the bottom level, buffers occupy the four vertical corners below the roof, and glass-clad fluid risers occupy the outside walls one block sideways from each face centerline, only where storage or an input needs a connection. Historical capacity tests retain their purpose-specific layouts. With `-PworldAcceptance=true`, the generator settles for 5,000 ordinary game ticks (250 seconds of simulated time; headless generation runs at 100 ticks/second and restores 20 before saving), checks all ten fixture assembly outcomes, all nine operator rigs and the production section, and only reports success after production acceptance passes and the server has saved `level.dat`. Successful generation writes `generation-complete.txt`; failed acceptance writes `generation-failed.txt` instead. Generator code lives in the development-only `gametest` package excluded from the published jar. World data and the isolated `run-world-generator` server files are ignored and never copied to mod resources.

In opt-in acceptance mode, the 5,000-tick settling period is a minimum. Vanilla cow XP varies from 1 to 3 points per kill. Four cows at the required 2× yield can produce only 160 mB XP, below P-06's 250 mB culture batch requirement. If culture production and returned-medium juvenile growth have not both occurred, generation continues through one dry replacement cohort, bounded at 5,000 + twice the configured cow juvenile growth duration (53,000 ticks with the fixture defaults). These additional ordinary game ticks run at 1,000 ticks/second. P-VAT has seventeen additional physical roof waste buffers for this longer observation. At tick 5,000 the operator row is verified, its controllers are temporarily removed to preserve its finite storage, then restored with their saved block-entity data and given 100 ticks to reassemble before all final checks. Saved tick rate returns to 20.

To reproduce the minimum legal XP outcome deterministically, run:

```sh
./gradlew buildTestWorld -PworldName=MobVatsMinimumXp -PworldAcceptance=true -PacceptanceMinimumCowXp=true
```

This development-only mode sets each positive vanilla cow XP reward inside P-VAT to 1 point before happiness yield. It does not preload XP or change recipes, growth timers, or production assertions. The saved acceptance report identifies this mode; the hook is excluded from the mod jar.

A separate blue operator row sits north of spawn at Z=-24, starting at `(8, -60, -24)` and continuing east at 16-block intervals. It is behind the default south-facing spawn, opposite the mirrored fixture rows at positive Z. All origins below use Y=-60 and Z=-24. These nine Vats are independent of the ten unchanged mirrored fixtures and the production acceptance section.

| ID | Origin X | Specimen / demonstration | Tool supply | Feed supply | Expected output / destination |
|---|---:|---|---|---|---|
| O-01 | 8 | Sheep / shearing and regrowth | 6 shears in separate slots | 64 wheat | Wool, east output chest |
| O-02 | 24 | Cow / milk container acquisition | 64 buckets, split into legal stacks of 16 | 64 wheat | Milk buckets, east output chest; milk upper wall buffer |
| O-03 | 40 | Cow / weapon harvest and replacement | 6 diamond swords in separate slots | 64 wheat | Beef/leather, east output chest; XP upper wall buffer |
| O-04 | 56 | Goat / ram target and horns | 64 stone | 64 wheat | Goat horns, east output chest; stone retained |
| O-05 | 72 | Sniffer / digging substrate | 64 moss blocks | 64 torchflower seeds | Ancient plant seeds, east output chest; moss retained |
| O-06 | 88 | Chicken / passive egg process and Sump | None | 64 wheat seeds | Eggs, east Sump output chest |
| O-07 | 104 | Cow / retained Blender and Sump | None | 64 wheat | Meat, front east wall buffer; waste, rear west buffer |
| O-08 | 120 | Sheared sheep / preferred item feed | None | 64 wheat | Wheat consumption and visible wool regrowth |
| O-09 | 136 | Armadillo / brush acquisition and wear | 54 brushes in double-chest slots | 64 spider eyes | Scutes, east output chest; 16 brush durability per operation |

Each front supply chest sits above a vanilla hopper aimed into the labeled Item Input Port, which acts as an internal Store adjacent to the Feed Port. Tool supplies use a separate rear chest/hopper/Input Port chain. Required inputs are never inserted directly into Tool Ports or process state. During settling, stacks move out of the supply chest into the hopper, Input Port and held tool normally; some operations have already completed when the save opens.

Tool results and passive Sump items share one labeled bottom Output Port per rig. A hopper underneath feeds one edge hopper and then an east output chest outside the shell, at ground level; the chest lid is unobstructed. A single fluid conduit ring around the roof and shell risers connect four filtered 4,000 mB upper wall buffers: front west milk, front east meat, rear west waste, rear east XP. Inspect or empty the labeled upper wall buffers with compatible containers. There is no interior wall of buffers. Every rig retains its Sump and passive-output destination.

Opt-in acceptance observes input arrival at each Input Port and actual Tool Port interactions or process progress during ordinary server ticks. Final verification requires formation, an operational specimen, assigned actors, discoverable intended processes/tool uses, supplied tools or retained catalysts, available item/fluid destination capacity, and an egg output plan. In opt-in acceptance mode, an unformed, inactive, unsupplied or stalled operator rig fails generation. Kill and Blender normally replace the adult with an active juvenile; the saved rig may be growing its replacement.

Normal timing, at 20 ticks per second:

- Goat ram: 40 ticks (2 seconds); 6,000 ticks (300 seconds) between horns. Two horns per encoded adult; re-encode a fresh goat after both are gone.
- Sniffer dig: 120 ticks (6 seconds); 9,600 ticks (480 seconds) between digs. Low comfort can carry fractional seed yield into the next dig, so the first completed dig may yield no item.
- Armadillo brush: 20 ticks (1 second), with a 20-tick interaction cooldown and Tool Port animation timing. Brushes are replaced automatically after breaking.
- Milk generation and sheep regrowth: 1,200 progress ticks (60 seconds divided by the machine frequency multiplier). Milk container filling requires 1,000 mB; low yield can require multiple milk cycles. No milk is seeded during generation.
- Chicken egg: 9,000 progress ticks (450 seconds divided by frequency). The saved egg timer has already advanced; the output chest is ready.
- Cow replacement growth: 24,000 progress ticks (1,200 seconds divided by frequency). Check the controller for current progress. Feed O-08 starts sheared; its regrowth uses wheat through the normal Feed Port path. After observing its first cycle, generation resets the specimen to sheared 200 ticks before saving, leaving the next regrowth visibly in progress; no input or timer is injected. No feeding slurry is supplied, so the preferred item-feed path is demonstrated.

Existing `config/mobvats/passive_mobs/_defaults.json` and authored profiles are preserved on disk. The generated save includes `operator-definitions.json` with the six species used above. The development-only manual-world listener restores this verified snapshot in memory when a generator-marked save opens, so stale local defaults do not require a manual reload. This override applies to those species throughout that generated world's session. `/mobvats reload_passive_mobs` explicitly reloads the normal local configuration; reopening the generated world restores its snapshot again.

### 7.6.1 Production acceptance section

The yellow paths at Z=89 and Z=109 identify `MIXER RECIPES` and `FULL PRODUCTION VAT`. All mirrored fixtures and nine operator Vats remain intact. Opt-in acceptance checks actual production and boundary transfers during ordinary server ticks. `acceptance.md` and `fixtures.md` record `PASS: Verified end-to-end`, `FAIL: Broken behavior`, or `BLOCKED: Unsupported capability`. A failed requirement writes `generation-failed.txt`; only successful acceptance and a saved `level.dat` permit `generation-complete.txt`.

| ID | Origin | Recipe / rig | Drive |
|---|---|---|---|
| P-01 | (8, -60, 96) | `feeding_slurry` | FE |
| P-02 | (26, -60, 96) | `culture_slurry` | Combustion |
| P-03 | (42, -60, 96) | `mobvats:filter_general` | FE |
| P-04 | (58, -60, 96) | `mobvats:filter_lava` | Combustion |
| P-VAT | (4, -60, 116) | Four cow assemblies, one shared sword Store | — |
| P-05 | (-1, -58, 117) | Vat service `feeding_slurry` Mixer | Combustion |
| P-06 | (13, -58, 117) | Vat service `culture_slurry` Mixer | FE |

Each Mixer has a glass 5×4×5 shell, a centered three-deep shaft reaching every level below the roof, twelve filtered glass-clad buffers in the four vertical corners, a real motor, a filtered Fluid Output Port, and a filtered Mixer Fluid Void connected through a TAP. A single fluid roof header connects four glass-clad corner-buffer risers at X=0/4, Z=1/3, plus the north input riser at X=3, Z=0 (relative to the enclosure origin). These columns sit one block sideways from the center of the outside wall. There is no decorative item riser or complete conduit ring. The floor inside the side walls is glass around the centered shaft; the interior contains no conduits or buffers. Six output buffers provide 24,000 mB total product capacity. All Mixer shell conduits and buffers use Mixer cladding. Bare conduits remain nonstructural and their physical envelopes may require uniform whole-vessel radius reduction; Vat cladding does not form Mixer shells. Real storage receives output before disposal; terminal Voids expose no Store capacity. Their filter, local status and lifetime disposed amount are available through Jade. Shift-use samples a fluid container while unformed; empty-hand shift-use clears the filter. Exact sampled component identities persist through block items and saved entities.

Feeding Mixers receive wheat from a chest/hopper/Item Input Port. Filters receive charcoal or sand through the same path. Combustion drives receive coal through a separate elevated chest/hopper/Input Port and roof item conduit. FE drives receive 1,000,000 FE through their exposed capability. Standalone Mixers receive water and waste/XP, or dirty water/lava culture, through the actual Fluid Input Port after formation. Service Mixers receive only external starting water: all their waste and XP must originate in the Vat. Water is replenished through the same capability 200 ticks before saving. No fixture writes process resources, commits recipes, advances timers, or substitutes a transport loop.

The culture annex has one extra TAP above its Controller because its two boundary
ports replace the Controller's neighboring riser cells. This restores storage
access without adding an interior conduit. Waste acceptance checks actual
production and conservation after transfer; larger Mixer storage may leave no
waste inventory in the Vat.

Earlier vertical-layout validation measurements and shutdown diagnostics are preserved in the [historical fixture-world guide](history/2026-10-10-manual-test-world.md). Current generation writes its own measured outcomes.

P-VAT is a 9×6×9 Vat. Cow footprints occupy local (2,2), (6,2), (2,6), and (6,6), with Flesh at Y=2 and encoded Heads at Y=3 as resolved by SimulacrumGeometry. Independent Harnesses occupy Y=5 and Sumps Y=0. The entire Y=1/Y=4 comfort clearance around each footprint remains open. One central Feed Port at local (4,1,4) reaches all four cows, including their replacement juveniles, through the existing radial assignment compiler. Its floor TAP and short central aisle branch connect to shared physical fluid storage.

Twelve individually named diamond swords start in separate slots of one external chest. A vanilla hopper supplies one filtered Item Input Port and an embedded item perimeter, with four symmetric Tool Ports on the north/south walls. During generation, a real lever temporarily locks the hopper after initial tool acquisition and releases it when one feeding batch has returned. This simulated player interaction allows the initial settling window to observe both startup feeding and actual consumption of returned slurry. The saved lever is off and the hopper runs normally. Tools are never preloaded. Every tick checks sword counts and unique identities; final checks require four kill assignments, four serviced cows, retained swords with matching durability wear, and conserved drops in the normal rear hopper/chest output path.

A separate visible roof buffer starts with 3,000 mB feeding slurry, enough for three four-cow feeding rounds. This reserve is primed through the physical buffer's supported container-fill destination before formation, without modifying process state. Returned component-bearing slurry enters through the Vat Fluid Input Port into an earlier separate buffer, so the unchanged storage ordering can consume returned fluid while retaining the reserve. A nearby chest holds 64 optional wheat; it stays disconnected for the required observations. Each cow must complete a slurry feeding and wheat consumption must remain zero.

The fluid floor perimeter replaces the former full floor grid. Roof/wall headers, one west wall riser, and short bare inward columns in the unoccupied cross aisle connect the Sumps, central feeder, visible filtered buffers, and boundaries. Structural conduits and buffers use Vat cladding; interior columns use bare conduits. Transit cells use TRUNK and actual endpoint attachments use TAP. Both annex slurry inlets have an explicit inward TAP connected to the same feeder network. Differently filtered buffers and ports remain endpoints. Waste/XP outputs, the shared-medium culture Fill Valve, and optional waste overflow remain available; conservation requires no unexplained disposal.

SpecimenClearance and runtime yield are checked during production and again at final verification. All four cows must have full credited clearance and the maximum 2.0× happiness yield; reduced productivity fails generation. Identity and process-state checks require independent specimens. The report records each cow's happiness, yield, effective culture exposure frequency and assigned tool, plus shared culture composition, medium volume, enrichment qualification, and resource balances. Culture profile frequency and each cow's effective exposure frequency are reported separately: a small enriched medium fill qualifies but does not fully immerse the clearance shells.

The service Mixers occupy opposite sides of the Vat, two blocks above its floor. Adjacent ports provide actual controller-driven transfer:

- West: Vat waste output `(4, -57, 118)` → P-05 input `(3, -57, 118)`.
- West: P-05 feeding output `(3, -57, 120)` → Vat feeding input `(4, -57, 120)`.
- East: Vat XP output `(12, -57, 118)` → P-06 input `(13, -57, 118)`.
- East: P-06 culture output `(13, -57, 120)` → Vat culture input `(12, -57, 120)`.

Both ports support active/passive mode; the active output owns a direct transfer, preventing duplicate input pulls. No Pipez or test-only transport is involved. The envelopes remain independent despite touching shell parts.

Acceptance requires Vat-origin waste and XP consumption by the service Mixers, returned feeding slurry consumption by the Vat, culture arrival in the retained medium and actual juvenile growth using its frequency multiplier. Fluid balances account for retained product, consumed inputs and medium volume. Independent machine production or simulated capability acceptance is insufficient to pass. Component-bearing Mixer feeding slurry remains usable without stripping components from retained fluid. Mixer multi-output disposal uses an atomic reservation: every output must fit real capacity or a matching connected Void, and changed resources, filters or topology reject stale reservations without disposal effects.

Client visual acceptance remains manual. This generation validates mechanics and persistence, and does not launch the client.

Use the commands in section 7.2 for current integration/acceptance validation. The saved world's acceptance.md contains that run's measured outcomes; historical results are not a current pass claim.

### 7.6.2 Unified mechanical materials

Fixture builders place universal functional blocks with their final `cladding_material` state directly. Material-specific mechanical IDs and direction-only Mixer port IDs are removed; reset disposable worlds with `buildTestWorld`. Gameplay cladding is tested separately through the actual casing/glass item interaction. Run `python tools/verify_unified_world.py run/saves/MobVatsAcceptance run/saves/MobVatsMinimumXp` to verify saved palettes, material/mode properties and fixture inventory IDs. See [current cladding](02-MULTIBLOCKS.md#24-universal-mechanical-parts-and-installed-materials) for replacement, recovery and ZeroCore ownership behavior.
