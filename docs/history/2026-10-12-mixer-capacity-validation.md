> Historical record. Original: `docs/MIXER_CAPACITY_VALIDATION.md`. Current reference: [07-TESTING.md](../07-TESTING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Mixer capacity and six-pixel Shaft validation

Validated October 12, 2026 against baseline commit
`49152c7ac552b382e4d4a9ac080a112e30963aa2`.

## Capacity implementation

Previously `MixerAssembly.slotCount()` divided polygon pixel columns multiplied
by usable pixel height by `MixerSweep.BASELINE_VOXELS` (21,808). Columns excluded
the Shaft, and floor-reaching height excluded the three-pixel shell. Those
details could change gameplay capacity when rendering dimensions changed.

Now `MixerAssembly.slotCount()` is the sole production implementation:

`floor((effectiveDiameter - 1)^2 * shaftLength / 8)`

Effective diameter is nominal diameter minus twice the whole-block radius
reduction. Installed Shaft length supplies actual usable depth; full depth equals
height minus one. Existing canonical validation determines legal reductions,
leaving effective diameter at least three. There is no independent slot clamp,
Shaft subtraction, floor-pixel deduction or paddle deduction.

| Footprint | Height 3 | Height 5 | Height 7 | Height 9 |
|---|---:|---:|---:|---:|
| 3×3 | 1 | 2 | 3 | 4 |
| 5×5 | 4 | 8 | 12 | 16 |
| 7×7 | 9 | 18 | 27 | 36 |
| 9×9 | 16 | 32 | 48 | 64 |

At nominal diameter 9 and depth 8, reductions 0/1/2/3 give effective diameters
9/7/5/3 and slots 64/36/16/4. Shortening an unreduced maximum Shaft from eight
blocks to seven changes capacity from 64 to 56; restoration returns to 64.

The baseline constant and obsolete `baselineVoxels()` utility were removed.
Geometric pixel counts remain explicitly named `geometricSweptVoxels()` and
`geometricSweptVolume()` for rendering diagnostics and liquid presentation.
They have no capacity consumers. Version-3 dimension tags still load; slot count
is recomputed rather than restored from a saved historical value.

## Shaft and structural geometry

`MixerVesselGeometry.SHAFT_WIDTH_PIXELS` is six, width 0.375 blocks and half-width
0.1875 blocks. The physical shape and both base/development construction models
span X/Z 5..11 pixels. The actual formed mesh uses the shared width and terminates
at the vessel's inner floor. Crossing paddles retain their centered attachment
and established reach. No impeller, Drive or texture redesign was performed.

The reference apothem remains 1.9365234375 blocks. Regenerated per-device
clearance tables are unchanged; only the capacity table changed. The generated
reference SVG now shows the six-pixel Shaft. Canonical polygons, TAP/conduit
envelopes, whole-block reductions and placement rules remain authoritative.

Acceptance main Mixer: depth three, effective diameter five, six slots,
no selected-vessel intersections. Culture annex: one radius reduction,
effective diameter three, one slot, no selected-vessel intersections.

## Drive and process behavior at 64 slots

The formed 9×9×9 FE-driven regression verifies 12,800-tick startup, no processing
before startup completes, normal 200-tick batch timing, exact FE payment,
resource conservation, reservation replay rejection, power-loss stop,
controller save/load, topology rebuild and structural Shaft shortening/restoration.
At default 2,500 FE per cycle, startup costs 160,000 FE; the following 200 running
ticks cost 2,500 FE. Dimension multiplication has a maximum numerator of 512.

Existing scheduling remains one active recipe at its defined batch cadence.
Slots scale startup duration and startup resource cost; they do not add new
parallel execution. Recipe definitions, priorities, routing, fluid-buffer
capacities and production cadence were not changed.

## Validation results

| Check | Result |
|---|---|
| `gradlew.bat build` | Passed, including CPU rendering/resource checks and the actual formed Shaft mesh check |
| `gradlew.bat runGameTestServer` | 741/756 passed; 15 pre-existing failures reproduced on clean HEAD |
| Clean-HEAD `runGameTestServer` | 738/753 passed; identical 15 failing test names |
| `buildTestWorld -PworldName=MobVatsAcceptance` | Passed: 10 mirrored fixtures, nine operator Vats, 5,000 observation ticks |
| `buildTestWorld -PworldName=MobVatsAcceptanceMinimumXp -PacceptanceMinimumCowXp=true` | Passed: culture/enriched-growth checks at 49,771 observation ticks; all dimensions saved |
| `git diff --check` | Passed |

All three added GameTests passed: full table/short Shafts/every legal reduction;
six-pixel models/collision/orientations/rotor containment; and the formed 64-slot
power/process/reassembly regression. The CPU check also validates the actual
formed Shaft mesh across every legal radius reduction, short/full depths and
both floor-inset states. Existing polygon, TAP and conduit-clearance regressions
passed.

Extended acceptance retained the original 53,000-tick deadline and suspension/
resumption window. P-06 initially held 160/250 mB XP at tick 5,000, reached
200 mB at tick 49,000, and completed culture/enriched-growth verification at
tick 49,771. No acceptance timing, recipe or production-balance changes were made.

The unchanged failing tests are:

- `exhaustionrestartandfullspeedrecipe`
- `mixerfluidmodesneighborsandbreakrepair`
- `mixerdisableretainsstateandresumes`
- `cultureatomicinputsblockedoutputandpersistence`
- `mixerformsandwaterbucketxpinteractions`
- `standardcapabilitiesfeedmixerbusanddisposeoutputs`
- `mixercadenceandprogresspersistence`
- `formedbreakandrepairswitchclickdiagnostics`
- `cookcycleconsumesoneeighthcoal`
- `consolidatedmixerportsacceptrecipeandexportafterdirectionchange`
- `recipeprogresssurvivesexhaustionandrestart`
- `creativedriveprovidesfulldriveonlyonformedimpeller`
- `mixerrecipesretainindependentserviceandpersistatowner`
- `formedmixerbucketsbothslurrieswithoutoutputbuffers`
- `mixercultureconsumesexperiencefluid`

These failures concern pre-existing recipe/XP expectations. For example, several
tests supply five XP points (100 mB at the current ratio), while the bundled
culture recipe requires 250 mB. Their assertions and production rules were left
unchanged. The diagnostic-label assertion was updated for the explicitly
geometric volume label; it now reaches the same XP failure as baseline.

Full logs are ignored temporary files under `.cache/`: `mixer-final-build.log`,
`mixer-final-validation.log`, `mixer-baseline-gametests.log`,
`mixer-acceptance.log` and `mixer-minimum-xp-acceptance.log`.

## Remaining discrepancies

No mechanical geometry discrepancy was detected by automated checks. Minecraft
client play and visual acceptance remain user-controlled and were not run.
Shaft/impeller placeholder textures and their runtime bindings remain classified
as development-only; release safeguards remain unchanged. No commit,
publication, release or deployment was performed.
