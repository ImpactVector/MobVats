# Culture recovery production benchmark

These measurements precede happiness-scaled fouling. See [the updated formula and benchmark regression](HAPPINESS_SCALED_FOULING.md) for current behavior, maintenance costs and XP-self-funded results. Fixture geometry and original operating-mode methodology below remain the reference.

Measured on NeoForge 1.21.1 / 21.1.252, 2026-10-23. No balance constants were changed.

## Reproduction and measurement boundary

Run the canonical Java 21 validation launcher from `AGENTS.md` in `validate` mode. `CultureProductionBenchmarkGameTests` builds the fixtures in an autonomous headless server and writes CSV files under `.cache/gametest/build/reports/culture-benchmark/`. Each simulation runs twice and requires identical output. Tracked CSV copies accompany this report.

The server captures native cow geometry, forms the actual multiblock, checks every Head/Harness/Flesh/Sump assembly and Tool route, checks wheat Feed routes and item/XP export routes, and completes native sword harvest/feed transactions before measuring. The sustained clock is a deterministic simulation, not a ten-hour wall-clock Minecraft run. It uses the controller's actual per-Head happiness and medium exposure, loaded cow growth/milk definitions, `ProcessState.advance` fractional biological progress, native weapon health/damage, ten-tick process cadence and twenty-tick Tool availability. A separate Tool serves each productive cow. Wheat and output extraction have sufficient supply/capacity; external automation latency and Mixer/Buffer exterior volume are excluded from the requested Vat-volume metric.

Productive cows start as juveniles; unfed cows in the original dense diagnostic remain adult and their milk/fouling is still included. Each run warms up for one simulated hour, and measures the following ten hours at twenty server ticks/second. Counts divided by ten give hourly values. Finite windows and synchronized cohorts cause small differences from continuous steady-state rates. Biological kills/hour is the integrated growth-only ceiling, without sword delay. Mechanical kills/hour includes harvest strikes and cadence. Effective kills sum the actual killed specimens' happiness multipliers; milk and Waste output never count as kills. The average happiness column is unweighted across productive cows, so it need not equal effective kills divided by kills when cow frequencies differ.

XP is deterministic **expected** production: native cow reward is uniform 1–3 points, mean two. The native reward range is checked, and real `YieldRemainders` apply happiness/carry to that mean. This avoids presenting a random loot sample as an economic constant. XP enrichment costs are actual whole committed Mixer batches, at the configured twenty mB per XP point. Native startup-harvest XP is excluded from the simulation totals.

## Fixture coordinates and legality

Coordinates below are relative to the Vat's exterior minimum; X is width, Y height, Z depth. All cows use their native captured geometry and facing, with a 1×2×1 substrate. Every Head has Flesh directly beneath, a Harness at the roof on its X/Z column, and a Sump on the floor on that column. Real specimen ownership, support selection, obstruction and actor compilation determine legality.

* Requested dense: 9×4×4, Heads at X=1..7, Y=2, Z=1,2. Tools alternate Y=1 on odd X and Y=2 on even X, on the corresponding north/south wall. Opposite wall cells alternate item input/output ports, giving each Tool its required item routes. West/east Feed Ports have adjacent wheat inputs. Body occlusion limits their reach. The floor's fourteen interior cells are all Sumps: there is no legal Drain Valve position. Full fourteen-cow sustained operation is therefore not assumed.
* Closest tested dense recovery equivalent within the agreed nine-block width: 9×4×5, Heads at X=1..7, Y=2, Z=1,3. The extra central aisle has Feed Ports at X=2,4,6, Y=1, Z=2, with wheat inputs immediately below. The Drain Valve uses (1,0,2), Fill Valve (4,3,2), and culture inlet (3,3,2). This preserves fourteen cows while adding one exterior depth block (144 → 180 exterior blocks).
* Two-row: 9×7×9, Heads at X=1..7, Y=3, Z=2,6. Each wall Tool is at Y=2, with item input at Y=3 and output at Y=1. Feed Ports at X=2,4,6, Y=1, Z=4 have wheat inputs below. Drain Valve (5,0,4), Fill Valve (4,6,4), culture inlet (3,6,4).
* Optimized: 9×7×9, Heads at X=2,6, Y=3, Z=2,6. Wall Tools/ports are as above; one central Feed Port at (4,1,4) has wheat input below. Valve/inlet coordinates are as above.

