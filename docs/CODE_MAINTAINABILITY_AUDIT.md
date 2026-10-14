# MobVats code maintainability audit

Audit date: 2026-10-13. Baseline: `1959f89f80ce82612f61101c372b28b7614ed2ef`.

**Formatter milestone, 2026-10-13:** The JSON artwork extraction resolved
P1-A's lexical coupling. Repository-wide normalization from clean revision
`d91425eb02b5be5cf9d7fe1acb4177b9e96d8c88` formatted the remaining 315 Java
files; all 322 authored Java files now pass pinned Spotless 8.10.4 / Palantir
2.102.0 and are enforced through `check`. P2-A's layout debt is addressed;
broader import policy and all structural/ownership findings remain separate.
Code tokens/literals are unchanged apart from formatter import cleanup and
the separately user-authorized removal of Java-source-text assertions from
one CPU test. All 766 required GameTests passed; full build, procedural preview
and immutable art hash comparison passed after that test-only adjustment. See
[CODE_STYLE.md](CODE_STYLE.md) and
[CODE_STYLE_MIGRATION.md](CODE_STYLE_MIGRATION.md). The source measurements,
line references and ranked findings below describe the original read-only audit;
formatting has moved those line locations and does not resolve architecture.


MobVats has useful domain boundaries and unusually substantial regression evidence, but compressed source obscures those boundaries. The highest-value cleanup is mechanical formatting followed by narrowly scoped ownership and planning improvements. A wholesale controller, routing, process-engine, or rendering rewrite would discard working contracts without addressing the main readability problem.

This was a read-only source audit apart from this document. No code, tests, dependencies, Gradle configuration, generated assets, or existing files were changed. No build, test, server, client, clone, installation, commit, push, or publication was performed. Test references below identify evidence to reuse; they do **not** claim a current passing run.

## 1. Workspace inventory and limitations

### Checkout and tools

| Item | Observation |
|---|---|
| Workspace | `C:\Projects\MobVats`; this outer folder is not a Git repository |
| Mod repository | `C:\Projects\MobVats\src` |
| Remote | `https://github.com/ImpactVector/MobVats.git` |
| Branch / SHA | `main` / `1959f89f80ce82612f61101c372b28b7614ed2ef` |
| Initial working tree | Clean: `git status --short` emitted no entries from the mod repository |
| Java requirement | Java 21 toolchain in `build.gradle:9` |
| PATH Java | Oracle Java 17.0.11; suitable to start this Gradle generation, not the required game/compiler toolchain |
| Cached Java 21 | `../.codex/gradle/jdks/eclipse_adoptium-21-amd64-windows.2/release` identifies Temurin 21.0.12.1+1-LTS; metadata inspected, not executed |
| Gradle | Wrapper pins 9.2.1; matching distribution exists in `../.codex/gradle/wrapper/dists/`. No global `gradle` command found |
| Minecraft / NeoForge | 1.21.1 / 21.1.252 (`gradle.properties:7`); ModDev plugin 2.0.148 |
| ZeroCore2 | External `curse.maven:zerocore-247921:6648746`, documented as 1.21.1-2.4.18 (`build.gradle:100`). Its binary JAR is cached |
| Toolchain resolver | Foojay resolver convention 1.0.0 in `settings.gradle`; potential downloading behavior was not invoked |
| Python | PATH resolves to the WindowsApps `python.exe` alias; a working interpreter/version was not established |

### Upstream references and external dependencies

Discovery followed actual workspace contents, documented relative paths, and build declarations. It did not assume that another project checkout existed at a conventional absolute path, search the entire drive, or fetch missing references.

* Selected XR2 source is available in `C:\Projects\MobVats\.codex\references\reactors`: 15 Java files plus build metadata, including Reactor/Turbine controllers, part types, individual entities, and client rotor models/renderers. Metadata targets Minecraft 1.21.1 and XR2 2.4.27. The companion cached tree records SHA `f6cc65140a8abbfc87137f5c54fc641085f66048`.
* Selected ZeroCore2 source is available in `C:\Projects\MobVats\.codex\references\zerocore`: 27 Java files plus build metadata, including controller, part, cuboid, lifecycle, and model contracts. Metadata targets Minecraft 1.21.1 but identifies **2.4.17**, whereas the dependency is **2.4.18**. The companion tree records SHA `64d892b1ba099192a35bfa4ee08465540a2f12e7`. These are partial cached references, not complete verified checkouts of the exact runtime revision; companion tree SHAs do not independently authenticate every flattened file.
* NeoForge 21.1.252 source JAR and Minecraft/NeoForge combined artifacts are cached under `../.codex/gradle/caches`. `../codex-work` contains selected Minecraft/NeoForge Java references and a ZeroCore GlassBlock reference. `../examples` contains old MobVats asset folders, not current XR2/ZeroCore source checkouts.
* The documented sibling `../workspace/AGENTS.md` and `../workspace` checkout are **missing**. Portable reference instructions are unavailable. This is an environment/documentation limitation, not evidence of defective MobVats lifecycle code.
* No composite source build, `includeBuild`, or external Java project dependency is declared by the inspected main build/settings. Required APIs come from Minecraft, NeoForge, and the published ZeroCore binary. Jade 15.10.6+neoforge is compile-only; manual-client runtime adds Jade, Pipez neoforge-1.21.1-1.2.31, Building Gadgets 2 1.3.9, and JEI 19.57.0.451 (`RI8WCow6`). They are not normal GameTest runtime requirements.
* `benchmarks/` is a separate development Gradle project, with six Java files. It consumes `../build/libs/mobvats-0.1.0.jar` or the published XR2 artifact `curse.maven:extreme-reactors-250277:7293725`, and ZeroCore. The hardcoded MobVats JAR filename is a maintenance dependency on the current mod version (`benchmarks/build.gradle:22`), not a production dependency.

### Existing checks and instructions

There is no configured Spotless, Checkstyle, PMD, SpotBugs, Error Prone, or JUnit dependency in the inspected builds. No dedicated contributor guide, EditorConfig, or CI workflow was found in the tracked inventory. Java compilation supplies baseline compiler checks. `git diff --check` is documented but is not a Java formatter or structural analyzer.

Seven `src/test/java` files implement CPU checks using Java entry points/`verify()` methods and explicit assertions. `build.gradle:12` wires `renderBufferTest`; `build.gradle:21` wires `mixerShaftGeometryTest`; `gradle/procedural-art.gradle:1183` wires `conduitArtGeometryTest`. The latter calls the shell-ownership, fixture-model, feeder, and buffer geometry checks. These classes are excluded from ordinary Gradle `test` discovery and run through custom tasks. Consequently, running `test` alone does not establish that these CPU checks ran. `check` also includes resource/provenance validation and development-hook exclusion. GameTests and acceptance-world generation are separate server tasks, not implied by a normal `build`.

Relevant reading order:

1. [Repository instructions](../AGENTS.md), outer `../AGENTS.md`, and [README](../README.md). The repository additionally requests the missing sibling instructions.
2. [Architecture and authoritative ownership](01-ARCHITECTURE.md), [multiblock contracts](02-MULTIBLOCKS.md), [resource routing](03-RESOURCE-ROUTING.md), and [processing](04-PROCESSING.md).
3. [Procedural art](05-PROCEDURAL-ART.md), [development](06-DEVELOPMENT.md), [testing and manual fixtures](07-TESTING.md), [configuration examples](examples/README.md).
4. [History index](history/README.md), particularly lifecycle parity, XR2 performance baseline, Mixer capacity, and unified cladding records. Historical validation is contextual evidence, not a current result.
5. [Attribution](../ATTRIBUTION.md), `LICENSE`, and [benchmark guidance](../benchmarks/README.md).

The outer instructions still name `src/AI_PLACEHOLDER_ASSETS.md`; current repository instructions point to `docs/history/2026-10-12-ai-placeholder-retirement.md`. Future documentation cleanup should reconcile this drift while preserving the mandatory public-art guard. This audit does not perform or authorize release validation. IntelliJ, Minecraft runs/worlds, build output, Gradle caches, and Python generated files are covered by `.gitignore`.

### Scope and confidence

The review combines repository-wide Java searches/measurements with detailed reads of representative paths in every major system, including registration/configuration, capture/items, specimen serialization, compound fluids, Jade, render/model code, process adapters, fixtures, and benchmarks. It is not a formal proof of every method or a runtime correctness certification. No AST analyzer was installed; method spans below are source spans, not cyclomatic complexity measurements.

Locations use the audited baseline's line numbers. To keep references readable, these prefixes denote exact repository-relative directories:

| Prefix | Directory |
|---|---|
| `J/` | `src/main/java/net/impactvector/mobvats/` |
| `V/` | `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/` |
| `M/` | `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/` |
| `C/` | `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/` |
| `B/` | `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/common/` |
| `T/` | `src/main/java/net/impactvector/mobvats/gametest/` |

## 2. Human readability

### Measurements

All 308 Java files under `src/main/java` were included in the textual scan. The 71 files in `gametest` are development code and excluded from the player JAR; the remaining 237 files are the production-source population for these measurements. Client files are assigned first to the client group, so the table groups do not overlap.

| Group | Files | Physical lines | Lines longer than 140 characters |
|---|---:|---:|---:|
| Production client packages | 27 | 2,006 | 120 |
| Conduit, excluding client | 33 | 1,726 | 207 |
| Mixer, excluding client | 38 | 2,132 | 314 |
| Vat, excluding process/client | 44 | 2,593 | 278 |
| Vat process | 47 | 2,474 | 329 |
| Other production: registration/config/items/mob/fluids/integration | 48 | 2,161 | 186 |
| **Production total** | **237** | **13,092** | **1,434 (11.0%)** |
| Development GameTests/fixtures/hooks | 71 | 11,738 | 2,437 (20.8%) |

Production contains 599 lines with at least three literal semicolons, versus 1,344 in GameTests/fixtures. There are 370 wildcard import lines in production and 544 in development GameTest sources. Semicolon counts include `for` headers, strings, and comments; they are a density indicator, not an exact count of multiple executable statements. Line length likewise includes strings/comments. Counts exclude generated build Java, cached upstream references, the seven CPU-test files, and the separate benchmark project.

`MultiblockVat` is 370 lines, `MultiblockMixer` 308, and `MixerState` 264. Their longest lines are respectively 541, 790, and 636 characters. Short physical files therefore do not establish small responsibilities. `ProductionWorldFixtures` is 513 lines; `VatGameTests` 765 and `CowProcessGameTests` 725.

### Representative findings

| Location | Example and assessment |
|---|---|
| `M/MultiblockMixer.java:190`, `:306`, `:307` | Assembly and both serialization methods fit whole operations on individual lines. Primarily formatting: expand before deciding whether helper extraction is needed. |
| `C/PhysicalFluidStorage.java:70`, `:73`, `:75`, `:124` | Cluster BFS and ordered tank fill interleave declarations, mutation, loops, and early exits. Formatting obscures the actual storage policy; cluster invalidation is a separate structural concern. |
| `C/ResourceRouting.java:98`, `:106`, `:114` | Complete/publish/pull contain nested loops and accumulating reservations on very long lines. Shared iteration concepts may merit helpers after formatting, but their different ownership policies must remain explicit. |
| `V/process/PassiveOperations.java:31` | `replace` spans 49 physical lines yet handles feed selection, actor priority, weapon damage, persisted death loot, legacy state, output capacity, XP, juvenile replacement, and metrics. This is a structural decomposition candidate, not just whitespace. |
| `M/process/MixerState.java:142` | The strategy planner spans 41 physical lines covering fluid allocation, item allocation, composition transforms, private output slots, physical/service capacity, and disposal. Stage-specific planning helpers would make review safer. |
| `M/process/MixerState.java:108` | Max-flow allocation is only 11 physical lines. Names such as `r`, `i`, and `n` are locally reasonable mathematical indices, but graph-node layout and residual-flow invariants need explanations. Do not replace the algorithm with a greedy loop. |
| `V/client/render/VatHeadEntityRenderer.java:91` | Rendering combines visual creation, pose/animation state, straps, mechanisms, scale correction, buffer submission, and renderer dispatch. Responsibilities are more significant than the line count. |
| `J/gamecontent/item/CaptureTransaction.java:40` | Planning uses explicit before/after inventory snapshots and commit validation. It is denser than desirable but has a readable transaction boundary worth retaining. |
| `J/gamecontent/mob/CapturedSpecimen.java:20`, `:35` | A narrow persistence whitelist and defensive copies communicate ownership. This is a useful model for other mutable API returns. |

