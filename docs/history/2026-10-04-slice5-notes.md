> Historical record. Original: `SLICE5_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
MobVats Slice 5 - Mixer and passive slurries
October 4, 2026
==========================================

1. Separate Mixer multiblock
   New gamecontent/multiblock/mixer package follows ZeroCore's cuboid controller,
   part-type, individual entity, process component and client renderer structure.
   MultiblockMixer owns MixerState and a cached Intake position list. Individual
   entities describe Controller, Casing, Glass, Fluid Input, Fluid Output, Item
   Input and Intake; ports neither own recipe storage nor run recipes.
   Exterior bounds: 3x4x3 minimum, 9 blocks maximum on each axis. Exactly ONE
   Controller is required on a side face. Casing occupies corners/horizontal
   frame edges and may fill any shell position; Glass is allowed on side faces
   and vertical frame edges. Intake is side-only; fixed Input/Output ports may
   use side/top/bottom faces. Interior must be air: the shaft/paddles are a
   coordinated visual, not mandatory physical shaft blocks. Missing shell,
   obstructed interior and incorrect controller count fail formation.
   Controller and parts use ordinary reference-coordinate ZeroCore persistence.
   Correcting a non-part interior obstruction may need a casing remove/replace
   to trigger ZeroCore structural revalidation; the recovery test performs that
   structural update explicitly. No continuous per-tick interior scan is added.
   Basic block/item models, translations, recipes and self-drop loot are included.

2. Water and XP acquisition
   - Mixer Fluid Input exposes standard NeoForge Water/WASTE input capability;
     it rejects slurry outputs. Mixer Fluid Output is output-only for slurries.
   - Survival Water Bucket right-click on a formed Mixer Controller or Fluid
     Input simulates whole 1000 mB capacity, fills and returns an empty Bucket.
     Full/unformed storage consumes nothing. Creative preserves its bucket.
   - Mixer Intake on a side face checks its outward adjacent block every 10
     active ticks. An actual source FluidState in the water tag adds up to
     250 mB Water per cadence (500 mB/s). Flowing water is rejected, no source
     is removed, and no FE cost or pipe network is introduced.
   - Sneak-empty-hand right-click Mixer Controller deposits up to 5 of the
     player's actual XP points. Successful storage precedes subtracting exactly
     those points. No XP fluid, solid XP item conversion or Vat XP exporter.
   - Vanilla hopper insertion into Mixer Item Input provides plant feedstock.
     Input automation cannot extract. Storage is initially four controller slots;
     this is unrelated to Vat Item Ports, which STILL have exactly one slot.

3. Atomic transactions and progress
   MixerRecipeDefinition separates id, fluid ingredient list, item/tag ingredient
   list, XP, output fluid/amount and batch duration from controller execution.
   Recipes and all provisional numbers are centralized in recipe definitions and
   SlurryBalance; no external config/data recipe system was added.
   MixerState builds aggregate fluid requirements and a per-slot item plan, checks
   XP and exact output-fluid capacity by simulation, then RECHECKS at commit.
   Commits touch only private controller FluidTanks/ItemStackHandler, never
   arbitrary external handlers or callbacks. Failed checks consume nothing.
   External views return defensive copies; ports are capability wrappers.
   Inputs stay in storage until the entire batch commits: progressing animation
   does not spend Water, WASTE, XP or crops early. Blocked work preserves progress.
   The selected recipe resumes after capacity/inputs return. With no selected
   batch, first runnable Feeding recipe wins, otherwise Culture. A selected batch
   is not preempted by inputs arriving for another recipe. Both finish/reset into
   idle and reevaluate on a subsequent cadence; no runtime GUI route selector.

4. Culture Slurry
   1000 mB Water + 5 XP points -> 1000 mB Culture Slurry in 100 active ticks.
   Vat consumes 10 mB Culture Slurry to pay for 10 active vat ticks at 4x milk
   timer advancement and 4x replacement growth. The paid budget is shared once
   per controller tick and reused for every specimen timer, not charged by each
   specimen or by a capability scan. Culture does NOT feed/start replacement.
   A complete 1000 mB batch funds 1000 vat ticks (50 active seconds). Consumption
   while continuously supported is 20 mB/s. Paid cultureCredit persists, pauses
   when unformed, expires without free continued acceleration, and is preserved
   across controller assimilation. Status reports 4x while credit or the next
   whole payment is available, avoiding a false 1x dip at budget boundaries.
   Sewer's passive WASTE timer retains its Slice 4 rate; this provisional culture
   effect applies to milk/growth, not every timer or harvest item yield.
   Culture continues funding active support even if downstream production is
   capacity-blocked; that balance can be revised explicitly in a later slice.

5. Feeding Slurry
   1000 mB Water + 250 mB WASTE + 1 tagged plant -> 1000 mB Feeding Slurry in
   100 active ticks. No XP required. One Cow IDLE replacement start consumes
   250 mB Feeding Slurry instead of one Wheat and enters the existing GROWING
   state. Thus one Mixer batch funds four replacement starts.
   Preferred feed order: available whole Feeding Slurry payment, then existing
   compiled direct Item Input -> specimen Wheat contacts. A slurry-fed cycle
   consumes no Wheat. No new Feed Port, second kill cycle or mandatory slurry
   dependency is added. Feeding Slurry alone does not accelerate milk/growth;
   only separately paid Culture does so. Existing harness/feed visuals remain.

6. Plant category
   data/mobvats/tags/item/plant_biomass.json supports Wheat, Wheat Seeds,
   Beetroot Seeds, Pumpkin Seeds, Melon Seeds, Potato, Carrot, Beetroot and Kelp.
   All count uniformly as one recipe biomass unit. Nonplants are rejected by
   Mixer input staging. The tag is extensible through standard Minecraft tags;
   no generalized nutrition/species/config framework is introduced.

7. Independent storage and routing
   Mixer: four 8000 mB logical tanks (Water, WASTE, Culture, Feeding), with two
   input-only and two output-only capability views, persistent XP, item stacks,
   selected recipe/progress, output cursor and lifetime metrics.
   Mixer amount-only output drains choose Culture then Feeding using a shared
   round-robin cursor; successful partial or whole execution advances it, while
   simulation is side-effect-free. Explicit-fluid drains select only the requested
   fluid/components. Each call returns one fluid, never an incompatible mix.
   Vat: extends ProductBuffers to FIVE existing-index-stable logical tanks:
   milk/legacy, MEAT, WASTE, Culture, Feeding (4000 mB each). Input routes slurries
   to their dedicated tanks while preserving earlier milk/water input behavior;
   MEAT/WASTE input remains rejected. Output order expands milk -> MEAT -> WASTE
   -> Culture -> Feeding. Exact-fluid draining and simulation semantics remain.
   Existing saves missing new slurry tanks/credit load them empty and preserve
   old products. Slurry buffers, paid budget and last feed source roundtrip with
   controller data. No filters or extra Vat Item Port slots were introduced.
   Mixer reconnection conserves committed fluids/XP/items. It can temporarily
   retain fluid above the normal intake/production ceiling and add controller
   item slots for excess distinct stacks so no input is discarded. Ordinary
   input refuses over-capacity fluid; recipes/export can clear the backlog. This
   exceptional conservation storage is not a Vat Item Port inventory expansion.
   Lifetime metrics combine; the consumed controller's component is cleared.

8. Provisional numbers (SlurryBalance / existing CowBalance)
   Mixer process/pump cadence: 10 ticks.
   Mixer batch duration: 100 ticks (5 active seconds), both recipes.
   Mixer logical tank capacity: 8000 mB each.
   Intake: 250 mB/cadence, maximum 500 mB/s, renewable source preserved.
   Culture: Water 1000 + XP 5 -> slurry 1000; cost 10 mB per 10 vat ticks, 4x.
   Feeding: Water 1000 + WASTE 250 + biomass 1 -> slurry 1000; feed cost 250.
   Vat logical tank capacity remains 4000 mB each.
   Baseline Cow milk/growth durations remain 1200 ticks; supported duration is
   approximately 300 ticks. Culture changes rate, not per-harvest yield or XP.
   Slurry fluids have distinct source/flowing FluidTypes, tinted vanilla water
   appearance, no placed-fluid blocks/buckets/solidification in this slice.

9. Performance and render coordination
   Mixer recipes and Intake source checks run centrally every 10 active ticks;
   Intake positions are cached on assembly. No recipe graph discovery per tick,
   independently ticking port logic or per-specimen external capability scans.
   A vat support tick does one shared budget/counter operation before iterating
   existing specimen timers. One paid environmental budget supports the entire
   controller; actual multi-simulacrum structure/scheduling is still NOT enabled.
   Mixer Controller BER caches shaft/paddle meshes by whole multiblock bounds.
   FULL: shaft plus paddles rotating at 4 degrees/second (90-second revolution).
   REDUCED: shaft/static paddles. MINIMAL: static shaft only. CULLED: immediate
   return. It reuses Config.CLIENT distances and VatRenderDetail, measures camera
   distance to the nearest AABB point, overrides getViewDistance, preserves
   shouldRenderOffScreen and the full multiblock render bounding box.
   The shaft/paddles reuse the existing native casing material; fixtures reference
   built-in vanilla block textures. No XR turbine physics/art was copied.
   No fluid-level volume or slurry shader was added; amounts are observable in
   controller status. Visual shaft, paddles, glass and colors remain user acceptance.

10. Observability
   Empty-hand Mixer interaction reports formed/active, Water/WASTE/XP, plant
   amount, selected recipe/progress, each slurry output and blocked reasons.
   It includes recent Culture/Feeding produced mB/s and persistent totals for
   water/waste/XP/plants consumed. There is no giant configuration UI. Recent
   rates use the existing 200-active-tick window; totals persist, offline time
   does not invent production. Vat Production adds both slurry amounts, current
   passive multiplier, current/last feed source and each slurry consumption rate.
   Existing MEAT/WASTE/Sword/Blender/Sewer/item-backlog diagnostics remain intact.
   No aggregate efficiency score is used.

11. Verification
   Ran gradlew.bat --offline build runGameTestServer using the existing cache.
   Build and packaged model/atlas/seven preserved artwork hash checks passed.
   All 57 required autonomous headless GameTests passed: the earlier 47 plus
   10 new tests. No conventional unit-test source set is present.
   MixerGameTests adds seven:
   - mixerFormsAndWaterBucketXpInteractions: real formation and fluid/item
     capabilities, simulated Water acceptance, actual block Water Bucket action,
     returned empty/full-capacity preserved bucket, exact player XP deposit/status.
   - mixerRejectsInvalidInteriorAndRecovers: blocked interior rejection, explicit
     structural revalidation after correction and missing frame rejection.
   - waterIntakeRequiresSourceAndPreservesSource: flowing-water rejection,
     outward source pumping without destruction, stop after source removal.
   - cultureAtomicInputsBlockedOutputAndPersistence: missing XP/Water and full
     output consume nothing; successful ratios, metric totals, persist/resume.
   - feedingAtomicPlantsAndBlockedOutput: all nine plants accepted, nonplant
     rejection, missing WASTE/biomass conservation, exact multi-input commit,
     full-output blocking and stored fluid/item persistence.
   - mixerAssimilationConservesItemsFluidsAndXp: merged overflow, six distinct
     plant slots, no loss/duplication, reload, over-capacity fill refusal and recipe.
   - mixerCadenceAndProgressPersistence: real controller cadence, no premature
     input consumption, progress roundtrip, both recipes and capability export.
   CowProcessGameTests adds three:
   - vatSlurryAcceleratesMilkGrowthAndReplacesWheat: real vat input capabilities,
     clear milk/growth acceleration, shared support consumption and preferred
     slurry feeding that leaves direct Wheat unchanged, support diagnostics.
   - culturePaidBudgetPersistsAndExpires: paid partially used credit roundtrip,
     exact one charge, exhaustion restoring baseline timer rate.
   - fiveVatFluidsPersistAndExportIndependently: all product/slurry buffers,
     exact-fluid simulation/draining, expanded round-robin behavior and reload.
   Existing Wheat/milk/MEAT/WASTE, actor priority, Sword loot/XP/enchantments,
   kill-item backlog/hopper extraction, structural and facing tests remain green.
   Headless runs use no manual keyboard/mouse, client launch or screenshot loops.

12. User-only manual checks
   Build a Mixer shell (e.g. 5x4x5 exterior), Casing horizontal frames/corners,
   glass sides, exactly one side Controller and air interior. Add fixed Fluid
   Input/Output, Item Input, and optionally a side Intake. Put a water source
   directly OUTSIDE the Intake's side face. Verify cached shaft/paddles and slow
   FULL-tier rotation, static REDUCED/MINIMAL representations and farther culling.
   Use Water Buckets on Controller/Fluid Input. Sneak-empty-hand Controller to
   transfer player XP; ordinary empty-hand part interaction prints status.
   Hopper plants into Item Input, supply WASTE through Fluid Input from a vat
   Sewer/Fluid Output. Verify both recipe outputs and blocked-output conservation.
   Transfer slurry using external fluid automation where available (no MobVats
   pipes or slurry bucket items are added). Vanilla-only Mixer Water and plant
   automation are supported; XP deposit remains manual for this slice.
   Supply slurries to existing Vat Fluid Input. Verify faster milk/growth, culture
   consumption/expiry, slurry replacement feeding with Wheat left in staging,
   save/restart persistence and expected provisional fluid colors.

13. Before multi-simulacrum work
   Shared buffers and environmental budget are already vat-level. Define how
   culture cost scales with specimen count/size or a finite support budget, then
   add explicit fair allocation for feeding/harvest schedules. Current prototype
   intentionally has one flat environmental payment per vat, and each replacement
   start uses its own 250 mB from the shared Feeding store.
   Relax structural Head/Harness/Drain validation only as a separate deliberate
   slice with physical assignment tests; current one-Cow validation is unchanged.
   Review multiple Controller reconnection/progress choice (one in-progress batch
   survives; no escrowed inputs), intake balance and actor reach for larger vats.
   A later data loader can construct MixerRecipeDefinition; add configurable
   recipe ordering and atomic transaction tests before introducing richer grades.
   Slurry transfer convenience, fluid-level visuals and a sampled Mixer menu can
   be later independent work; none require changing the current private-store
   atomic boundary. Avoid one slurry support machine/buffer per specimen.

No hostile slurry/mobs, lightning, advanced grades, upgrades, multiple-specimen
scheduler, filters, external recipe config, solidification or XP-fluid/solid
conversion was added. No Minecraft client, publication, release, commit or push
performed. Prior local kill-buffer, renderer and Slice 4 changes are preserved.
```