Unused floor cells carry clad fluid TAPs connecting Sumps and recovery outlets. Byproduct output is at (0,1,1), controller at (0,2,1). No interior comfort or actor eligibility is credited by inspection of a drawing; the controller and geometry APIs supply it.

## Operating modes

* **Water**: ordinary water, initially full; normal fouling continues without replacement. This is a specified ten-hour operating window, not the eventual fully dirty asymptote. Free-water flushing can improve it without XP/charcoal costs.
* **Fresh**: full clean enriched Culture, ideal biological control with fouling held off. It establishes the clean-medium ceiling, not a sustainable zero-XP claim.
* **Sustained**: full initial enriched Culture, normal growth/milk fouling, dirty-first 1,000 mB batch recovery. A one-Mixer model takes 200 filtering + 200 enrichment ticks before refilling the drained amount; the remaining volume is used in the actual exposure calculation. Real `MixerState` transactions filter and enrich with one charcoal and 250 mB actual XP. Initial Vat-fill XP is excluded; this measures recurring upkeep. Partial dirty tails and any in-flight batch remain inventory, not free restoration. The requested 9×4×4 has no legal recovery outlet and receives no claimed sustained result.

The default Waste secondary output is unaccelerated and currently does not invoke biological fouling. Growth completion and accelerated milk output do. The simulation preserves this existing behavior, including milk timer carry across juvenile periods; it does not add a new Waste fouling trigger.

## Results

The table uses the validated fixtures above. Legal/productive counts are separate. “Dense + aisle” is the 9×4×5 correction; all large Vats are 9×7×9.

| Fixture | Mode | Exterior volume | Legal/productive | Happiness yield | Culture frequency | Growth ceiling kills/h | Mechanical kills/h | Effective kills/h | Effective/100 blocks |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Dense requested | Water | 144 | 14/4 | 0.500 | 0.545 | 6.534 | 6.800 | 3.400 | 2.361 |
| Dense requested | Fresh ceiling | 144 | 14/4 | 0.500 | 1.294 | 15.529 | 15.600 | 7.800 | 5.417 |
| Dense + aisle | Water | 180 | 14/14 | 0.535 | 0.619 | 25.982 | 26.200 | 14.010 | 7.783 |
| Dense + aisle | Fresh ceiling | 180 | 14/14 | 0.535 | 1.672 | 70.235 | 70.600 | 37.781 | 20.989 |
| Dense + aisle | Maintained | 180 | 14/14 | 0.535 | 1.665 | 69.943 | 69.600 | 37.244 | 20.691 |
| Two-row | Water | 567 | 14/14 | 0.941 | 0.939 | 39.438 | 39.600 | 37.447 | 6.604 |
| Two-row | Fresh ceiling | 567 | 14/14 | 0.941 | 4.546 | 190.941 | 190.800 | 180.988 | 31.920 |
| Two-row | Maintained | 567 | 14/14 | 0.941 | 4.542 | 190.784 | 189.800 | 180.018 | 31.749 |
| Four cows | Water | 567 | 4/4 | 2.000 | 0.996 | 11.957 | 12.000 | 24.000 | 4.233 |
| Four cows | Fresh ceiling | 567 | 4/4 | 2.000 | 5.000 | 60.000 | 60.000 | 120.000 | 21.164 |
| Four cows | Maintained | 567 | 4/4 | 2.000 | 4.996 | 59.956 | 60.000 | 120.000 | 21.164 |

