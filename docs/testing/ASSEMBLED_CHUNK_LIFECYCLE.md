# Assembled multiblock chunk lifecycle follow-up — 2026-10-24

Baseline: `60b7c64988b414d8f7847e5c3ef063ee9451f9ec`.
This supersedes the assembled-retention finding in
[the October 23 logistics baseline](INTER_MACHINE_LOGISTICS_BASELINE.md).
No client test, dependency patch, publication, or mod release is part of this work.

## Reproduction and actual XR2 control

The experiment loads the **actual published XR2 JAR**, not a controller imitation:

- Minecraft 1.21.1, NeoForge 21.1.252, Zero CORE 2 1.21.1-2.4.18.
- Extreme Reactors 1.21.1-2.4.18, CurseForge file 5970602;
  Gradle coordinate `curse.maven:extreme-reactors-250277:5970602`.
- XR2 JAR SHA-256: `6dcbc067041acf8d00abf1974fe79796546111fda73bd427e697e7a525b83d2e`.
- Eclipse Adoptium Java 21.0.12.1+1-LTS, canonical Windows launcher.
- Genuine 3×3×3 Basic reactor: casing, one fuel rod, control rod and controller;
  empty, inactive, passive cooling. Both normal Automatic and Manual waste-ejection
  configurations are tested. Manual is selected through XR2's public configuration
  setter, never a controller ID, assembly flag or ownership mutation.
- Each fixture occupies a distant chunk, 32 chunks from the next control, outside
  GameTest arena tickets. Each receives the same level-31 forced ticket, removed
  at fixture tick 100. Observations inspect holders and DistanceManager's real ticket
  sets without fetching world cells. The observation continues to tick 24,100:
  24,000 ticks after release, equivalent to 20 minutes at normal game speed.
  GameTest advances ticks faster than real time; this is a tick-duration experiment.

**Before any production change**, the controls produced:

| Intact fixture | After ticket release | At +24,000 ticks | Native reload |
| --- | --- | --- | --- |
| XR2, Automatic waste ejection | 31 → 33 | 33, `unknown` timestamp still renewed | Blocked by retained chunk; no unsafe eviction attempted |
| MobVats Vat, enabled | 31 → 33 | 33, `unknown` timestamp still renewed | Blocked by retained chunk |
| MobVats Mixer, enabled | 31 → 33 | 33, `unknown` timestamp still renewed | Blocked by retained chunk |
| Otherwise equivalent unassembled Vat shell | No FULL holder by +20 ticks | Absent, no tickets | Control only |
| XR2, Manual waste ejection | No holder by +20 ticks | Absent, no tickets | New chunk/controller; intact reformation and Manual setting persisted |

Thus a long unload delay does **not** explain the retained fixtures. Their expiring
`unknown` tickets are repeatedly refreshed. XR2 is configuration-dependent; the
Automatic fixture is not a validated unloading reference. The Manual fixture is.
The experiment does not generalize these results to every XR2 mode, version or modpack.

## Concrete retention mechanism

Pinned dependency bytecode and the matching Minecraft/NeoForge sources establish:

1. ZeroCore `MultiblockWorldRegistry.tickStart` calls `updateMultiblockEntity` for
   nonempty controllers at **level** scope. The final controller driver checks
   assembly, but does not require block-ticking chunks.
2. Minecraft `ServerChunkCache.getChunkFutureMainThread(..., create=true)` adds or
   refreshes a `TicketType.UNKNOWN` ticket at the requested status. For FULL, that
   is level 33. A cache miss can do this even for an already-loaded chunk.
3. A loaded guard followed by `Level.getBlockEntity` or `Level.getBlockState` is
   insufficient to avoid renewal: those lookups fetch a chunk with creation allowed.
4. In the Vat, the old `updateServer → storage.evacuate → enabled predicate →
   Level.getBlockEntity` ran even for empty staging. Vat controller/actor redstone
   polling also traversed vanilla world reads.
5. The Mixer independently polls `processingEnabled → controllerInhibited →
   RedstoneControl.inhibited → Level.hasNeighborSignal` while enabled, even without
   a motor or running recipe. Vanilla signal queries reach `Level.getBlockState`.
   Thus an idle enabled Mixer has a concrete renewal path unrelated to Vat storage.
   Mixer intake fluid reads and live storage identity checks were additional world
   access sites requiring nonloading access.
