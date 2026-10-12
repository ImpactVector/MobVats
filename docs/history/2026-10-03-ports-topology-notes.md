> Historical record. Original: `PORTS_TOPOLOGY_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
Historical Slice 1 record. Slice 2 supersedes no-production/empty-tool rules.
See 2026-10-03-cow-process-notes.md for the current implementation and acceptance.

MobVats Ports / Topology Compiler Slice 1 -- October 3, 2026

Implementation files under src/main/java/net/impactvector/mobvats/
  gamecontent/multiblock/vat/part/AbstractVatPortEntity.java
  gamecontent/multiblock/vat/part/VatItemPortEntity.java
  gamecontent/multiblock/vat/part/VatFluidPortEntity.java
  gamecontent/multiblock/vat/part/VatToolPortEntity.java
  gamecontent/multiblock/vat/part/VatPortBlock.java
  gamecontent/multiblock/vat/menu/VatPortMenu.java
  gamecontent/multiblock/vat/client/screen/VatPortScreen.java
  gamecontent/multiblock/vat/VatPortCapabilities.java
  gamecontent/multiblock/vat/VatActor.java
  gamecontent/multiblock/vat/VatTopology.java
  gamecontent/multiblock/vat/VatTopologyCompiler.java
  gamecontent/multiblock/vat/MultiblockVat.java
  Content, VatPartType and VatClient register the parts, menu, capabilities and screen.

1. Item Port
  One block/entity with ZeroCore IoDirection.Input/Output, initially Input.
  One-slot local TileEntityItemStackHandler staging; normal stack limits.
  ItemHandlerPolicyWrapper.inputOnly/outputOnly protects external automation.
  Capabilities.ItemHandler.BLOCK is available on all sides, including unformed
  staging endpoints. Actual vanilla hoppers are tested. Player GUI can insert
  only in Input mode and retrieve staging contents in either mode. Shift-click
  follows the same rules. Mode changes occur through the GUI button, never an
  ordinary world click. Standard inventory button packets are reused.
  NBT and network sync retain direction and staging contents. Mode changes
  invalidate capability caches and rebuild controller topology. Removed ports
  drop their staging contents.

2. Fluid Port
  Implements ZeroCore IFluidPort and uses FluidPortHandlerForge in Passive mode.
  Its delegated handler comes from MultiblockVat.fluidHandler(IoDirection).
  FluidHandlerPolicyWrapper.inputOnly/outputOnly applies direction at the shared
  controller store. No tank exists on the port. Unformed/broken vats delegate to
  EmptyFluidHandler. Assembly, break and mode callbacks refresh the delegation.
  Capabilities.FluidHandler.BLOCK is exposed on all sides. Minimal GUI changes
  direction only; there is no active pumping or fluid-volume renderer.

3. Tool Port
  A distinct VatActor, not an Item Port subtype or IIoEntity. No GUI/capability.
  One internal held-item slot, limit one. Any nonempty ItemStack is supported.
  Server right-click mounts one item, consuming one in survival and returning
  the previous item to inventory (dropping it if full). Empty-hand click retrieves
  it. Client only predicts interaction success. Persisted/synced held state uses
  the existing ZeroCore inventory/NBT path. Removal drops the held item.
  Action-bar feedback reports active assignment or unassigned/unformed state.
  Held ItemStack access supports later rendering, but no tool item/arm render or
  sword/shears/bucket action is implemented.

4. Controller topology
  ZeroCore part attachment callbacks maintain the controller's attached port/actor
  set. The compiler produces an immutable VatTopology of positions: specimen
  Heads, item inputs/outputs, fluid inputs/outputs, active actor assignments and
  direct Item Input-to-specimen contacts. Actor assignments carry adjacent
  (Manhattan distance one) Item Input/Output endpoints. Ordinary Item Inputs can
  have direct inward LOS contacts for future feeding. Live block entities are
  not persisted in topology. Rejected actor candidates exist only during compile.

5. Rebuild triggers
  Assembly/restoration compile; break/pause clear active topology. Direction
  changes and Tool Port held-item changes recompile server topology. Structure
  changes use ZeroCore validation/lifecycle callbacks. No tick performs graph
  discovery, transfer, biological action or scheduling. Local staging contents
  do not change geometry and do not force rediscovery.

6. LOS
  Tool Ports are valid only on cardinal side faces, never top/bottom/frame/corners.
  Bounds determine the inward normal. A ray from the wall block center must hit
  the specimen's registry-dimension body AABB. Another specimen intersected first
  blocks assignment. Intervening collision-bearing blocks block LOS; the assigned
  specimen's own Head/Flesh construction column is ignored. Empty tools are not
  compatible. Existing structural validation still permits only one cow and air
  outside its construction column; obstacle logic is tested independently too.

7. Priority
  VatActor supplies priority(), useKey(), compatible() and overridable reaches().
  Current generic Tool priority is 100. Candidates sort by descending priority,
  then actor X/Y/Z, Head X/Y/Z and use key. Only the first compatible candidate
  per Head/use is retained. Future Blender/Sewer actors can override these hooks;
  no future priority table or action implementation was introduced.

8. References / APIs
  Downloaded source snapshots are in ../examples/ExtremeReactors2-ports:
    ReactorFluidPortEntity.java
    TurbineFluidPortEntity.java
    ReactorSolidAccessPortEntity.java
    MultiblockReactor.java
    MultiblockTurbine.java
  Repository: https://github.com/ZeroNoRyouki/ExtremeReactors2
  The reference snapshots use older Forge signatures; implementation was adapted
  to and compiled against installed NeoForge 21.1.252 / ZeroCore2 1.21.1-2.4.18.
  Reused: IIoEntity, IoDirection read/write, IFluidPort, FluidPortHandlerForge,
  IoMode.Passive, TileEntityItemStackHandler, ItemHandlerPolicyWrapper,
  FluidHandlerPolicyWrapper, ZeroCore attachment/assembly callbacks and NBT sync.
  NeoForge RegisterCapabilitiesEvent / IMenuTypeExtension / SlotItemHandler and
  Minecraft MenuProvider / AbstractContainerMenu provide platform integration.
  No generic replacement IO network, custom packets or pipe system was added.

9. Verification
  .\gradlew.bat --offline build runGameTestServer
  Build, packaged resource verification and all 18 required GameTests passed.
  Six added tests:
    portsCapabilitiesAndTopology: legal optional shell parts, item capability
      policies, fluid sharing/direction, inventory/mode/held/controller NBT,
      GUI mode mutation, direct input contacts, adjacent IO, LOS obstruction,
      removal/replacement, active rebuild and break/reform.
    itemPortHoppers: actual hopper insertion/extraction and forbidden operations.
    minimumManualToolVat: 3x4x3 with a manual tool and no automated IO cell.
    topologyPriorityAndPositions: highest priority, stable tie, discard lower
      alternatives, independent uses, top/bottom Tool Port rejection.
    portWorldMenuRouting: held/empty item clicks reach default menu interaction,
      menu providers bind the correct part; world clicks do not toggle direction.
    controllerFluidAssimilation: empty fragment and compatible controller stores
      preserve fluid up to capacity.
  Existing 12 tests retain formation, validation, facing, bounds, panel geometry,
  straps, render-tier selection and construction lifecycle coverage.
  cowLifecycleAndDrainSump replaces the temporary milk-output expectations:
  no running milk timer, no Drain bucket output, no server-side specimen entity.
  Packaged model/atlas/material checks and seven preserved texture hashes pass.
  Headless menu routing does not prove client packet negotiation or screen layout.
  A mock connection lacks NeoForge advanced_open_screen negotiation; client
  acceptance is left to the user. No client/smoke/reload task ran.

10. Provisional storage / assets
  Controller has a single shared 4000 mB FluidTank, persisted through its ZeroCore
  reference-coordinate NBT, marked dirty/synced when contents change. It accepts
  arbitrary existing fluids through Input and exposes them through Output, with
  no production or active pumping. It survives ordinary break/reform. Assimilation
  accepts compatible fluid up to 4000 mB; incompatible fluids or overflow from
  joining independent filled vats are not retained by this provisional store.
  This is not the eventual biological tank layout.
  Legacy Head milk fields remain readable but the controller no longer advances
  the timer and Drain no longer provides extraction. Drain is structural sump.
  Three new port model/loot/blockstate/item assets use vanilla placeholder face
  textures and the existing automatic 3px wall-panel/lip model. Temporary shapeless
  recipes combine casing + iron ingot + hopper/bucket/piston respectively.
  No XR/BR artwork, new biological fluid, dedicated Feed Port or pipe was added.

11. User client acceptance (not performed by an agent)
  - Place optional Item/Fluid Ports on legal shell faces; Tool on cardinal side.
  - Open Item Port with held item and empty hand. Verify staging slot and toggle.
  - Confirm Input insertion and Output extraction with hoppers and mode switches.
  - Confirm Fluid Port GUI toggle and external fluid Input/Output delegation.
  - Right-click Tool with generic items, replace, and empty-hand retrieve. No GUI.
  - Check Tool assigned/unassigned feedback and manual minimum-vat formation.
  - Save/reload port modes, staging and mounted item; test break/reform.
  - Confirm formed 3px port panels, glass-adjacent lip textures and existing vat
    glass/casing/specimen/straps/facing/render tiers remain visually correct.
  - Verify Drain no longer turns a bucket into milk.

12. Recommended Slice 2
  Add one controller-owned process definition and scheduler consuming only active
  compiled routes. Start with one explicitly selected generic Tool interaction,
  including staged input/tool/output transfer rules, result capacity reservation,
  durability and controller-owned timers. Add transaction/no-duplication/reload
  tests before adding other actors or multi-mob routing. Keep feeding a process
  of an ordinary Item Input, and keep Drain as sump. Tool item/arm rendering can
  follow once the process and sync contract are defined.

Current changes are local. No client run, publication, release or push performed.
```
