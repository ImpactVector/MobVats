# Happiness-scaled biological fouling

Each successfully committed eligible biological operation converts clean medium to dirty medium using its own specimen's current happiness yield multiplier `H`:

```text
U = clamp((2 - H) / 1.5, 0, 1)
F = 1 + (M - 1) * U
scaled mB = process base mB * F + carried fractional mB
```

`M` is `fouling.max_multiplier` in `config/mobvats/culture/*.json`, alongside `default_mb`, `waste_formula_mb` and action overrides. It defaults to 5.0 when omitted and must be finite and at least 1. Perfect happiness always costs 1×. Existing authored configuration files are preserved; omission enables the new default without rewriting them.

| Happiness H | Total fouling F (default) | Mean mB at base 50 |
|---:|---:|---:|
| 0.50 | 5.000000 | 250.000000 |
| 0.75 | 4.333333 | 216.666667 |
| 1.00 | 3.666667 | 183.333333 |
| 1.25 | 3.000000 | 150.000000 |
| 1.50 | 2.333333 | 116.666667 |
| 1.75 | 1.666667 | 83.333333 |
| 2.00 | 1.000000 | 50.000000 |

This changes converted volume, with an upper bound of the available clean amount. It preserves the selected clean/dirty prototypes, total stored volume and the existing XP-to-Waste formula replacement. Zero bases remain zero. Ordinary water receives the same scaling and can be drained/voided and replaced with fresh water without charcoal or XP. Enriched Culture has higher filtration and re-enrichment demand; the Vat never consumes XP directly. No fouling triggers, timers, drops, rewards, recipes, actor assignments or happiness/frequency calculations changed.

## Fractional and transactional accounting

`SpecimenProcessState.foulingRemainders` persists an independent fractional mB entry for each action. The engine's existing Head/specimen UUID ownership checks prevent a replacement specimen from inheriting it. Each entry is bound to the canonical SHA-256 of the compiled mob definition, species, action, base amount and configured maximum. A configuration mismatch starts a new fraction at its next successful commit; old fractions are not pooled between configurations. Happiness itself can change without discarding carry.

Plans do not mutate state. Their reversible step commits with the existing biological output and medium steps. Failed preflight, failed apply and later rollback preserve the previous carry and both medium fractions. Whole mB use deterministic floor-with-carry, matching the existing yield/timer floating-point tolerance. A 50 mB operation at 1.75 happiness emits 83, 83, 84 mB over three commits. Saving/reloading between every commit preserves that sequence. Dry, unrecognized and fully dirty medium accrue no new fraction or deferred whole-mB debt. Whole amounts beyond available clean medium are discarded, as in existing saturation behavior.

## Benchmark protocol

The existing `CultureProductionBenchmarkGameTests` fixtures and operating modes remain. Geometry, feeding eligibility, native sword damage/strike cadence, biological timers, milk carry, expected native cow XP and real Mixer recovery transactions follow the [original protocol](CULTURE_PRODUCTION_BENCHMARK.md). The benchmark now invokes production `Fouling.commit` with a separate specimen context/state for each cow. It also asserts actual per-specimen fouling volumes across the fixtures, including cows with different happiness.

Water, ideal fresh Culture and externally funded maintained Culture retain one hour of warmup and ten measured hours. Water is an initially full, unflushed operating window and can eventually become entirely dirty. Fresh suppresses fouling as an ideal ceiling. Maintained mode retains its unlimited external XP supply and is explicitly reported even when XP-negative.

The additional **self-funded** mode starts with the same full enriched fill, but an empty spendable XP bank. Only that Vat's committed harvest XP enters the bank, including warmup harvests. A recovery batch can start only after reserving its actual configured XP cost; filtering/enrichment still take their full recipe times. Insufficient XP leaves culture dirty and lets production slow normally. Charcoal, wheat, sword service and output handling retain the original ideal-supply assumptions. The initial enriched fill is a separate startup investment, excluded from recurring XP costs, not a recurring external subsidy.