6. When `updateServer` returns true, ZeroCore's final driver marks the machine's
   chunks dirty with `Level.getChunk(...).setUnsaved(true)`. This is another renewal
   path; fixing just the Vat predicate cannot solve it for operating machines.
7. XR2's actual `performRefuelingCycle` returns true whenever Automatic waste
   ejection is selected, including an empty idle reactor. `ReactorLogic.update`
   propagates that result and `MultiblockReactor.updateServer` returns it to the
   same ZeroCore dirty-chunk loop. The Manual configuration removes this repeated
   result in the idle control and demonstrably permits native unload/reload.

This is a positive feedback loop: controller ticks continue in non-ticking FULL
chunks, world/dirty reads refresh FULL tickets, and unload callbacks never get an
opportunity to retire the controller. It is not a durable MobVats force-load ticket,
shared controller ownership, a standalone Buffer joining both machines, or simply
the presence of a dangerous read. The dangerous reads and the ticket renewal were
investigated separately. No assertion attributes all possible XR2 retention to
MobVats or claims that every ZeroCore controller necessarily retains chunks.

The historical forced level-33 eviction crash is not reproduced or retained.
`ServerChunkEviction.request` still requires level 34 and zero generation references.
Its scheduler behavior and safeguards are unchanged; added diagnostics only read
holder/ticket information.

## Correction and compatibility

`ConduitCuboidController.canTickMachine` requires Minecraft's own
`ServerChunkCache.isPositionTicking` for **every chunk in the last validated
structural envelope** before Vat or Mixer server operations run. It performs no
loading lookup, does not inspect ticket types, and does not mutate ZeroCore state.
At the non-ticking FULL fringe, processing suspends and returns false, preventing
both machine reads and ZeroCore's dirty-chunk fetch. Normal native unload callbacks
then pause/detach the real parts, and native load/discovery restores the machine.
A partially available cross-chunk machine suspends operations until all its chunks
are ticking; legal cross-chunk formation remains supported.

This is an intentional MobVats lifetime policy, closer to normal Minecraft block
entity ticking than XR2 Automatic's observed behavior. MobVats does not inherit
XR2's automatic-ejection retention merely because it uses the same library. Its
intact unload/reload result matches the experimentally validated XR2 Manual control.
No ZeroCore/XR2 class, registry membership, controller identifier, or assembly flag
is patched. No chunk eviction trigger is used by the new intact lifecycle tests.

`LoadedWorld` supplies nonloading chunk/block-entity/block-state access. The audit
updates Vat evacuation, process endpoint resolution, Mixer intakes, active fluid
port neighbor identity reads, Buffer capability/cluster identity checks and deferred
Buffer presentation. Redstone uses vanilla `SignalGetter` rules over nonloading
reads, including conductor strong power. An incomplete two-block neighborhood
inhibits the affected device instead of loading it or assuming permission to consume.
Empty staging is checked before evaluating endpoint predicates.

World pumps use nonloading source/search reads. Pulling suspends if the source's
water-regeneration neighborhood is unavailable, before changing world fluid or
spending destination capacity. Placement also suspends before a world write if its
notification neighborhood is unavailable: vanilla neighbor notifications themselves
use loading world reads. An enclosed border-cell pump regression verifies unchanged
world state, all 1,000 mB retained, and no renewed FULL neighbor ticket. Existing active boundary transfer guards and capability
simulation/commit rules remain in place. `PortLifecycle.isItemConnected` now checks
loaded neighbor availability before capability discovery. Existing structure discovery
and lifecycle callbacks retain their loaded guards, cached-envelope notifications,
normal ownership reconciliation and no-fetch unload behavior.

## Acceptance coverage

`AssembledLifecycleGameTests` retains the actual XR2 Automatic characterization,
Manual native reload control, unassembled control, and enabled intact Vat/Mixer
native reload controls. It records early holder/ticket transitions and the extended
24,000-tick result. The Vat/Mixer controls have no redstone inhibition or shell edits.

