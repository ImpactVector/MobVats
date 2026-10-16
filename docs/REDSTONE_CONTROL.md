# Redstone execution control

Power inhibits execution; operator preference and persistent ownership are separate.
Signals are read with vanilla `Level.hasNeighborSignal` at the execution boundary.
This includes direct signals and strong power through adjacent solid conductors,
does not depend on component orientation, and derives the current signal after load.
Only six local neighbor checks are needed; signal changes do not rebuild topology.

## Inspection and integration boundaries

- ZeroCore calls `updateServer` for assembled machines regardless of operator activity.
  Neither redstone nor the operator preference should prevent that lifecycle callback.
- Vat `updateServer` separates boundary devices, process execution, and active ports.
  `VatProcessEngine` owns specimen progress and actor transactions. Actors are cached
  in `ProcessContext`; inhibiting a route must not remove its compiled assignment.
- Vat Tool Ports use world-time interaction epochs. These need explicit pause
  accounting; simply omitting process execution would let cooldowns expire.
- Passive Vat Item Ports own staging; Fluid Ports forward to connected storage via
  ZeroCore handlers. Transfer gating belongs on each endpoint, including retained
  handlers, rather than on shared tanks or capability registration.
- Mixer `tickDrive` owns spin-up, running cadence, and recipe progression. Its
  `stopDrive` resets mechanical progress, so redstone must avoid that path.
  Evacuation, pumps, boundary ports, and intake cadence are separate transport work.
- Mixer initially validates at most one cardinally coupled top-layer drive and
  `attachedTo` relies on that unique drive. Multi-drive support is a separate slice.
- Extruder `processCycle` owns both active downward insertion and conversion. Its
  bottom output capability is independent and remains extractable while powered.

## R1 — Extruder

`ExtruderEntity.processCycle` returns before querying or mutating resource handlers
when powered. The existing ten-tick world cadence stays unchanged: no deferred work
is accumulated. Passive bottom extraction remains available.

Two additional headless tests cover direct power, lever-through-stone power,
horizontal orientations, serialized entity replacement while powered, forming state,
retained output, fluid conservation, passive extraction, ejection, and resume.

R1 validation: Java 21 `format`, diff review, `validate` succeeded; all 884 required
GameTests passed (882 existing plus two new).

## R2 — Mixer controller and single-drive inhibition

Controller entities are held in an attached-part role collection. `processingEnabled`
combines the unchanged operator preference with derived controller power. Drive
execution is skipped while the controller or installed drive is powered; spin-up,
running cadence, batch progress, FE credit, and burning fuel are retained. Resource
evacuation, boundary ports, pumps, intakes and metrics continue on the existing loop.
Drive APIs also reject operational use when inhibited. No signal is serialized.

Four new tests cover the controller and all three drive types, direct and indirect
power, exact resume cadence, saved drive state, FE/fuel preservation, operator
preference, and connected fluid input under controller inhibition. Existing chunk
unload still follows ZeroCore's established mechanical stop/reassembly lifecycle;
redstone itself never invokes that reset path.

R2 validation: Java 21 `format`, diff review, `validate` succeeded; all 888 required
GameTests passed. Validation caught and corrected an operator-disabled availability
regression and normalized the creative test's automatically started fixture epoch.

## R3 — Multiple Mixer drives

Multiple cardinally coupled drives may occupy the existing legal positions around
the Impeller. Existing attachment geometry, renderer, drive-local inventory/FE
persistence, and controller assembly persistence need no schema changes. Ownership
checks are independent of selection. Priority is FE, combustion, creative; equal
types sort by x, then y, then z. Powered drives are excluded. A combustion drive
may qualify using a simulated connected-fuel reservation; only the selected drive
commits a refill and pays for its operating tick.

`drive()` retains an unpowered but empty installed drive for compatibility with
existing inspection APIs when no motor can run. Such a fallback never executes
without satisfying its resource requirements. Item ports discover installed fuel
consumers independently, so backup fuel remains transportable while FE operates.

All providers have equal normalized mechanical output. Continuous failover retains
spin-up and running cadence. Redstone pauses retain that epoch even when an enabled
backup is empty. Ordinary fuel/energy exhaustion with no redstone still requires
the established full restart. Batch progress remains retained in either case.

Two new tests exercise three-type priority, direct/indirect failover, all-inhibited
pause, exact recipe resume, single payment, equal-type position ties, starvation
failover, and an empty backup during inhibition. Two old rejection tests now verify
multi-drive formation and deterministic discovery instead.

R3 validation: Java 21 `format`, diff review, `validate` succeeded; all 890 required
GameTests passed, including the unchanged deterministic recipe selection suite.

## R4 — Vat controller and processing actors

Controller power gates only the process engine; committed-output transport, boundary
devices, metrics, and ports retain their existing scheduling. Actor routes are
filtered at execution time using cached entity references. Compiled assignments
remain intact. Tool acquisition, use, Blender production, Feed Port feeding, and
other operations using those routes cannot run through a powered actor. Enabled
actors and specimen processes continue independently.

Tool `interactionStart` and process `interactionAt`/`interactionCooldown:*` epochs
are shifted by suspended world ticks. The last maintenance time is saved separately
from specimen processing state, with no persisted power flag. Powered reloads freeze
the interval since that saved epoch; history of signal changes while unloaded is
not reconstructed. Shared per-specimen interaction cooldowns pause when all assigned
actors for that use are inhibited. Each Tool Port's own cooldown pauses separately.
Elapsed progress, held tools, pending results, configuration, and assignments are
preserved. Ordinary unpowered cooldown behavior is unchanged.