Names generally express domain concepts well: `PhysicalFluidStorage`, `ResourceReservation`, `SpecimenClearance`, and `PortLifecycle` are useful. The most problematic names hide mutation or preserve historical implementation:

* `M/process/MixerState.java:205` calls a mutating planner from `canCommit`; planning sets `blocked`. `workingFluid` at line 50 saves/restores that field around planning. The `try/finally` preserves the advertised presentation behavior, but reveals coupling between a query and diagnostic state.
* `V/MultiblockVat.java:94` accepts `processStateChanged(boolean sync)` but does not use `sync`. Callers appear to choose synchronization semantics that do not exist. Removal/renaming requires a caller review, not a change to the actual ZeroCore dirty/update behavior.
* `C/PhysicalFluidStorage.java:47` calls `service.tank(request)` from `outputTanks`, and `selectOutput` at line 114 can create a service tank. `prepareOutputs` deliberately uses pending staging instead. Similar-looking selection APIs therefore differ in mutation.
* `V/process/VatProcessEngine.java:30` embeds feeding eligibility in `toolTurn`; `V/process/PassiveOperations.java:17` embeds actual feed planning. These repeat the slurry-family preference/item-feed conditions in different forms. One is a scheduling predicate, the other a consumption plan; unify only their definition-level eligibility, not reservation state.
* `M/process/MixerState.java:81` uses legacy numeric fluid indices for compatibility/UI. Tests such as `T/MixerOperationGameTests.java:81` depend on `amount(0)` through `amount(3)`, making generic recipe behavior look like fixed species/product mechanics. Keep persisted compatibility while introducing named test assertions.

Dense conditions deserve named local decisions: the class/role test in `C/ResourceRouting.java:96`, nested part-position ternary in `C/ConduitStructure.java:48`, transform/source candidate filter in `M/process/MixerState.java:129`, and actor-selection condition in `V/process/PassiveOperations.java:41`. Streams are suitable for sorting and immutable projections. Stateful stream callbacks, nested `anyMatch`, and whole-operation lambdas in `V/process/PassiveProcessCompiler.java:27` obscure control flow; use named operations when the lambda describes a business operation rather than a projection.

Constants are mixed. `CowBalance`, `SlurryBalance`, `FluidBufferEntity.CAPACITY`, and `WorldFluidPumps` already centralize meaningful balance values. Less clear values include `slotCount()*200` (`M/MultiblockMixer.java:55`), a 1,000 mB boundary budget (`B/ActiveBoundaryPort.java:20`), legacy five-fluid indices, bit masks/ordinal clamps (`C/ConduitGeometry.java:24`), and fixture coordinates. Name units and purposes, but retain intentional pixel coordinates, bounded protocol values, and fixture layout data rather than manufacturing a constant for every number.

Comments are often strong: server-thread transaction limits in `V/process/ProcessTransaction.java:7`, unload notification rationale in `M/MultiblockMixer.java:237`, and component-preserving feed rationale in `V/process/PassiveOperations.java:19`. Two misleading claims need correction: `VatBoundaryEntity` calls itself “Stateless” despite filter/status/pulse state (`V/part/VatBoundaryEntity.java:16`); `MixerState` says controller NBT holds “only legacy escrow” while `save` persists inventories, progress, signatures, and metrics (`M/process/MixerState.java:18`, `:239`). Intent comments must match authoritative ownership.

## 3. Architectural maintainability

### Ownership and boundaries

The established XR2 organization is present: family controller, part type, individual entity, and client package. Cached XR2 Reactor/Turbine controllers demonstrate explicit activation and lifecycle ownership; their length is not a reason to imitate all their implementation. Cached ZeroCore `lib/.../AbstractMultiblockController.java` handles pause around line 192, assimilation around line 297, and restore around line 1332. MobVats should continue using these contracts rather than introduce another multiblock manager. Exact 2.4.18 behavior remains subject to the reference limitation above.

| System | Ownership that works | Maintenance difficulty |
|---|---|---|
| Vat controller | Assembly, retained medium, aggregate metrics; Heads own specimen identity; engine owns per-Head process progress | `rebuildTopology` also configures mutable service capacities, migrates medium, refreshes capabilities, computes clearances, and publishes presentation (`V/MultiblockVat.java:171`). The name understates its work. |
| Mixer controller | Geometry/assembly, drive lifecycle, controller-owned scheduling | Drive spin-up and `runningCadence` differ from controller intake `cadence` and recipe progress. Assembly, capabilities, compound presentation, and resource binding share the controller. |
| ZeroCore bridge | `ConduitCuboidController` adapts passive structural blocks, defers notifications, respects pause | Framework protected state `_shouldCheckForDisconnections` and registry adaptation are concentrated in `C/ConduitCuboidController.java:73` and `C/ConduitStructure.java:21`; document these as version-sensitive integration seams. |
| Conduit topology/routing | Topology caches physical connectivity; routing chooses live role/filter candidates; owned reservations commit | Boolean input/bulk flags, direct-adjacency fallback, mutable reservation maps, and anonymous synthetic cluster endpoints make policies hard to compare. Do not make stores tick or make connectivity recipe-specific. |
| Physical storage | Each Buffer persists contents; `ClusterTank` writes through members; service staging is endpoint-owned | Container interaction, controller storage compilation, and presentation discover different member sets. Cached cluster identity and exact filter changes must be reasoned about separately from cached conduit connectivity. |
| Specimen engine | Per-Head UUID ownership, definition compilation, cadence and actor round robin | Compilation can rebuild topology; execution can change Head stage, rebuild routes, and replace the current context mid-definition iteration (`V/process/VatProcessEngine.java:162`). Reentrant invalidation is difficult to follow. |
| Mixer recipes | Definition parsing, precedence, signature binding, owned atomic resource plans | `MixerState` mixes allocator, planner, diagnostics, migration, inventory adapters, persistence, execution, and metrics. Pure planning is the best first structural boundary. |
| Boundary devices/capabilities | Explicit world/medium crossings; shared `PortLifecycle` guards unloading and loaded neighbors | Family adapters duplicate capability wiring; Fill/Drain diagnostics and commit exceptions differ from process transactions. External capability simulation is intentionally not rollback authority. |
| Client rendering/sync | Detached visuals, server-derived descriptors, distinct FullSync/NetworkUpdate | Cross-family code lives under Vat client packages: conduit rendering imports Vat cables/feeder art, and common `FixtureModel` uses Vat casing state. This is discoverability debt, not proof that dedicated-server classes load client code. |
| Procedural art | Declarative input recipes, deterministic output/provenance, Gradle integration | One 1,786-line Groovy file mixes primitives, models, previews, tests, resource packaging, and Java-source parsing. Separate concerns without changing generated hashes. |
| Tests/world builders | Shared geometry fixtures and actual production observation; dev hooks excluded from player JAR | Historical “Slice” naming, static session state, coordinate-heavy setup, and monolithic verification make mechanics hard to discover. |

Public implementation access is extensive: `V/MultiblockVat.java:64`, `:70`, `:76`, `:127` expose mutable output, metrics, engine, and storage; `M/MultiblockMixer.java:75`, `:99` expose mutable storage/state. `C/FluidBufferEntity.java:47` exposes a public mutable tank. In contrast, `C/ConduitTopology.java:18` returns a map made immutable by its constructor at line 39. Narrow mutable access only after classifying production consumers versus diagnostics/tests; capability facades and immutable status snapshots already provide a pattern. Do not abruptly hide APIs that persistence, adapters, or fixtures legitimately use.

### Five end-to-end traces

#### 1. Fluid through a Vat Fill Valve

External fluid first arrives through `V/part/VatFluidPortEntity` and its registered capability (`V/VatPortCapabilities.java:13`), or through a Pull Pump. The Fill Valve itself is a medium-transfer device, not an external FluidHandler registration. Physical buffers/staging retain that incoming fluid.

`V/MultiblockVat.java:294` evacuates service staging, then calls `BoundaryDevices.tickOrdered`. `V/BoundaryDevices.java:29` builds a connected storage supply; `:34` dispatches FillValve; `fill` at line 45 scans candidates, applies the Valve filter and medium rejection policy, simulates capacity/source drain, then drains the bus and fills `VatMediumStorage`. Buffer writes mark physical parts dirty; medium mutation calls the controller change callback. Valve-owned diagnostics are updated via `fillDiagnostic`.

This crossing is intentional. Difficulty comes from numeric diagnostic stages and the hidden selection/mutation inside handler wrappers. `V/BoundaryDevices.java:66` throws after a source mismatch, and line 67 throws after a medium mismatch: these are invariant failures, not reversible external transfers. Preserve the server-thread/private-storage assumption; do not expand this path to arbitrary external handlers without a transfer design. Existing evidence: `T/VatFillDiagnosticGameTests.java:102` (`finiteWaterThroughCowVatRoute`), `:103` (`mixerProducedCultureThroughCowVatRoute`), and BoundaryDevice/PumpExterior tests.

#### 2. Mixer recipe selection and execution

JSON loading/whole-file validation feeds `MixerRecipeRegistry`, whose `recipes` sorts priority descending then ID ascending (`M/process/MixerRecipeRegistry.java:22`). Strategy order is normalized in definitions. `M/MultiblockMixer.java:244` runs assembled storage/boundary work; `tickDrive:258` owns drive availability, spin-up, and running cadence. Only a running enabled motor calls `MixerState.cadence`.

`M/process/MixerState.java:229` rebinds/reset-checks active definitions, finds an executable recipe, retains progress during blocking, and advances by cadence. `plan:142` allocates overlapping item/fluid demands, derives component-aware outputs, reserves separate private item outputs and real/service fluid capacity, then terminal overflow. `Reservation.commit:194` checks registry identity, storage binding, connected tank identity, destinations/filters, and resource snapshots. `ResourceReservation.commit` applies owned writes; only success publishes service staging/disposal and records metrics. State persists definition signature, inventories, progress, and migration escrow; ports export output identities.

This indirection supports necessary atomicity. The removable coupling is diagnostic mutation in planning and placement of all planner stages in one method. Preserve max-flow allocation, exact component identity, output order, signatures, and single-use reservations. Evidence: MixerOperation tests for overlapping selectors, late output blocking, stale reservations, and setter rollback; MixerFluidVoid tests for atomic overflow; MixerFeDrive/Combustion/Drive tests for cadence and startup.

#### 3. Specimen feeding and harvesting

`CapturedSpecimen` is encoded into a Head; structure validation builds its `MobAssembly`. `VatTopologyCompiler.compile:20` chooses Feed/Tool/Sump assignments and physical paths. `VatProcessEngine.compile` binds specimen UUID/state and profiles; its persisted head cursor and `toolTurn` arbitrate shared actors.

