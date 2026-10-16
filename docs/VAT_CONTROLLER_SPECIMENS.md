# Vat Controller specimen interface — Slice 1

The Controller opens a specimen list/detail view. Scroll over the left pane to
navigate Heads and over the right pane to inspect biology, production and actors.
Diagnostics switches to the original four pages; Next page cycles them. Enable /
Disable uses the original operator command. Redstone inhibition is reported
separately. No inventories, assignments or biological values can be edited here.

## Projection and ownership

`VatSpecimenSnapshot` reads connected Heads in `VatTopologyCompiler.POSITION`
order, including unformed/invalid Heads. UUID, captured custom name, species and
stage come from the Head. Runtime stage/feed source and compiled timer states come
from the engine, guarded by its existing specimen UUID ownership. Assignments come
from topology, with Harness/Sump membership from existing assemblies. Tool item
identity/name, redstone state and mechanical cooldown come from actor entities.
The client receives records, strings and numbers, never entities or process state.

Happiness is the normalized existing happiness/yield metric (the inverse of the
existing 0.5 + 1.5 * credited-clearance fraction mapping), not a new efficiency
score. Yield and culture frequency call existing controller calculations. The
pre-exposure culture multiplier calls existing medium frequency. Timers use the
compiled definition's actual duration and rate and last evaluated reason. Reads do
not execute requirements, compile topology or create missing runtime timers.
Legacy diagnostics and the cow compatibility snapshot now also avoid installing
missing timers as a side effect of reading them.

## Menu synchronization

A menu-only server-to-client payload carries a detached JSON view. The server
samples after 20 menu ticks, compares the encoded value, and sends only changed
views. Initial data is sampled immediately. Long views are split at Unicode-safe
boundaries into at most 16,000 UTF-16 units per packet (under 48 KB of UTF-8 data),
with at most one packet per menu tick. There is no specimen-count cap. A transfer
finishes before the next sample, so large lists apply backpressure rather than
accumulating generations. The client atomically installs a complete generation,
rejects stale/duplicate/out-of-order chunks, and retains selection by UUID. If the
selected UUID disappears, it selects the first remaining row, or clears selection.
No whole-controller/part NBT channel is enabled. Operator activation retains the
existing small ZeroCore boolean sample and command path.

All four diagnostic pages travel through this same chunking path, removing the
menu's previous per-page 32,767-character truncation. Existing diagnostic-summary
limits inside the diagnostic generator itself remain unchanged.

## Deliberate limits and acceptance

Forecasts are sampled estimates; they do not animate or count down between server
updates. Only a timer whose last evaluation is running, whose rate is positive,
and whose machine/specimen is operational receives an ETA. Waiting, blocked,
unformed and paused states have no forecast. A configured operation without a
compiled progress timer has status but no fabricated duration/ETA. Actor cooldown
is the existing mechanical interaction cooldown; executor-specific interaction
cooldown metadata is not projected in this slice. Tool icons show the base item;
the actor's actual hover name is retained, but item components/glint are not sent.
Feeding availability reports physically reachable item feed or Feeding Slurry;
it does not promise that every other prerequisite for harvesting is satisfied.

Live 3D preview and in-world rendering changes are excluded. Manual client visual
acceptance remains required: normal GUI scales, long/custom specimen names,
independent scrolling with dozens of Heads, tool icons, progress bars, diagnostics,
operator activation and redstone/paused labels. No client smoke task or manual
input automation is used for this slice. No publication is performed.

## Changed files

| File (relative to repository) | Purpose |
| --- | --- |
| `src/main/java/net/impactvector/mobvats/MobVats.java` | Register the menu payload. |
| `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/MultiblockVat.java` | Enumerate connected Heads for projection. |
| `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/VatSpecimenSnapshot.java` | Immutable specimen, process and actor records. |
| `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/VatStatusSnapshot.java` | Non-mutating legacy diagnostic reads. |
| `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/menu/VatControllerMenu.java` | Sampling, change detection, transfer and UUID selection. |
| `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/menu/VatSpecimenNetworking.java` | Chunk codec and atomic receiver. |
| `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/client/screen/VatControllerScreen.java` | List/detail presentation and secondary diagnostics. |
| `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/process/VatProcessEngine.java` | Read existing timers, evaluated statuses and enabled feeding endpoints. |
| `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/process/SpecimenProcessState.java` | Non-installing timer lookup. |
| `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/process/CowProcesses.java` | Non-mutating compatibility snapshot. |
| `src/main/java/net/impactvector/mobvats/gametest/VatSpecimenMenuGameTests.java` | Eight focused headless tests, including a 512-entry transfer. |
| `docs/VAT_CONTROLLER_SPECIMENS.md` | Architecture, limitations and acceptance notes. |