| Fixture | Mode | Expected XP/h | Enrichment XP/h paid | Net XP/h | Wheat/h | Culture fouled mB/h | Charcoal/h |
|---|---|---:|---:|---:|---:|---:|---:|
| Dense requested | Water | 6.800 | 0.000 | 6.800 | 6.800 | 930.0 | 0.0 |
| Dense requested | Fresh ceiling | 15.600 | 0.000 | 15.600 | 15.600 | 0.0 | 0.0 |
| Dense + aisle | Water | 27.600 | 0.000 | 27.600 | 26.200 | 1310.0 | 0.0 |
| Dense + aisle | Fresh ceiling | 75.400 | 0.000 | 75.400 | 70.600 | 0.0 | 0.0 |
| Dense + aisle | Maintained | 74.400 | 45.000 | 29.400 | 69.600 | 3600.0 | 3.6 |
| Two-row | Water | 75.000 | 0.000 | 75.000 | 39.600 | 1980.0 | 0.0 |
| Two-row | Fresh ceiling | 361.600 | 0.000 | 361.600 | 190.800 | 0.0 | 0.0 |
| Two-row | Maintained | 359.600 | 130.000 | 229.600 | 189.800 | 10430.0 | 10.4 |
| Four cows | Water | 48.000 | 0.000 | 48.000 | 12.000 | 600.0 | 0.0 |
| Four cows | Fresh ceiling | 240.000 | 0.000 | 240.000 | 60.000 | 0.0 | 0.0 |
| Four cows | Maintained | 240.000 | 40.000 | 200.000 | 60.000 | 3320.0 | 3.3 |

Original 9×4×4 maintained operation: **unavailable** with fourteen installed specimens, because a normal Drain Valve has no legal floor cell. The four productive cows above are the two end columns; the other ten remain physical obstructions and their adult milk fouling is included. These counts describe this validated arrangement, not an exhaustive search of every possible wall-actor permutation. The 9×4×5 candidate is one dimension step away, remains within the agreed nine-block width, and verifies all fourteen feed/harvest relationships plus recovery-valve placement.

Raw measurements: [dense.csv](../benchmarks/culture-recovery-2026-10-23/dense.csv), [dense-recovery.csv](../benchmarks/culture-recovery-2026-10-23/dense-recovery.csv), [two-row.csv](../benchmarks/culture-recovery-2026-10-23/two-row.csv), [optimized.csv](../benchmarks/culture-recovery-2026-10-23/optimized.csv).

## Mechanical constraints and economics

Diamond swords use the native damage calculation and require multiple ordinary strikes; the model waits twenty ticks between strikes and respects the ten-tick process cadence. A continuously ready two-hit target would give a Tool at most 1,800 kills/hour. Each fixture has a separate Tool per productive cow, whose biological rate is at most about fifteen kills/hour. Harvest delay therefore has a small effect; it is never assumed instantaneous. The finite-window counts can slightly exceed the integrated growth ceiling because completed cohorts cross measurement boundaries.

Feed payment is one actual wheat per committed cow replacement. Feed Ports and their inventory routes are necessary: putting wheat beside a Tool does not make a cow feedable. Shared Feed Ports can serve several independent cows when compiled reach and inventory reservations permit it. Native first harvests prove the fixture transactions; output backpressure is excluded by the stated sufficient-storage operating condition. In play, a full Tool result buffer, blocked XP output, missing wheat or exhausted sword stops normal production. Budget two durability per two-hit kill; the model's eleven-hour per-sword workload remains below ordinary diamond durability.

At full uninterrupted drive, one 200-tick filter batch plus one 200-tick enrichment batch takes twenty seconds: 180 complete recovery batches/hour, or 180,000 mB Culture/hour. Two continuously driven dedicated Mixers can pipeline 360 batches/hour. Startup spin-up, transport/programming latency and power/fuel provisioning are outside these steady-drive ceilings. Actual maintenance demand is only about 3.3–10.4 filter batches/hour, so Mixer throughput is not the limiting factor here. Extra Mixers primarily separate control and routing.

