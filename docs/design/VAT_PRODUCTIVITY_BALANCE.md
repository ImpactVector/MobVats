# Vat productivity and operating economics — balance goals

Status: design goals and hypotheses; **not** a validated performance benchmark. Last updated: 2026-10-23.

## Metric

The primary spatial-efficiency measure for livestock harvest is **effective kills per hour per exterior Vat block**.

An *effective kill* is the output of a standard 1x-yield harvest. Native drops, including XP where the existing yield scaling applies, are normalized by happiness yield. This measures useful loot rather than mere specimen deaths.

At unconstrained steady state, for each specimen:
- base growth period for default cows is 24,000 ticks (20 minutes);
- culture exposure scales biological replacement-growth frequency;
- happiness scales loot quantity per completed kill, **not** kill count or growth speed;
- effective kills/hour approximately = 3 x effective culture-frequency multiplier x happiness-yield multiplier.

Total effective kills/hour is summed over all specimens; divide by actual exterior Vat volume (X x Y x Z). Account for actual per-specimen geometry and actor reach, not just average multipliers. Tool strike speed, availability, feed, output routing, and other throughput limits must be validated.

## Layouts to compare

1. **Extreme dense 14**: 9 x 4 x 4 exterior, fourteen cows with no spare comfort clearance; actual legal formation/actor assignments to be tested.
2. **Two-row 14**: 9 x 7 x 9 exterior, two rows of seven cows facing individually assigned wall Tool Ports; space between rows; formation/actor assignments to be tested.
3. **Optimized 4**: 9 x 7 x 9 exterior, four well-spaced cows with working Tool Ports and maximal practical comfort/exposure.

The two-row geometry is a design hypothesis based on each cow needing reachable wall actors and other cows blocking lines of sight. Do not assume an arbitrary solid-filled center can harvest.

## Intentional trade-offs

- Under ordinary water or poor culture infrastructure, compact/high-density farms **may** be best per exterior volume. That is acceptable and creates interesting early-game and low-input designs.
- With good, **sustainably maintained enriched culture**, the four-cow optimized arrangement should outperform both fourteen-cow examples in *effective drops per unit exterior volume*.
- Resource cost of Vat casing/glass is intentionally fairly trivial and is **not** the primary balancing metric.
- Meaningful economics are net useful drops, especially XP, and external consumables such as wheat. Water is effectively free.
- Mixer ratios, culture creation/restoration XP, and fouling amounts are tuning variables, not sacred constants.
- An optimized four-cow Vat being XP-neutral or XP-positive over time is desirable; substantial XP profit is acceptable. Do **not** require all installations to be self-sufficient: specialized dense XP farms supplying other Vats are valid.
- Compare steady-state operation, including fouling and XP restoration, rather than a short full-strength burst.

## No frozen benchmark numbers

Prior conversational estimates used illustrative layouts and assumptions that have **not** been confirmed by actual assembly, clearance and process simulations. The agreed T1 max was later raised from 7 x 7 x 7 to **9 x 7 x 9**, so older four-cow ratios are especially noncanonical.

A validation should enumerate legal geometry/actor positions, compute each cow's comfort-shell credit and culture exposure through the real code, run sustained growth/harvest cycles, tally effective drops and actual XP upkeep, and report gross/net throughput divided by exterior volume. Keep the model reproducible and testable before changing constants.

See `CULTURE_XP_FOULING.md` for the target XP upkeep mechanic and `VAT_TIERS_AND_CONSTRUCTION.md` for structural constraints.

## Implemented benchmark

The culture-recovery slice adds [reproducible fixture measurements and operating economics](CULTURE_PRODUCTION_BENCHMARK.md). Those results supersede conversational estimates for the measured fixtures only. They distinguish legal Head count from productive cow count, native feed/harvest validation from the sustained simulation, and growth-only ceilings from mechanical throughput. The requested dense dimensions are retained as a diagnostic; an additional dense fixture tests the closest recovery-capable geometry when normal actor access and a Drain Valve require more space. Balance goals above remain goals; the slice does not tune constants to force them to pass.