`PassiveProcessCompiler.java:46` schedules acquisition, Blender replacement, and ordered interaction definitions. `ToolInteractions.acquire:19` builds definition-owned tool demands; `execute:40` applies cooldown rules and dispatches the registered executor. The `weapon_harvest` adapter in `ToolInteractionExecutorRegistry` delegates to `PassiveOperations.replace:31`.

Feed planning prefers the configured slurry family with its actual components, then item inputs. Weapon damage and health tracking commit before final replacement; rolled loot is persisted as pending output so backpressure does not reroll it. Final replacement reserves result storage and XP, consumes feed, transitions to juvenile, updates yield/metrics, and clears completed pending state. Tool-local results leave through `V/process/VatProcessEngine.java:120` (`transferCommittedOutputs`), routing and configured output ports. The engine detects stage/claim changes and recompiles contexts.

The concern is distributed responsibility: kill work lives behind a registry adapter, feed eligibility appears in scheduling and consumption, and output publication is engine code. These are not redundant processes. Preserve separate acquisition, damage, pending-output, and replacement phases. Evidence: CowProcess, MultiSpecimen, ToolInteraction, PassiveDefinition, DynamicResource, and Slice11 tests, plus actual-feed/returned-slurry production acceptance.

#### 4. A Fluid Buffer joins a compatible cluster

On assembly/topology refresh, each controller enumerates buffers within its validated bounds and calls `PhysicalFluidStorage.compile:68`. Position-sorted BFS groups cardinal neighbors with equal filters and compatible exact fluid/components; an initially empty group acquires its first nonempty identity. `ClusterTank:79` sums member contents and redistributes committed writes deterministically into the member tanks, retaining per-block persistence. `bulkConnected:92` caches connectivity candidates but rejects removed members live.

For container use, `C/FluidBufferEntity.java:61` (`interactionCluster`) first BFS-enumerates adjacent Buffer entities, then invokes the same compiler. For visuals, `C/FluidBufferPresentation.java:30` (`refresh`) separately discovers loaded members and fences them by machine owner before using that compiler. Filter changes notify `ConduitStructure`; contents changes queue presentation and Mixer geometry checks.

Sharing the compiler is good; differing discovery rules are not self-evidently equivalent. Interaction discovery lacks the loaded-chunk and owner checks present in presentation. Cluster getters refresh amounts but assume identities remain compatible; stale grouping after a direct member identity change should also be characterized. Existing tests prove static compatibility and explicit recompilation, not every retained interaction/controller view transition. Evidence: `T/Slice11GameTests.java:55`, `:59`, `:65`, `:67`; `T/FluidBufferArtGameTests.java:44`; `T/ConduitPerformanceGameTests.java:70`.

#### 5. Multiblock unloading and restoring

ZeroCore part unload causes controller pause. Vat pause (`V/MultiblockVat.java:271`) drops assembly/derived routing/storage/contexts without changing Head presentation or committed resources. Mixer pause (`M/MultiblockMixer.java:235`) forgets neighbor snapshots, stops presentation silently, and retires geometry/storage/attached-role collections. `PortLifecycle.beginUnload/refresh` suppresses capability/world notifications that could reacquire unloading chunks. Queued conduit work rejects paused controllers at enqueue and delivery.

FullSync loads persisted controller/part state; `onMachineRestored` uses the assembly path to rebuild derived state and rebind storage. Assimilation merges committed controller state before the donor retires runtime state. Heads retain specimen identity, physical parts retain tanks/inventory/tools, and transient drive startup resets.

The broad design follows ZeroCore. Cleanup must preserve the distinctions among pause, disassembly, donor retirement, activation, and client refresh. Compressing them into a universal teardown helper would hide deliberate differences. Evidence: MachineLifecycle tests, SyncArchitecture tests, PortLifecycle tests, VatRenderLifecycle descriptor tests, and the historical real chunk lifecycle benchmark. Most server render tests cannot establish the lifetime of actual client display entities.

## 4. Tests as maintainability evidence

The source scan found **766 literal `@GameTest(` occurrences**. This is a source annotation count, not a discovered runtime count or “766 passing tests.” Many checks express valuable behavior: component conservation, no partial recipe consumption, single-use/stale reservations, fair shared-tool scheduling, no unload notifications, exact capture state, and real input/output acceptance. The suite should be retained.

| Evidence | Strength | Refactoring constraint / improvement |
|---|---|---|
| `T/MixerOperationGameTests.java:77`, `:91`, `:106`, `:167` | Independent quantities, blocked final outputs, snapshot comparisons, injected setter failure | Compound assertions combine selection, commit, and several balances; split assertions so failure reports name the changed resource. Whole NBT snapshots also couple to serialization shape. |
| `T/Slice11GameTests.java:65` | Split conservation and transaction write-through | The split test calls `compile` explicitly. Add future scenarios using actual filter/content/lifecycle transitions rather than assuming automatic recompilation. |
| `T/ConduitPerformanceGameTests.java:70` | Retained handlers recheck filter/removal and storage generation | Exact cache-build counts encode optimization contracts. Keep focused performance checks separate from gameplay behavior; changes need evidence rather than simply relaxing counts. |
| `T/MachineLifecycleGameTests.java:49`, `:53`, `:137` | Repeated restore/activation/retained-state coverage | Reflection invokes private lifecycle/update methods, and tests reset internal metrics/state. Helpful focused tests, but moving methods or changing internal representation breaks fixtures. Pair with ordinary lifecycle integration rather than delete them. |
| `T/SyncArchitectureGameTests.java:31` | Explicit dirty/update budget protects performance and unload safety | Exact correction-count formula is deliberately implementation-sensitive. Its purpose should be named in the test category and failure message. |
| `T/MixerOperationGameTests.java:33`, `:189`; `T/PassiveDefinitionGameTests.java:125` | Global registry snapshots restored in `finally` | Definitions remain process-global. Synchronous scoped calls avoid tick interleaving, but those assumptions are implicit. Do not move scoped work across delayed callbacks or run global-override tests concurrently without isolation. |
| `T/GenericCaptureGameTests.java:31` | Tests custom loaded capture definitions | Its snapshot reconstructs `Loaded` from definitions with empty companion maps/error lists; verify that every global registry component is restored, not just specimen definitions. |
| `T/ProductionWorldFixtures.java:35`, `:307`, `:419` | Real tools/ports, identity checks, conservation, returned feeding/culture and juvenile observation | Static lists/maps/flags represent one generation session; a roughly 90-line `verifyVat` has many independent responsibilities. Encapsulate session data, preserve reports and observations. |
| CPU `FixtureModelTest.verify` and geometry tests | Normals, unchanged shared geometry, cladding, clearance, exported models | Some comparisons invoke the same transform for expected and actual quads. Independent plane/normal bounds provide stronger evidence and must remain. Main-entry task wiring should be explicit to contributors. |
| Art checks in `gradle/procedural-art.gradle:1666` | Hash/provenance, packaged consumers, deterministic repeated generation | Golden hashes intentionally protect art output. A formatting-only slice should preserve generated pixels/models; do not update hashes just to make a refactor pass. |

Fixed callbacks at tick 35/75/95 are widespread, e.g. `T/MachineLifecycleGameTests.java:136`. Some exact timing is essential (cadence, spin-up, cooldown); formation/setup timing often could use a bounded readiness assertion. Avoid turning every timing test into polling: preserve tests whose behavior is timing itself.

Fixture responsibilities also need separation. `MachineFixtures` and `AcceptanceMixerFixtures` share construction; many historical tests still carry local `rig` methods. Names such as `Slice111GameTests` describe development chronology rather than behavior. Start with a behavior-to-test index and explicit fixture contracts, then move/rename one domain at a time while preserving method discoverability and manual sign/report references. Do not merge fixtures merely because their shells resemble each other: capacity, interference, resource-role, and acceptance layouts test different mechanics.

No tests were rewritten or executed for this audit. Client smoke/reload classes exist but are not permitted routine validation. CPU/headless checks cannot establish lighting, transparency, subjective art quality, or actual client cache collection.

## 5. Proposed coding standard and tooling

### Formatter decision

Recommend **Spotless with a pinned Palantir Java Format version**, using its standard 120-column layout. Its lambda-friendly layout suits NeoForge event/adaptor code. Spotless is the Gradle enforcement layer, not the formatter itself. First trial the selected version against Java 21 syntax, Gradle 9.2.1, record compact constructors, and long capability declarations in a later authorized slice; this audit installed nothing and establishes no compatibility pass.

