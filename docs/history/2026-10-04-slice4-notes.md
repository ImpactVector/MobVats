> Historical record. Original: `SLICE4_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
SLICE 5 UPDATE - October 4, 2026
Mixer/passive slurries are now implemented; see SLICE5_NOTES.txt. Vat ProductBuffers
now adds Culture/Feeding storage after the existing three indexes, with five-fluid
output arbitration and slurry input routing. Slice 4 actor and product rules below
remain applicable; its Before Mixer recommendations are historical.

MobVats Slice 4 - Blender, Sewer and alternate harvest outputs
October 4, 2026
============================================================

1. Blender actor
   VatBlenderEntity is a distinct AbstractVatEntity/VatActor shell part. It is
   allowed on a side face and uses the existing controller compiler's physical
   specimen ray/LOS test. It has no tick handler, held item, inventory or menu
   route selector. VatPartType registration supplies its block/entity/item.
   The shared controller CowProcesses component executes its selected route.
   Provisional recipe: casing + stonecutter. Break loot returns the fixture.

2. Blender versus Sword
   Both claim CowBalance.HARVEST_USE (cowHarvest), per specimen. Fixed priorities
   are Blender 200 > Sword Tool 100, with existing X/Y/Z position ordering for
   ties. Compiler discards losing active claims; the controller never executes
   a rejected Sword route as an alternate. A blocked selected Blender leaves
   READY intact and does not fall back to Sword. Removing/moving Blender rebuilds
   topology, allowing a physically valid Sword to claim the role again.
   Fluid-only actors have usesLocalItemPorts=false. Neighboring Item Outputs
   remain generic unless a separate valid Tool relationship reserves them.
   Physical valid rejected Tool candidates still reserve their local endpoints,
   consistent with the prior kill-buffer fix. Feed contacts remain physical.

3. MEAT production, storage and export
   One READY replacement yields 1000 mB MEAT into a dedicated controller-owned
   4000 mB logical tank. This consumes the SAME READY -> IDLE state as Sword.
   It generates no vanilla beef/leather, no item-kill deposit, no sword damage,
   no spawned entities/orbs and zero XP (CowBalance.BLENDER_XP=0).
   Prepared legacy Sword pending loot is cleared only when Blender commits.
   Whole-yield MEAT capacity is checked before mutation. Full tank preserves
   READY and waits; exported capacity permits a later controller cadence commit.
   ProductFluids registers source/flowing MEAT with its own FluidType. No world
   fluid block, bucket, solidification, meat block or ingot is introduced.

4. Sewer / Drain relationship
   VatSewerEntity is a distinct bottom-wall actor, NOT Tool behavior or a port.
   It is a sump tap on a BottomFace tile at the SAME Y as the validated Drain,
   exactly one cardinal block beside it. A diagonal/distant tile cannot claim.
   The compiler verifies the actual Drain entity and its alignment with the
   specimen's head; normal vat validation already verifies the biological
   head/flesh/Drain vertical column. Drain stays required, structural, and has
   no ItemHandler/FluidHandler capability. Sewer claims cowDrain separately
   from cowHarvest, with stable position tie-breaking when multiple taps reach
   the same Drain. Only the selected tap's specimen produces waste.
   This implements the conceptual Drain -> Sewer path as an adjacent sump tap,
   avoiding a second shell block directly below the floor Drain. Use a vat with
   spare interior floor tiles (e.g. the tested 5x5 footprint); do not put Sewer
   on a frame edge/corner. Provisional recipe: casing + cauldron.

5. WASTE production/storage
   Chosen balance: passive 250 mB WASTE per 1200 active ticks, only while a valid
   selected Sewer exists. This is independent of wheat/READY/harvest and milk.
   CowProcessState owns a persistent wasteTicks timer. Unformed time and time
   without the selected Drain route do not advance it. Removing a tap preserves
   partial progress; reconnecting a valid tap resumes it. A full 4000 mB waste
   buffer holds the timer at its threshold and retries on process cadence.
   Only successful whole-yield insertion resets the timer/counts production.
   ProductFluids registers separate WASTE source/flowing fluids and FluidType.
   All yields, intervals and capacities are centralized in CowBalance.

6. Fluid output arbitration and persistence
   ProductBuffers is a controller component containing MEAT/WASTE FluidTanks
   and a reference to the existing milk/legacy tank. No incompatible products
   are mixed. The output handler exposes THREE logical tanks: milk, MEAT, WASTE.
   Every assembled OUTPUT Fluid Port exposes the same output-only handler; it
   can be anywhere on the shell. No actor adjacency/filter/priorities are added.
   Amount-only drain chooses one nonempty tank from a shared controller cursor,
   ordered milk -> MEAT -> WASTE -> milk. Each successful EXECUTE advances the
   cursor to the following logical tank, even for partial drains. A call always
   returns exactly ONE fluid. SIMULATE neither drains nor advances the cursor.
   A fluid-specific drain request selects only that exact fluid/components;
   successful execution likewise advances the cursor. A consumer that locks
   itself to one fluid can use this explicit API rather than amount arbitration.
   Arbitrary external storage is not pushed into or simulated by the controller;
   these are passive capabilities pulled by normal fluid automation.
   Input Fluid Ports retain the existing milk/legacy tank input behavior (including
   water from Slice 1). MEAT/WASTE FluidTypes are rejected on input so products
   cannot contaminate that tank. Existing milk bucket routing directly accesses
   the milk tank and never consumes MEAT/WASTE.
   Existing NBT fluidBuffer remains unchanged. New products compound stores
   meat, waste and next cursor for persistence and compact fluid network sync.
   Old saves without products load both new buffers empty, retaining legacy milk.
   Recent rates reset on FullSync reload; persistent lifetime metrics survive.
   MEAT/WASTE assimilation preserves both buffers, including temporary overflow
   above the production ceiling. Export can drain that overflow; further production
   waits for enough room. The consumed controller clears its product storage.

7. Item-kill buffer interaction
   Sword item harvest still checks the shared item backlog BEFORE new harvest
   eligibility/loot work and cannot kill until it is empty. Blender does not use
   that list and does not consult it as a capacity prerequisite. Buffered Sword
   drops remain unchanged while Blender consumes a newly READY replacement into
   MEAT. Both consume the shared per-specimen replacement state, so no independent
   second kill cycle or double consumption is possible. Milk and Sewer continue.
   Automatic Sword loading does not override an already selected Blender claim.
   Diagnostics now call this an item-kill global pause, explicitly exempting Blender.

8. Metrics and diagnostics
   Added lifetime/recent meatMb, wasteMb and blenderHarvests metrics. Controller
   Production displays mB/s MEAT and WASTE, buffered amounts/capacities, Blender
   count and selected harvest actor per specimen. Efficiency includes explicit
   mB MEAT / wheat; no invented waste per feed ratio because waste is passive.
   Existing harvests/durability efficiency remains Sword-specific and is labeled
   accordingly. GUI status includes Blender/Sewer assigned versus unassigned,
   displaced Sword status, missing physical Drain relationship, product capacity
   blocking and no-output/safe-fluid-storage or waiting-extraction conditions.
   Output diagnostics describe round-robin arbitration rather than claiming an
   external consumer exists or is actively draining. Rejected routes are not
   reported as active. Sampled ZeroCore menu status sync remains in place.

9. Testing and render scope
   Ran gradlew.bat --offline build runGameTestServer. Build and packaged model/
   atlas/art verification succeeded; all 47 required headless GameTests passed.
   The original 39 tests remain green (milk, physical feeding, sword loot/XP/
   durability/enchantments, controller item backlog/hopper extraction, topology,
   status/menu sync, facing, validation and renderer resource checks).
   Eight added tests:
   - blenderPriorityMeatCommit: valid formation, priority, rejected Sword absence,
     neighboring generic Item Output, READY consumption, no item loot/XP/damage,
     no production entities and active/displaced diagnostics.
   - blenderCapacityAndItemBufferIndependence: full MEAT stalls without partial
     commit, milk continues, MEAT capability export resumes Blender while item
     backlog remains intact.
   - productFluidArbitrationAndPersistence: actual Fluid Port capabilities, three
     tanks, independent product roundtrip, SIMULATE safety, persisted cursor,
     partial round-robin/exact-fluid extraction, input policy and old-save loading.
   - sewerPhysicalDrainAndWastePersistence: valid cardinal tap versus diagonal
     unassigned tap, WASTE production/roundtrip/export and non-pipeable Drain.
   - sewerFullBufferRetriesWithoutLoss: threshold timer retained, exact retry
     after WASTE export, capacity diagnostic and committed metric conservation.
   - blenderReachAndSwordFallback: unreachable Blender cannot displace Sword;
     valid Blender removes Sword route, and its removal recompiles Sword.
   - productAssimilationPreservesOverflow: two committed MEAT stores merge above
     capacity without loss, persist and export completely before new production.
   - physicalFeedBlenderAndSewerCycle: autonomous real wheat feeding -> shared
     replacement -> Blender MEAT, independent milk/WASTE, correct explicit ratio
     and no vanilla drops/orbs/Cow entities.
   New blocks use basic vanilla stonecutter-side/hopper-top fixture materials,
   item models, self-drop loot tables and provisional recipes. Formed fixtures
   automatically inherit the common three-pixel panel treatment. No new interior
   animations, client render loops or tier changes were added. Registered client
   fluid appearance uses built-in water sprites tinted red/brown, not imported
   historical art. No Minecraft client task ran; visual acceptance remains user-owned.

10. Before a Mixer slice
   Independent product storage and fluid-specific drain already provide the
   necessary foundation. Mixer should be a distinct actor/use with explicit
   controller-owned input/output transaction semantics, an atomic multi-fluid
   consumption/production plan, and centralized provisional yields. Decide its
   physical input relationships and product names/capacities before adding it.
   Expose targeted fluid requests or a later deliberate filtering API for consumers
   that cannot use mixed product availability. Do not merge incompatible fluids
   into the milk tank or repurpose the Sword item backlog. Any new recipe ratios
   should identify their actual inputs rather than a synthetic efficiency score.
   Generalized species/grip/render metadata remains later work; current validation
   is still one cow specimen and the existing headless multi-specimen item-gate
   fixture does not enable multi-Head world structures.

Primary new source files
   gamecontent/fluid/ProductFluids.java
   gamecontent/multiblock/vat/part/VatBlenderEntity.java
   gamecontent/multiblock/vat/part/VatSewerEntity.java
   gamecontent/multiblock/vat/process/ProductBuffers.java

Manual checks, performed by the user only
   - Mount reachable Blender plus Sword on a formed fed vat: controller should
     select Blender, produce MEAT and leave Sword durability unchanged.
   - Place Sewer on a bottom face cardinally beside Drain: verify WASTE. Move it
     diagonally/distantly and verify unassigned/no additional production.
   - Extract milk/MEAT/WASTE through OUTPUT Fluid Ports; verify consumer behavior
     for the documented one-fluid-per-drain arbitration and persistence on restart.
   - Verify new fixtures use the intended thin wall treatment and fluids have
     suitable provisional colors. No fancy animation acceptance is required here.

No Mixer, slurry, solids, filters, XP export, upgrades, other species, client
launch, publication, release, commit or push performed for this slice. Earlier
local kill-buffer and mechanism-render corrections remain in the working tree.
```
