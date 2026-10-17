# Vat Controller specimen interface — Slices 1 and 2A

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

In-world rendering changes are excluded. Manual client visual
acceptance remains required: normal GUI scales, long/custom specimen names,
independent scrolling with dozens of Heads, tool icons, progress bars, diagnostics,
operator activation and redstone/paused labels. No client smoke task or manual
input automation is used for this slice. No publication is performed.

## Slice 2A: mob-only observation preview

The right pane starts with an 88 GUI-pixel containment window that scrolls with
the detail content. Existing detail sections retain their full width and scroll
range. Harnesses, Sumps, Tool Ports, Blenders and topology are not rendered here.

The detached snapshot adds `appearance`, an SNBT string containing only the fixed
`CapturedSpecimen.appearance` allowlist. This includes captured variants, colors,
shearing, size, villager presentation and custom name; adapter-owned interaction
fields are excluded. It does not carry controller NBT, live block entities, AI,
equipment or process state. The existing JSON/chunk transport and UUID selection
rules are unchanged.

`VatSpecimenPreview` is a screen-owned single-entry cache with no client imports,
so its construction and lifecycle can be exercised in headless GameTests. Only
the client screen uses it in production. The key includes specimen UUID, species,
authoritative adult flag and appearance string, plus level identity. An unchanged
key preserves the entity across snapshot refreshes and reforming. A changed key
rebuilds it. Missing selection/world clears it, including while Diagnostics is
open; closing the screen releases both entity and level. Reopening starts fresh.
If the selected UUID disappears, the existing menu chooses the first remaining
row, or clears selection when empty.

Construction uses the registered entity type's `create`, applies filtered captured
appearance, then overrides baby state with the synchronized runtime life stage.
The detached Mob is never spawned, added to a level, or ticked. AI, gravity and
sound are disabled. Body and head share fixed yaw 155 degrees with zero pitch;
previous-frame angles match. This gives a fixed shallow three-quarter observation
view. No mouse coordinates, rotation controls or simulation clock affect it.

`VatSpecimenPreviewRenderer` uses the normal entity dispatcher with inventory
lighting, full brightness, no shadow or debug hitboxes, and a private pose stack.
It restores camera orientation, lighting and hitbox state in `finally` and restores
the normal GUI shadow setting. Nested scissors contain the model inside both its
window and the scroll pane. Scale fits bounding-box height and 1.4 times width,
with margins and a 48-pixel-per-block cap to avoid enlarging small juveniles to
adult size. There are no species overrides.

Unregistered/non-Mob types, malformed appearance and construction/render exceptions
produce a controlled placeholder. Failed keys are cached to avoid retrying or
logging every frame; a changed key or reopening allows recovery. Debug-level logs
retain failure information. Existing Diagnostics remains accessible and unchanged.

### Limitations and manual visual acceptance

Headless tests cover construction and state, not GPU output. Client visual
acceptance remains pending and is performed by the user. Unusually long models,
horns, wings or modded renderers extending beyond their bounding box may clip.
Renderers requiring simulation ticks may present a static/default pose or fall
back. Appearance fidelity is limited to the existing capture allowlist; appearance
that was never captured cannot be reconstructed. No per-species framing framework
or in-world renderer changes were added.

Check in the client:

- Open with cow, pig, sheep (including colored/sheared variants), chicken and
  rabbit specimens; verify species, captured appearance and initial selection.
- Switch rows repeatedly; move the pointer throughout the window. Body yaw stays
  fixed and the head faces with the body without cursor tracking.
- Compare juvenile/adult forms and maturity refreshes; juveniles retain their
  proportions and captured baby flags do not override runtime maturation.
- Inspect horse, camel, iron golem and other large supported mobs for useful scale
  and acceptable framing; record any species needing later small adjustments.
- Test normal GUI scales, long names/content and independent pane scrolling.
  Preview, text, bars and icons must not overlap; every detail section is reachable.
- Remove the selected specimen, remove all specimens, break/reform the multiblock,
  close/reopen the GUI and check refreshes. Selection follows existing UUID rules;
  no stale preview remains when selection is empty.
- Verify placeholders for unavailable identities and confirm all four Diagnostics
  pages, scrolling and Enable/Disable remain usable.

Three new GameTests cover age/appearance/detached construction, cache refresh and
failure recovery, and missing/invalid/filtered data. The existing menu refresh /
replacement / diagnostics test additionally checks actual preview entity reuse,
replacement identity and clearing. No client was launched or publication performed.

Validation on October 16, 2026: the canonical Java 21 launcher `format` and
`validate` modes passed (`spotlessApply`, then `spotlessCheck build runGameTestServer`).
All 925 required GameTests passed, including the three added in Slice 2A; existing
CPU lifecycle/buffer/geometry and packaged-resource checks also passed.

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
