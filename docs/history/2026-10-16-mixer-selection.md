# Mixer refactor slice 1: deterministic recipe selection

Selection reuses `MixerState`'s integral allocation and output reservation plan.
Eligible plans sort by descending requirement slot count, descending exact
normalized consumption, ascending recipe ID, then ascending strategy ID within
a recipe. Each recipe competes with its best executable strategy. Legacy
priorities remain parseable but do not affect selection. No buffer, routing,
transaction, construction or rendering changes are included.
Stored strategy and registry enumeration retain their legacy order for signature
and accessor compatibility; selection compares plans independently of that order.

`MixerState.select()` returns the existing single-use reservation abstraction
and preserves status, metrics and buffers. Cadence commits the chosen plan through
the existing reservation path. An active executable batch finishes normally;
an unavailable batch can fall back with reset progress. Completion always starts
a fresh selection on the next cadence. If no alternative is executable, existing
blocked progress is retained.

## Limitations and later slices

- The existing recipe model cannot express non-consumed tools/catalysts. Every
  item and fluid requirement is consumed. `record_in_composition` affects formula
  recording only. Adding non-consumed allocation, reservation and extraction
  semantics would change processing, outside this selection slice. The focused
  regression explicitly verifies that unrecorded inputs remain consumed; it does
  not claim coverage of unsupported non-consumed-tool behavior.
- Item allocation uses the Mixer's four input staging slots, filled through
  recipe item input ports. Selection does not independently acquire items from
  connected item buffers. Connected fluid buffers and service tanks
  are queried directly. A network-wide item acquisition plan needs a later slice;
  conduit/buffer architecture is unchanged here.
- Alternatives use the actual existing deterministic max-flow allocation. That
  allocation is not optimized for normalized consumption. Slot/tank enumeration
  determines which valid allocation is selected when several are possible.
- Existing compound formula derivation records authored item selectors rather
  than allocated item identities. This is separate from ranking, which uses actual
  allocated stacks and their component-defined maximum sizes.
- Existing progress belongs to a recipe, so changing its strategy mid-batch can
  reuse progress. Transaction revalidation and rollback behavior are unchanged.

## Verification

`MixerSelectionGameTests` covers repeated-slot complexity, consumed quantities,
stack sizes 1/16/64, full-stack and bucket equality, nearby fluid/item fractions,
actual alternatives and component stack sizes, stable IDs, disabled/unavailable
strategy fallback, supply changes, next-batch reselection and selection purity
with connected service storage. Existing operation tests retain schema and
reservation coverage, including legacy enumeration/signature compatibility.

Final validation used Java 21: `spotlessApply`, `spotlessCheck build
runGameTestServer`, and `git diff --check`. The build and CPU checks passed;
all 787 required headless GameTests passed, including the 12 new selection tests.
No client was launched.
