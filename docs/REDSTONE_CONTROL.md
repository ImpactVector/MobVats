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
