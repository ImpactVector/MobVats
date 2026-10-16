# Mixer manual meters (Slice 2B)

Item Meters and Fluid Meters are optional Mixer shell components. Place them on a
face of the vessel (including its roof or floor), away from frame edges. They do
not expose item/fluid capabilities or add storage. Ports, conduits and buffers
continue to transport and store resources. The existing single-impeller geometry
is unchanged.

## Configuration

- Shift-right-click an Item Meter with an item sample, or a Fluid Meter with a
  filled fluid container. The sample is not consumed. This uses the existing port
  sampling convention: item identity is the item ID; fluid identity includes
  sampled components/composition. An incompatible fluid sample leaves the old
  filter unchanged and reports why.
- Shift-right-click with an empty hand to clear the filter.
- Right-click normally to open the quantity menu. Enter a positive decimal integer
  and click Apply. Item quantities count items; fluid quantities are mB. Values
  range from 1 through 2147483647. Zero, signs, whitespace, fractions, malformed
  text and overflow are rejected on the server without changing the stored value.
  The menu shows the effective stored quantity and assigned filter; hover a long
  filter to inspect its full identity.
- New Item Meters default to quantity 1, Fluid Meters to 1000 mB. Neither has a
  filter initially, so both block the next batch until configured.
- Filters and quantities can be edited while formed. The next batch sees the
  edits. A batch that already started finishes under its existing recipe/resource
  behavior, even if a meter is subsequently cleared. Ordinary resource/output
  availability and registry reload checks still apply.

Craft an Item Meter from a Mixer Casing, comparator and hopper; use a bucket
instead of the hopper for a Fluid Meter. Both are also in the MobVats creative tab.
Their models reuse vanilla dispenser/dropper textures; no new generated art is
introduced.

## Eligibility and diagnostics

Every installed meter must resolve to a valid filter and positive quantity.
Missing, corrupt, incompatible, tag-based or unresolved filters block processing;
invalid saved quantities also block processing. The Mixer can still form. Its
existing controller diagnostic report identifies the offending meter and position
under `Next batch blocked`. Fix or remove that meter to resume subsequent batches.

Meters combine using AND. There is no separate meter-count limit. Each meter
must match one entire recipe ingredient slot with exactly the configured consumed
quantity. Multiple meters require distinct ingredient slots; they cannot split a
single ingredient or count it twice. Extra unmetered ingredients are permitted,
and surplus stored resources do not change the required recipe quantity.

Slice 2A's allocation limitation is preserved: checks inspect the planner's chosen
allocation. They do not search for another allocation. For example, a broad fluid
ingredient may be allocated to a different composition than the meter sampled,
even if a matching composition is also stored. Such a recipe can remain
ineligible. Item identity retains the existing sample convention (item ID rather
than arbitrary item components). Recipe complexity, normalized consumption and ID
ranking, staging, reservations and consumption are otherwise unchanged.

## Persistence and implementation

Configuration belongs to each meter entity, serialized through ZeroCore's existing
sync contract. Save/reload and chunk entity reconstruction use the same data;
client update tags carry it. Disassembly/reformation retains independent meter
state. Pick/drop/place also carries the configuration via the existing endpoint
item-state mechanism. Invalid saved configuration remains visible and blocked;
it is not silently converted to defaults.

The controller derives `MixerMeterRequirements` from its connected meter parts
at planning time. Live configuration remains solely on the parts. Because the
executor replans
running batches, it persists the accepted batch's immutable meter constraints as
execution state until completion; this preserves the original strategy checks
across edits, disassembly and reload without making that snapshot editable.
`MixerState` applies the existing Slice 2A matcher for new selections and continues
active batches against their accepted constraints instead of edited meters.
A previously selected but not
started reservation is rejected if its effective meter requirements change.
The quantity payload accepts only the sender's currently open meter menu and
container ID; the server also checks entity identity, world and player distance.
Menu data splits the positive 32-bit quantity across two 16-bit properties.

`MixerManualMeterGameTests` adds 17 autonomous headless tests for registration,
physical integration, unmetered ranking, item/fluid constraints, AND semantics,
failed-selection non-consumption, invalid configuration, server quantity validation,
sample interactions, persistence/sync/portable state, active/next-batch changes,
priority, stale reservations, reformation, multi-strategy batch continuity, three
physical meters with distinct slots, and component-exact fluid identity.
Existing selection, allocation, geometry and processing tests remain in the suite.
Client visual acceptance is left to the user; no client task is launched.

## Changed files

Java paths below are relative to `src/main/java/net/impactvector/mobvats/`.

- `gamecontent/Content.java`: menu and networking registration.
- `gamecontent/item/EndpointState.java`: portable meter configuration.
- `gamecontent/multiblock/mixer/MixerPartType.java`: placeable part/entity registration.
- `gamecontent/multiblock/mixer/MultiblockMixer.java`: installed-meter collection.
- `gamecontent/multiblock/mixer/part/AbstractMixerMeterEntity.java`: shared sample,
  quantity, persistence and interaction behavior.
- `gamecontent/multiblock/mixer/part/MixerItemMeterEntity.java` and
  `MixerFluidMeterEntity.java`: individual part entities.
- `gamecontent/multiblock/mixer/part/MixerPartBlock.java`: sample/menu interaction.
- `gamecontent/multiblock/mixer/menu/MixerMeterMenu.java` and
  `MixerMeterNetworking.java`: synchronized quantity and validated server updates.
- `gamecontent/multiblock/mixer/client/MixerClient.java` and
  `client/screen/MixerMeterScreen.java`: menu screen registration and UI.
- `gamecontent/multiblock/mixer/process/MixerState.java`: physical eligibility,
  diagnostics and accepted active-batch constraint snapshot.
- `gametest/MixerManualMeterGameTests.java`: 17 additional headless tests.
- `gametest/AppliedCladdingGameTests.java`: registry count includes the two meters.

Resource paths below are relative to `src/main/resources/`.

- `assets/mobvats/lang/en_us.json`: meter names and configuration text.
- `assets/mobvats/blockstates/mixer_item_meter.json` and `mixer_fluid_meter.json`.
- `assets/mobvats/models/block/mixer_item_meter.json` and `mixer_fluid_meter.json`.
- `assets/mobvats/models/item/mixer_item_meter.json` and `mixer_fluid_meter.json`.
- `data/mobvats/loot_table/blocks/mixer_item_meter.json` and `mixer_fluid_meter.json`.
- `data/mobvats/recipe/mixer_item_meter.json` and `mixer_fluid_meter.json`.

`README.md` links this guide; `docs/MIXER_MANUAL_METERS.md` documents the slice.

## Validation (October 16, 2026)

The canonical Java 21 `format` and `validate` modes passed, including
`spotlessCheck build runGameTestServer`. All 816 GameTests passed (17 added by
this slice, 799 existing). Headless geometry/render checks and packaged resource
checks passed. `git diff --check` passed. The lifecycle test uses the same
30-tick disassembly/reformation intervals as the existing Mixer fixture tests.
No Minecraft client was launched and nothing was published.
