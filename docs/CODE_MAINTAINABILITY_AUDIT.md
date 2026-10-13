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

Severity and confidence are separate. **P0 entries below are source-supported safety review gates, not reproduced runtime failures.** Their missing guards/retention paths are concrete; the affected gameplay/client scenarios were not executed. Characterize them before further feature development, fix confirmed failures, and downgrade a concern only with recorded evidence. P1/P2 findings are confirmed source-level maintainability problems.

### P0-A — Buffer interaction discovery can escape the controller's member set

**Location/example:** `C/FluidBufferEntity.java:61` through `:72` discovers every adjacent Buffer using `level.getBlockEntity` with no loaded-chunk or controller-owner fence. Compare `C/FluidBufferPresentation.java:34`, `:39` (loaded and owner checks) and bounds-limited controller storage compilation.

**Why:** a container action can form a larger logical tank than either controller/presentation recognizes. Compatible buffers touching across independent machine envelopes may be filled/drained together; traversal at a chunk edge can also request block entities outside loaded chunks. Retained controller cluster views can subsequently observe changed members without membership being rebuilt immediately. This is an ownership/lifecycle hazard, not just duplicate BFS code.

**Correction:** first characterize unformed adjacent clusters, same-owner formed clusters, touching independent owners, and unloaded neighbors through actual container interactions. Define the intended owner boundary in routing documentation. If cross-owner interaction is unintended, centralize only loaded/owner-constrained member discovery and retain the existing compatibility compiler and member persistence. Never use presentation snapshots as gameplay storage.

**Scope/risk/evidence:** small targeted discovery change plus autonomous tests, approximately 1–2 days; medium risk to legitimate standalone clusters. Existing Slice11 split/write-through, FluidBufferArt incompatibility, ConduitPerformance retained-handler, and PartOwnership tests are relevant but do not prove this exact container boundary case.

### P0-B — Detached client specimen can indirectly retain the weak cache's world

**Location/example:** `V/client/render/VatHeadEntityRenderer.java:24`, `:41`, `:110`. A weak-key cache value holds a Mob created with the real client Level. Removal at line 63 requires another call for the invalid part. The comment rules out a direct back-reference but not `value -> specimen -> Level -> loaded chunks/parts -> key`. No explicit disconnect/level-change cleanup was found in `V/client/VatClient.java`.

**Why:** WeakHashMap alone does not guarantee old world/part collection when values reach their keys indirectly. Renderer recreation on resource reload is not a declared per-world retirement contract. Actual retained-world behavior on logout/dimension change remains unverified.

**Correction:** verify renderer lifetime and implement explicit cache retirement on client level change/disconnect if needed, preserving detached, never-ticked specimens. A small testable cache owner can prove retirement without launching Minecraft; the human operator handles any later client acceptance. Do not attempt to solve this by spawning live entities or retaining only weak visual Mobs that may disappear unpredictably.

**Scope/risk/evidence:** roughly 1 day for lifetime characterization and targeted retirement; medium presentation risk. VatRenderLifecycle tests protect descriptors/Head restoration, not GC of actual client Mobs. Existing client smoke code is not authorization to launch it. This requires new focused lifetime evidence if confirmed.

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
