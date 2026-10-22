# Mixer Controller monitoring GUI

Normal controller right-click opens a server-owned ZeroCore menu. Crouch-click
deposits XP without opening the menu. Other part interactions retain their existing
paths. Unformed and disconnected controllers can open a read-only status view.

The industrial screen contains current/next production, a cadence-aware progress
bar and ETA, one last-completion card, a vertically scrolling resource list, drive
and operating status, and a secondary scrolling Diagnostics view. Product strips
scroll horizontally to retain every item/fluid output. Hover reveals full names,
locations, availability, capacity where meaningful, and exact components. Shift +
scroll pages long component tooltips. No inventory slots or recipe controls exist.

## Snapshot and synchronization

`MixerControllerSnapshot` contains formed/enabled/inhibited/advancing flags,
condition, selected drive and power/fuel availability, current batch identity/name,
active-versus-next classification, progress/duration/remaining ticks, expected
products, the last successful batch and actual products, location/purpose-labeled
resource entries, and the existing diagnostic report.

The snapshot reads live domain objects. It neither parses diagnostics nor reads
controller save NBT. Per-product stack codec NBT is detached icon/component data.
The existing recipe planner supplies expected products and eligibility. A read-only
planner mode resolves products even when output capacity is blocked; its input
allocation, composition calculation and strategy comparison are shared with
execution. Presentation queries restore transient planner/status fields.

Menus sample once per 20 server ticks and send changed snapshots only. JSON is
split into at most 16,000 UTF-16 character chunks without splitting surrogate pairs,
with one chunk sent per server tick. Related values replace the client view only
after a complete numbered generation arrives. Menu IDs isolate receivers. There
is no entry-count cutoff; a large in-flight generation finishes before resampling.
The whole-controller NBT menu channel is disabled, matching the Vat observer.

ETA uses recipe duration, the existing ten-tick cadence and current cadence phase.
All drive providers use that cadence. Operator pause, redstone inhibition, lack of
drive power, spin-up, missing inputs and blocked outputs suppress the ETA.

## Resources and completion ownership

Internal staged items, legacy fluid escrow, pending item outputs, controller service
fluids, and combustion-drive fuel/remainders retain separate purposes. Connected
fluid resources use exactly the physical storage membership used by the Mixer
planner. Cluster tanks are counted once; service tanks use identity deduplication.
Connected items use ResourceRouting's supply discovery for this controller and
its coupled combustion drives, including endpoint availability, roles and filters.
Handler/slot identities are deduplicated across routes, roles and consumers.
Unavailable routed items remain labeled as stored/filter-excluded. Connected items
are not claimed as committed recipe inputs: recipe items still require internal
staging. No world-wide capability scan or unrelated storage is included.

Stacks are never merged by item/fluid ID. Exact quantities, components and compound
compositions remain distinct. Capacity belongs to an individual reported physical
tank; no aggregate fill percentage is invented. Selected-drive status is separate
from standby-drive fuel inventory.

`MixerState.Completion` stores only one successful commit's recipe ID, detached item
stacks and resolved fluid stacks. It updates after the resource transaction succeeds,
survives subsequent batches and saves under `lastCompletion` in existing Mixer state.
Stack codecs preserve components and quantities. On assimilation the receiving
controller retains its completion, adopting the donor's only when absent; donor
clear retires its record. Ordinary disassembly/reformation retains state through
the existing controller ownership path.

## Validation and acceptance

Final canonical Java 21 `format` and `validate` runs succeeded. Formatting checks,
build, CPU/resource checks and all **947 required GameTests passed** (929 existing
plus 18 new). `git diff --check` also passed. The first run was stopped after a
new fixture placed its fuel port on a forbidden frame edge; the corrected top-face
fixture passed in the complete rerun. No production publication or client launch
was performed.

Eighteen new headless GameTests cover structured batch products, planner purity,
cadence/paused ETA, completion success/failure/reload/ownership and component copies,
live physical storage, drive power/spin-up/redstone, disconnected menu validity,
item aliases/filtering/disconnection/components, fluid compositions/service aliases,
connected motor fuel, and 5,000-entry Unicode snapshot codec/atomic chunk transfer.
Existing diagnostic interaction tests now verify controller menu opening, absence
of diagnostic chat, XP-only crouch interaction and normal-click XP preservation,
while retaining non-controller report and break/reformation assertions. Existing
Mixer routing, recipe selection, Meter, Blocker, drive and inhibition suites remain.

No client was launched. Manual visual acceptance must verify GUI scales and long
names; item/fluid icons; current/next distinction and progress/ETA updates; successful
completion products; resource and multi-product scrolling; component tooltip paging;
clear drive/blocking conditions; Diagnostics navigation; and closing/reopening.

Intentionally absent: aggregate capacity percentages, efficiency metrics, prediction
of future power/fuel supply, and a countdown while not advancing. When matching
inputs no longer exist, dynamic transformed identity/composition is explicitly
unresolved rather than guessed. There are no history, configuration or editing
controls. This is a local development change; nothing was published.

## Changed files

Paths below are relative to the source repository:

```text
src/main/java/net/impactvector/mobvats/gamecontent/Content.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/PhysicalFluidStorage.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/conduit/ResourceRouting.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/MultiblockMixer.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/MixerControllerSnapshot.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/client/MixerClient.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/client/screen/MixerControllerScreen.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/menu/MixerControllerMenu.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/menu/MixerControllerNetworking.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/part/MixerControllerEntity.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/part/MixerPartBlock.java
src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/process/MixerState.java
src/main/java/net/impactvector/mobvats/gametest/MixerControllerGameTests.java
src/main/java/net/impactvector/mobvats/gametest/MixerDiagnosticGameTests.java
docs/history/2026-10-22-mixer-controller-gui.md
```