Ten new tests cover direct and indirect controller/Tool/Blender/Feed signals,
biological pause/resume, enabled item transport, assignment preservation, independent
biology, controller serialization, and Tool/process cooldowns across powered reload.

R4 validation: Java 21 `format`, diff review, `validate` succeeded; all 900 required
GameTests passed, including the existing lifecycle and processing-state checks.

## R5 — Vat resource endpoints and final lifecycle coverage

Item and Fluid Port transfer calls check the endpoint's current signal, including
simulation, retained passive handlers, active boundary work, native routing, and
internal item transactions. The Fluid Port gates its ZeroCore forwarding target,
so retaining an old pipe handler cannot bypass power. Shared tanks and port
configuration remain intact; enabled endpoints can still use connected storage.

Pull/Push Pumps and Fill/Drain Valves skip their operational entry points when
powered. Current valves expose no passive fluid capability. Sumps suspend service
evacuation and collection, including direct production allocation paths. Completed
production stays pending. Individually powered Tool/Blender service outputs remain
held. Item and Fluid Voids reject disposal while powered. Controller inhibition
alone does not apply these endpoint gates.

The shared physical-storage helper adds only an optional per-service evacuation
predicate. Its existing no-argument path, used by the Mixer, is unchanged. Buffer
and conduit operation is unaffected. No topology, inventory, filter, or storage
model is replaced.

Twelve new tests cover direct/indirect pump and valve signals, item/fluid voids,
retained item/fluid capabilities, both transfer directions, simulation, powered
endpoint serialization, shared access through another port, active transport
under controller inhibition, Sump collection/ejection, internal item supply, and
the actual powered Mixer ZeroCore unload/reassembly path. The latter confirms
batch/FE/preference retention and the existing rotor restart on chunk restoration.

R5 validation: Java 21 `format`, diff review, `validate` succeeded; all 912 required
GameTests passed. The initial Sump fixture was corrected to respect the existing
1,000 mB service-tank capacity; no storage capacity or processing model was changed.

## Changed-file inventory

All implementation paths below are under `src/main/java/net/impactvector/mobvats/`.
This document is updated in every slice.

| Slice | Changed Java files |
| --- | --- |
| R1 | `gamecontent/extruder/ExtruderEntity.java`; `gametest/ExtruderGameTests.java` |
| R2 | `gamecontent/multiblock/common/RedstoneControl.java`; `mixer/MultiblockMixer.java`, `mixer/part/AbstractMixerDriveEntity.java` under `gamecontent/multiblock/`; `gametest/MixerRedstoneGameTests.java` |
| R3 | `mixer/MultiblockMixer.java`, `mixer/part/AbstractMixerDriveEntity.java`, `mixer/part/MixerCombustionDriveEntity.java` under `gamecontent/multiblock/`; `gametest/MixerDriveGameTests.java`, `gametest/MixerFeDriveGameTests.java`, `gametest/MixerRedstoneGameTests.java` |
| R4 | `vat/MultiblockVat.java`, `vat/part/AbstractVatEntity.java`, `vat/part/VatToolPortEntity.java`, `vat/process/ProcessContext.java`, `vat/process/SpecimenProcessState.java`, `vat/process/VatProcessEngine.java` under `gamecontent/multiblock/`; `gametest/CowProcessGameTests.java` |
| R5 | `conduit/PhysicalFluidStorage.java`; `vat/BoundaryDevices.java`, `vat/MultiblockVat.java`; `vat/part/VatBlenderEntity.java`, `VatBoundaryEntity.java`, `VatFluidPortEntity.java`, `VatItemPortEntity.java`, `VatSumpEntity.java`, `VatToolPortEntity.java`; `vat/process/ItemResultBuffer.java`, `PassiveOperations.java`, `PassiveProcessCompiler.java`, `ProcessContext.java`, `ResourceProcessRuntime.java`, `VatItemTransaction.java`, `VatProcessEngine.java`, all under `gamecontent/multiblock/`; `gametest/BoundaryDeviceGameTests.java`, `CowProcessGameTests.java`, `MixerRedstoneGameTests.java`, `VatRedstoneEndpointGameTests.java` |

The separate mechanical formatting commit `464f370` changes only
`gametest/ExtruderWorldFixtures.java`, which was unformatted at the starting revision.

## Validation and acceptance limits

Every slice uses the canonical Java 21 launcher: `format`, formatting-diff review,
then `validate` (`spotlessCheck build runGameTestServer`). Builds include the existing
CPU/render/resource checks. Required GameTest totals increase from the initial 882
to 884 (R1), 888 (R2), 890 (R3), 900 (R4), and 912 (R5).

Only autonomous headless testing is performed. Client visual acceptance remains
with the user. ZeroCore's pre-existing unload/reassembly rotor restart is retained;
redstone changes alone preserve rotor progress. Extruder opportunities retain the
existing ten-tick world phase rather than accumulating deferred work. Unloaded
signal history is not reconstructed; the current world signal is authoritative.
No production publication, client launch, recipe-ranking change, Blocker/Inscriber
refactor, or physical rotor refactor is part of this work.
