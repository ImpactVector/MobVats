> Historical record. Original: `docs/ZEROCORE_LIFECYCLE_PARITY.md`. Current reference: [01-ARCHITECTURE.md](../01-ARCHITECTURE.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# ZeroCore lifecycle parity — slices 1–3

## Source comparison before implementation

Inspected local upstream sources: ExtremeReactors2 branch `1.21`, revision
`f6cc65140a8abbfc87137f5c54fc641085f66048`; ZeroCore2 revision
`64d892b1ba099192a35bfa4ee08465540a2f12e7`. Paths below are relative to
`src/main/java/it/zerono/mods/` in those reference checkouts.

| Topic | XR2 / ZeroCore behavior | MobVats behavior before this slice | Classification and decision |
|---|---|---|---|
| Assembly | Fluidizer rebuilds recipe sources/holder; Reprocessor rebinds collector. ZeroCore validates, sets assembled, invokes controller then part callbacks. | Vat compiles specimen/topology/process services; Mixer compiles sweep/storage/routes. | Equivalent behavior; retain controller-owned derived services. |
| Restoration | `MultiblockFluidizer.onMachineRestored` and `MultiblockReprocessor.onMachineRestored` call assembly. | Both controllers call assembly. | Equivalent behavior; retain and test repeated rebuilding without duplicating resources. |
| Pause | Fluidizer does nothing; Reprocessor nulls collector. ZeroCore `AbstractMultiblockController.detachPart` sets paused before pause and removal callbacks. | Mixer silently stops drive and retires descriptors/routes; Vat calls `clearPresentation`, whose Head setter calls `notifyBlockUpdate`. Vat process contexts and storage clusters retain endpoint references. | Suspected correctness defects: notification during unload and retained runtime references. Keep local invalidation; remove world-facing pause/removal work. |
| Disassembly | Fluidizer/Reprocessor assign `_active=false` directly, avoid public setter, render only on client. Reactor does not reset activation; Turbine retires data through `TurbineData`. | Mixer calls notifying drive setter; Vat clears Head presentation and geometry. | Unnecessary divergence for Mixer notification; use silent drive stop. MobVats-specific operator preference survives structural repair; ZeroCore assembly remains execution gate. |
| Activation | Fluidizer/Reprocessor/Turbine implement `IActivableMachine`: flag, part activation callbacks, logical-server reference update. Reactor exposes this through `IReactorWriter`. | No operator enable setting; assembly/drive act as production gates. | Unnecessary divergence; adopt existing interface and callbacks, default enabled for compatibility. Separate drive state from preference. |
| Tick ownership | Fluidizer gates recipe processing on active; Reactor/Turbine also maintain attached tickable parts and transfer resources separately. | Controllers schedule engines, boundary ports and pumps. | Justified MobVats-specific behavior; keep scheduling, gate productive work only. Pumps, valves, voids, active/passive I/O, service evacuation, Mixer intakes remain connected and scheduled. |
| Assimilation | ZeroCore transfers parts then invokes recipient merge and donor cleanup. XR2 merges committed tanks/inventories/energy; Reactor/Turbine merge machine data. | Engines merge persisted process state; resources live in physical buffers and service parts, with legacy escrow in controllers. | Justified MobVats-specific ownership; retain transactions and merge rules. Disabled preference must survive joining an empty fragment. |
| Persistence | Active stored through controller `syncDataTo/From`, direct field assignment on load; save delegate owns controller NBT. Controller parts dispatch activate/deactivate via `TileCommandDispatcher`. | Engines/medium in controller; inventories, tools, physical fluid/service stages in parts; drive runtime resets on server load. | Equivalent ownership with missing activation field; persist/sync flag through ZeroCore, load without operational notifications. |

Relevant sources: XR2 `extremereactors/gamecontent/multiblock/{fluidizer,reprocessor,reactor,turbine}/Multiblock*.java`, corresponding `part/*ControllerEntity.java` and abstract entities; ZeroCore `zerocore/lib/IActivableMachine.java`, `zerocore/lib/multiblock/AbstractMultiblockController.java`, `AbstractMultiblockPart.java`. These are source-derived decisions, not claims of complete XR2 equivalence.

## Scope boundaries

No new lifecycle framework, tickable-part migration, routing/topology rewrite, recipe or specimen scheduling redesign, catch-up timer, or P-06 XP starvation change. Structural authority stays in ZeroCore. Client visual acceptance remains manual; only autonomous headless tests are run.

## Implemented contract

- Both controllers implement ZeroCore `IActivableMachine`. `active` is the operator preference, defaults to true (including older saves), and is included in FullSync and NetworkUpdate. Loading assigns it directly without operational callbacks. XR2-compatible `activate`/`deactivate` tile commands are registered on the Controller entities.
- Vat Controller menu has Enable/Disable. For either machine, crouch-use a redstone torch on its Controller to toggle without consuming the torch. Existing Mixer empty-hand XP deposit and bucket behavior are retained.
- Disable freezes Vat specimen execution, growth/regrowth, production input consumption, process cadence and next-Head scheduling. It does not reset specimen state or tool assignments. Committed tool/legacy item results still route to ordinary output ports or voids. Boundary transfer runs on its existing cadence; production cadence resumes from its saved value without catching up.
- Disable stops Mixer drive execution immediately and silently resets spin-up/runtime cadence. Availability remains independently queryable. Recipe selection/progress, inventories, escrow and reservations are not cleared. Reenable requires ordinary spin-up before recipes advance.
- Pumps, Fill/Drain Valves, voids, active/passive ports, service evacuation and Mixer intake collection continue while assembled and disabled. Metrics measure assembled time, including disabled time; no offline time is synthesized.
- Pause only retires local runtime data: Mixer drive/assembly/render descriptors, topology/routing/intakes/geometry; Vat assembly/topology/routing/clearance/facing/process contexts. `PhysicalFluidStorage.clear` drops cluster wrappers, endpoint services and connectivity caches without mutating physical tanks. Coordinate-only medium geometry and immutable recipe definitions may remain; they cannot acquire chunks or tick while ZeroCore is paused.
- Vat Controller pause/removal never invokes Head presentation setters. Structural break presentation belongs to ZeroCore's Head callback, guarded against removed/unloaded Heads. Mixer structural/removal callbacks use silent drive stop. Assembly/restoration rebinds services and publishes current presentation when the structure is loaded.
- Structural break preserves the operator preference (a deliberate difference from Fluidizer/Reprocessor), while ZeroCore assembly gates execution. Assimilation combines preferences with logical AND so a disabled machine cannot become enabled by joining an empty default-enabled fragment; donor transient state is retired after committed state transfer.
- P-06 operator suspension saves only enable preferences, deactivates the nine assembled Vats, and restores those preferences afterward. Final fixture verification requires the row to be enabled. Dedicated Controller removal/NBT restoration tests retain coverage of the replaced workaround.

## Regression evidence and limits

`MachineLifecycleGameTests` covers defaults, production freeze/resume, save/network flag round trips, active/disabled Vat and idle Mixer pause, repeated restoration, physical fluid components, Controller and port replacement, both assimilation directions, and exact once-per-server-tick execution after reload. Existing `MixerDriveGameTests` spinning/running unload regressions are unchanged. Disabled boundary regressions cover both machines' scheduled active transfers and passive capability access, Pull/Push Pumps, Vat valves, fluid void/service evacuation and committed Vat item voiding. Existing connectivity snapshot performance tests remain in the full suite.

Unload regressions invoke actual ZeroCore pause/detach callbacks with the test chunk loaded: reference dirty state and Head presentation revisions detect unsafe notifications without hanging the server. Source review additionally confirms no world queries/chunk acquisition in these controller pause/removal paths. These tests do not simulate eviction of a GameTest-owned chunk from the server's loaded map. Client flag serialization/menu sampling is tested headlessly; rendered client acceptance remains user-controlled.

Later slices may address GUI parity (Mixer still uses diagnostics rather than a full XR2-style controller screen), XR2 variant/tickable-part organization, and P-06 XP supply. No claim of full lifecycle or performance equivalence is made.

## Validation (October 11, 2026)

| Command | Result |
|---|---|
| `gradlew.bat build` | Passed compilation and existing packaging/resource guards. No standalone unit test sources; regression execution is in GameTests. Existing deprecated mock-player warnings remain. |
| `gradlew.bat runGameTestServer` | 703 required tests passed; server saved all dimensions and JVM/Gradle exited successfully. Includes 27 added lifecycle/disabled-boundary regressions. |
| `gradlew.bat buildTestWorld -PworldName=MobVatsAcceptance` | Passed original, creative-fed and operator acceptance at 5,000 observation ticks; saved all dimensions, generated completion marker and exited normally. |
| `gradlew.bat buildTestWorld -PworldName=MobVatsAcceptanceMinimumXp -PacceptanceMinimumCowXp=true` | Passed the extended branch at 49,771 observation ticks. At 5,000 ticks P-06 had 160/250 mB XP; operator production was deactivated during the unchanged observation window, then resumed. Culture and enriched-growth checks completed; all dimensions saved and JVM/Gradle exited successfully. |
| `git diff --check` | Passed. |

Logs are retained in the sibling workspace's ignored `.cache/`: `lifecycle-build-final.log`, `lifecycle-gametest-final.log`, `lifecycle-acceptance-final.log`, `lifecycle-acceptance-minimum-xp.log`. Early fixture assertion failures were corrected and rerun; the standard suite now validates physical milk directly when an unformed controller has retired its storage view. No client was launched and nothing was published or deployed.

## Slice 2 — attached scheduling, ownership and synchronization

Source baseline: existing `main` checkout plus the uncommitted Slice 1 implementation
above. No Slice 1 work was reverted. References remain XR2 `1.21` revision
`f6cc65140a8abbfc87137f5c54fc641085f66048` and ZeroCore2 revision
`64d892b1ba099192a35bfa4ee08465540a2f12e7`, inspected in the sibling
`examples/ExtremeReactors2` and `examples/ZeroCore2` checkouts.

### Source call paths and classification

| Concern | Concrete upstream call path | MobVats equivalent and classification |
|---|---|---|
| Process execution | `MultiblockFluidizer.updateServer` gates `_recipeHolder.processRecipe` on activation; `MultiblockReprocessor.updateServer` gates `IHeldRecipe.processRecipe`. Both separately advance update tickers. Fluidizer `onPartAdded/Removed` maintains injector subsets; assembly rebuilds recipe sources. Reprocessor discovers its one collector during assembly and clears it on pause. | **Equivalent / justified:** `MultiblockVat.updateServer → VatProcessEngine.tick` and `MultiblockMixer.updateServer → tickDrive → MixerState.cadence`. Controller scheduling owns atomic allocation, recipe commits, specimen ordering and tool selection. Disabled production remains gated separately from boundary transport. |
| Part ticks | Reactor/Turbine `onPartAdded/Removed` register/remove `ITickableMultiblockPart`; `updateServer` explicitly dispatches `onMultiblockServerTick`. `ReactorRedstonePortEntity.onMultiblockServerTick` maintains a local notification cadence; creative generators also implement the interface. | **Justified:** no equivalent independent redstone/generator tick consumer needs migration here. Active ports use controller-rotated position order and return transfer results; pumps share topology/storage; drives participate in spin-up and recipe gating. A second independent part ticker would duplicate scheduling. |
| Actor-local timing | XR2 redstone parts own their local notification timer, while controller logic owns machine simulation. | **Justified:** `VatToolPortEntity.available` checks its persisted interaction epoch for cooldown, and `VatProcessEngine.toolTurn` uses the part's last target for per-actor round robin. Controller commits set these values; no independent tick is needed. They also drive rendering but are not purely cosmetic. A misleading setter comment was corrected. |
| Framework tick ownership | ZeroCore `updateMultiblockEntity` exits if unassembled, selects logical server/client, and calls the controller loop once. `ITickableMultiblockPart` only declares a callback; ZeroCore does not automatically dispatch it. A true return raises data subscribers and dirties loaded machine chunks. | **Equivalent:** both machines use this one execution path. Mixer client loop advances only `MixerRotorAnimation`; Vat client loop does no gameplay. No new block ticker or framework was added. |
| Repeated role discovery | Reactor/Turbine retain role subsets; Fluidizer retains injectors. Reprocessor's assembly-only collector discovery demonstrates that all structural enumeration need not be cached. | **Divergent, aligned:** Mixer previously filtered all connected parts for every `drive()` query and passed all parts to pump and active-port scheduling. Vat filtered/sorted its broader port/actor subset each dispatch. Both now retain ordered role subsets, maintained by attachment callbacks. Validation/topology-only scans remain. |
| Pause references | ZeroCore `detachPart(part,true)` changes state to paused and calls `onMachinePaused` **before** removal. Restoration invokes validation and `onMachineRestored`; XR2 process machines reconstruct derived services. | **Defect, corrected:** Vat's existing `attachedPorts` retained other parts during pause; retirement now also clears that set and the new device subsets. Mixer clears drive/pump/active-port subsets. Removed snapshot references are dropped immediately; no unload world query or notification is introduced. |
| Assimilation | ZeroCore `assimilateController` invokes `partAdded` for acquired parts, then recipient `onAssimilate` and donor `onAssimilated`. It does not deliver ordinary donor removal callbacks for every part. XR2 recipients transfer/merge committed resources; Reactor clears donor role/layout references, Fluidizer clears injector subsets, and Reprocessor has an empty donor callback. | **Equivalent:** identity sets prevent duplicate registration; donor retirement explicitly clears subsets. Assembly rebuild validates the recipient against ZeroCore's parts. Existing resource and disabled-preference merge rules remain. |
| Persistent updates | ZeroCore `markReferenceCoordDirty` calls `Level.blockEntityChanged` **and** `WorldHelper.notifyBlockUpdate`; `markReferenceCoordForUpdate` calls just the latter. `WorldHelper.notifyBlockUpdate` calls `sendBlockUpdated(...,3)`. This source behavior is more specific than the dirty helper's no-description-update Javadoc. | **Divergent, corrected:** Vat `processStateChanged` and Mixer `changed` previously called both helpers; now each persistent notification uses the dirty helper once. The Vat boolean signature is retained for callers, but even `sync=false` already notified under this framework version. No new persistence mechanism overrides ZeroCore. |
| Drive presentation | Turbine separates machine data, client rotor presentation, and client update tracking; Reprocessor client ticks only collector presentation. | **Divergent, aligned:** Mixer spin-up advancement and notifying drive stop now call only `markReferenceCoordForUpdate`. Fuel/FE mutation remains part-owned; recipe/activation mutations retain persistent notifications. Startup ticks remain transmitted each tick because `MixerRotorAnimation.tick` derives speed from the synchronized startup value. |
| Unchanged blocked state | Fluidizer/Reprocessor separate recipe results and update ticker callbacks. | **Divergent, corrected:** `MixerState.cadence` previously notified even when its blocked recipe state was unchanged. It now compares recipe, progress and blocked status, and explicitly preserves notification for a successful resource commit even if final recipe/progress values match their initial values. Migration still notifies when resources actually move. |
| Idle tick results | ZeroCore's true `updateServer` result raises data subscribers and marks all loaded machine chunks unsaved; XR2 process controllers return actual recipe change results. | **Divergent, corrected:** Mixer previously returned true on every intake cadence, including zero transfers. It now combines actual evacuation, pump, port and intake transfers. Recipe state mutations independently use the persistent callback. `MachineMetrics.tick` ages transient recent samples; `save` persists only lifetime totals, so idle sampling does not justify periodic dirtying. |
| Port synchronization | XR2 `ReactorSolidAccessPortEntity.syncDataTo/From` owns local inventory/direction state; controller entities dispatch activation and request local rendering changes. | **Equivalent / justified:** MobVats part NBT owns endpoint configuration, inventories and physical stores. `MachineFluidPort.refresh` retires the previous forwarding target and binds the current route without dirtying machine state. Mixer `rebuildTopology` no longer refreshes fluid ports a second time after `compileConduits` already refreshed them. |

`AttachedPartCollection` is shared only by Vat and Mixer. It is an identity-based
role subset with a lazily sorted immutable view, not an additional topology cache
or authoritative connected-part registry. ZeroCore owns attachment and structural
validation. Adding/removing parts invalidates the view; removal also immediately
drops the collection's old view. Pause/break/assimilation retire it; assembly and
restoration rebuild from ZeroCore. Mixer validation uses a temporary drive subset
while unassembled, so failed validation cannot repopulate a paused machine's caches.
Parts are not registered into subsets while paused. Normal assembled ticks do not
perform reconstruction. The existing topology/connectivity snapshots are unchanged.

### State ownership matrix

| State | Authoritative owner and persistence | Rebuildable/runtime or client projection |
|---|---|---|
| Structural membership and assembly state | ZeroCore controller/part registry and lifecycle; controller save delegate persists its data through part NBT | Controller role subsets, bounds and validated assembly |
| Operator activation | Vat/Mixer controller `active`, FullSync and NetworkUpdate | Part activation callbacks/menu sampling; independent of structural validity |
| Vat specimen process, growth/regrowth, ordering | `VatProcessEngine.states`, UUID `stateOwners`, cadence and next-Head in controller FullSync | `ProcessContext`, evaluated status, scheduling view and actor routes retire on topology invalidation |
| Specimen identity and physical representation | `VatHeadEntity` identity, specimen UUID, encoded compound state and geometry in Head NBT | Life stage projects the engine's state when assembled; `VatPresentation` is a derived Head projection |
| Vat medium | `VatMediumStorage` committed fluid/component state in controller NBT | Coordinate-only exposure/medium geometry rebuilt from structure; no block-entity references |
| Vat products and XP | Physical `FluidBufferEntity` tanks and part `ServiceStaging`; controller `ProductBuffers` and engine legacy XP retain only unmigrated escrow | Storage cluster/endpoint wrappers and resource-selection metadata; no additional committed copy |
| Vat kill/tool outputs and feed/tool items | `KillOutputBuffer` legacy controller output; `VatToolPortEntity.results/held`, last target and interaction epoch/cooldown, and `VatItemPortEntity.staging` in part NBT | Tool assignments/topology and process plans; aim is presentation. Last target and epoch also govern actor ordering/availability and therefore legitimately persist |
| Machine metrics | Vat `MachineMetrics`, Mixer `MixerState.metrics` lifetime totals in controller FullSync | Recent buckets, cursor and sampled assembled time reset on load; sampling continues while disabled. Menu status is a projection |
| Mixer recipe and item inventories | `MixerState` recipe/progress, inputs/produced items, restored identity metadata and legacy escrow in controller NBT | Recipe definitions/plans/reservations are selected from loaded registry and current physical resources |
| Mixer physical fluid and service staging | `FluidBufferEntity` tanks and Controller-part `ServiceStaging` NBT, including exact compound components | `MixerState.bind` and `PhysicalFluidStorage` expose current storage without copying contents into controller tanks |
| Mixer drive resources | Combustion fuel/burn state and FE in their individual drive entities' NBT | Controller `activeDrive`, startup/running cadence and operating state reset on server load/pause/disable |
| Mixer vessel/rotor | Loaded blocks and validated `MixerSweep` geometry determine usable capacity | `MixerAssembly` is a derived descriptor (also serialized for bounds/presentation); server revalidates on assembly. `MixerRenderDescriptor` and rotor interpolation are client presentation |
| Boundary configuration and disposal | Endpoint filter/mode/direction, void disposal totals and part staging are part-owned | Active port forwarding/remote capability source and pump diagnostic status are runtime; productive enable does not remove boundary access |
| Topology/routing/storage connections | Loaded physical structure and committed endpoint resources | `ConduitTopology`, `ResourceRouting`, `PhysicalFluidStorage`, clearances/facings and all role views retire/recompile; existing connectivity optimization remains |
| Client gameplay views | Server state transmitted through NetworkUpdate/menu | Vat client XP/process/output counts, Mixer client working-fluid copy, liquid selection and animation never commit resources |

No committed state moved between parts and controllers. Restoration rebinds existing
physical resources; legacy migration only transfers amounts actually accepted.
Assimilation leaves physical resources in their parts and merges controller escrow
and process state using existing rules. No recipe, fluid composition, capacity,
balance, routing reservation or atomic transaction semantics changed.

### Counts and verification limits

These are source-derived operation counts and deterministic regression assertions,
not timing benchmarks. Let N be connected parts and P the broader Vat port/actor set.

| Path | Before | After, stable assembled membership |
|---|---|---|
| Mixer `updateServer` boundary scheduling | Two `getConnectedParts()` acquisitions each tick; active scheduling filters N each tick, pumps filter/sort N on pump cadence | Zero connected-part acquisitions for these paths; only ordered active-port and pump subsets traversed |
| Mixer `drive()` | One N-part filter and list allocation per query, including `tickDrive` and port/status queries | Zero N-part discovery; stable sorted drive view reused |
| Vat active-port scheduling | Filter/sort P each tick | No repeated discovery/sort; position-ordered subset with unchanged rotating start |
| Vat boundary-device cadence | Sort P, then runtime filter | Only ordered boundary-device subset; removed/unassembled devices skipped |
| Explicit Mixer topology rebuild | Two fluid-port refresh passes | One pass |
| Vat/Mixer persistent change helper | Two reference block update invocations when both dirty and update requested | One; dirtying retained |
| Twenty unchanged blocked Mixer recipe cadences | Twenty callback invocations | Zero after initial status stabilizes; resource changes and commits still notify |
| Forty idle disabled Mixer controller ticks | Four true intake-cadence results and associated framework dirtying | Zero state-change results/dirtying when no resources move; transient samples still advance |
| Registered dispatch probe over five controller calls | Each part expected once | Five callbacks; removal gives zero additional calls, reattachment gives one |

`PartOwnershipGameTests` exercises both real controller dispatch loops (including
disabled production) with an isolated counting boundary probe, silent role-only
rebuilds, actual port unload/clearRemoved/restoration, removal/replacement,
disassembly, role transfer during assimilation, unchanged blocked cadence, and
spin-up presentation versus persistent item mutation, and idle framework dirtying.
The probe counts callback
ownership, not successful real transfers. Existing boundary tests validate real
transfer amounts, filters, adjacency single ownership and compound identity;
existing lifecycle/drive/resource/transaction tests supply the complementary
committed-state checks. No global production counter or new benchmark harness was
introduced. GameTest chunk-unload limitations described in Slice 1 still apply.

### Remaining concerns and next-slice assessment

- Vat's `processStateChanged(false)` cannot suppress networking through this
  ZeroCore version's dirty helper. Slice 2 removes the duplicate call; coalescing
  broader gameplay notifications would require explicit subscriber/client timing
  analysis. Likewise startup packets still track changing presentation each tick.
- `MachineMetrics` lifetime totals remain persistent; its recent window is
  intentionally transient, without synthesized offline time.
  `MixerStructureEvents` retains coordinate/block-state snapshots under
  weak controller keys to detect corrections of invalidated structures; those
  snapshots contain no block entities and are separate from routing topology.
- Assembly/topology compilation still enumerates parts and physical storage.
  Those event-driven scans rebuild authoritative structure-derived views and are
  not competing per-tick caches. Vat item/tool subfilters remain operation-level
  work over its existing subset, not full connected-part discovery.
- Head life-stage projection and Mixer derived-descriptor serialization remain
  deliberate compatibility mechanisms. Their server owners and rebuild gates are
  documented above. Rendered client verification remains user-controlled.
- Another broad controller/part architecture rewrite is not supported by this
  audit. A focused synchronization/subscriber slice should precede comparative
  performance work if packet-rate parity is part of that benchmark's target;
  Mixer GUI parity is a separate client feature task. No comparative harness was
  created here.

### Slice 2 validation

Completed October 11, 2026:

| Command | Result |
|---|---|
| `gradlew.bat build` | Passed final compilation, packaging and resource guards; existing deprecated mock-player warnings only. No standalone unit test sources. |
| `gradlew.bat runGameTestServer` | All **712 required tests passed** (703 baseline plus nine new regressions). All dimensions saved; server/JVM/Gradle exited successfully. Final behavioral changes were also validated with `build runGameTestServer`. |
| `gradlew.bat buildTestWorld -PworldName=MobVatsAcceptance` | Passed ten mirrored fixtures and nine operator Vats at **5,000 observation ticks**. All dimensions saved, completion marker written, exit successful. |
| `gradlew.bat buildTestWorld -PworldName=MobVatsMinimumXp -PacceptanceMinimumCowXp=true` | Passed the preserved extended P-06 branch at **49,771 observation ticks**, including production suspension/resumption and culture/enriched growth checks. All dimensions saved, completion marker written, exit successful. |
| `git diff --check` | Passed; new source/document files additionally checked for trailing whitespace. |

Final evidence is in the sibling workspace's ignored `.cache/`:
`slice2-build-final.log`, `slice2-validation-complete.log`,
`slice2-acceptance-final.log`, `slice2-acceptance-minimum-xp.log`.
The initial two test failures came from missing specimen encoding in the new Vat
fixture; it was corrected before the successful full-suite runs. Later source
review added the paused-validation and idle-dirtying safeguards, each followed by
successful full-suite validation. No client was launched, no public artifact was
created, and no benchmark harness, commit, push, release or deployment was performed.

## Slice 3 — synchronization and event ownership

Baseline: clean working tree containing the completed Slice 2 changes. Inspected
the actual local XR2 `1.21` checkout at
`f6cc65140a8abbfc87137f5c54fc641085f66048` and ZeroCore2 at
`64d892b1ba099192a35bfa4ee08465540a2f12e7`. No upstream files were modified.
Paths in the source table are relative to upstream `src/main/java/it/zerono/mods/`.

### Source trace and synchronization comparison

| Source / call path | Observed behavior | MobVats comparison and classification |
|---|---|---|
| ZeroCore `zerocore/lib/multiblock/AbstractMultiblockController.markReferenceCoordDirty` | Logical-server `blockEntityChanged` plus `WorldHelper.notifyBlockUpdate`. The implementation contradicts the comment claiming no description update. `markReferenceCoordForUpdate` only notifies. | **Equivalent:** persistent controller mutations use the dirty helper once. Presentation-only geometry and drive changes use the update helper. Count the dirty helper's implicit notification separately from explicit update-helper calls. |
| ZeroCore `zerocore/lib/world/WorldHelper.notifyBlockUpdate` → `Level.sendBlockUpdated(...,3)`; `zerocore/lib/block/AbstractModBlockEntity.getUpdatePacket/getUpdateTag` | A notification requests a block update. Packet construction reads NetworkUpdate NBT; notification is neither immediate packet construction nor a per-player send. | **Equivalent:** retain ZeroCore block updates. Diagnostics distinguish requests, serialization, and packet construction. Vanilla chunk update delivery may coalesce requests; no request-to-packet conversion is assumed. |
| ZeroCore `AbstractMultiblockPart.syncDataTo` | Only the save delegate nests controller data under `multiblockData`; other parts send their own data. FullSync is also used for persistence, not solely networking. | **Equivalent:** controllers own process/medium/activation state; Heads, tools, ports, drives, physical buffers and service staging own their local persistent data. A Head update sends controller state only if that Head is the delegate. |
| ZeroCore `AbstractMultiblockController.updateMultiblockEntity` | Runs one logical-side loop while assembled. A true server result raises `DataUpdated` and dirties loaded machine chunks. It does not itself invoke `sendUpdates`. | **Equivalent:** controller loop owns execution; client loop owns Mixer animation. Preserve true results for real committed state changes. Structural registry dirtiness, chunk persistence, and client description notifications are distinct. |
| XR2 `extremereactors/gamecontent/multiblock/{reactor,turbine}/Multiblock*.updateServer/checkAndSendClientUpdates` | Reactor performs logic/tickables, sends GUI updates separately, and delays fuel-rod layout notifications. Turbine separately tracks RPM via `RpmUpdateTracker(100,5,10,100)` before dirty notification. | **Unnecessary divergence corrected:** Mixer previously requested the entire reference description for every startup tick. Publish drive transitions immediately and startup corrections every 20 ticks; the client advances startup locally between samples. Server acceleration, spin-up duration, running cadence and recipes are unchanged. This uses the existing description channel, not a new packet or sync framework. |
| XR2 `MultiblockFluidizer.updateServer` and `MultiblockReprocessor.updateServer` | Recipe mutation determines the returned boolean; tickers send updates separately. Fluidizer delays fluid status. Reprocessor client loop advances collector presentation locally. | **Equivalent / justified:** MobVats also separates simulation and presentation. Vat committed growth/regrowth timers remain persistently notified; reducing those to transition-only notifications would require a separate persistence/UI analysis. |
| XR2 corresponding Reactor/Turbine/Fluidizer/Reprocessor `part/*ControllerEntity` | Controller parts listen for controller data updates and delegate enlist/delist/send to the controller's sync provider. ZeroCore `NetworkTileEntitySyncProvider` stores a player set and creates a message only when recipients exist. Controller `clearDataUpdatedSubscribers` clears lifecycle subscriptions on break/assimilation/retirement. | **Justified deviation:** MobVats has no `listenForControllerDataUpdates` registrations. Vat menu uses sampled Boolean/String/ItemStack data and calls `syncFrom(null)` to disable optional whole-part NBT. Mixer uses its existing diagnostics interaction. Do not add an unused second GUI sync channel or subscriber lifecycle. |
| XR2 Fluidizer Output and Reprocessor Access ports | Forward capability handlers bind on assembly and reset on break. Turbine Fluid port refreshes its handler on attach/detach/assimilation/assembly/break and notifies outward neighbors at structural transitions. | **Equivalent / justified:** `PortLifecycle` owns the part's capability source and unload guard. Configuration changes notify the part; resource mutations notify their storage owner. Topology/cache reconstruction does not inherently dirty controller persistence. Physical storage remains distributed rather than XR2's controller tanks. |
| ZeroCore `zerocore/lib/item/inventory/handler/TileEntityItemStackHandler.onContentsChanged` | Calls linked entity `setChanged` before its contents listener. | **Unnecessary divergence corrected:** the ToolPort listener called `setChanged` again. Remove that duplicate; retain client notification and topology rebuild. ItemPort already used the correct listener pattern. |
| XR2 `proxy/CommonProxy/ClientProxy`; ZeroCore `internal/Lib`, `internal/proxy/ClientProxy`, `lib/world/ChunkCache` | Mod/proxy initialization registers process-lifetime listeners once; client registrations belong to client proxy. ChunkCache explicitly removes strong world/chunk caches at unload. | **Equivalent / justified:** MobVats static handlers and constructor registrations are process-lifetime. Controller indexes use weak keys and values without reverse references; no strong world cache needs a duplicate shutdown service. Paused queued structure work still required an explicit execution guard. |

MobVats state flows remain:

1. Persistent controller mutation → dirty helper → reference chunk persistence and
   block update request → delegate NetworkUpdate → client controller/UI state.
2. Persistent part mutation → `setChanged` → required part notification → part
   NetworkUpdate → client presentation. ZeroCore's inventory handler already
   performs the first step for linked inventories.
3. Derived geometry change → equality check → description update only → client
   mesh/render invalidation. Routing, storage wrappers, compiled contexts and
   attached subsets rebuild locally; unchanged rebuilds request no updates.
4. Passive structural edit → weak per-controller queued notification → level-thread
   dispatch → normal ZeroCore disconnection/validation. This is not controller
   persistence dirtiness. Queued pre-pause events must not execute structural work
   after pause.

### Trigger audit and confirmed corrections

| Trigger / owner | Persistence / presentation responsibility | Correction or retained behavior |
|---|---|---|
| Vat `processStateChanged`, product/medium callbacks, activation | Authoritative progress, resources, feed provenance, activation and persisted cursor | Engine formerly notified for the cursor even when unchanged, again for changed cadence, and again at tick end. Compare the next cursor and issue one engine notification at tick end. Preserve changing multi-Head cursor, progress/resource callbacks and exact scheduling order. |
| Mixer `changed`, recipe/resource/activation callbacks | Authoritative recipe/items/escrow/activation | Retain persistent callbacks and successful transaction notifications. Unchanged blocked cadence was already fixed in Slice 2. |
| Mixer `tickDrive`, `stopDrive`, `recheckPhysicalAssembly` | Drive/startup and compiled assembly descriptor are presentation/derived data | Reduce startup requests as above. Changed sweep descriptor and interference invalidation now request description updates rather than authoritative dirty marks. Interference combines drive stop and descriptor invalidation into one reference request. Geometry/validation remain unchanged. |
| Head specimen NBT, life stage, presentation, encode/rotation | Specimen NBT belongs to the Head; stage projection/presentation derive from controller state | `updateSpecimenState` lacked explicit Head persistence marking and notified/revised on identical filtered data. Add `setChanged` on real changes and return for equal filtered NBT. Preserve local notification, stage/projection equality guards, restore encoding and client revalidation. |
| Tool/ItemPort inventory and retained output demand | Inventory and demand configuration belong to the physical part | Remove the ToolPort listener's duplicate mark. ItemPort demand announcements formerly dirtied even when no type was added; return for unchanged demand. Retain storage client updates and component-sensitive demand identity. |
| Conduit geometry adoption and client render invalidation | Geometry snapshot/mesh is derived; equality guards suppress unchanged descriptions | Retain update-only geometry transport and local render invalidation. Clearing on pause remains silent. |
| Conduit queues and Mixer neighbor snapshots | Structure-time invalidation, not resources or processing | Reject paused controllers before notification-time bounds queries and in `conduitChanged` execution. This prevents queued pre-pause work from calling neighbor discovery during unload. Forget Mixer snapshots at pause and donor assimilation; restoration replaces one map entry. Retain snapshots on structural disassembly so correcting an invalid interior can still revalidate. |
| Explicit packets | Mixer compound-name definition payload on login/explicit reload; ZeroCore menus/commands | Retain existing registration and send sites. No recurring custom controller payload dispatch was found. |
| Buffer/service/drive resource mutations; void flare | Physical owner persists resources; rendered void flare requires prompt part update | Retain existing quantities, storage distribution, capability mechanisms and feedback. No Fluid Buffer mutation behavior was changed. Some transactions still cause several callbacks; those require measured workload evidence before further batching. |

### Subscriber inventory and lifetime audit

For this resolved NeoForge/FML loader (`4.0.44`), inspection of
`AutomaticEventSubscriber` bytecode confirms event method types select mod versus
game bus via `IModBusEvent`. The annotation's deprecated default bus value alone
does not establish the effective bus. No class below is also explicitly registered
after automatic subscription. Each explicit registration path is reached once
from `MobVats` construction, not from assembly/restoration.

| Owner / subscriber | Bus and logical side | Lifetime, retained references, unload/duplication analysis |
|---|---|---|
| `MobVats` → `Content.register`, component/product deferred registries, `CompoundFluids.register` | Mod bus, registration/common | Process-lifetime registry handles; no worlds/controllers. One mod-constructor registration. No overlap with ZeroCore structural work. |
| Static `VatPortCapabilities`, `MixerCapabilities`, `CreativeCapabilities`; MixerDefinitionEvents item capability listener | Mod bus `RegisterCapabilitiesEvent`, common | One automatic/explicit registration. Factories receive the queried entity; they do not retain world-specific subscriptions. Dedicated-server registration is required. |
| Static `VatClient`, `MixerClient`, `ProductFluidClient` | Mod/client events, `Dist.CLIENT` | Client renderer/model/screen/fluid setup only. No dedicated-server execution; cached models are client resources, not multiblock controller subscriptions. |
| `PassiveDefinitionEvents.startup/commands`, registered by `MobVats` | NeoForge game bus, server | Definition loading and command registration; no Controller/part/world instances retained. Commands use loaded-position arguments. Reloaded servers publish new definitions. |
| `MixerDefinitionEvents.register` | Mod payload/capability events; NeoForge startup/login/commands, server; payload enqueued to logical client | One constructor path. Static recipes/names and registry provider; no controller listener references. Login/reload sends names only to supported channels. Registry selector caches reset when a registry provider is installed. |
| Static `ConduitStructure.tick`; direct block-change calls into its queues | NeoForge `LevelTickEvent.Pre`, both logical sides | Weak controller set/map, Boolean queue values, weak Mixer geometry keys. Values never retain their keys/worlds. Queues coalesce structural edits per controller and drain on the matching level thread. New pause guards prevent notification-time bounds discovery and post-pause structural dispatch. Normal restoration owns rebuilding. |
| Static `MixerStructureEvents.neighbors` | NeoForge `NeighborNotifyEvent`, server snapshots only | Weak controller keys; values contain immutable positions/block states, no parts/chunks/controllers. One entry per machine, replaced on restore. Changed state queues ZeroCore revalidation through the conduit bridge. Pause/donor assimilation now remove snapshots; broken-but-correctable structures intentionally remain indexed. |
| Block/entity lifecycle methods and `PortLifecycle` | Direct ZeroCore/vanilla callbacks, matching level thread | Per-part capability source and per-part inventory/storage listeners. These are not additional global bus registrations. Parts/controllers clear runtime services through existing pause/remove/assimilation callbacks. Loaded-neighbor guards remain in capability transfers. |
| Specimen/loot/tool interaction path | Direct transactional execution; native NeoForge entity events raised by vanilla execution, server | No production entity-event subscriber was found in MobVats. Entity proxies are operation-owned; existing tests cover transactional delivery/rollback. No persistent specimen-to-global-listener registration to clean up. |
| `ManualTestWorld` started/tick/stopped; `ProductionWorldFixtures.minimumCowXp` | NeoForge server/game events | Development package only, gated by test-world properties. Manual-world static fixture state is reinitialized for generation; stopped handler verifies saved world. Minimum-XP hook also requires its own property. Excluded from production JAR. |
| `CaptureTestEntity`, `TestFluidContainer` | Mod entity/component/attribute/capability registration | Development package only; excluded from production JAR. |
| `gametest/client/{VatClientSmokeTest,MixerClientSmokeTest}` | NeoForge client tick, `Dist.CLIENT`, opt-in properties | Development package only; excluded from production JAR. No client run was launched. |
| `ToolInteractionGameTests` temporary gameplay listener | NeoForge server entity event | Method-local listener unregisters in `finally`; excluded from production JAR. |
| `SyncDiagnostics`, `SyncArchitectureGameTests` | Explicit thread-local diagnostic scopes; no added bus subscriber | Diagnostics are inactive by default; `capture` rejects production. Owner references exist only for a scope and are cleared on close; counters store strings/numbers. GameTest/logging/payload-size sampling lives in the excluded development package. |

No duplicate global registration, strong global controller retention, separate
MobVats chunk-lifecycle subscriber, or client-only dedicated-server execution was
found. Weak indexes do not require a new shutdown cleanup framework. Existing
ZeroCore/vanilla chunk lifecycle remains the authority. The corrected paused
delivery is separate from structural break, which must retain correction detection.

### Measurements and limits

`SyncDiagnostics.capture(owner...)` is an explicit development scope, closed with
try-with-resources. It counts controller dirty calls, explicit reference requests,
render calls, FullSync/NetworkUpdate serialization, part dirty/update calls,
part packet construction, structure/neighbor deliveries, and repeated calls for
the same owner/event/world tick. Dirty calls themselves also request reference
updates in this ZeroCore version. They are not counted as explicit calls to the
separate update helper. Part construction counters cover Vat/Mixer part bases;
vanilla/physical Buffer packet sends are not intercepted.

Measurements below use deterministic synchronous controller/engine invocations,
not wall-clock network-rate sampling. Thousands of simulated invocations occur in
one actual server tick, so `.sameTick` measures repeated requests within that tick,
not steady live-play duplication. Actual recipient packets, compressed wire bytes,
vanilla chunk coalescing and rendered-client smoothness are **not measured**.
Packet construction is explicitly sampled in the port regression; it is not
treated as an actual send. FullSync serialization is not called a full-controller
network event. Payload samples are uncompressed NBT, excluding protocol framing.

| Scenario / metric | Before | After |
|---|---|---|
| Mixer 800-tick spin-up, explicit reference requests | **800 measured**, 799 repeated in the same real tick | **41 measured**, 40 repeated: start, 20-tick corrections, RUNNING transition |
| Mixer unchanged running/blocked, 1,000 invocations after first blocked transition | Not separately measured | 0 dirty, 0 reference requests, 0 render invalidations |
| Mixer idle without drive, 1,000 invocations | Not separately measured | 0 captured notification work; subsequent disable produces 1 dirty call, repeated disable produces none |
| Mixer disabled, 1,000 invocations + unchanged cache rebuild | Not separately measured | 0 dirty/reference/render work |
| Mixer stale derived assembly descriptor rebuilt against unchanged world | Source: authoritative dirty helper invoked | 0 dirty, 1 reference request; descriptor returns to actual compiled geometry |
| Vat deterministic idle single-Head profile, 1,000 engine invocations after initialization | Source: unchanged cursor notified every cadence; no before run of this fixture | 0 dirty/reference/render work |
| Vat disabled, 1,000 invocations + unchanged cache rebuild | Not separately measured | 0 dirty/reference/render work |
| Vat deterministic progress, 20 invocations crossing a cadence | Source: 22 engine dirty calls (20 tick-end + cursor + changed cadence); not a before measurement | **20 measured**; progress/cursor save round trip preserved |
| Head specimen mutation + identical filtered-state repeat | Source: 2 notifications, no explicit local dirty mark; not a before measurement | 1 part dirty, 1 part update, 1 revision; no explicit controller dirty call |
| Tool held inventory change | Source: handler mark + listener mark | 1 part dirty, 1 part update; retained topology refresh |
| ItemPort new demand + repeated same type + insertion | Source: both demand announcements dirty the port | 1 demand dirty plus 1 inventory dirty/update; no explicit controller dirty call |
| Three Mixer restorations, one posted native neighbor event, then pause and repost | No before delivery measurement | Exactly 1 delivery before pause; 0 additional after pause |
| Pre-pause queued structure notification drained after pause | Source: structural neighbor discovery could execute | 0 target structural delivery/reference/dirty calls |

Representative uncompressed NBT samples: idle Mixer NetworkUpdate **3,876 bytes**
versus FullSync **475 bytes** (derived conduit geometry is network-only); Vat
NetworkUpdate **1,116 bytes**, FullSync **1,681 bytes**; nondelegate ItemPort
NetworkUpdate **207 bytes**. These are fixture-specific samples, not maximum
payload sizes or wire traffic. Geometry transport is retained for compatibility;
its cost is a comparative-benchmark target.

Raw scenario counters and uncompressed payload sample sizes are in
`../workspace/.cache/slice3-validation-minimum-xp-complete.log`. The baseline spin-up sample
is in `slice3-baseline-gametest.log`; that exploratory run exposed a new fixture's
first meaningful blocked-status transition and is not a passing validation run.
Source-derived before values are explicitly distinguished from measurements.

### Regression coverage, retained deviations and next work

Nine new `SyncArchitectureGameTests` cover spin-up request cadence and unchanged
running/disabled state; idle activation persistence/description serialization;
Head persistence and no-op suppression; unchanged Vat cursor/cache work; queued
post-pause structural work; native neighbor subscription after repeated restore;
exactly one engine dirty call per changed tick including cadence with process save
round trip; and part resource/demand notification plus packet construction/NBT
sampling. Existing lifecycle, assimilation, unload, restore, growth, medium,
boundary capability and routing tests remain in the full suite.
The Mixer test additionally forces a stale cached floor descriptor and verifies
recompilation requests only presentation synchronization. A headless call to the
client presentation callback verifies the sampled linear ramp, bounded startup
extrapolation, server-owned RUNNING transition and absence of notification work.
It does not replace rendered client verification.

Retain MobVats physical storage/escrow, transactional reservations, multi-Head
cursor persistence, committed progress notifications, Head presentation transport,
sampled Vat UI, derived conduit snapshots, disassembly correction detection and
independent activation preferences. No change to processing rates, recipe choice,
rotor mechanics, geometry, Fluid Buffer behavior, specimen scheduling or P-06
criteria was introduced. The AI-placeholder public-release blocker remains intact.
`verifyDevelopmentHooksExcluded`, attached to `check`, inspects the built JAR to
ensure all GameTest/acceptance/client-smoke classes remain excluded.

Next work is the XR2-versus-MobVats lifecycle performance benchmark harness, not
another broad architecture refactor. Benchmark actual vanilla per-player packet
delivery/coalescing, menu sampling, NBT payload size under large multi-Head Vat
state, active medium/output transaction notification multiplicity, weak-index
scan cost, and late-join/startup interpolation under latency. Further batching or
progress persistence changes require that evidence. This slice does not build the
comparative harness or claim packet-rate parity.

### Slice 3 validation

Completed October 11, 2026. Generated logs/worlds remain ignored; no client,
commit, push, release, publication or deployment was requested or performed.

| Command | Final result |
|---|---|
| `gradlew.bat build` | Passed compilation, resource/model/atlas/hash guards and the new development-hook JAR guard. Existing deprecated mock-player warnings remain. AI-placeholder release guard and manifest were unchanged; no public release validation/bypass was introduced. |
| `gradlew.bat runGameTestServer` | **721 required tests passed** (712 baseline + nine new regressions), including the final changed-descriptor assertion. All dimensions saved and GameTest server shut down normally. |
| `gradlew.bat buildTestWorld -PworldName=MobVatsAcceptance` | Final gameplay-code run passed at **5,000 observation ticks**, ten mirrored fixtures and nine operator Vats. All dimensions saved; completion marker written; Gradle exited successfully. An earlier run received only 240/250 mB initial XP and passed through the preserved native extension at 48,361 ticks; it was not reported as a 5,000-tick pass. |
| `gradlew.bat buildTestWorld -PworldName=MobVatsMinimumXp -PacceptanceMinimumCowXp=true` | Passed at **49,771 observation ticks**. Minimum-XP event logs confirm 1-point rewards before yield. P-06 extension/suspension/resumption and culture/enriched growth checks passed; all dimensions saved, completion marker written and Gradle exited normally. |
| `git diff --check` | Passed. New diagnostic/test files additionally checked for trailing whitespace. |

The final build, full suite and minimum-XP acceptance ran sequentially in one
Gradle invocation (`build runGameTestServer buildTestWorld` with the minimum-XP
properties). Evidence: sibling workspace ignored `.cache/`
`slice3-validation-minimum-xp-complete.log` and `slice3-acceptance-final.log`.
Earlier exploratory/fixture runs remain in the same directory. A new event
fixture's constructor argument was corrected, capture ownership was narrowed to
avoid other fixtures' queued events, and an idle profile was isolated from valid
production timers before the successful complete-suite runs. Client data/callback
tests are headless; visual acceptance and latency-dependent packet delivery remain
outside this validation.
