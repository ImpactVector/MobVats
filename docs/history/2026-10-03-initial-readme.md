> Historical record. Original: `README.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
MobVats 0.1.0 — Minecraft 1.21.1 / NeoForge cow vertical slice

This directory is the repository root. All implementation, build files,
resources, and developer test sources are contained here. No historical codebase
was modified. Nothing has been published or released.

Slice 12.1 environment/productivity contract and validation: SLICE12_1_NOTES.txt.
Culture configuration example: CULTURE_PROFILES.example.json.

Build and run (from this directory)
  .\gradlew.bat build
  .\gradlew.bat runGameTestServer
  .\gradlew.bat buildTestWorld -PworldName=MobVatsDev
  .\gradlew.bat runClient

Unified mechanical blocks, applied materials and resource recovery: UNIFIED_MECHANICAL_CLADDING.md.
Manual fixture world generation/reset: MANUAL_TEST_WORLD.md.
Omit worldName for MobVatsTest. Output is run/saves/<worldName>.
Jade, JEI, Pipez and Building Gadgets 2 are restricted to manual runClient
sessions via manualClientRuntime; automated tests and world generation use only
the required mod environment. Resolve the client without launching it with
  .\gradlew.bat prepareClientRun

Client launch commands are for the user. Coding agents may compile, run unit
tests, and run autonomous headless GameTests that handle their own interactions
and assertions. Agents must not control the mouse/keyboard or perform manual
client testing with screenshot loops. Client smoke/reload tasks require a
specific request.

Java 21 is required for compilation and Minecraft. The official Gradle toolchain
resolver downloads it if necessary. Gradle itself can start with Java 17.
NeoForge is pinned to 21.1.252. ZeroCore2 is a mandatory external dependency:
  curse.maven:zerocore-247921:6648746 (official 1.21.1-2.4.18 artifact)
Its author's Maven endpoint was inaccessible during setup, so Curse Maven is used.
This version matches the dependency in ExtremeReactors2's 1.21 branch. It is not
claimed to be the latest ZeroCore2 release. No ZeroCore2 classes are bundled.

For an isolated portable Gradle cache, commands from this directory can set:
  $env:GRADLE_USER_HOME=[IO.Path]::GetFullPath((Join-Path (Get-Location) '../workspace/.cache/gradle'))
This is an environment-specific cache location, not a requirement of this mod.

How to use the slice
  1. Craft/get a syringe, carry a glass bottle, and right-click an adult entity
     with a loaded passive definition (Cow is the bundled default). Survival
     consumes one bottle. Essence stores identity and resolved geometry at capture.
  2. Build a shell with exterior dimensions of at least 3 x 4 x 3 (X,Y,Z).
     Each dimension has an initial implementation cap of 16 blocks.
     All corners and horizontal top/bottom frame edges must be casing.
     Vertical edges may be casing or glass.
     Side faces may be casing/glass, with exactly one controller on a side face.
     Top/bottom faces may hold the specimen's Harness/Drain service endpoints.
  3. Place a Head and exactly the Flesh cells required by saved geometry. Default
     Cow geometry is 1 x 2 x 1, with Head above one Flesh block. Add Harnesses
     above each unique footprint corner, all at one height, with 0..4 spacer
     blocks above the specimen. Add one Drain below the occupied volume, with
     0..4 vertical spacers and up to 2 blocks of horizontal offset from its footprint.
     Service endpoints can also be interior parts. Air/water/Culture medium and
     legal conduits are accepted in service space; arbitrary blocks are rejected.
     Extra Flesh is unowned and invalid. One active specimen is still supported.
     Sneak while placing block items against interactive vat parts.
  4. Right-click the unencoded head with cow essence. The vat forms automatically.
     Head/flesh models disappear and a stationary vanilla cow representation
     appears. The representation can turn its head toward the client player.
  5. Optional Item/Fluid Ports expose configurable Input/Output endpoints. Their
     GUIs change direction. Tool Ports accept one held item by right-click and
     return it by empty-hand right-click. Milk accumulates passively: 1000 mB per
     1200 active ticks in the shared controller tank. Mount a bucket to obtain a
     milk bucket, or drain through a Fluid Output Port. A direct cardinal Item
     Input supplies one wheat per replacement cycle; after 1200 active ticks a
     mounted sword harvests into adjacent Item Outputs. Beef/leather may require
     two free output slots. Sword stays mounted and wears. Controller right-click
     reports milk, replacement progress and buffered XP.
     Drain is the specimen's waste-service endpoint. Sewer connects beside that
     selected Drain; Drain itself does not provide bucket or fluid extraction.
  6. Break a shell block to disassemble, then replace it to reform. Construction
     models return while disassembled. Sneak/right-click a part for ZeroCore2's
     validation feedback. Save/reload retains the encoded head, port state and controller fluid buffer.

Class layout
  net.impactvector.mobvats.MobVats
  gamecontent.Content
  gamecontent.item.ItemSyringe / ItemEssence
  gamecontent.multiblock.vat.MultiblockVat / VatPartType / MobAssembly
  gamecontent.multiblock.vat.part.AbstractVatEntity
  gamecontent.multiblock.vat.part.Vat{Controller,Casing,Glass,Harness,Drain,Head,Flesh}Entity
  gamecontent.multiblock.vat.part.VatPartBlock / VatGlassBlock / VatConstructionBlock / VatHeadBlock
  gamecontent.multiblock.vat.client.VatClient
  gamecontent.multiblock.vat.client.render.VatHeadEntityRenderer
  gamecontent.multiblock.vat.client.render.VatRenderDescriptor
  gamecontent.multiblock.vat.client.model.VatGlassModel
  gamecontent.multiblock.vat.VatGlassState
  gamecontent.multiblock.vat.client.model.VatCasingModel
  gamecontent.multiblock.vat.client.model.VatWallPanelModel
  gamecontent.multiblock.vat.VatWallPanelState
  gamecontent.multiblock.vat.VatCasingState / VatCasingGeometry / HarnessStraps
  gamecontent.multiblock.vat.client.render.CableRenderer
  gamecontent.mob.MobRenderDefinition / CableCurve
This follows XR2's controller/part-type/individual-entity/client-render organization.

ZeroCore2 extension points actually used
  it.zerono.mods.zerocore.lib.multiblock.cuboid.AbstractCuboidMultiblockController
    isMachineWhole; bounding boxes; getConnectedParts; part ownership, discovery,
    attachment/detachment, assembly/disassembly, and controller ticking.
    onMachineAssembled/Restored/Paused/Disassembled; updateServer;
    checkIfMachineIsWhole; markMultiblockForRenderUpdate.
  it.zerono.mods.zerocore.base.multiblock.part.AbstractMultiblockEntity
    Cuboid-part base, model-data cache/update handling, assembly callbacks.
  it.zerono.mods.zerocore.lib.multiblock.cuboid.PartPosition
    FrameEastWest, FrameSouthNorth, FrameUpDown, faces, Interior.
    Outward BlockFacings distinguishes corners from vertical edges because the
    controller validator also supplies FrameUpDown for corners.
  it.zerono.mods.zerocore.lib.block.multiblock.MultiblockPartBlock
    Part factories and existing sneak/right-click validation feedback.
  it.zerono.mods.zerocore.lib.block.multiblock.IMultiblockPartType
    Enum-driven entity construction and identifiers.
  it.zerono.mods.zerocore.base.multiblock.part.GlassBlock
    Transparent face culling, skylight propagation, and glass properties.
  it.zerono.mods.zerocore.lib.client.model.BlockVariantsModelBuilder
    Original/hidden model variants and per-block fallback for item rendering.
  it.zerono.mods.zerocore.lib.client.model.data.multiblock.CuboidPartVariantsModelData
    Variant index and part model data; no custom baked-model hiding framework.
  ZeroCore2's inherited syncDataFrom/syncDataTo with ISyncableEntity.SyncReason
    FullSync and NetworkUpdate, setChanged, and notifyBlockUpdate.

References inspected before implementation
  https://github.com/ImpactVector/MobVats-old2 (master; formerly MobVats)
    MultiblockVat; MobAssembly; VatHead/Flesh/Harness/Drain; ItemSyringe/Essence.
    Recovers essence-driven identity and harness -> head -> flesh -> drain intent.
  https://github.com/ImpactVector/MobVats-old (master)
    ItemSyringe; MobAssembly; SpawnerManager.
    Recovers bottle/essence behavior and physical substrate intent. Historical
    power/spawning machinery was not retained because it conflicts with this slice.
  https://github.com/ZeroNoRyouki/ZeroCore2/tree/1.21
    Authoritative extension-point sources; implementation also compiled and ran
    against the published dependency to verify signatures and runtime behavior.
  https://github.com/ZeroNoRyouki/ExtremeReactors2/tree/1.21
    MultiblockReactor and MultiblockTurbine; AbstractReactorEntity and
    AbstractTurbineEntity; ReactorPartType and TurbinePartType; ReactorCasingEntity
    and ReactorGlassEntity; TurbineRotorComponentEntity, TurbineRotorComponentBlock,
    TurbineRotorModelBuilder, TurbineRotorBearingEntity, RotorBearingEntityRenderer.
    Patterns reused: per-part validation, enum factories, model-data hidden variant
    for internal construction, assembly render refresh, one coordinated BER with
    a multiblock-sized render bounding box. No reactor simulation was copied.

MobVats-specific code and why
  Shell rules and specimen-column validation implement gameplay constraints that
  differ from reactors. ZeroCore2 still classifies/traverses the whole cuboid.
  Essence capture/encoding and physical topology implement MobVats gameplay;
  ZeroCore2 is not a biological simulation library.
  The Cow BER replaces XR2's rotor model rendering with vanilla entity rendering.
  ZeroCore2's model builder performs construction suppression. Construction blocks
  transmit light so the retained invisible substrate does not black out the cow.
  When synchronized identity changes, the head calls ZeroCore2's existing
  checkIfMachineIsWhole API; encoding does not replace a structural block and
  therefore needs an explicit revalidation request. No custom packets, discovery,
  attachment system, generic capability framework, or bounds framework was rebuilt.

Rendering and persistence
  The renderer creates a Cow only on the physical client and calls its normal
  vanilla renderer. It never registers the object in a Level, spawns it, calls
  its entity tick, or serializes it. Stationary body, client head tracking, no AI,
  no entity-manager participation. A weak per-head cache stores the visual only.
  Encoded registry identity belongs to VatHeadEntity and persists through
  ZeroCore2's existing block-entity NBT/sync path. Legacy Head milk NBT remains readable
  for compatibility; active production uses controller fields. Drain extraction
  remains disabled.
  List<MobAssembly> is reconstructed from validated blocks, not persisted as live
  entity references. Controller assimilation combines compatible provisional fluid contents up to capacity.

Acceptance tests
  Initial slice acceptance: four GameTests and client/restart checks passed.
  Formation-feedback update, October 3, 2026: six GameTests passed. A client attempt
  exited before acceptance completed; restored artwork is not yet verified
  in-game. The user's clarified testing policy permits autonomous headless
  GameTests and unit tests; manual client acceptance belongs to the user.
  The pre-port slice contained twelve Minecraft/ZeroCore2 integration tests.
  Current acceptance adds six port/topology tests (see 2026-10-03-ports-topology-notes.md):
    renderDetailThresholds: inclusive tier boundaries, normalized misordered
      distances, 8..512 clamps and feature permissions without client classes.
    headPlacementAndSpecimenFacing: actual block-item placement in four directions,
      player-facing Head, persisted state, specimen yaw, unchanged world-down scan,
      and break/reform orientation.
    functionalWallPanels: 3px controller/harness/drain panels, shared broad-face
      materials, glass-only thickness edges, adjacency changes, and break/repair.
    casingRimsAndLifecycle: native-casing model state, full exterior hull area,
      3px L rims, semantic/directional edge materials, reflected ceiling geometry,
      no overlapping coplanar patches, glass-attached lips, and break/repair lifecycle.
    bodyAnchorsAndHarnessCurves: dimension-derived body anchor and overrides,
      four harness underside corners, 16-segment curves and 0.10-block midpoint sag.
    glassBoundsAndFormationLifecycle: bounds-derived side/roof perimeter masks,
      both planes at vertical glass edges, wall interruptions without new borders,
      disassembly/reformation, and no server-side cow.
    cowLifecycleAndDrainSump: vertical glass, horizontal/corner glass rejection,
      break/repair/reform, engine NBT roundtrip, inactive legacy production,
      structural Drain with no bucket output, and no server-side cow.
    captureEncodeAndLargerVat: survival capture, bottle consumption, item/block
      encoding interaction, shape alone rejected, 5 x 4 x 5 vat formation.
    invalidSubstrateRejected: missing flesh fails validation.
    rectangularTallVat: 3 x 6 x 5 vat forms with a longer substrate column.
    minimumVatThroughPlayerPlacement: block-item placement from bottom to top,
      changing player yaw, sneak-placement item path, then essence interaction.
    misalignedDrainAndExtraFleshFeedback: distinct error keys and highlighted
      coordinates, repair/reformation for each failure; validation not relaxed.
  Opt-in client runs (launch and close Minecraft automatically):
    .\gradlew.bat runClientSmokeTest
    .\gradlew.bat runClientReloadTest
  Run these separately and in that order. Both use only the dedicated
  run-client-smoke/saves/MobVats-Smoke world. The first resets its specimen; the
  second reopens the world saved by the first. They assert actual baked model
  quads, rendered-cow cache, entity-manager isolation, client assembly lifecycle,
  and disk recovery. Screenshots are under run-client-smoke/screenshots.
  Gradle fails if the client acceptance marker is missing or any check fails.
  Developer test classes and their empty structure are excluded from the built
  distributable JAR. Logs, runs, Gradle output, and IDE files are gitignored.
  tools/generate_resources.py regenerates models/translations/recipes while
  retaining the recovered PNGs. See 2026-10-03-asset-provenance.md for source paths/hashes.

Manual formation investigation
  The screenshot's old generic message marks the bottom of the specimen column,
  not necessarily the actual offending block. It combined substrate volume,
  flesh outside the column, horizontal room, and drain alignment failures.
  Cow registry dimensions are 0.9 x 1.4: rounded requirements are 1 x 2 x 1,
  with two head/flesh blocks. The documented 3 x 4 x 3 exterior remains correct:
  its interior is 1 x 2 x 1, with Harness -> Head -> Flesh -> Drain at one X/Z.
  Scan direction is world-down; player facing does not change it. Bounding-box
  min/max are inclusive, so interior size is max - min - 1 per axis.
  Once shell and column checks pass, ordinary cow substrate/room requirements
  are already met. Extra flesh or drain alignment explains the remaining old
  error branches. The laptop world is not available locally; screenshots alone
  do not establish which branch failed. No geometry defect has been proven.
  New messages report substrate counts, room dimensions, required-part counts,
  extra flesh at its own position, or expected/actual drain coordinates.
  Minimum placement and invalid-layout integration tests passed, but they do
  not reproduce the unknown laptop-world state. In-game acceptance belongs to
  the user. With this source built, sneak/right-click for the specific error.
  Optional read-only saved-world diagnostic (exit Minecraft/save first):
    python tools/inspect_saved_vats.py "path/to/world"
  It lists overworld MobVats block-entity IDs, coordinates, and head state.

Recovered artwork
  Five original MobVats PNGs were copied unchanged from MobVats-old2. They are
  byte-identical to the older MobVats equivalents. Head uses vat_head on five
  sides and vat_face on its north side, matching the historical block model.
  The later Head placement update adds four facing variants; the source PNGs
  remain unchanged. Flesh, syringe and essence use their original textures.
  Assembly still hides construction models in favor of the cow representation.
  The initial shell used vanilla placeholders; casing now uses native art and
  formed glass uses native tank materials. Controller/drain/harness retain their
  placeholders. No reactor,
  port, power-tap, turbine, or other likely BR/XR artwork was imported.

Continuous formed tank rendering, October 3, 2026
  Vat Glass retains its ordinary vanilla-glass baked model while unformed.
  Formed glass uses chunk-rendered translucent planes with faint uniform tint
  and one-pixel outer-perimeter strips. Each plane is selected from assembled
  bounds; an opaque/functional part interrupting a wall does not introduce a new
  border. Vertical glass edges own both perpendicular outward planes. Inward
  windings on the same planes preserve the far wall's perimeter through the
  near wall. There are no internal cube faces and no glass block-entity renderer.
  ZeroCore2 assembly/break callbacks refresh immutable model data; no packets,
  new blockstates, or validation changes were introduced.
  The unique Head renderer now caches VatRenderDescriptor with the full vat AABB,
  open interior AABB, specimen position, harness, and drain coordinates. Its
  revision changes at formation/break callbacks. Future fluid rendering can use
  interiorBounds directly for one prism and derive its top Y from amount/capacity;
  no fluid state or fluid rendering is implemented yet.
  Build and all seven autonomous headless GameTests passed on the laptop.
  Opt-in client tests have additional glass-layer, winding, model-data transition,
  descriptor reuse, and reload assertions. They were not run for this update.
  Ordinary client startup and atlas loading were checked from logs; a restarted
  client reported assembly on both sides without missing-sprite warnings.
  The user confirmed the rendering works in the client on October 3, 2026.
  Shader compatibility and dedicated reload-test coverage remain unverified.
  See 2026-10-03-rendering-notes.md for design, files, and acceptance steps.

Native casing rims and harness straps, October 3, 2026
  Casing now uses three original 16x16 semantic materials: face, edge, and inner.
  Unformed casing/items remain full cubes. Formed casing becomes a hollow shell
  with 3/16-block panels measured inward from the actual outer tank planes.
  Floor/ceiling surfaces recess into the base/cap; perimeter walls end flush at
  the unchanged glass boundary. Perpendicular panels union into L/corner shells.
  No raised lip enters the glass area and no inset ring remains. Collision,
  validation and glass rendering are unchanged. Functional wall parts share the
  same 3px panel geometry, with matching inner/outer component-face textures and
  casing lip material only on glass-adjacent edges. See 2026-10-03-casing-harness-notes.md
  for dimensions and checks.
  The Head's coordinated renderer now caches four harness straps as geometry.
  Each starts at an actual thin-panel underside corner (local Y=13/16) and ends
  inside the body bounding box,
  at center X/Z and 55 percent of body height. MobRenderDefinition permits optional
  per-entity anchor fractions; the Cow uses the default. Body straps do not use
  head angles. Sixteen curve segments, 0.10-block parabolic midpoint sag, and
  3/64-block square cable thickness provide initial restrained suspension.
  No physics, additional block entities, fluids, mask, tubes, or upgrade features.
  Build, verifyRenderResources, and all eleven headless GameTests passed. Build
  checks the JAR's 16x16 materials, native model/atlas references, and seven
  preserved texture hashes. No client/smoke/reload task ran in this pass.
  See 2026-10-03-casing-harness-notes.md for exact geometry, assets, and manual checks.

Limitations and next slice
  One active specimen with a loaded passive definition is supported. Item/Fluid/Tool Ports and
  controller topology and cow milk/replacement/harvest processes are implemented.
  Blender/MEAT and Sewer/WASTE are implemented with controller-owned product
  buffers and fixed actor priorities. Mixer/passive slurries are implemented. XP output, upgrades and
  multi-mob scheduling are not implemented. Straps use actual conduit obstruction geometry; Tool
  Port held items and cached mechanisms are rendered by the coordinated renderer.
  Recipes, placeholder vanilla port textures, dimensions cap and 4000 mB per
  logical fluid buffer are provisional development choices. Extra space grants no bonus.
  Saved Head geometry owns exact substrate cells; neither Harness nor Drain
  determines specimen extent. Clearance is observable and applies no bonus/penalty. Encoded identity
  remains in the placed Head; breaking it drops an unencoded Head. Collision
  remains block-based. Arbitrary modpacks/shaders/unload permutations remain
  outside current acceptance. See 2026-10-04-slice4-notes.md for current product/actor rules;
  2026-10-03-cow-process-notes.md and 2026-10-03-presentation-notes.md document the earlier slices.

Head placement orientation, October 3, 2026
  Head Face points toward the placing player using the opposite horizontal look
  direction. Its four facing blockstates rotate the unchanged north-face model;
  existing Heads without an explicit saved facing default to north. Structure
  rotations/mirrors preserve orientation. Block-state persistence supplies facing
  without a duplicate entity NBT field.
  The formed specimen body adopts Head facing (S=0, W=90, N=180, E=270 yaw).
  Head tracking remains limited to +/-60 degrees relative to that body direction.
  Render descriptors cache that yaw and refresh on facing or formation changes.
  Scan/validation still proceed world-down regardless of placement orientation.
  All four model variants retain ZeroCore construction/hidden model selection.
  Build validates packaged facing variants; all eleven headless GameTests passed.
  Client orientation acceptance remains with the user.

Controller ownership
  MultiblockVat is the authoritative ZeroCore controller object: validation,
  assembled bounds, specimen assemblies, lifecycle and server ticking reside there.
  VatControllerEntity is the controller block part, not the controller object.
  Part entities expose bounds-derived visual snapshots; client models/renderers
  cache meshes and render-only specimens. Head stores its encoded identity and
  legacy inactive production fields. The controller owns the fluid buffer and
  active compiled topology; active cow process state belongs there. Head blockstate stores placement
  facing. There are no independent per-part simulation tick loops.

Client interior render detail/culling config, October 3, 2026
  config.Client contains ModConfigSpec.IntValue fields; config.Config exposes
  Config.CLIENT and registers ModConfig.Type.CLIENT via ModContainer, only on
  the physical client. XR Client/Config reference sources were read from:
    https://github.com/ZeroNoRyouki/ExtremeReactors2/blob/master/src/main/java/it/zerono/mods/extremereactors/config/Client.java
    https://github.com/ZeroNoRyouki/ExtremeReactors2/blob/master/src/main/java/it/zerono/mods/extremereactors/config/Config.java
  This adapts the dedicated settings/spec-holder pattern to NeoForge 21.1.252.
  Reference downloads live in ../examples/ExtremeReactors2-config (not shipped).

  Config file: <Minecraft instance>/config/mobvats-client.toml
  Development client path (relative to this repository): run/config/mobvats-client.toml
  The file is generated/loaded when the user launches the client, not by the
  dedicated headless GameTest server. TOML keys:
    [client]
    fullDetailDistance = 32
    reducedDetailDistance = 64
    cullDistance = 96
  All are integer block distances bounded 8..512. At use time each value clamps
  to that range; reduced=min(reduced,cull) and full=min(full,reduced). The configured
  cull limit remains authoritative. Misordered values never crash or invert tiers;
  equal distances can leave empty tiers.
  Config reads use Config.CLIENT.<value>.get(), with no server/gameplay consumer.

  VatRenderDetail provides FULL, REDUCED, MINIMAL and CULLED plus normalized
  Distances and common feature permissions. The renderer makes one decision from
  squared camera distance to the nearest point on descriptor.vatBounds(), with
  inclusive boundaries and no square root. Each camera coordinate clamps to the
  corresponding AABB axis interval; a camera inside/on the vat has zero distance. All current/future interior features can use
  that decision. No feature has its own independent distance calculation.
    FULL <=32: vanilla specimen, player head tracking, cached harness straps.
    REDUCED >32..64: static specimen, cached basic straps, no head tracking.
    MINIMAL >64..96: static vanilla specimen only, no strap work or head tracking.
    CULLED >96: return before Cow creation, positioning, head or cable work.
  Thresholds above illustrate defaults. Both static tiers reset head yaw/pitch
  and freeze animation/interpolation so prior FULL tracking cannot remain stuck.
  Straps are already static cached meshes; REDUCED reuses that existing geometry.
  MINIMAL retains the cheapest existing useful specimen representation rather
  than introducing new proxy art. Fluids, tools and particles are not implemented.

  Multiblock-sized render bounds and shouldRenderOffScreen=true are preserved.
  getViewDistance returns Config.CLIENT.cullDistance.get(). The shouldRender BER
  gate uses the same nearest-AABB-point metric, replacing its default Head-anchor
  check so a large vat is not prematurely hidden. The detail decision still occurs
  inside render before specimen or cable work. Chunk loading and global client
  visibility still limit which vats can render. No server state or validation,
  collision, shell/glass geometry or block model render distances are affected.
  Culled vats keep existing cached geometry; no distant rebaking occurs. Assembly
  changes still invalidate cached descriptors/visuals through existing lifecycle.

  Verification: offline build/resource checks and all twelve autonomous headless
  GameTests passed, including nearest-point tests for inside/face/edge/corner
  cameras and a far vat face whose center is beyond the cull range. No client,
  smoke/reload run, or mouse/keyboard testing used.
  Manual acceptance: inspect head tracking and straps within 32 blocks of the nearest vat surface; confirm
  static mob plus straps at 33..64, static mob only at 65..96, and no coordinated
  interior beyond 96. Move back inward to check restoration. Increase normal
  chunk/render distance enough to keep the test vat loaded. Confirm shell/glass
  remain visible and large vat bounds do not change at tier transitions. Edit
  ordered and misordered distances in the TOML, relaunch if needed, and verify
  the new normalized cutoffs. Client config generation/load and visual tier
  transitions remain for the user to verify. Nothing published or released.

Ports / topology compiler Slice 1, October 3, 2026
  Item Port: one local staging slot, insertion-only Input / extraction-only Output
  automation, GUI direction toggle. Fluid Port: ZeroCore delegated passive handler,
  shared controller-owned 4000 mB buffer and GUI direction toggle. Tool Port:
  generic one-item manual mount/retrieve, no GUI or I/O mode. All are optional
  thin functional shell panels. Tool Ports require cardinal side positions.
  Controller compiles active actor assignments, direct Item Input contacts,
  adjacent Tool IO endpoints and fluid direction subsets on relevant changes.
  Priority selects one compatible actor per specimen/use; rejected alternatives
  are discarded. No per-tick graph discovery or production process is introduced.
  Drain bucket output and the temporary milk timer are disabled. Legacy Head
  fields remain readable for compatibility. Build/resource verification and all
  eighteen autonomous headless GameTests pass. No client task was run.
  See 2026-10-03-ports-topology-notes.md for exact APIs, references, tests and manual checks.

Cow processes / item routing Slice 2, October 3, 2026
  MultiblockVat owns CowProcesses: per-Head replacement/milk timers, cached pending
  harvest loot and machine XP. CowBalance centralizes provisional defaults: 10-tick
  process cadence, 1000 mB milk / 1200 ticks, one wheat / 1200 replacement ticks,
  4000 mB shared tank. Only assembled active ticks count; no offline catch-up.
  NeoForge minecraft:milk is enabled at mod construction. Fluid Output exposes
  controller storage; Input remains generic. Non-milk fluid in the single buffer
  blocks milk until drained. Buckets transform and route, safely remaining in
  Tool Ports when outputs are blocked. Swords remain mounted and wear.
  Feeding uses compiled direct cardinal Item Input contacts; replacement runs
  IDLE -> GROWING -> READY -> sword harvest -> IDLE. No visible breeding entity.
  Empty loading connections are separate from active milk/harvest claims. Fixed
  priorities discard rejected extractors; no independent port/process ticking.
  Actual cow loot tables use transient non-added/non-ticking Cow/FakePlayer context.
  Looting, Fire Aspect, standard combat durability, Unbreaking and unbreakable item
  components apply. XP goes to a controller number; no XP orb is spawned.
  Output snapshots reserve aggregate capacity before harvest commits. Blocked
  loot persists across retries/reload unless the mounted weapon changes.
  Controller right-click shows milk, replacement progress and XP. Offline build,
  resources and all 28 headless tests pass. No client task ran. Optional client
  harness source now reads controller progress; its runtime remains unverified.
  See 2026-10-03-cow-process-notes.md for exact APIs, tests and user manual checks.

Controller GUI / presentation Slice 3, October 3, 2026
  Controller right-click now opens a read-only ZeroCore menu with Overview,
  Production, Efficiency and Diagnostics tabs. Physical placement/mode controls
  remain configuration. Controller metrics expose a 200-active-tick recent window
  for milk mB/s, routed items/s and XP/s, plus persistent lifetime input/output/
  harvest/durability ratios. Recent samples reset on reload; unformed time pauses.
  Compiled server anchors drive mounted actual-item rendering, a straight arm with
  a cosmetic post-commit extend/retract pulse, and cached feed hoses. FULL animates
  arms; REDUCED retains static arms/tools/hoses; MINIMAL remains specimen only.
  Controller network status excludes raw pending loot/weapon and topology graphs.
  Static anchors travel on Head changes/initial chunk sync; GUI values use sampled
  ZeroCore changed-value sync. No client or manual visual automation was run.
  Build/resource checks and all 33 autonomous headless GameTests passed.
  See 2026-10-03-presentation-notes.md for architecture, verification and user acceptance.

Kill output buffer / generic outputs, October 4, 2026
  Supersedes Slice 2 external-capacity harvest preflight: READY sword kills commit
  into a persistent controller-owned stack list, then pause all further kills
  until that list drains completely. Milk/feeding continue independently.
  Generic unreserved one-slot OUTPUT ports anywhere on the vat drain partially
  in deterministic position order; bottom outputs support hopper extraction.
  Physically valid actor-local outputs remain reserved, including empty Tool
  loading connections. Add a generic output for automation of buffered kills.
  Controller status distinguishes buffered harvest output from local workflows.
  Build/resource validation and all 39 required headless GameTests passed.
  See 2026-10-04-kill-output-notes.md for persistence, compatibility and detailed coverage.

Mechanism render corrections - October 4, 2026
  Controller-compiled feeding endpoints now target the neutral adult cow head-box
  center (model pivot plus box center), rotated to the placed specimen facing.
  The endpoint stays cached; head tracking does not rebake the hose each frame.
  Tool item planes run along the arm travel axis; vanilla generated swords have
  their handle-to-tip diagonal aligned with it. Models use their resolved
  third-person right-hand scale with no extra half-scale, and NONE display context
  avoids a second display rotation. Vanilla sword size is 0.85 instead of 0.5
  blocks per model unit (1.7 times the prior size). Custom models retain their own
  baked held scale; their semantic blade/grip axes cannot be inferred generally.
  Build/resource validation and all 39 headless GameTests pass. The controller
  presentation test checks the head endpoint against vanilla CowModel geometry.
  No client task ran; visual acceptance remains with the user.

Tool mount correction - October 4, 2026
  Supersedes the diagonal-to-axis rotation above: native image right now faces
  the specimen, keeping swords naturally diagonal. MobVats syringe is reversed
  so its left-side needle faces the specimen. The centered model is offset by
  half its held X scale to mount the rear image edge at the arm tip and prevent
  the standard item footprint from extending through the back of the panel.
  Held size is unchanged; no client task ran. User visual acceptance remains.

Sword grip attachment correction - October 4, 2026
  Sword-tagged items now mount the native grip near image pixel (3,3), measured
  from bottom left, at the arm tip. Both vertical and travel offsets compensate
  for the grip position at held scale, replacing the previous mid-height image
  boundary anchor that visibly detached the sword. Natural diagonal/right-edge
  orientation and the syringe reversal remain unchanged. Custom sword artwork
  may eventually need explicit grip metadata rather than the vanilla fallback.

Blender / Sewer Slice 4, October 4, 2026
  Blender is a reachable side-wall harvest actor with fixed priority 200 above
  Sword 100. It consumes the same READY replacement for 1000 mB MEAT and zero
  XP/vanilla drops/durability; full MEAT capacity retains READY and never falls
  back to a rejected Sword route. Item-kill backlog only gates item-producing
  Sword work, not Blender. Sewer is a cardinally adjacent bottom-face Drain tap,
  producing 250 mB WASTE per 1200 active ticks while its Drain claim is selected.
  Controller owns separate 4000 mB milk/MEAT/WASTE logical stores. Fluid outputs
  expose all three; amount drains round-robin a persisted shared cursor, while
  fluid-specific requests drain only the requested type. No filters or mixing.
  Added basic thin-panel fixture models, recipes, drops and tinted product fluids.
  Build/resources and all 47 required autonomous headless GameTests passed.
  See 2026-10-04-slice4-notes.md for exact physical rules, metrics, compatibility and tests.

Mixer physical construction, October 7, 2026
  Build a ZeroCore Mixer shell with exterior width/depth 3, 5, 7, or 9 and
  height 3..9. Keep exactly one Mixer Controller on a side face. Place exactly
  one Mixer Impeller in the center of the top face and at least one Mixer Shaft
  immediately below it. Shafts form a contiguous vertical column at the
  Impeller's X/Z and may stop early or reach the centered bottom-face cell.
  Gaps and extra/off-axis Shafts prevent formation. Keep storage/conduits clear
  of these cells.
  Craft an Impeller from Mixer Casing surrounded by four Iron Ingots; three
  vertical Iron Ingots craft three Shafts. Both parts drop themselves.
  The controller renderer anchors the mechanism to this physical assembly and
  uses its actual length. Paddles remain provisional. Existing recipe, storage,
  conduit and input/output behavior is retained; no power or motor is required.
  Build/resource verification and all 428 required headless GameTests passed.
  Client visual acceptance remains user-controlled.

Mixer processing capacity
  Processing slots = floor((effectiveDiameter - 1)^2 * shaftLength / 8).
  Effective diameter = nominal diameter - 2 * whole-block radius reductions.
  Only actual contiguous installed Shaft depth counts; full depth is height - 1.
  Full-depth widths 3/5/7/9 at heights 3/5/7/9 yield respectively:
    3x3: 1, 2, 3, 4; 5x5: 4, 8, 12, 16;
    7x7: 9, 18, 27, 36; 9x9: 16, 32, 48, 64.
  No separate maximum clamp or pixel/shaft/paddle capacity deductions apply.
  Precise canonical polygon and occupied-device envelopes determine formation
  and uniform radius reduction. The centered Shaft is exactly 6x6 pixels.
  Floor-shell inset remains physical/rendering clearance only. Geometric volume
  diagnostics and liquid presentation remain separate from processing slots.
  Assembly data persists/syncs diameter, reduction, depth and floor inset;
  slots recompute after loading and structural reassembly.
  Empty-hand status shows actual depth, effective diameter, geometric volume
  and processing slots. See docs/MIXER_GEOMETRY.md for the current contract.
  Ordinary interior edits trigger structure revalidation through block-change
  notifications; unchanged notifications and idle ticks retain the cached data.
  No drive, power, spin-up, cadence or recipe-parallelization changes are added.
  Build/resource verification and all 437 required autonomous headless GameTests
  passed. No client task ran.

Mixer / passive slurries Slice 5, October 4, 2026 (historical)
  Separate 3x4x3-minimum ZeroCore Mixer shell (max 9 per axis), one side Controller,
  casing frames/corners, glass sides and air interior; controller-rendered shaft
  and slow paddles. Fixed fluid/item ports and outward source-water Intake.
  Water Bucket -> Controller/Fluid Input returns Bucket; sneak-empty-hand
  Controller deposits up to 5 player XP points. Hopper plants into Item Input.
  Private controller stores commit recipes atomically on 10-tick cadence:
  Water 1000 mB + XP 5 -> Culture 1000 mB in 100 ticks;
  Water 1000 + WASTE 250 + tagged plant 1 -> Feeding 1000 in 100 ticks.
  Vat stores five logical fluids. Culture funds shared 4x milk/growth support
  (10 mB per 10 active ticks); Feeding costs 250 mB per replacement start and
  is preferred before existing physical Wheat feed. No mandatory slurry/feed port.
  Explicit-fluid and round-robin output draining remain; no filters or mixing.
  Build/resources and all 57 required headless GameTests passed. No client ran.
  See 2026-10-04-slice5-notes.md for structure, tags, ratios, XP/water paths, tests and
  user manual checks. No slurry transfer pipes/bucket items or hostile support.


Slice 6A — Conduit blocks and topology (October 4, 2026)
------------------------------------------------------
Added bare, glass-clad and casing-clad Item and Fluid Conduits. They have no BEs,
inventories, tanks or capabilities. Empty-hand right-click toggles TRUNK/TAP.
TRUNK traverses same-resource conduits only; TAP also attaches explicitly compatible
endpoints. Actor routes flatten direct and conduit endpoint lists in stable order.
Conduit-connected actor Item Outputs are reserved and excluded from generic kill
output drains. Physical specimen reach, direct feeding and controller shared fluid
storage remain unchanged. Vat/Mixer cladding rules and minimum-size validation
include passive conduit shell cells; structural changes queue ZeroCore revalidation.
Construction models are simple cubes with distinct mode caps. Slice 6B supplies
formed narrow geometry; obstruction/intersection remains deferred to 6C. See 2026-10-04-slice6a-notes.md for registrations,
recipes, diagnostics, verification details and the user manual acceptance checklist.

Slice 6A verification: build passed; all 71 required headless GameTests passed
(57 existing + 14 new). No client tasks were run. Changes remain local.

Slice 6B — Conduit rendering and endpoint geometry (October 4, 2026)
-----------------------------------------------------------------
Controller-owned snapshots now supply cached 6/16 pipe bodies and TAP fittings to
Vat Head / Mixer Controller renderers. Formed bare cubes disappear; glass/casing
cladding shares normal bounds-selected thin shell/frame geometry. Mixer preserves
its iron casing palette and shares existing continuous tank glass; functional Mixer
faces become shallow panels. No PNG art changed. Item single flanges and Fluid
paired rounded-square cuffs distinguish the networks beyond color.
EndpointGeometry separates offset backside service anchors from centered actor
interaction origins. Shared ConduitGeometry volumes include pipes and fittings for
future 6C intersection tests; they do not change validation or resource routing.
FULL/REDUCED render plumbing; MINIMAL/CULLED omit it. No per-conduit BE or renderer,
per-frame graph traversal, or new distance state. Build/resources and all 78 required
headless GameTests passed; dedicated server shut down normally. See 2026-10-04-slice6b-notes.md
for the full 13-point report, API, geometry limits, and manual visual checklist.
No client tasks ran. Manual visual acceptance remains pending.

Slice 6C — Physical obstruction and route validity (October 4, 2026)
-----------------------------------------------------------------
The controller now compiles narrow pipe/fitting intersections for required straps,
Tool arms, direct feed tubes and Blender paths. Blocked actor candidates are removed
before priority resolution; clear Sword routes can replace obstructed Blenders.
Cached harness obstruction pauses the affected specimen without breaking the vat.
Presentation and diagnostics follow the compiled validity; no per-tick/per-frame
intersection checks or specimen-body collision were added. Build/resources and all
88 required headless GameTests passed. No client tasks ran. See 2026-10-04-slice6c-notes.md
for the full report, geometry approximations, slurry-origin and harness-placement
limitations, test coverage and manual acceptance checklist. Manual acceptance remains pending.

Slice 7A — Generic processing engine refactor (October 4, 2026)
------------------------------------------------------------
MultiblockVat now owns VatProcessEngine with typed definitions, requirements,
per-process state, runtime contexts, effects and reversible private-resource
transactions. Cow processes are registered in-code in deterministic order; the
scheduler consumes cached topology claims and preserves existing tick/cadence,
Culture/Feeding Slurry, milk, wheat, replacement, Sword, Blender, Sewer, buffer and
metric behavior. CowProcesses remains only as a save/status compatibility adapter.
Legacy Cow saves migrate while extra generic process IDs persist. No new lifecycle,
species, datapack loader or rendering behavior was added. Build/resources and all
100 required headless GameTests passed (88 existing + 12 new); no client tasks ran.
See 2026-10-04-slice7a-notes.md for the full 13-point report and manual acceptance checklist.
Manual visual acceptance remains pending.

Slice 7B — Passive definitions and in-place lifecycle (October 4, 2026)
--------------------------------------------------------------------
Passive behavior now comes from deserialized config/mobvats/passive_mobs JSON.
Missing _defaults.json generates once; siblings replace whole definitions in
lexical order. Use /mobvats reload_passive_mobs (permission level 2) after edits.
Default Cow is Adult dairy without a harvest claim. Sword/Blender plus valid feed
harvests immediately and turns the same specimen Juvenile for 24000 active ticks;
milk/bucket use pauses until Adult. Culture retains paid 4x acceleration, Feeding
Slurry substitutes for Wheat, and output backpressure never consumes feed.
Cow behavioral compiler/executor removed; generic stage/state/strategies now own
processing. Old GROWING progress migrates proportionally from 1200 to configured
growth duration; old IDLE/READY maps Adult. Full report and manual checks are in
2026-10-04-slice7b-notes.md; ../examples/PASSIVE_DEFAULTS.example.json documents the generated schema.
Build/resources and all 111 required headless GameTests passed. No client tasks ran.
Manual visual acceptance remains pending.

Juvenile connection geometry follow-up (October 4, 2026)
------------------------------------------------------
Harness/body attachments, feed head targets and tool arm endpoints now adapt to
juvenile geometry. Calf bodies use half size; their unscaled heads use vanilla
CowModel's juvenile translation. Tool arms aim down from high wall ports. The
controller rebuilds/publishes paths on stage transitions and reload; obstruction
checks follow those same paths. Adult substrate reach eligibility remains stable.
Build and all 113 headless GameTests passed with -x createMinecraftArtifacts,
reusing the current mapped dependency JAR because the user's running client locked
it against regeneration. No client testing performed. Restart the development
client to load these Java changes, then check juvenile and adult endpoints manually.

Slice 7C geometry ownership (October 4, 2026)
--------------------------------------------
Essence and Head persist resolved dimensions, Head offset, facing, and body extents.
Head geometry claims exact cells; contiguous Flesh no longer discovers specimens.
Harnesses occupy unique footprint corners on one plane with up to four air/medium
spacers. A single Drain associates below the volume with bounded vertical/horizontal
offset; neighboring Sewer follows that Drain. Straps, rendering, actor targets, and
physical route checks share resolved geometry. Per-specimen comfort shells expose
intrusions and actual service/body geometry without changing throughput.
Standard legacy Cow geometry migrates without recapture; old oversized Flesh rods
must remove unowned cells or use explicitly larger geometry. One active specimen
remains the runtime limit. Full implementation report and user manual acceptance
checks are in SLICE7C_NOTES.txt. Build/resources and all 129 required headless
GameTests passed. No client task ran; nothing was published or released.

Slice 12.2 culture fouling and filtration
----------------------------------------
Recognized biological completions convert clean medium into dirty medium without
changing volume. Clean/dirty fractions share capacity; Drain Valves export dirty
first. Mixer charcoal/sand filtration removes waste while preserving culture
formulas and safely restoring ordinary wrappers. Frequency degrades by the clean/
dirty volume mix; happiness/yield is unchanged. Save migration and reconnection
preserve all fluid. Action volumes, frequency endpoints, filters, batch sizes,
timings, and transforms are configurable. See 2026-10-05-slice12-2-notes.md and the updated
../examples/CULTURE_PROFILES.example.json / MIXER_RECIPES.example.json. Build and all 412
required headless GameTests passed. No client task, publication, or release ran.

Handoff 1.2 Mixer visual vessel and first rotor
---------------------------------------------
Formed Mixers render an open polygon vessel from cached canonical geometry,
a centered shaft matching placed depth, and horizontal arms only at the first
Shaft level. A presentation-only active flag ramps rotation over 40 ticks to one
revolution per 200 ticks. Canonical polygon boundaries and uniform whole-block
radius reductions determine vessel and arm clearance. Recipe behavior is unchanged. Details and manual
acceptance are in HANDOFF1_2_NOTES.txt. Build/resource checks and all 443 required
headless GameTests passed; client visual acceptance remains user-controlled.

DEVELOPMENT ART / PUBLIC RELEASE BLOCKER
  The current Mixer uses development-only AI placeholder art. See
  2026-10-12-ai-placeholder-retirement.md and AGENTS.md. Original human-authored artwork is
  untouched; sources live in dev/ai-placeholder-art/ and Gradle generates the
  local resource overlay. build/libs JARs are development-only while blocked.
  Public release/publish/upload tasks must pass verifyNoAiPlaceholderArt.
  Run gradlew verifyNoAiPlaceholderArt publicReleaseValidation to check the
  blocker without publishing. This intentionally fails until all placeholders
  and references are explicitly replaced/removed. Normal build and GameTests work.

Mixer Recipe Schema v2
----------------------
Default culture: 1,000 mB water + 250 mB XP -> 1,000 mB culture slurry.
Machine XP quantities use mB throughout storage, resource requirements,
transactions and diagnostics. Configurable resource-process type "xp" uses
amount in mB for both inputs and outputs, like ordinary fluid resources.
Mixer consumption reports experienceConsumedMb; resource-process production
reports experienceProducedMb. Vanilla player/mob rewards convert at the boundary.
A player deposit covers one recipe requirement using whole vanilla points and
preserves all converted fluid: at 20 mB/point, 13 points deposit 260 mB, and a
250 mB culture batch leaves 10 mB available for subsequent batches.
Runtime startup/reload creates missing Mixer _defaults.json from bundled data
and never rewrites existing configuration. Normal development Gradle builds
invalidate only run/config/mobvats/mixer/_defaults.json, including up-to-date
builds. Custom JSON siblings remain untouched. Linked paths are refused.
Production installations are not subject to this Gradle housekeeping.
Recipes support authored recipe/strategy priorities, alternative atomic input
strategies, and atomic item/fluid output bundles. Legacy JSON remains readable.
Bundled defaults use negative priorities. Item output ports export produced
items from separate persisted output slots. Reloading a replaced/removed active
recipe resets its progress before execution. See 2026-10-10-mixer-recipes-v2.md for schema,
transform semantics, capacity ordering and reload behavior.

## Shared boundary ports

Machine item/fluid ports persist ZeroCore `IoMode` (`Active`, `Passive`, `Both`,
`None`) in `ioMode`. Missing, empty, or unrecognized modes default to `Both`,
including portable pick/drop/place state. Both enables passive capability access
and active boundary transfer. Mode does not change internal
Input -> Store / Output -> Consumer roles, filters, or retained staging.

Direction/filter interaction is unchanged. Mode is persisted configuration and
shown in inspection/Jade; there is no mode GUI.
Passive/Both permit external insertion/extraction; Active/None reject passive
boundary transfers. Only Active/Both discover outward adjacent capabilities on
assembled ports. Vat's existing unassembled item staging access is preserved.

Both machines use `PortLifecycle` for association, assembly/break, configuration,
and neighbor refresh. Neighbor events refresh connections/rendering without
rebuilding topology or notifying back. Only direction/filter changes request a
configuration-driven topology rebuild. ZeroCore's fluid mode is immutable and its
remote source cannot be cleared, so `MachineFluidPort` retires the old delegate
and replaces the handler on refresh, retaining one stable external forwarder.
Item connections reuse ZeroCore's source helper. Controllers own active cadence.

Removal, chunk unloading, and server shutdown clear owned connection/delegate
state without reacquiring unloading chunks through capability invalidation.
The full headless GameTest run also verifies normal server shutdown.

Slice 2: controller-driven active boundary transport

Active = active transport only; Passive = passive capability only;
Both = both; None = neither. Item input remains passive only (Active input
ports do not pull items). All active paths use the existing filtered machine
views and preserve complete item/fluid components. Internal ResourceEndpoint,
Producer/Consumer/Store roles, reservations and operation bundles are unchanged.

Assembled Vat and Mixer controllers call ActiveBoundaryPort.tick every server
tick, independently of process/drive cadence. Ports are sorted in x/y/z order;
the starting index rotates deterministically with game time. Each fluid port
can move at most 1000 mB per controller update. This bounded per-port policy
reduces starvation without adding a balancing network or changing Vat timing.
Handlers own HOW (neighbor discovery and transport); controllers own WHEN and
HOW MUCH. Item output ejects existing output staging through the same update
path, retaining the exact remainder. Passive extraction remains available in
Passive/Both. Server-thread hopper extraction and active ejection consume the
same staging inventory sequentially; no separate output copy is created.

MachineFluidPort uses ZeroCore's IOPortBlockCapabilitySource discovery pattern
and the same local filtered delegate used for passive I/O. ZeroCore 2.4.18 and
the current upstream 1.21 FluidPortHandlerForge inputFluid/outputFluid helpers
reject isPassive(), which includes Both. MobVats therefore uses a separate
ZeroCore neighbor source with NeoForge FluidUtil.tryFluidTransfer rather than
calling those helpers. The upstream handler and stable passive forwarder remain
in use. Typed per-tank requests skip rejected fluids and keep exact components;
NeoForge simulates drain/fill before committing accepted amounts. A full or
rejecting destination leaves the source unchanged. No upstream code is forked.

For directly adjacent machine fluid ports, output-side push owns transport when
the output mode is Active/Both. The adjacent input suppresses pulling from that
output, independent of controller tick order. Active/Both input may pull from
Passive output. Active-only ports expose no passive capability, so transport
requires a passive-capable counterpart (Passive/Both). Both/Both uses one push
path and cannot transfer again through input-side pull in the same update.

Configuration and lifecycle refresh stop active transfer on mode/direction
changes, removal, detachment, break and chunk unloading; repair and replacement
restore discovery. Active transport does not acquire unloaded neighbor chunks.
Direct adjacency requires two independently valid structural envelopes. Vat and
Mixer cladding now keep those envelopes separate even when they touch; inward
TAPs connect ports to each machine's storage.

Machine-specific conduit and buffer shells
-----------------------------------------
Bare item/fluid conduits and bare fluid buffers are shared interior blocks.
Casing and glass variants carry a Vat or Mixer shell identity. Structural
traversal, bounds and validation accept only the containing machine's family;
bare blocks never continue a shell. Resource routing still follows resource
type and TRUNK/TAP mode within each controller's bounds. Adjacent Vat/Mixer
ports can transfer directly without merging their envelopes.

There are ten conduit blocks (twenty TRUNK/TAP items) and five fluid buffers.
Mixer casing variants use the existing Mixer casing palette; conduit geometry
and fluid rendering are retained. Craft Mixer cladding with Mixer casing/glass;
conduit shell conversion recipes preserve TRUNK/TAP mode.

Existing cladded registry IDs retain their saved blocks and now mean Vat shell.
Existing Mixer machines using those blocks require Mixer-clad replacements.
Filled buffers retain their portable contents when broken and placed with the
same family; empty Mixer buffers have separate crafting recipes.

Mixer structural enclosure
--------------------------
Mixer Casing and Mixer Glass are legal throughout the outer shell, including
top/bottom faces, all edges and corners. Mixer-clad conduits and fluid buffers
replace the corresponding shell material in those same positions. Vat cladding
remains incompatible. Validated envelopes fence passive structural traversal,
including while shell replacement triggers revalidation.

Keep one side Controller, one centered top Impeller, and a contiguous centered
Shaft. Any installed Drive must remain horizontally adjacent to the Impeller.
The Impeller and all Drive variants retain full solid cube models when formed;
the shaft/rotor animation remains separate. Dimensions and drive requirements
are unchanged. Glass rims follow the full Mixer enclosure perimeter.

Interior cladded conduits/buffers are invalid. Bare item/fluid conduits and bare
fluid buffers remain structurally legal interior infrastructure. Physical fit
uses the shared polygon and actual buffer/conduit occupied volumes. Intersections
shrink the entire vessel in one-block radius steps; formation fails if none clears.
There are no local obstruction deductions. Bare and clad pipe positions are
identical for the same cell. See docs/MIXER_GEOMETRY.md and its generated charts.
Recipes, liquid simulation and resource routing retain their existing behavior.

MixerShellGameTests cover glass and passive infrastructure across every shell
cell, running solid machinery, interior rejection/recovery and exterior bridges
between formed Mixers. The placement matrix checks every cell against both the
shared policy and structural bridge for each cladding family, resource and buffer
variant. Mutation tests reject all Vat-clad variants at a Mixer corner and bare
infrastructure at shell faces, edges and corners, then verify shell recovery.
Client visual acceptance is operator-controlled: inspect
the enclosure from above, machinery solidity, the oxidized copper green vessel,
integrated cladding and visible bare interior conduits. Headless tests cannot
verify final on-screen appearance.

Architecture sources and upstream notices: [ATTRIBUTION.md](../../ATTRIBUTION.md).

Mixer world-fluid pumps
----------------------
Mixer Pull Pump: world source -> reachable physical Fluid Buffers.
Mixer Push Pump: reachable physical Fluid Buffers -> world source.
Fluid Port: machine <-> adjacent fluid capability.
Fluid Void: explicit disposal.

Pumps operate once per 10 ticks in a formed Mixer, moving exactly 1,000 mB.
Place on a boundary face with a fluid TAP directly inward; side-adjacent TAPs
and TRUNK connections do not attach. Filters use the existing shift-use fluid
container sampling convention and persist through saves and dropped items.
Pull requires full physical storage capacity and preserves renewable water
sources. Push validates a bounded loaded-cell placement search before consuming
fluid, and rejects component-bearing identities and unsupported world fluids.
Pumps never exchange fluid with adjacent machine capabilities or recipe state.
```
