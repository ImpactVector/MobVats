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
