# Mixer refactor: Slice 2A manual meter requirements

Baseline: `c9df318` (Slice 1 deterministic recipe selection).

`ItemMeterRequirement` and `FluidMeterRequirement` hold a single existing exact
filter string and a positive item count or mB quantity. `sampled(...)` uses
`EndpointState` identities and `ResourceSelectors` matching: items match the item
ID, while sampled component-bearing fluids pin their components. Tags, empty
samples and nonpositive quantities are rejected. `MixerMeterRequirements` copies
the two lists into an immutable configuration; `NONE` preserves unmetered behavior.

`MixerState.meterRequirements(...)` is the transient integration seam. Candidate
planning still checks availability through the existing fluid allocation and
staged item inventory. Before reserving consumption or outputs, meter compatibility
checks the actual allocated identities for each ingredient slot. Each meter must
match the entire slot's authored quantity and every stack allocated to that slot.
A slot can draw from several physical stacks. Separate meters must own separate
slots; a single six-wheat slot cannot be partitioned into four-wheat and two-wheat
meter matches. Additional unmetered slots remain eligible.

An augmenting-path assignment handles overlapping meter filters without depending
on meter order or double-counting a slot. Compatibility does not change recipe
quantities, recipe selectors, output reservations, or the Slice 1 comparison:
ingredient slot count, normalized allocated consumption, then stable recipe ID
(and strategy ID for otherwise tied strategies). Changing meter configuration
invalidates outstanding reservations before resource mutation.

## Integration boundaries for Slice 2B

- There are no physical meters, GUI, enabled flags or configuration persistence.
  The caller supplies the complete active requirement set. New states default to
  `NONE`; save/load does not serialize requirements, and structure assimilation
  does not collect them. Slice 2B must rebuild requirements from component state.
- Item inputs still use the existing staged inventory. No connected-buffer item
  acquisition or idle inventory ownership was added.
- Compatibility evaluates the existing planner's deterministic allocation. It
  does not search for a different allocation for a broad/tag recipe selector when
  the chosen allocation fails an exact meter. For example, a broad slot allocated
  carrots fails a wheat meter even if unused wheat is also available. Supporting
  that alternative would require a separate constrained-allocation change.
- A manual meter constrains one whole ingredient slot, not aggregated consumption
  across multiple slots or a selectable fraction of one slot. Sample filter
  semantics, including item-ID-only matching, remain unchanged.

## Validation

The existing Slice 1 GameTests remain in place. Added headless tests cover exact
and mismatched quantities, surplus buffers, mixed AND constraints, extra unmetered
ingredients, duplicate meters, split and mixed allocations, sampled identities,
overlapping-filter reassignment, invalid configuration, deterministic ranking and
fallback, unsuccessful selection/cadence, output backpressure, and stale/single-use
reservations. No Minecraft client testing or publication is part of this slice.

Validation uses the Java 21 Gradle runtime and the wrapper's default writable
`workspace/.cache/gradle` cache:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck build runGameTestServer
git diff --check
```

Formatting, the local development build and its CPU/resource checks passed.
All 799 required headless GameTests passed, including the 12 new meter tests.
