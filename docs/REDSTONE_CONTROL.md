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