Self-funded mode warms up for 200 simulated hours and measures the next 100 hours, so the initial clean reservoir cannot mask a recurring XP deficit. Rates use the longer window only for this mode. Each scenario runs twice and requires byte-identical results. The ordinary ten-hour and self-funded hundred-hour windows have different cohort/batch boundary quantization; do not interpret those small differences as balance changes.

The long-window harness retires its isolated Mixer's empty escrow only after a complete recovery batch and assertions that both fluid sides and charcoal input are empty. Otherwise the isolated model accumulates empty tanks and its integral allocation matrix grows without bound. No in-flight batch, fluid or reagent is discarded. The real Mixer implementation, recipe transactions, batch times and costs are unchanged. Ideal sword servicing is assumed throughout the extended window, in addition to the existing ideal feed, charcoal and output handling.

## Results

All rates are per simulated hour. XP is in points (20 mB per point); fouled volume is mB. Effective production is normalized to 100 exterior bounding-volume blocks, matching the original benchmark. Happiness is the unweighted average across productive cows. Maintained means externally funded; self-funded has no recurring external XP.

| Layout | Mode | Effective kills/h/100 | Happiness | Fouled mB/h | Charcoal/h | XP produced/h | XP consumed/h | Net XP/h | Wheat/h |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 14 dense, 9×4×5 | Water | 7.368 | 0.535 | 3856.600 | 0.000 | 26.200 | 0.000 | 26.200 | 24.800 |
| 14 dense, 9×4×5 | Fresh ceiling | 20.989 | 0.535 | 0.000 | 0.000 | 75.400 | 0.000 | 75.400 | 70.600 |
| 14 dense, 9×4×5 | Maintained | 20.691 | 0.535 | 17662.200 | 17.600 | 74.400 | 220.000 | -145.600 | 69.600 |
| 14 dense, 9×4×5 | Self-funded | 13.655 | 0.535 | 3940.000 | 3.940 | 49.180 | 49.250 | -0.070 | 45.940 |
| 14 two-row, 9×7×9 | Water | 6.433 | 0.941 | 7360.000 | 0.000 | 73.000 | 0.000 | 73.000 | 38.600 |
| 14 two-row, 9×7×9 | Fresh ceiling | 31.920 | 0.941 | 0.000 | 0.000 | 361.600 | 0.000 | 361.600 | 190.800 |
| 14 two-row, 9×7×9 | Maintained | 31.749 | 0.941 | 39471.200 | 39.400 | 359.600 | 492.500 | -132.900 | 189.800 |
| 14 two-row, 9×7×9 | Self-funded | 17.739 | 0.941 | 16091.300 | 16.100 | 201.140 | 201.250 | -0.110 | 106.120 |
| 4 spaced, 9×7×9 | Water | 4.233 | 2.000 | 600.000 | 0.000 | 48.000 | 0.000 | 48.000 | 12.000 |
| 4 spaced, 9×7×9 | Fresh ceiling | 21.164 | 2.000 | 0.000 | 0.000 | 240.000 | 0.000 | 240.000 | 60.000 |
| 4 spaced, 9×7×9 | Maintained | 21.164 | 2.000 | 3320.000 | 3.300 | 240.000 | 40.000 | 200.000 | 60.000 |
| 4 spaced, 9×7×9 | Self-funded | 21.037 | 2.000 | 3292.000 | 3.290 | 238.560 | 41.125 | 197.435 | 59.640 |

Raw CSVs: [dense 14](../benchmarks/happiness-fouling-2026-10-23/dense-recovery.csv), [two-row 14](../benchmarks/happiness-fouling-2026-10-23/two-row.csv), [four cows](../benchmarks/happiness-fouling-2026-10-23/optimized.csv). These include capacity, productive/legal cow counts, biological and mechanical kill rates, culture frequency and unnormalized effective production as well as the requested metrics above. The retained original 9×4×4 dense diagnostic has only four fed cows and no legal recovery outlet; its water/fresh results remain in [dense.csv](../benchmarks/happiness-fouling-2026-10-23/dense.csv), without a claimed maintained case.

## Economic changes

The comparable ten-hour maintained windows show the following change from [base-only fouling](CULTURE_PRODUCTION_BENCHMARK.md):

