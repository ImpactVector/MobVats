# 01 — Architecture

## 1.1 Components and integration

MobVats targets Minecraft 1.21.1 / NeoForge with external ZeroCore2. The Java root is `net.impactvector.mobvats`; registration is owned by `MobVats`, `Content`, `MixerContent`, and resource-specific content registries. Vat and Mixer packages use controller, part-type, individual part entity and client subpackages following ExtremeReactors2's organization. No reactor simulation or replacement multiblock framework is embedded.

Both controllers extend `ConduitCuboidController`, which integrates ZeroCore's `AbstractCuboidMultiblockController`. ZeroCore discovers parts, owns attachment/assimilation, classifies cuboid positions, validates structure and dispatches lifecycle callbacks. `VatPartType` / `MixerPartType` and `AbstractVatEntity` / `AbstractMixerEntity` adapt physical parts to that framework. Mechanical family adapters remain distinct block-entity types even where inventory items and blocks are universal.

## 1.2 Ownership

| State | Authoritative owner |
|---|---|
| Captured identity, saved geometry, filtered specimen NBT and specimen UUID | Individual Head |
| Per-Head progress, stage, pending work and rotating scheduling cursor | Vat process engine, persisted through controller |
| Environment clean/dirty medium, aggregate metrics and committed kill output | Vat controller |
| Inventory, equipped tools, service staging, physical buffer contents and drive resources | Physical part that holds them |
| Recipe progress/input inventory/private output inventory | Mixer state, persisted through controller |
| Topology, storage views, assembly descriptors, clearances, endpoint assignments | Derived services rebuilt from validated structure |
| Render meshes and detached display entities | Client-only caches |

`PhysicalFluidStorage` composes physical tanks and references; a controller routing view is not a second copy of their contents. Transaction machinery preserves committed resources across backpressure, reload and controller assimilation.

## 1.3 Structural lifecycle

Assembly and restoration rebuild attached-part subsets, topology, storage views and process contexts. Mixer assembly compiles one physical vessel descriptor. Vat assembly resolves every Head and its unique services. Repeated restoration replaces derived state rather than duplicating stored resources or subscriptions.

Pause is a chunk/unload lifecycle state. Controllers retire runtime references and presentation snapshots locally; Vat pause does not mutate Head presentation or notify unloading chunks. Mixer pause silently resets drive presentation and forgets neighbor snapshots. Queued conduit notifications reject paused controllers both when queued and when delivered. Loaded-neighbor checks protect capability transfers.

Disassembly invalidates structure-dependent services and permits later repair through ZeroCore. Mixer neighbor snapshots needed to detect correction remain indexed for a broken structure, but are removed on pause and donor assimilation. Render updates occur on the client where appropriate. Assimilation merges committed state into the recipient and retires donor caches/resources through existing callbacks.

## 1.4 Operational activation

Vat and Mixer implement ZeroCore's `IActivableMachine`. Operator activation is persisted and synchronized independently of structural assembly and Mixer drive state. Identical activation requests do nothing. Part activation callbacks and logical-server dirty notification follow ZeroCore's contract; paused changes avoid world-facing work.

Assembled structure remains the execution gate. Disabling production does not sever connected boundary devices or discard resources. Vat committed-output transfers and boundary I/O can continue while biological work is disabled. Mixer productive recipe work also requires full drive and a running motor.

## 1.5 Persistence and synchronization

Controller `syncDataTo` / `syncDataFrom` use ZeroCore FullSync and NetworkUpdate reasons. FullSync owns persisted progress/resources; NetworkUpdate carries required client presentation, including derived conduit snapshots and Mixer working-fluid identity/components. Server reload resets transient drive startup and rebuilds validated geometry through restoration.

Persistent controller mutation uses the controller dirty helper. In this ZeroCore version, dirty marking already requests an update; adding a second notification duplicates work. Physical part mutation uses `setChanged` and its needed part update; linked inventory handlers already supply persistence marking. Derived descriptor changes request description synchronization without treating unchanged geometry as resource mutation.

Equality checks suppress identical Head state, cached geometry, demand announcements and activation changes. Vat engine scheduling changes are persisted, with one engine notification at changed tick completion. Mixer spin-up publishes its start, 20-tick corrections and transition to RUNNING; client interpolation remains presentation only. These conclusions preserve the three parity slices; their original evidence and measurement limits are in the [lifecycle parity record](history/README.md).

## 1.6 Rendering and subscriber lifetimes

Specimens are detached visual objects, never live level entities with AI, ticks or pathfinding. Heads own captured state; client render descriptors own display placement, straps and mechanisms. Construction visibility and glass/casing variants use ZeroCore model data. Render and resource-reload caches belong to client packages.

Global registration occurs at mod initialization or static event subscription, not on each restoration. Structural notification and neighbor indexes use weak controller keys with values that do not retain their owners/worlds. Development fixtures, GameTests and smoke hooks are excluded from the player JAR and checked by `verifyDevelopmentHooksExcluded`.

## 1.7 Optional inspection

Jade is compile-only in the normal mod build and included only in the manual client runtime. `MobVatsJadePlugin` transports `MachineInspection` snapshots of existing formation errors, inventories, endpoint configuration, process status and compiled conduit membership. Inspection does not recompile routes or mutate resources. Entries are bounded, with omitted counts. Detailed multiplayer inspection also requires Jade on the server; it is not a mandatory MobVats dependency.

See [multiblocks](02-MULTIBLOCKS.md), [routing](03-RESOURCE-ROUTING.md), and [testing](07-TESTING.md) for the contracts exercised by these components.
