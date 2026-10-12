> Historical record. Original: `docs/CREATIVE_ACCEPTANCE.md`. Current reference: [07-TESTING.md](../07-TESTING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Creative-fed acceptance area

Vat medium now renders a translucent glass overlay clipped to the actual liquid height,
with a horizontal top surface. See `2026-10-11-medium-liquid-acceptance.md` for diagnostics,
rendering details and validation results.

Generate with `./gradlew.bat buildTestWorld -PworldName=MobVatsAcceptance`.
No graphical client is launched. Follow the cyan path from spawn south to Z=156.
Existing acceptance machines, historical test mirrors and the four-cow production
installation retain their layouts.

| Fixture | Origin | Supply | Collection |
| --- | --- | --- | --- |
| Feeding Mixer | 8, -60, 156 | Water, waste, plant biomass | Glass corner buffer, filtered Fluid Void overflow |
| Culture Mixer | 26, -60, 156 | Water, XP fluid | Glass corner buffer, adjacent Vat, filtered Fluid Void overflow |
| General filtration | 44, -60, 156 | Dirty water, matching filtration material | Ordinary water buffer, filtered Fluid Void overflow |
| Lava filtration | 62, -60, 156 | Dirty lava, matching filtration material | Ordinary lava buffer, filtered Fluid Void overflow |
| Water filling Vat | 8, -60, 176 | Creative water source | Actual Vat medium |

`CreativeProductionFixtures` resolves current enabled recipe strategies, selectors,
quantities and output transforms. Incompatible future recipe shapes fail explicitly.
Creative endpoints store identity samples, including compound components. Their
generation is unlimited; normal active fluid-port and hopper cadence controls transfer.
No fixture tick inserts resources into a controller or executes a substitute routing
loop. Item inputs use the existing port-backed recipe inventory; this project does
not have a separate Item Buffer block.

All Mixers reuse the canonical 5×4×5 acceptance enclosure and a Creative Drive.
Fluid sources sit outside north input ports. Shell headers connect filtered glass
corner buffers; the vessel interior stays clear. Item sources feed an outward hopper
into the normal Item Input. One 4,000 mB glass product buffer retains observable
output; the normal filtered Fluid Void handles overflow only after available storage.

The culture installation uses directly adjacent Mixer Output and active Vat Input
ports. The Vat inlet has its required inward TAP, a short service-margin riser,
a glass culture buffer and a roof Fill Valve. Both new Vats have a 5×4×3 enclosure
and an encoded cow Head required for formation, without feeding or harvest actors.
These are separate fixtures from the existing four-cow production Vat.

The generated `creative-acceptance.md` records actual produced, retained, transferred
and disposed volumes, structure/drive state, recipe progress, storage and working
fluid. Product accounting includes the Vat medium and its buffer. Acceptance requires
multiple completed batches, input replenishment, SPINNING_UP and RUNNING observations,
nonempty presentation data throughout processing, output accumulation, overflow
disposal, preserved composition and a capacity-filled Vat. The separate water test
checks an initially disconnected empty Vat, progressive filling, exact capacity,
and no consumption of retained buffer contents after the inlet is disabled at full.

Vats reach capacity during headless settling. Inspect their actual volume through
machine diagnostics. Vat medium currently has no liquid-level renderer; glass walls
alone cannot show this stored medium. Mixer color, level and final pixel visibility
remain manual acceptance: inspect each running vessel from above and through its glass.
A productive Mixer with valid working-fluid data but no visible liquid indicates a
presentation defect. The harness does not change rendering, recipe priorities or
last-recipe persistence.

Validation commands:

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat buildTestWorld -PworldName=MobVatsAcceptance
git diff --check
```

Report GameTest completion independently of the existing saving-worlds shutdown hang.

## Validation on October 11, 2026

- `build`: passed.
- `runGameTestServer`: all 668 required tests passed; the server saved all dimensions
  and Gradle exited successfully.
- `buildTestWorld -PworldName=MobVatsAcceptance`: all original and creative fixture
  assertions passed. Each creative Mixer produced 18,000 mB. Both new Vats filled
  to 6,000 mB capacity, and the culture production balance was 4,000 mB retained
  in the Mixer, 10,000 mB in the Vat/buffer and 4,000 mB disposed through the Void.
- World generation subsequently hit the known saving-worlds shutdown hang in
  `ChunkMap.scheduleUnload` / `processUnloads` during `stopServer`. The hung server
  was stopped after a thread dump. Final save completion is unconfirmed; the
  `generation-complete.txt` marker was not produced. Fixture evidence is in
  `run/saves/MobVatsAcceptance/creative-acceptance.md` and `fixtures.md`.
- `git diff --check`: passed.

One earlier generation attempt exposed XP starvation in the unchanged P-06:
240 mB available against a 250 mB recipe requirement. The retry passed that original
fixture. World reporting now preserves the creative-area evidence independently
of an original production-fixture failure.

No graphical client was launched. Mixer pixel visibility/color remains manual
acceptance; the missing Vat medium liquid-level renderer is a separate limitation.