| Layout | Fouled mB/h before → after | Charcoal/h before → after | XP upkeep/h before → after | Net XP/h before → after |
|---|---:|---:|---:|---:|
| Dense 14 | 3,600 → 17,662.2 | 3.6 → 17.6 | 45 → 220 | +29.4 → −145.6 |
| Two-row 14 | 10,430 → 39,471.2 | 10.4 → 39.4 | 130 → 492.5 | +229.6 → −132.9 |
| Four spaced | 3,320 → 3,320 | 3.3 → 3.3 | 40 → 40 | +200 → +200 |

The perfect-happiness fixture is unchanged in every retained mode, as are all fresh-ceiling results. Crowded maintained layouts incur approximately 4.91× and 3.78× their previous actual fouled volume. The externally funded two-row layout still wins volume efficiency, but now requires a recurring external XP subsidy. Fouling alone does not impose a layout restriction or reduce drop yields.

With only the Vat's own XP available, four cows beat two-row fourteen cows by about **18.6%** and dense fourteen cows by about **54.1%** in effective production per exterior volume. Four cows retain about **197.435 net XP/hour**. Both crowded layouts spend essentially their whole XP income, remain mostly dirty, and produce less. Progress samples at simulation hours 50–250 confirm the two-row clean inventory fluctuates around 1–2 buckets of its 245-bucket capacity, with only a few XP points banked; this is no longer an initial-full-fill burst. Four cows stay near fully clean while their own XP bank grows.

The −0.070/−0.110 XP/hour measured net values in crowded self-funded cases are finite-window batch/bank effects: some XP earned before measurement is spent during measurement. The bank starts empty at simulation hour zero and is never negative. No external XP is inserted without a prior reservation from that bank. Completed enrichment, reserved XP in a batch crossing a boundary, dirty tails and stored clean volume explain small differences between hourly production, paid batch costs and newly fouled volume. At 12.5 XP per recovered bucket, accounting for all newly fouled volume rather than only completed batches gives maintained upkeep estimates of 220.778, 493.390 and 41.500 XP/hour respectively.

The self-funded fouled-volume column counts actual clean-to-dirty conversion. When clean culture is exhausted, unavailable whole mB are discarded under the pre-existing saturation behavior; it does not create deferred fouling debt. Water's extra fouling has no XP or charcoal charge. Its retained window includes no flushing; draining/voiding and refilling fresh water remains a separate free-water management option.

In the original 9×4×4 diagnostic, unfed adult cows continue producing milk and foul the entire water reservoir during warmup. Its measured water fouling is therefore zero: no clean water remains to convert. This is saturation, not an exemption from happiness scaling.

## Validation

The canonical Java 21 launcher passed `version`, `format` and final `validate` modes (Eclipse Adoptium 21.0.12.1, Gradle 9.2.1). Final `spotlessCheck build runGameTestServer` completed successfully in 10m 8s, including all CPU/resource checks and **all 965 required headless GameTests** (958 existing + 7 new). All fourteen operating cases ran twice with identical output. The four tracked CSVs match the final generated reports byte-for-byte by SHA-256. Baseline comparison confirms all four ideal-fresh cases and every prior optimized-four-cow mode are unchanged. `git diff --check` passed. No Minecraft client or mod release/publish task was run.

Seven new focused GameTests cover the endpoint/intermediate formula and configuration validation; carry accuracy through 300 save/load cycles; engine reload/specimen UUID ownership; apply/preflight rollback and stale-plan rejection; process/specimen/configuration separation; zero opt-outs; and water/enriched-volume conservation with unchanged normalized XP-to-Waste composition. Existing biological growth, passive output, regrowth and process-override tests now expect happiness-scaled quantities. The benchmark fixtures assert production fouling for each individual cow, rather than their average happiness.

The extended benchmark initially exposed unbounded empty escrow in the isolated harness; that interrupted run is not counted as passing. The harness fix above avoids altering the production Mixer. An intermediate complete-suite attempt also exposed an existing harness-resumption assertion that required positive XP from a single native cow reward. At low happiness a native 1-XP reward can correctly produce zero whole XP with carry. That test now checks the committed harvest counter, retaining its operational, milk and tool-durability checks; no XP formula or reward was changed.