`IntactMachineReloadGameTests` adds nine exact-state paired native reloads:

- Vat/Vat, Mixer/Mixer, Vat/Mixer, with a one-block gap and standalone Buffer.
- Entire pair in one chunk (x offset 1).
- Each machine entirely in a separate chunk, Buffer in the gap (offset 11).
- The first machine's legal shell spans a chunk boundary (offset 14).

Each releases every test-controlled ticket, waits for **complete native eviction**
(including updating/pending unload maps and the original chunk's loaded flag), then
loads normally. Every involved chunk and controller must be a new instance. Exact
bounds/part ownership and the unowned standalone Buffer must survive intact reformation.
Neither the eviction scheduler helper nor shell break/repair is used in these tests.

The state fixtures keep operator enablement true and use ordinary local controller
redstone to freeze an exact checkpoint around disk persistence. Mixers start an
actual recipe through their real drive/controller update path and must have nonzero
progress before the checkpoint; recipe, input/output/escrow state and progress are
compared after reload. Vats have a baby cow, specimen UUID/state/geometry and explicit
fixture growth progress 137, named staged items, a held tool and named medium with
clean/dirty amounts 1243/257. The direct initial growth assignment is fixture seeding,
not an ownership or assembly mutation. Core enabled, uninhibited controls separately
prove that this redstone checkpoint is not needed to unload either family.

The snapshot compares every physical Buffer's filter and exact FluidStack, including
component-bearing compound culture; machine input/output fluids; items and components;
port direction/mode/filter; drive block configuration; specimen identity/geometry;
process data; enabled preference; and both culture fractions. Metrics that count
assembled observation time are excluded from exact process equality. Old standalone
Buffer and machine-port capabilities must reject fill/drain after unload and again
after chunk replacement.

The unavailable-boundary regression holds only an intentional test FULL ticket on
the formed source. Its neighbor becomes unavailable as a FULL chunk; the resulting
level-34 dependency holder is legitimate Minecraft behavior, not evidence of retained
FULL loading. Repeated transfer attempts and nonloading endpoint reads must move
nothing, conserve the source fluid and never reacquire FULL availability. This test
is distinct from the complete-eviction tests above.

All twenty previous **disassembled** native reload/repair regressions are retained.
Their obsolete assembled-retention assertion is removed; the test ticket is held
until actual disassembly, then the original guarded level-34 scheduler path runs.
Existing logistics, cladding, hostile Head, persistence and transfer tests remain.

## Validation receipt

Focused autonomous Java 21 run before the final capability assertions:
**15/15 required tests passed**, including nine exact-state intact paired reloads,
the unavailable-boundary case and all five actual-dependency controls.

Final receipt, 2026-10-24:

- `tools/gradle-java21.ps1 -Mode format`: PASS; formatting diff inspected.
- `tools/gradle-java21.ps1 -Mode validate`: **BUILD SUCCESSFUL**, final revision,
  **all 1,166 required GameTests passed** (1,151 baseline + 15 new lifecycle/control tests).
- Final server receipt: `09:26:31.184`, October 24; final canonical run completed
  with exit code 0. The earlier full run also passed before the final pump guard.
- Build, CPU geometry/cache/render ordering, procedural-art and packaged-resource
  checks passed. Test hooks and the extra lifecycle structure are excluded from the JAR.
- Exact-state paired reloads and stale Buffer/machine-port capabilities: PASS.
- Enclosed border-cell pump operation: suspended, no fluid spent, world cell unchanged,
  neighbor FULL chunk not reacquired: PASS.
- Final controls sampled Vat/Mixer at release +1 tick: level 33, zero generation
  references; +2: native removal level 45, no tickets; +3: completely absent.
  No manual scheduler call intervened. XR2 Manual also unloads and reloads intact;
  XR2 Automatic remains level 33 with renewed `unknown` tickets through +24,000 ticks.
- No client run, production publication or mod release performed.

Local detailed receipts are retained under `.cache/`: `lifecycle-long-control.log`,
`lifecycle-xr2-native-control.log`, `lifecycle-focused-pass.log`,
`lifecycle-full-first-pass.log` and `lifecycle-final-validation.log`.
These are development diagnostics, not shipped mod resources.