| Option | Evaluation |
|---|---|
| Spotless + Palantir | Preferred: deterministic style with relatively little configuration and wider layout for API-heavy code. [Official formatter documentation](https://github.com/palantir/palantir-java-format) describes its 120-character design. |
| Spotless + google-java-format | Strong alternative if the team prefers Google's narrower layout and very few style knobs. The formatter uses a 100-column limit; [official formatter source](https://github.com/google/google-java-format/blob/master/core/src/main/java/com/google/googlejavaformat/java/Formatter.java) and [FAQ](https://github.com/google/google-java-format/wiki/FAQ) document this policy. |
| Spotless + Eclipse JDT | Best when matching a carefully chosen existing four-space layout is more important than minimizing options. Commit the XML configuration; otherwise IDE and command-line output will drift. |

[Spotless's official Gradle guide](https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md) documents these integrations plus import handling and check/apply tasks. Pin plugin and engine versions; do not use “latest” in Gradle or silently upgrade them with behavioral work. Confirm the chosen formatter runtime requirement separately from the project's Java 21 compilation toolchain.

Before a formatter touches `C/ConduitGeometry.java`, remove the generator's whitespace-sensitive source dependency: `gradle/procedural-art.gradle:248` requires `/DIAMETER=(\d+(?:\.\d+)?)\/16/`. Formatting `DIAMETER=6.0/16` as `DIAMETER = 6.0 / 16` changes no Java behavior but fails its assertion. A narrowly broadened parser is an acceptable first repair; longer term export the dimension through the existing geometry artifact rather than maintain source-text parsing. Do not make an undocumented formatter exemption permanent.

### Enforceable rules

* **Layout/imports:** one declaration/statement per line where meaningful; braces around multi-step control flow; formatter-owned wrapping/indentation; explicit imports, no wildcard imports; stable lexicographic ordinary imports and separately grouped static imports. Permit fully qualified names only for real type-name conflicts. Import expansion/removal is a distinct reviewed mechanical step, because wildcard expansion needs reliable type resolution.
* **Naming:** UpperCamelCase types, lowerCamelCase Java methods/fields, UPPER_SNAKE_CASE constants. Include units in balance/timing names (`Mb`, `Ticks`, `Pixels`); use domain names in planning and commit code. Compact graph indices are acceptable inside a documented allocator. Preserve snake_case JSON/NBT keys and established serialized identifiers; Java accessor renaming must not silently change schemas.
* **Responsibilities:** controllers orchestrate framework lifecycle and domain services; physical parts own their resources; topology computes connectivity; planners return plans/reasons; committers mutate owned resources; renderers consume descriptors. A method that mixes discovery, migration, mutation, and publication needs named phases. Use 40 formatted lines or nesting beyond three levels as review prompts, not automatic failure thresholds. Review classes above roughly 400 formatted lines for multiple responsibilities rather than arbitrary splitting.
* **Side effects:** queries and `SIMULATE` must not publish storage, mutate diagnostics, or notify worlds. Distinguish `find`, `prepare`, `ensure`, and `commit`; return structured planning failure instead of changing a status field as an incidental query effect. A UI/status projection may cache data, but must declare that cache ownership.
* **Errors:** report file, definition/recipe ID, field path, and reason for user-authored config errors; reject a malformed file atomically as today. Keep unsupported detached interactions distinct from invalid definitions and invariant failures. Preserve exception causes for unexpected failures. Do not swallow broader runtime errors to turn broken commits into “waiting for input.”
* **Documentation/invariants:** class comments state authoritative owner, thread, persisted versus derived state, and invalidation triggers. Complex algorithms state ordering/conservation assumptions. Lifecycle helpers document forbidden unload-time world notifications. Public mutable returns require an ownership justification or a narrower facade. Comments explain why rather than translate assignments.
* **Tests:** descriptive behavior names; visible arrange/action/assert stages; one assertion per independent outcome with actual/expected quantities. Shared fixtures describe geometry/resources and whether setup bypasses capabilities. Global-override tests explicitly restore full snapshots and remain synchronous/isolated. Keep behavior, timing, cache budgets, persistence compatibility, art goldens, and end-to-end acceptance distinguishable.
* **Dependencies:** shared resource/transaction code should not acquire species rules, family-specific render types, Jade APIs, or GameTest helpers. Keep ZeroCore protected hooks behind the bridge. Client classes remain client-only; procedural tooling and benchmarks remain outside the player JAR. Relocating shared `MachineMetrics` or geometry types is optional follow-up, not a prerequisite to formatting.

Future workflow: run formatter apply deliberately, inspect the diff, run formatter check and `git diff --check`, then the relevant CPU/integration checks. Configure formatter check as a normal `check` dependency and CI gate when CI is introduced; never auto-apply formatting during `build`. IDE and Codex use the same wrapper tasks. Keep the independent benchmark build under the same committed policy. A temporary ratchet can keep early slices small, but final adoption should cover all authored Java, including tests. Exclude downloaded references and generated outputs.

After formatting, evaluate a small Checkstyle policy for wildcard imports and boundary conventions. Consider SpotBugs/Error Prone only in a separate measured integration slice: Minecraft/NeoForge generated APIs and toolchain integration need validation and an explicit suppression policy. They do not replace ownership review, and adding all analyzers at once would create noise.

## 6. Ranked cleanup backlog

Severity and confidence are separate. **At the original audit baseline, P0 entries were source-supported safety review gates, not reproduced runtime failures.** The October 13 follow-up below records reproduced failures, narrow corrections and remaining integration gates. P1/P2 findings are source-level maintainability problems.

### P0-A — Buffer interaction discovery can escape the controller's member set (confirmed defects corrected; real-eviction verification remains)

**Location/example:** `C/FluidBufferEntity.java:61` through `:72` discovers every adjacent Buffer using `level.getBlockEntity` with no loaded-chunk or controller-owner fence. Compare `C/FluidBufferPresentation.java:34`, `:39` (loaded and owner checks) and bounds-limited controller storage compilation.

**Why:** a container action can form a larger logical tank than either controller/presentation recognizes. Compatible buffers touching across independent machine envelopes may be filled/drained together; traversal at a chunk edge can also request block entities outside loaded chunks. Retained controller cluster views can subsequently observe changed members without membership being rebuilt immediately. This is an ownership/lifecycle hazard, not just duplicate BFS code.

**Correction:** first characterize unformed adjacent clusters, same-owner formed clusters, touching independent owners, and unloaded neighbors through actual container interactions. Define the intended owner boundary in routing documentation. If cross-owner interaction is unintended, centralize only loaded/owner-constrained member discovery and retain the existing compatibility compiler and member persistence. Never use presentation snapshots as gameplay storage.

**Scope/risk/evidence:** small targeted discovery change plus autonomous tests, approximately 1–2 days; medium risk to legitimate standalone clusters. Existing Slice11 split/write-through, FluidBufferArt incompatibility, ConduitPerformance retained-handler, and PartOwnership tests are relevant but do not prove this exact container boundary case.

### P0-B — Detached client specimen can indirectly retain the weak cache's world (confirmed cache-ownership defect; client integration check remains)

**Location/example:** `V/client/render/VatHeadEntityRenderer.java:24`, `:41`, `:110`. A weak-key cache value holds a Mob created with the real client Level. Removal at line 63 requires another call for the invalid part. The comment rules out a direct back-reference but not `value -> specimen -> Level -> loaded chunks/parts -> key`. No explicit disconnect/level-change cleanup was found in `V/client/VatClient.java`.

**Why:** WeakHashMap alone does not guarantee old world/part collection when values reach their keys indirectly. Renderer recreation on resource reload is not a declared per-world retirement contract. Actual retained-world behavior on logout/dimension change remains unverified.

**Correction:** verify renderer lifetime and implement explicit cache retirement on client level change/disconnect if needed, preserving detached, never-ticked specimens. A small testable cache owner can prove retirement without launching Minecraft; the human operator handles any later client acceptance. Do not attempt to solve this by spawning live entities or retaining only weak visual Mobs that may disappear unpredictably.

**Scope/risk/evidence:** roughly 1 day for lifetime characterization and targeted retirement; medium presentation risk. VatRenderLifecycle tests protect descriptors/Head restoration, not GC of actual client Mobs. Existing client smoke code is not authorization to launch it. This requires new focused lifetime evidence if confirmed.

### Lifecycle/ownership verification — October 13, 2026

Baseline: clean `main`, `bff8e446683d21e1e485993d71a63f41e755d4c2`.
The committed Spotless 8.10.4 / Palantir 2.102.0 configuration and full authored
Java coverage were inspected; baseline `spotlessCheck --offline` passed. The
requested lifecycle document is now archived at
[2026-10-11-zerocore-lifecycle-parity.md](history/2026-10-11-zerocore-lifecycle-parity.md).
The sibling `../workspace/AGENTS.md` remains unavailable. No pre-existing edits
were present, no client was launched, and no commit/publication is authorized.
The original audit's read-only statements above apply to that earlier audit,
not to this follow-up investigation.

#### A: observed failures and authoritative contract

**Classification: confirmed defects, high confidence for the reproduced paths.**
Original interaction BFS could cross independent assembled owner envelopes.
`FluidBufferOwnershipGameTests.touchingIndependentOwnersDoNotShareContainerStorage`
builds a touching Vat and Mixer with separate owners and 4,000/2,000 mB water.
On unchanged production code it failed `Container discovery escaped its owner`.
The correction also tests actual bucket drain/fill against the second machine,
with exact 4,000/1,000 then 4,000/2,000 physical distributions and container items.
An initial fixture accidentally replaced the Mixer Controller; its failure was
corrected before recording the independent-owner reproduction.

The existing contract is `ConduitStructure.containing`: assembled controller
identity plus its validated bounds. Physical Buffers are passive blocks, not
ZeroCore parts. A null owner deliberately permits standalone/unformed adjacent
clusters; pause/disassembly retires controller routing but does not destroy
physical tanks or forbid direct standalone container use. Operator disable is
distinct from pause: assembled disabled boundary transport remains supported.
Presentation already used this owner fence. Compatibility stays in the existing
compiler: exact filter equality and same fluid/components, with deterministic
member ordering. No alternative clustering model or presentation-based storage
was introduced.

Additional before-fix evidence:

| Regression | Observed invalid state |
| --- | --- |
| `retiredClusterRejectsPreparedTransactionAndDirectHandler` | `ResourceReservation.commit()` wrote through a retained cluster after `PhysicalFluidStorage.clear()` |
| `unloadCallbackRetiresReachabilityBeforeRemoval` | Cached membership exposed a Buffer after `onChunkUnloaded()` but before removal |
| `compatibilityChangeRejectsRetainedClusterWithoutRedistribution` | A retained cluster aggregated water and lava before deferred recompilation |
| `machineRepairAndControllerReplacementRetirePreparedStorage` | Mixer part removal retained storage authority; a prepared plan committed after Controller replacement |
| `preparedFillRechecksChangedFilter` | A prepared water output committed after the empty Buffer changed to a lava filter |
| `chunkBoundaryUnloadAndUnloadedQueriesConservePhysicalStorage` | Unload followed by reload revived a prepared standalone transaction when only current availability was checked |

Corrections are restricted to `C/FluidBufferEntity.java`,
`C/PhysicalFluidStorage.java`, `C/ResourceReservation.java`, and
`M/MultiblockMixer.java`. Interaction discovery checks loaded chunks, Buffer
availability and the addressed owner. Bounds enumeration checks loaded chunks.
The unload callback immediately retires Buffer availability without world reads.
Cluster wrappers bind to their storage generation, member lifecycle epochs and discovered owner, reject
removed/replaced/unloaded members and live compatibility changes, and expose zero
capacity/empty contents after invalidation. Zero capacity makes existing snapshot
transactions reject stale wrappers before writes; old wrappers never become valid
again after clear/recompile or a member unload/removal, even after reload. Current connectivity snapshots still reuse endpoint
identities until an actual rebuild. Mixer removal now invokes its existing
`invalidateConduitTopology`, matching Vat retirement. Reservation commit checks
the current fluid validator before any item/fluid writes. Tanks, NBT, composition,
Store/Producer/Consumer roles, direction filters, and routing algorithms stay intact.

Characterization also covers a loaded physical chunk boundary, one Buffer's
unload/load callbacks and NBT round trip, machine break/repair, Controller
replacement, an unformed plan followed by owner assignment, and empty queries
over genuinely unloaded coordinates with unchanged loaded-chunk counts. The
boundary fixture uses absolute placement because `GameTestHelper.relativePos`
rotates coordinates; its earlier placement assertion was a fixture failure.
Existing `Slice11`, `Slice111`, `FluidBufferArt`, `ConduitPerformance`,
`MachineLifecycle`, `PartOwnership`, `ResourceRole`, Mixer operation/drive and
compound-fluid suites supply complementary identity, conservation, assimilation,
endpoint caching and transaction evidence.

`MixerFluidVoidGameTests.removedVoidDoesNotLoseFluids` now compares every
persisted member's complete FluidStack before/after removal and requires the
controller view to be empty and disposal to fail. Its former controller-view
`amount(0)==100` assertion depended on the stale view being retained; an empty
retired view does not mean the persisted resource was lost. Exact amount and
component checks are preserved at the authoritative owner instead.

**Limit:** GameTest-owned chunks are not evicted from the server map. Unload
callbacks are invoked autonomously while the chunk remains loaded; actual missing
chunks are exercised separately by read-only storage enumeration. Minecraft
1.21.1 `LevelReader.hasChunkAt` delegates to `ServerChunkCache.hasChunk`, which
checks visible holders without creating a chunk. Every newly added block-entity
lookup follows this guard. Buffer unload and controller retirement callbacks
perform no immediate world lookup. Real server eviction/reload under operator
play remains a bounded integration check, not an asserted automated result.
Keep the **P0-A real-eviction verification gate** open: particularly exercise a
boundary chunk containing only passive Buffers/conduits, with Controller/Head
parts still loaded in the other chunk. Verify actual chunk-map eviction,
framework pause/restoration behavior, fresh endpoint identities after reload,
and conserved physical NBT totals. Callback tests and guarded missing-chunk
queries establish the corrected access fence, but do not prove that full event
sequence or passive-only chunk reattachment.

#### B: cache graph, correction and evidence boundary

**Classification: confirmed cache-ownership defect; high confidence in the
source graph and direct retirement contract. Live client integration remains
an unresolved P0-B verification gate until the manual checks below pass.**
`V/client/render/VatHeadEntityRenderer.java` holds actual detached Minecraft
`Mob` objects in `VisualState`, created with the real client Level. Minecraft
`Entity.level` is a strong reference; ClientLevel owns its chunk cache and loaded
chunks own block entities. Thus the map value can reach its weak Head key through
the Level. `WeakHashMap` cannot supply the claimed lifetime bound. A later render
of an invalid Head was the only explicit removal; distance culling or logout can
prevent that render. Geometry/descriptors, the separate detail cache, and
`VatRenderDiagnostics`' value records do not themselves hold specimen entities.
Head identity/state/life-stage/geometry/yaw revisions already rebuild visual state
when queried; resource reload recreates renderers through vanilla
`BlockEntityRenderDispatcher.onResourceManagerReload`. Those facts did not provide
explicit per-chunk or per-world retirement for retained visual Mobs.

`V/client/render/VatVisualCache.java` is a small renderer-owned map with explicit
world/Head retirement. It supplements weak keys and does not introduce a gameplay
lifecycle abstraction. `V/client/VatRenderLifecycle.java` retires chunk/world
states on client unload events and prunes removed/replaced/unassembled or revised
Heads each client tick, including culled Heads. A current-Level identity check
rejects old-world render calls. `V/client/VatClient.java` registers resource-reload
retirement. The renderer registry itself has weak renderer keys; retired renderers
are not held by the registry. Stable worlds/revisions reuse cached models/Mobs;
specimens remain detached, never world-spawned or ticked. No new packets are used.

`src/test/java/net/impactvector/mobvats/render/VatVisualCacheTest.java` runs through
`vatVisualCacheTest` and `check`. Its original-map mode
(`vatVisualCacheTest -PvatCacheBaseline`) fails the direct retirement assertion
with a deterministic value -> specimen -> world -> Head graph. The corrected
owner passes creation/reuse, revision invalidation, Head replacement, selective
chunk retirement, reload with a fresh Head instance, world replacement, resource
clear and disconnect. This is an ownership harness with stand-in objects, not an
actual ClientLevel/Mob renderer run. It makes no GC request or memory-size claim.
Existing `VatRenderLifecycleGameTests` verify actual Head synchronization and
independent revisions. The new development-only `/mobvats_render cache` command
reports actual `cachedHeads`, `specimenMobs` and `foreignLevels` without exporting
references or changing synchronization.
As with the existing CPU harnesses, this Java entry point is excluded from
framework `test` discovery and executed explicitly by `check`; no assertion is
removed or skipped by that wiring.

**Remaining evidence:** headless execution cannot prove client event delivery,
visual acceptance, live-world GC or the cause of intermittent missing simulacra.
No connection between missing simulacra and this ownership defect was reproduced.
P0-B's client integration gate stays explicit; the implemented ownership defect
is not described as an ongoing unchanged bug.

#### ZeroCore2 / XR2 comparison

The established local references remain ZeroCore2 `1.21` metadata 2.4.17,
revision `64d892b1ba099192a35bfa4ee08465540a2f12e7`, and XR2 2.4.27,
revision `f6cc65140a8abbfc87137f5c54fc641085f66048`; the runtime dependency is
ZeroCore2 **2.4.18**. These are partial cached source references, not exact-runtime
source authentication. `lib/multiblock/AbstractMultiblockController.detachPart`
pauses before removal callbacks; `AbstractMultiblockPart.setRemoved` and
`onChunkUnloaded` detach through that authority. Assimilation transfers parts and
invokes recipient/donor callbacks; MobVats donor retirement and reassembly rebuilds
remain unchanged. ZeroCore does not own MobVats passive physical Buffers or client
specimen caches. Those added services must fence their own references and queries.
XR2 `turbine/MultiblockTurbine` retires turbine data on disassembly and attached
role references on donor assimilation;
`turbine/client/render/rotor/RotorBearingEntityRenderer` consumes a bearing-owned
rotor descriptor/baked models rather than a renderer map of world-bound Mobs.
That difference justifies explicit MobVats specimen retirement, not copying XR2
storage or replacing ZeroCore's lifecycle.

#### Bounded manual checks (human operator; no automatic client launch)

1. In the development client, load a valid single-Head cow Vat, look at it, and
   run `/mobvats_render cache`. Expect a specimen Mob and `foreignLevels=0`.
2. Replace/re-encode the Head, including a geometry or life-stage change. After
   one client tick, check the old visual retires; looking back creates the correct
   specimen without accumulating replaced Heads. Verify straps/mechanisms too.
3. Move beyond chunk loading distance (including a previously distance-culled
   Head), run the cache command, then return. Expect unloaded entries to disappear
   and valid simulacra to reappear. For a boundary reservoir, unload one side and
   return; verify physical amount/components and capacity before/after.
4. Switch dimensions, then disconnect/reconnect or load another world. Check
   `foreignLevels=0` and no old Head count before looking at new Vats. World exit
   event delivery can be inspected at `VatRenderLifecycle.levelUnloaded` in the
   debugger; directly verify the renderer map drops the old values.
5. Reload resources with F3+T. Inspect `clearVisualCaches` at reload (zero owned
   entries), then verify a visible Vat recreates its visual and repeated stable
   frames reuse it. Do not use eventual GC timing as a pass/fail condition.

#### Validation results and remaining gates

Report finalized October 14, 2026. Java 21 / Gradle 9.2.1 used the existing
workspace cache with `--offline`. All 326 authored Java files remain in the
pinned formatter's positive roots.

| Check | Result |
| --- | --- |
| Baseline `spotlessCheck` | Passed on the clean baseline before editing |
| Final `spotlessCheck` / `build` | Passed, including CPU cache, existing render/geometry checks, development-hook exclusion and resource/provenance guards |
| `runGameTestServer` | All **773 required tests passed**: 766 existing plus seven focused ownership regressions; server saved all dimensions and stopped normally |
| `vatVisualCacheTest` | Passed direct cache ownership/lifecycle assertions; original weak-map characterization mode failed as expected |
| Standard `buildTestWorld -PworldName=MobVatsAcceptance` | Initially failed with legacy ignored runtime definitions; isolated bundled-default rerun passed at **48,381 observation ticks** |
| Minimum-XP `buildTestWorld -PworldName=MobVatsMinimumXp -PacceptanceMinimumCowXp=true` | Initially failed with the same legacy definitions; isolated bundled-default rerun passed at **49,791 observation ticks**; minimum native XP events recorded |
| Untouched baseline acceptance control | Exact SHA source archive, fresh runtime defaults: passed standard acceptance at **5,000 ticks** |
| `git diff --check` | Passed; Git's LF/CRLF informational warning is not a whitespace error |

The GameTests passed in the combined build/GameTest invocation; that invocation's
overall failure was solely Gradle's attempt to discover the new CPU entry point
as a framework test. The harness was given the existing CPU-test discovery
exclusion, retained under `check`, and the full build passed afterward. Production
code and server regressions did not change after the 773-test passing run.

Acceptance failures were investigated rather than attributed to historical
intermittency. Existing `run-world-generator/config/mobvats/mixer/_defaults.json`
specified **100 mB XP** per culture batch; bundled defaults and the acceptance
assertion specify **250 mB XP**. The persisted passive definitions also differed
from fresh defaults. Both original runs failed `Culture batches did not consume
1000 mB water + 250 mB XP`; P-06 actually produced 7,000/3,000 mB culture under
those legacy recipes. An untouched SHA archive passed with fresh defaults.
Both current-code reruns then passed using an ignored init script that selected
`.cache/lifecycle-clean-runtime`, allowing the unchanged bundled definitions to
generate there. Original ignored configurations were preserved: Mixer SHA-256
`4174EF9E887AB5417AD9CA9612676F3980C9D52811873073D84F0886BAE8A76C`, passive
definitions SHA-256
`52A1010AB3E6B41833B23057938EFD2CF0A9D2810884501E437A1DC77B780571`.
No authored recipe/resource was changed. This explains these configuration-bound
failures; it does not close every historical acceptance intermittency report.

Evidence is retained in ignored `.cache/`: `lifecycle-baseline-spotless.log`,
`lifecycle-before-corrected.log`, `lifecycle-filter-before.log`,
`lifecycle-reload-before.log`, `lifecycle-client-baseline.log`,
`lifecycle-final-validation.log`, `lifecycle-final-build.log`,
`lifecycle-acceptance.log`, `lifecycle-minimum-xp.log`,
`lifecycle-baseline-acceptance.log`, `lifecycle-acceptance-clean.log` and
`lifecycle-minimum-xp-clean.log`. Failed acceptance reports and the exact baseline
source archive are retained there too. Both successful generated worlds contain
`generation-complete.txt`, `level.dat` and acceptance reports.

Confirmed defects have narrow, regression-protected corrections. **P0-A actual
server eviction/reattachment and P0-B live client event-delivery gates remain
open**, with precise checks above. Missing simulacra are not attributed to the
cache defect without live evidence. Mixer recipe-planning maintainability remains
a separate slice after these gates; no planner refactor is included here.
No client, commit, push, public release, upload or deployment occurred. AI
placeholder release safeguards remain unchanged.

#### Exact final working-tree inventory

`main`, HEAD unchanged at `bff8e446683d21e1e485993d71a63f41e755d4c2`.
Eleven modified tracked files and four untracked authored Java files; nothing
staged. Ignored logs, source-control archive, runtime configs and generated worlds
are not part of the patch.

```text
 M build.gradle
 M docs/03-RESOURCE-ROUTING.md
 M docs/CODE_MAINTAINABILITY_AUDIT.md
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/FluidBufferEntity.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/PhysicalFluidStorage.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/ResourceReservation.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/MultiblockMixer.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/client/VatClient.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/client/render/VatHeadEntityRenderer.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/client/render/VatRenderDiagnostics.java
 M src/main/java/net/impactvector/mobvats/gametest/MixerFluidVoidGameTests.java
?? src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/client/VatRenderLifecycle.java
?? src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/client/render/VatVisualCache.java
?? src/main/java/net/impactvector/mobvats/gametest/FluidBufferOwnershipGameTests.java
?? src/test/java/net/impactvector/mobvats/render/VatVisualCacheTest.java
```

### P0 runtime acceptance follow-up — October 14, 2026

The P0-A blocker recorded in this historical follow-up is superseded by the
passive-only eviction closure below.

**P0-A: BLOCKED — runtime verification unavailable for the passive-only partial
eviction criterion. Full-machine eviction/reattachment passed in the actual
dedicated server. This is partial coverage, so P0-A remains open.**

**P0-B: PASS — runtime verified.** Actual graphical client events, rendered
cache contents, resource reload, disassembly/reformation, model updates and
disconnect/rejoin were exercised for Vat, Mixer, Buffer and conduit paths.
Human visual acceptance and eventual garbage collection are separate claims.

#### Baseline and isolation

The current starting tree was clean `main`, HEAD
`f1505fc` (`fix: enforce storage and render cache lifecycles and relocate Gradle
cache`), rather than the historical uncommitted `bff8e44` handoff. The previous
corrections and all seven ownership regressions were already committed.
The sibling workspace instructions were available and read. No existing changes
were reset. Java 21 (Eclipse Adoptium), Gradle 9.2.1, Minecraft 1.21.1,
NeoForge 21.1.252 and ZeroCore2 2.4.18 were used with `--offline` and the standard
`../workspace/.cache/gradle` cache. The pasted request explicitly authorized
this task's autonomous client execution. No input takeover, screenshots,
manual play, commit, push, publication or procedural-art change occurred.

All new runs use ignored disposable directories:

- `.cache/p0-server-runtime`: flat dedicated server, no players, view/simulation
  distance 2, port 25575, harness tick rate 100, fixtures far outside spawn.
- `.cache/p0-client-runtime`: fresh client options and generated creative flat
  world `MobVats-Smoke`; integrated server and actual renderer frames.
- `.cache/p0-world-runtime/server` and `saves`: both acceptance worlds with
  bundled defaults, selected by the new `-PisolatedAcceptance` switch.

The original `run/` and `run-world-generator/` worlds/configuration were not
modified. The original Mixer/passive default SHA-256 values still match the
earlier evidence: `4174EF9E887AB5417AD9CA9612676F3980C9D52811873073D84F0886BAE8A76C`
and `52A1010AB3E6B41833B23057938EFD2CF0A9D2810884501E437A1DC77B780571`.

#### Real server results and remaining blocker

`ServerChunkLifecycleAcceptance` is an opt-in dedicated-server subscriber,
outside GameTest chunk ownership and excluded from the mod JAR. Four fixtures
formed: a single-chunk cow Vat, a single-chunk Mixer receiving finite water
from an adjacent passive-output Vat, a two-chunk Mixer, and a four-chunk Mixer
whose corner chunk contains only four passive Buffers and no ZeroCore parts.
The finite source uses a different machine family to preserve independent
ZeroCore ownership. Buffers intentionally have no external fluid capability;
the active transport goes through the actual machine ports.

The trigger removes the harness's forced tickets after saving. It requires
both `ServerChunkCache.hasChunk=false` and `getChunkNow=null`, actual
`ChunkEvent.Unload` delivery, and 40 consecutive absent observations before
reloading. Load events during the absence interval are rejected. The full
eviction run observed exactly **eight unloads and sixteen loads**. Fresh Buffer
instances, fresh controller ownership and fresh storage wrappers were observed
after restoration, including the passive-corner fixture when all its chunks
were released. Evidence: `.cache/p0-final-build-server.log`.

| Required invariant | Actual full-eviction evidence |
| --- | --- |
| No chunk reacquisition during unload | Eight initial loads, eight unloads, no further target loads until the explicit reload trigger; bounded absent interval |
| Ownership retirement | Old controllers unassembled, old Buffers unavailable, owner lookup empty while evicted |
| Endpoint/transaction retirement | Old wrappers have zero capacity; prepared reservation rejects; direct retained drain returns empty; old wrappers remain retired after reload |
| Persistence and conservation | Physical water is 3,000 mB before a 250 mB drain, 2,750 through eviction/reload, and 2,500 after the resumed reservation |
| Exactly-once resumed mutation | Fresh reservation commits once and rejects its second commit |
| Active transport | Adjacent finite Vat source delivers 3,000 mB to Mixer before eviction; after reload a new 250 mB source charge becomes exactly 250 mB in the Mixer, with source empty after 40 ticks |
| Part restoration | Valid owner envelopes return; cow Head is assembled and retains its encoded identity |

The optional `-PpassiveOnlyChunkProbe` tests the audit's stronger partial case:
release only passive-corner availability while the three framework-part chunks
remain at FULL through radius-zero region tickets. The corner becomes
inaccessible (`hasChunk=false`, `getChunkNow=null`) but its holder remains at
**ticket level 34 / INACCESSIBLE**. No ninth unload event arrives within the
6,000-tick bound. This is not proven chunk-map eviction. The harness explicitly
reports `P0 SERVER PROBE BLOCKED`, and Gradle rejects that result; the requirement
was not weakened into a simulated callback test. Evidence:
`.cache/p0-passive-blocker.log` and the runtime world's `p0-lifecycle-result.txt`.

Earlier short bounds and observations before delayed unload callbacks were
fixture failures, not established production defects. The full suite's bound
was increased, and the partial probe now requires the actual unload event
before testing callback retirement. No production server lifecycle change was
made. Partial passive-only pause/restoration, endpoint reattachment, resource
components beyond the water fixtures, and renderer-facing state under that
specific partial transition remain unverified. A way to produce genuine
eviction while preserving adjacent framework-part chunk availability is needed
to close the stronger P0-A gate. Removing neighboring tickets would repeat the
already passing full-machine scenario.

#### Live client defect, correction and results

The first extended live probe reproduced **Mixer/Buffer per-entity caches
surviving distant chunk loss** (`.cache/p0-client-shared-before.log`). Holding
the departed Level for identity comparison makes explicit retirement observable
without depending on GC. This is a cache-lifecycle contract failure; no memory
size or eventual world-GC claim is made.

`MixerControllerRenderer` and `FluidBufferRenderer` now have weak renderer
registries and explicit per-chunk/per-Level retirement. Family-specific client
subscribers retire cache entries on real unload events and prune removed,
replaced or out-of-range entities each client tick; Mixer pruning also rejects
unassembled or changed descriptor entries. The existing resource-reload hook
clears all three per-entity render owners. Reusable world-free Buffer shapes
and conduit mesh batches retain their existing ownership: they hold geometry,
not Levels, block entities or detached Mobs. No gameplay routing, balance or
ZeroCore persistence was changed.

The existing smoke fixture also had two stale assertions: unformed glass now
uses the current translucent layer, and a removed glass position contains air.
Both were corrected while retaining model-data, geometry, winding, material,
descriptor and assembly checks. P0 mode adds the Mixer/Buffer/conduit fixture,
suppresses screenshot capture and extends the existing autonomous sequence.
All instrumentation and private-cache reflection are in the development-only
GameTest package, excluded from the JAR. Production has no per-frame logging.

Final live evidence: `.cache/p0-client-verified.log`, ending:

```text
P0 CLIENT PROBE PASSED chunkLoads=8 chunkUnloads=2 levelUnloads=1
cachedHeads=1 specimenMobs=1 foreignLevels=0
```

| Boundary/assertion | Live result |
| --- | --- |
| Initial formation and correct frame state | Real detached cow rendered; actual Mixer vessel and Buffer state maps populated; current nonempty conduit pipe geometry present in the shared render cache |
| Disassembly/reformation | Vat descriptor and shell model data follow assembly; Mixer geometry retires and reformation produces a different geometry object |
| Distance removal | Teleport 1,024 blocks away; nonloading client chunk-cache lookup proves absence; all three per-entity cache owners are empty after 40 ticks |
| Unload event delivery | Real server-to-client chunk-forget packets for both fixture chunks deliver two NeoForge unload events; LOWEST observers verify the corresponding owners were retired by the production subscribers |
| Reload and rebuilding | Real chunk/light packets restore new client block entities; actual renderer frames repopulate all owners; current conduit geometry is selected |
| Resource/model reload | Actual `reloadResourcePacks()` completes; retained old Mixer/Buffer maps empty; new renderer maps and detached cow rebuild on demand |
| Changed Buffer model data | Server changes water from 2,000 to 500 mB; client receives amount 500 and rendered liquid height equals the synchronized visual height (0.265625) |
| Disconnect/world departure | One unload event for the intentional departure; all per-entity maps empty before opening the world again |
| Rejoin/no stale world | New ClientLevel identity; all renderer paths rebuild; actual current conduit geometry has rendered; no foreign-Level specimen |
| Duplicate processing/listeners | Exactly eight fixture-chunk loads and two packet unloads over initial load, travel return, packet reload and rejoin; one intentional world-unload boundary. Final application shutdown produces its separate normal world unload |

`ClientLevel.hasChunk()` always returns true in this runtime; the probe uses
`ClientChunkCache.getChunk(FULL, false)` instead. Long-distance view-center changes
did **not** emit chunk-unload events here. Tick maintenance supplies retirement
for that real path. Explicit network chunk-forget packets separately prove
event delivery without directly invoking event handlers or cache methods.
Render readiness is bounded: a new synchronized descriptor can precede the
frame that caches its conduit geometry. The probe waits for the current
geometry to be present rather than accepting an old cached object.

#### Validation and review state

| Check | Result |
| --- | --- |
| `spotlessApply`, mechanical diff inspection, `spotlessCheck` | Passed; only authorized Java changes were formatted |
| Final `build` | Passed, including CPU cache/geometry checks, resources/provenance and development-hook exclusion |
| `runGameTestServer` after the production client corrections | **All 773 required tests passed**, unchanged count; new runtime probes are opt-in subscribers, not additional GameTests |
| Full dedicated eviction probe | Passed for all four machines and active port route |
| Passive-only partial eviction probe | **BLOCKED**, intentionally not accepted by its Gradle task |
| Final graphical client probe | Passed all expanded lifecycle/render-path assertions |
| Standard isolated acceptance | Passed at 48,381 observation ticks; completion marker and saved world verified |
| Minimum-XP isolated acceptance | Passed at 49,791 observation ticks; completion marker and saved world verified |
| `git diff --check` | Passed |

Regression logs: `.cache/p0-regressions.log`, `.cache/p0-final-regressions.log`,
`.cache/p0-final-build-server.log`, `.cache/p0-acceptance.log` and
`.cache/p0-minimum-xp.log`. Acceptance generation ran during this investigation;
the later production corrections are client-only. Server probe/fixture changes
were compiled and exercised afterward. Original configurations retain their
hashes. The Mixer planner refactor, processing balance, cladding, artwork and
release safeguards remain outside this patch.

HEAD remains `f1505fc`, nothing staged. Six modified tracked files and five new
authored Java files; generated runtime output remains ignored:

```text
 M build.gradle
 M docs/CODE_MAINTAINABILITY_AUDIT.md
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/client/FluidBufferRenderer.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/client/render/MixerControllerRenderer.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/client/VatClient.java
 M src/main/java/net/impactvector/mobvats/gametest/client/VatClientSmokeTest.java
?? src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/client/FluidBufferRenderLifecycle.java
?? src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/client/MixerRenderLifecycle.java
?? src/main/java/net/impactvector/mobvats/gametest/ClientLifecycleFixtures.java
?? src/main/java/net/impactvector/mobvats/gametest/ServerChunkLifecycleAcceptance.java
?? src/main/java/net/impactvector/mobvats/gametest/client/ClientLifecycleAcceptance.java
```

### Passive-only chunk eviction closure — October 14, 2026

**P0-A: PASS — runtime verified.** The previously blocked passive-only criterion
now has actual server eviction and disk-restoration evidence with neighboring
framework parts remaining loaded. **P0-B retains its preceding live-client PASS**;
no client was launched for this server follow-up. Normal worlds/configuration
and all pre-existing working-tree changes were preserved. Nothing was committed
or pushed; HEAD remains `f1505fcfa3fcce9d7c16ad3d0fd522b02f23fedf`.

#### Ticket retention and the bounded native trigger

The exact Minecraft/NeoForge runtime sources supplied by ModDev were inspected:
`DistanceManager`, `ChunkLevel`, `ChunkMap`, `ChunkHolder`, `ServerChunkCache` and
`LevelChunk`. A neighboring FULL holder at level 33 propagates a dependency level
34 to the corner. That is INACCESSIBLE to `hasChunk`/`getChunkNow` but still within
`ChunkLevel.MAX_LEVEL`. `ChunkMap.updateChunkScheduling` does not put such a holder
in `toDrop`; ordinary ticket removal therefore cannot produce the required
partial eviction while those neighbors remain FULL.

`gametest/ServerChunkEviction.java` supplies an explicitly opt-in, development-only
fault trigger at the native scheduler boundary. After observing the settled
level-34 dependency with zero generation references, it invokes
`ChunkMap.updateChunkScheduling` at `ChunkLevel.MAX_LEVEL + 1` and queues the
returned holder for the normal `DistanceManager` future update. Reflection is
limited to these nonpublic runtime interfaces and read-only evidence fields.
The helper is excluded from the packaged JAR.

The server's ordinary `processUnloads`/`scheduleUnload` code then performs save,
loaded-flag retirement, NeoForge event delivery, `ServerLevel.unload`, block-entity
callbacks and chunk-map removal. The harness does **not** directly remove map
entries, complete save futures, change block entities, invoke unload callbacks,
post lifecycle events, or replace a neighboring holder. Thus the injected trigger
bypasses the natural retention policy, while the lifecycle under verification
remains real Minecraft server execution. This proves fault-driven partial
eviction, not that player movement can overcome the dependency policy.

The criterion is stronger than the earlier loaded-state observation: success
requires the unload event, the old LevelChunk's native loaded flag becoming false,
absence from **visible, updating and pending-unload maps**, `hasChunk=false`,
`getChunkNow=null`, and 40 consecutive absent observations. The three neighboring
holders must retain identity and FULL availability throughout; all **80 framework
parts** must remain the same nonremoved block entities through reload.

#### Reproduced defect and minimal correction

The native trigger first reproduced a real defect:
`Passive-only eviction retained assembled controller despite missing shell`
(`.cache/p0-native-eviction-settled.log`). The Buffer callback and its retained
storage wrapper retired, but no ZeroCore part detached, so the controller still
reported assembled. Simulated whole-part unload checks had not covered this path.

`ConduitStructure` now queues structural revalidation for server chunk unloads
intersecting a controller's last validated envelope. The event callback examines
cached bounds only and performs no block/chunk lookup. Server chunk loads notify
controllers awaiting restoration; already assembled controllers are not
unnecessarily revalidated. Existing level-tick delivery and ZeroCore's normal
validation/disassembly/reassembly authority perform the transition.

`ConduitCuboidController.isMachineWhole` rejects unavailable chunks in the last
validated envelope **before** ZeroCore enumerates world cells. This prevents
validation from reacquiring the evicted chunk. Routing, endpoint views and
renderer-facing descriptors retire through the existing disassembly callbacks;
no new ownership or persistence model was introduced. The localized status
message is in `en_us.json`. Reload validates and reforms the same controller with
the existing parts and fresh Buffer/storage identities.

#### Final runtime evidence

The final serial run used Java 21, Gradle 9.2.1, Minecraft 1.21.1, NeoForge
21.1.252 and ZeroCore2 2.4.18, bundled defaults and the dedicated
`.cache/p0-server-runtime` world. Invocation:

```powershell
.\gradlew.bat build runLifecycleAcceptanceServer -PpassiveOnlyChunkProbe -PisolatedAcceptance --offline
```

Evidence: `.cache/p0-passive-final-serial.log`, ending `BUILD SUCCESSFUL`.
The freshly written world receipt reports:

```text
P0 SERVER PROBE PASSED
ticks=3361 unloads=9 loads=17
```

Eight ordinary full-fixture unloads and sixteen loads precede the isolated
passive-corner cycle. The ninth unload and seventeenth load are exclusively the
passive-only target `[1040,1040]`. No callback/validation load is permitted before
the explicit reload trigger.

| Acceptance assertion | Final passive-only evidence |
| --- | --- |
| Actual eviction | Native unload event, loaded flag false, all three chunk maps empty, 40 stable absent observations |
| Neighboring parts preserved | Same three FULL holders and same 80 nonremoved framework parts before, during and after the cycle |
| Invalid machine authority retired | Controller unassembled; storage clusters empty; render descriptor absent; retained Buffer unavailable and wrapper capacity zero |
| No stale transaction/transfer | Pre-unload reservation rejects during eviction; a separate reservation held untouched until after reload also rejects; old wrapper stays retired |
| Exact persisted resources | Every one of the four passive members restores its complete FluidStack and filter, including custom-name components on the 2,500 mB water stack |
| Reattachment and ownership | New Buffer instances attach to the same revalidated controller; all four member positions appear once, with no duplicate endpoint references |
| Resumed mutation exactly once | Fresh reservation changes 2,500 to 2,250 mB once and rejects its second commit |
| Renderer-facing restoration | Empty descriptor while disassembled; fresh descriptor after reassembly |
| Active I/O regression | Earlier fixture in the same run restores the finite Vat-to-Mixer route and transfers exactly 250 mB after reload |

The runtime helper is version-specific and fails closed if expected scheduler
interfaces or the settled dependency preconditions differ. It is not production
ticket manipulation and does not alter normal server chunk policy.

#### Validation, failures retained, and remaining risk

- `spotlessApply`, mechanical diff review and `spotlessCheck` passed. Final
  build, CPU/resource/provenance checks and development-hook exclusion passed.
- The final standalone `runGameTestServer -PisolatedAcceptance --offline` passed
  **all 773 required tests**, with unchanged count:
  `.cache/p0-passive-final-gametest.log` (`BUILD SUCCESSFUL`).
- Standard acceptance reruns passed at 5,000 observation ticks:
  `.cache/p0-passive-acceptance.log` and `.cache/p0-passive-acceptance-final.log`.
- Minimum-XP acceptance passed at 49,791 ticks with unchanged assertions in
  `.cache/p0-minimum-xp-trace.log`. Two earlier current-code runs failed the
  O-09 Armadillo supplied-brush assertion at final verification. A control run
  compiling only the two committed pre-fix lifecycle classes also passed at
  49,791 ticks (`.cache/p0-minimum-xp-control.log`). Temporary bounded traces
  confirmed the same assembled controller stayed disabled, with brush damage
  and stock unchanged throughout the long waiting interval; its chunk unloaded
  only at final shutdown. The brush failures remain an **unresolved acceptance
  intermittency**, not a proven consequence of the passive lifecycle correction.
  No tool stock, processing balance, recipe or acceptance assertion was changed
  to obtain a pass. Tracing source is archived in ignored `.cache/` and removed
  from authored source.
- An initial combined invocation's endpoint-count assertion assumed one cluster
  at a Buffer coordinate. The fixture legitimately has differently filtered
  neighboring clusters. It now verifies four unique physical member positions
  and nonduplicated endpoint identities; exact per-member contents checks remain.
- A concurrent control compilation disturbed shared class outputs and caused
  one startup crash. That also exposed stale receipt reuse in the Gradle check.
  The task now deletes its prior disposable receipt before launch, so crashes
  cannot inherit an old pass. Final build/eviction verification ran serially
  after returning to normal source compilation and removing the temporary
  tracing class.
- `git diff --check` passed. Mixer/passive operator defaults retain SHA-256
  `4174EF9E887AB5417AD9CA9612676F3980C9D52811873073D84F0886BAE8A76C` and
  `52A1010AB3E6B41833B23057938EFD2CF0A9D2810884501E437A1DC77B780571`.
  Existing build logs show normal `run/config` Mixer defaults were absent and
  nothing was invalidated. `-PisolatedAcceptance` now explicitly skips that
  normal-runtime invalidation step; the final isolated builds log it as SKIPPED.

Nine modified tracked files and six new authored Java files, nothing staged.
The preceding client changes remain intact; this follow-up adds only the
passive-server correction, development eviction helper/assertions, localized
status message, isolated-build/receipt safeguards and this evidence update.

```text
 M build.gradle
 M docs/CODE_MAINTAINABILITY_AUDIT.md
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/ConduitCuboidController.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/ConduitStructure.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/client/FluidBufferRenderer.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/client/render/MixerControllerRenderer.java
 M src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/client/VatClient.java
 M src/main/java/net/impactvector/mobvats/gametest/client/VatClientSmokeTest.java
 M src/main/resources/assets/mobvats/lang/en_us.json
?? src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/client/FluidBufferRenderLifecycle.java
?? src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/client/MixerRenderLifecycle.java
?? src/main/java/net/impactvector/mobvats/gametest/ClientLifecycleFixtures.java
?? src/main/java/net/impactvector/mobvats/gametest/ServerChunkEviction.java
?? src/main/java/net/impactvector/mobvats/gametest/ServerChunkLifecycleAcceptance.java
?? src/main/java/net/impactvector/mobvats/gametest/client/ClientLifecycleAcceptance.java
```

### Minimum-XP brush observation closure — October 14, 2026

This follow-up supersedes the unresolved brush intermittency recorded above.
The failure is a final-observation race in the development world generator,
not a passive chunk-retirement defect or loss of supplied brushes.

After the long P-06 observation, the operator row resumed for exactly 100 ticks
before the complete verification. A fresh brush paused halfway through its
first operation can break on that last observation cadence. Its scute must
drain before the next acquisition mounts the staged replacement. The strict
held-tool assertion correctly rejects that transient empty port, even though
the normal resource pipeline replenishes it ten ticks later.

Two isolated diagnostic minimum-XP worlds passed at 49,791 ticks with the
original fixed observation timing (`.cache/brush-baseline.log` and
`.cache/brush-baseline2.log`). They did not reproduce the random full-world
failure. Transition tracing showed actual brush breakage with staged stock
and a ten-tick output-drain/reacquisition interval. The new deterministic
`EnvironmentalToolGameTests.armadilloResumeSnapshotCatchesBreakageAndReplenishes`
then reproduces the precise failing snapshot: it pauses a fresh brush with ten
ticks of committed progress, resumes ordinary controller processing for 100
ticks, requires the original supplied-tool predicate to reject the empty port,
and verifies a genuinely mounted replacement and retained scute ten ticks
later. No acquisition, output drain or processing tick is substituted during
that observation interval.

`ManualTestWorld` retains the existing 100-tick resumed-processing minimum.
Only an empty brush port with an actual brush in the input staging slot can
defer its final snapshot, for at most two ordinary ten-tick cadences. Every
original acceptance assertion still runs; a staged brush never satisfies the
final held-brush assertion. Missing stock fails immediately, and a blocked or
nonreplenishing rig reaches the hard deadline and fails the original checks.
The existing bucket exception is unchanged. Failure messages now include held
input, staged input, pending output and game time.

A second new regression,
`armadilloAcceptanceRequiresMountedReplacementAfterBreakage`, commits a real
brush break, rejects staged stock as final acceptance evidence, verifies that
pending scutes block acquisition, and lets ordinary controller ticks drain the
output and mount the conserved replacement. The build and **all 775 required
GameTests** passed in `.cache/brush-final-validation.log`.

The same final validation invocation passed complete minimum-XP world
acceptance at **49,791 observation ticks**, with all ten mirrored fixtures and
nine operator Vats verified. The generated manifest confirms one vanilla XP
point before yield, O-09 delivering 239 scutes through its output chest, and
P-06 producing 1,000 mB of culture through the real fluid route. The disposable
world is `.cache/p0-world-runtime/saves/MobVatsBrushFixed`; its fresh
`generation-complete.txt` receipt confirms full verification. Formatting,
CPU/resource/provenance checks and development-hook exclusion also passed.

Standard acceptance passed separately at **48,381 observation ticks** in
`.cache/brush-standard-validation.log` (`BUILD SUCCESSFUL`). Its first cohort
naturally rolled 240 mB XP, so the unchanged 250 mB culture requirement kept
the existing replacement-cohort path active. All ten mirrored fixtures and
nine operator Vats passed final verification, with a fresh receipt in
`.cache/p0-world-runtime/saves/MobVatsBrushStandard`.

Only development GameTest/acceptance sources and this audit change. Production
tool code, durability, stock quantity (54 brushes), processing balance,
recipes and acceptance predicates remain unchanged. Temporary transition
logging is removed. The build skipped normal development-default invalidation
with `-PisolatedAcceptance`, and the normal Mixer/passive defaults retain the
same SHA-256 hashes recorded in the passive eviction closure.

### P1-A — Formatting depends on an art-generator lexical assumption

**Location/example:** `gradle/procedural-art.gradle:247`–`:249`; `C/ConduitGeometry.java:11`. Whitespace-only Java formatting breaks the exact diameter regex.

**Why/correction:** it blocks safe automated normalization. Make the parser whitespace-tolerant first, preserving the asserted numeric value and output hashes; later replace lexical coupling with a generated geometry contract.

**Scope/risk/tests:** hours, low gameplay risk and moderate build risk; `testProceduralArt`, `conduitArtGeometryTest`, `verifyProceduralArtResources`. Verify both existing and normally spaced declarations with isolated parser fixtures in that future slice. Do not disable the assertion.

### P1-B — Mixer planning mixes policy, mutation, and diagnostics

**Location/example:** `M/process/MixerState.java:50`, `:142`, `:194`, `:205`, `:229`; `workingFluid` restores `blocked` around planning.

**Why/correction:** callers cannot infer query side effects, and planner changes can alter selection diagnostics or atomicity. After formatting, extract named allocation/output-reservation stages and return a plan or typed rejection. Keep reservation commit checks and generation binding intact.

**Scope/risk/tests:** 2–3 small changes over 2–4 days; high regression risk if selection/progress semantics change. MixerOperation, MixerDefaults, MixerFluidVoid, MixerRendering, and drive tests; require unchanged resources/metrics after planning and late failure.

### P1-C — Topology refresh also mutates authoritative state

**Location/example:** `V/MultiblockVat.java:171` configures service tanks/capacities and migrates medium while rebuilding derived routes; `V/process/VatProcessEngine.java:59` and `:162` compile/rebuild during execution.

**Why/correction:** a route invalidation has nonobvious persistence and capability effects; context replacement inside a definition loop is risky to alter. Introduce named phases with unchanged order, document invalidation dependencies, and separate pure derived compilation from service reconciliation only after that order is protected.

**Scope/risk/tests:** phase naming/decomposition 1–2 days, deeper separation later; high lifecycle/resource risk. MultiSpecimen, PhysicalRoute, DynamicResource, MachineLifecycle, SyncArchitecture, VatMediumRendering, and VatRenderLifecycle tests. Preserve registry reload, specimen UUID replacement, and actor acquisition behavior.

### P1-D — Harvest commit phases are difficult to reason about

**Location/example:** `V/process/PassiveOperations.java:31`–`:79`, particularly persisted damage/loot at `:54`–`:58` and final replacement at `:74`.

**Why/correction:** loot preparation, durability, legacy migration, XP capacity, feed consumption, and life stage are interleaved. Extract named planning/commit phases, without rerolling pending loot or charging a replacement twice. Document which effects intentionally precede final feed/output commit.

**Scope/risk/tests:** 2–3 days in separately verified helpers; high risk. CowProcess, PassiveDefinition, ToolInteraction, MultiSpecimen, Slice11, and production minimum-XP acceptance. Do not change loot or balance data while decomposing.

### P1-E — Generic acquisitions expose an implicit immediate-commit contract

**Location/example:** `C/ResourceRouting.java:36`, `:46`, `:49`; commit delegates to `ResourceReservation`, whose validation at `:28`–`:29` checks resources/capacity, not current endpoint role, filter, availability, or topology generation. Mixer reservations add those checks explicitly.

**Why/correction:** current same-thread immediate use limits interleaving, but the public reservation can appear safe to retain. Document lifetime now; in a later contract slice either make immediate consumption explicit or capture/revalidate routing conditions. Do not conflate private owned resources with reversible external capabilities.

**Scope/risk/tests:** 1–2 days; medium-to-high routing risk. ConduitItem/ResourceRole/DynamicResource stale-plan tests and ConduitPerformance generation tests; add a prepare/change-filter-or-remove/commit acquisition case if retained reservations remain public. No live delayed-call failure was established in this audit.

### P2-A — Broad formatting/import debt (layout debt addressed)

**Location/example:** measured throughout production; `M/MultiblockMixer.java:306`, `C/PhysicalFluidStorage.java:124`, `V/process/ProcessTransaction.java:17`, `T/Slice11GameTests.java:55`.

**Why/correction:** reviewers cannot see atomic steps and failures point to overloaded lines. Adopt the formatter after P1-A, then normalize one package at a time; expand imports separately with compilation. Do not rename or extract helpers in whitespace commits.

**Scope/risk/tests:** several small package slices, roughly 1–2 days after tool selection; low behavior risk, moderate merge/build risk. Compile/check for production slices; focused GameTests for affected domains after mechanical equivalence review. Art output/hash checks when source-consuming files change.

### P2-B — Duplicated eligibility/adapters and overly mutable APIs

**Location/example:** feed checks `V/process/VatProcessEngine.java:30` versus `V/process/PassiveOperations.java:17`; synthetic cluster endpoints `V/MultiblockVat.java:115` versus `M/MultiblockMixer.java:115`; storage/service mutation and public tank access described above.

**Why/correction:** definition changes can drift between “eligible” and “executable”; duplicated adapters obscure ownership. Extract only proven-identical policy and stable endpoint-view construction, then narrow query consumers to snapshots/capabilities. Preserve reservation identity and deterministic ordering.

**Scope/risk/tests:** one duplication or API at a time, 1–3 days total initial scope; medium risk. MultiSpecimen shared-feed/tool tests, ResourceRole/ConduitItem tests, Mixer port tests, and CPU/render status checks.

### P2-C — Test diagnostics and fixture discovery

**Location/example:** `T/MixerOperationGameTests.java:81`, `:108`; reflection in `T/MachineLifecycleGameTests.java:49`; chronological `T/Slice11GameTests.java`; CPU check aggregation in `src/test/java/net/impactvector/mobvats/render/ConduitArtGeometryTest.java:19`.

**Why/correction:** one combined assertion hides which invariant failed, private reflection complicates moving code, and ordinary `test` is misleading. First create a behavior/task index and split assertion messages; later name narrowly scoped fixtures and organize by behavior. Keep intentional internal-performance tests recognizable.

**Scope/risk/tests:** 1–2 days in test-only changes; low gameplay risk, medium evidence risk. Re-run affected tests and prove CPU entry points remain wired. Preserve manual-world GameTest method references.

### P2-D — Acceptance generation uses static session state and long verification

**Location/example:** `T/ProductionWorldFixtures.java:35`–`:53`, `:419`; `T/ManualTestWorld.java:30`–`:36`, `:39`; `T/GenericCaptureGameTests.java:31` reconstructed registry snapshot.

**Why/correction:** resets/overrides are distributed and session isolation is difficult to prove. Encapsulate generation state in a per-run object; extract named observation/conservation assertions and full scoped registry snapshots. Preserve actual port feeding, pending cohorts, sword identities, and report/completion ordering.

**Scope/risk/tests:** 2–3 days, medium risk to acceptance reliability; both normal and minimum-XP acceptance generation plus existing fixtures. Those future runs launch servers and were expressly not run for this audit.

### P2-E — Procedural generator combines too many concerns

**Location/example:** `gradle/procedural-art.gradle:10`, `:99`, `:190`, `:1167`, `:1666`; Java-source regex at `:248`.

**Why/correction:** primitives, production model binding, previews, and verification change together. After parser repair and output baselining, extract one helper or test group at a time into build tooling with explicit input/output ownership. Keep deterministic seeds, authorship, collision checks, manifests, and hashes unchanged.

**Scope/risk/tests:** 2–4 days incremental; medium build/art risk. CPU geometry exports, deterministic two-run comparisons, packaged resource verification, placeholder guard fixtures. A source move may legitimately change source-provenance hashes; pixel/model hashes must still prove equivalence.

### P3-A — Comments, units, and historical names

**Location/example:** `V/part/VatBoundaryEntity.java:16`, `M/process/MixerState.java:18`, ignored boolean `V/MultiblockVat.java:94`, `M/MultiblockMixer.java:55`, `B/ActiveBoundaryPort.java:20`.

**Why/correction:** misleading comments, ambiguous knobs, and unlabeled units raise onboarding cost. Correct comments, name timing/budget constants, and document compatibility names. Unused-parameter removal belongs in a separately compiled caller cleanup; serialized keys stay stable.

**Scope/risk/tests:** hours per cluster, low risk; compile plus relevant drive/sync tests for API/constants. Avoid naming churn in published IDs or saved tags.

### P3-B — Portable workspace/documentation and benchmark drift

**Location/example:** `AGENTS.md:3`, `docs/06-DEVELOPMENT.md:5`, outer `../AGENTS.md` placeholder-ledger path, `benchmarks/build.gradle:22` hardcoded versioned JAR.

**Why/correction:** a newcomer follows unavailable reference instructions or a stale artifact name. Document actual partial-reference availability and missing portable checkout; reconcile ledger links. Later use an explicit benchmark artifact input rather than a literal mod version. Do not clone references or weaken release rules as a documentation fix.

**Scope/risk/tests:** hours for documentation, small separate benchmark task change; low production risk. Documentation link verification; benchmark preparation/build checks if that later task changes. No benchmark/server run was performed here.

## 7. First three corrective slices

1. **Resolve the two P0 safety questions with bounded evidence.** Add autonomous characterization for Buffer interactions at owner/chunk boundaries and a testable client-cache retirement contract. If either fails, fix only that boundary/lifetime and rerun the relevant conservation/lifecycle checks. If evidence demonstrates intentional cross-owner storage or guaranteed renderer retirement, record the contract and downgrade the item. Estimated 1–3 days. No client launch or wholesale lifecycle rewrite. This limited exception precedes cosmetic work because ownership/unload mistakes can destabilize working mechanics.
2. **Make formatting safe and enforceable.** Repair the art diameter parser without changing output; trial and pin Spotless/formatter with Java 21 and Gradle 9.2.1; document check/apply and CPU task entry points. Select a tiny representative Java batch first. Exit evidence: parser accepts current/normal spacing, generated geometry/pixels remain equivalent, formatter is deterministic, compile and CPU/art checks pass. Estimated half to one day. Keep build-tool wiring separate from the later mass formatting diff.
3. **Normalize source in reviewable package batches.** Start with shared resource/storage and small value objects, then Mixer/Vat process code, controllers, client packages, and tests. Each batch changes layout only; import organization is separately compiled. Record baseline and post-change relevant checks, preserve NBT/schema/IDs, and leave helper extraction for subsequent P1 slices. Estimated 1–2 days spread over small changes. Exit evidence: understandable diffs, formatter check passes, no changed mechanics or generated art, and affected automated checks pass.

After these slices, begin P1-B with a pure Mixer planning result rather than simultaneous controller/process rewrites. Estimate ranges assume familiarity with the tests; they are planning estimates, not delivery promises.

## 8. Areas that should not be rewritten

* ZeroCore attachment, validation, pause/restoration, activation, and assimilation authority. Preserve the family controller/part-type/entity/client structure and the passive-block bridge rather than duplicate the framework.
* Physical per-Buffer persistence, exact fluid/components, resource-only connectivity, deterministic routing, Producer/Consumer/Store roles, and TRUNK/TAP semantics. Do not replace these with one hidden controller tank or a generic ticking bus.
* Private owned-resource transactions, single-use reservations, pending service publication, backpressure, retained loot, and fractional yield remainders. Do not make external capabilities pretend to support rollback.
* Mixer max-flow handling of overlapping ingredients, authored priority/ID order, definition signatures, atomic multi-output execution, real storage before Voids, motor power and spin-up rules. Keep generic recipe execution generic.
* Captured-state whitelists, specimen UUID ownership, detached non-ticking visuals, saved geometry, independent per-Head state, shared-actor fairness, and legacy migration compatibility. Preserve the limited detached-interaction adapter model rather than simulate an entire server.
* Documented MobVats geometry: specimen clearances/displacement, whole-vessel Mixer radius policy, shaft/impeller placement, universal mechanical cladding, and family adapter identity. These intentionally differ from XR2 reactors.
* FullSync versus NetworkUpdate, equality suppression, unload-safe notifications, and disabled-machine boundary/output behavior. Do not infer that “disabled” means disconnected or that every dirty mark requires an extra update.
* Deterministic authored art, provenance history, AI-placeholder release blockers, protected outputs, and generated resource ownership. Never manually repair generated resources or retire safety assertions to make formatting pass.
* Behavioral GameTests, focused lifecycle/performance contracts, production/minimum-XP acceptance, and user-controlled visual acceptance. Improve discoverability and failure messages while retaining this evidence.

The goal is to expose and enforce existing contracts. Smaller methods and a formatter are useful only when they make those contracts easier to see without changing them.