The model performs filtering at its configured completion tick, then enriches at its separate completion tick. It counts charcoal when filtering commits and XP when enrichment commits. These can differ by one in-flight batch at a measurement boundary. Dirty tails remain dirty; filtered inventory is not treated as returned clean medium. This explains the four-cow window's 3.3 charcoal/hour versus 3.2 completed enrichment batches/hour.

At the current 250 mB XP per 1,000 mB Culture and twenty mB/XP, each recovered bucket costs 12.5 XP points. Applying that ratio to *all newly fouled volume*, including tails rather than just completed batches, gives long-run upkeep estimates of 45.0 XP/hour for corrected dense, 130.375 for two-row, and 41.5 for four cows. Corresponding expected net production is approximately +29.4, +229.225 and +198.5 XP/hour. All three satisfy the XP-neutral-or-positive goal with substantial margin for four cows. One growth fouling event costs 0.625 XP points to restore; milk adds a small additional cost in productive fixtures.

Wheat per effective kill is about 1.87 for corrected dense, 1.05 for two-row, and 0.5 for four cows. Thus spacing saves wheat per useful drop even where it loses the exterior-volume comparison.

Startup Culture is separate from upkeep: measured medium capacity is 42,000 mB in corrected dense and 245,000 mB in each large Vat. Manufacturing a full enriched fill requires 525 XP points and 3,062.5 XP points respectively. The original dense diagnostic holds 28,000 mB, requiring 350 XP points for a full fresh fill. These are actual medium-capacity results; substrate blocks currently do not register numeric fluid displacement. They are not empty-air cell estimates.

## Balance assessment and later tuning candidates

The corrected dense water fixture wins volume efficiency over both large water fixtures, matching the intended low-infrastructure trade-off. Four maintained cows beat corrected dense by about 2.3%, but lose to the two-row fourteen-cow fixture by about 33.3% (the latter produces about 50% more per equal exterior volume). The full spacing progression therefore **fails** under the current constants. The requested 9×4×4 fourteen-cow arrangement also fails the normal service/recovery requirements and is reported as a diagnostic, not as fourteen productive cows.

With the measured two-row kill rate held fixed, its weighted happiness yield would have to fall below roughly 0.632 effective kills per harvest for four cows' 120 effective kills/hour to win. Alternatively its effective culture frequency would need to fall from roughly 4.54 toward 3.03, holding other factors fixed. The four-cow side would need over 180 effective kills/hour at the current two-row rate—roughly fifty percent above its present 120. These are sensitivity targets, not proposed changes already applied.

Candidate follow-up experiments should strengthen the cost of shared/obstructed comfort for enriched production while preserving the water trade-off, or test a stronger spacing-dependent yield response. Measure each candidate through the same fixtures before adopting it. Simply raising the global Culture frequency or happiness ceiling also benefits fourteen cows and is not a demonstrated fix. Changing the XP recipe ratio alone does not fix drops/volume when sufficient XP is supplied, and the benchmark gives no reason to make XP self-sufficiency harder merely to mask the spatial issue. Increased fouling could create a real infrastructure limit, but should be tested explicitly rather than used to force the desired result.

Do not retune growth periods, fouling amounts, XP conversion, recipes, Tool cadence or happiness/frequency formulas as part of this slice. All reported values use the existing balance.

## Validation

The canonical Java 21 `format` and `validate` modes completed successfully. Final validation passed the build/resource/CPU checks and **all 958 required headless GameTests**, including recovery transactions, configurable XP inputs, exact meters, persistence and recognized legacy-pair migration, native fixture harvests, and repeated deterministic benchmark results. The four tracked CSV files contain eleven operating cases and were checked byte-for-byte against that run's generated reports. `git diff --check` passed. No Minecraft client or release/publish task was run.
