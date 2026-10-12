> Historical record. Original: `docs/XR2_PERFORMANCE_BASELINE.md`. Current reference: [07-TESTING.md](../07-TESTING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# XR2 / MobVats lifecycle performance baseline — October 11, 2026

The separate headless benchmark is working and has produced **578 accepted rows**
from **46 fresh server processes**: 20 profiled suites, two instrumentation controls,
and 24 snapshot-based cold restores. Each suite has three measured repetitions.
The initial results do not justify a MobVats production performance change.

Both minimum MobVats machines and both XR2 process machines completed assembly,
structural break/repair, real chunk eviction/reload, inactive ticking and operational
transitions at **1, 4, 16 and 32 machines**. The larger Mixer, four-specimen Vat,
connected pair and basic Reactor completed single-unit suites and three cold restores.
The Reactor qualifies as the complex reference machine. No Turbine fixture was added.

## Revisions and environment

| Component | Recorded baseline |
|---|---|
| MobVats source HEAD | `6ad065191c05c5d8cfcb965454f650c68176041e` |
| MobVats development JAR SHA-256 | `d11f0cb1f960e4e7d88110af8e9e6b39304af95b0bd8769e7bbf7e0dca553b1a` |
| XR2 inspected `1.21` source | `f6cc65140a8abbfc87137f5c54fc641085f66048` — version bump to 2.4.27 |
| XR2 runtime | Published `ExtremeReactors2-1.21.1-2.4.27`, `curse.maven:extreme-reactors-250277:7293725` |
| XR2 JAR SHA-256 | `d8f53792b7583dd2b3540956b98ff9f95ac0c86b1c91e20e353aa5a05684024d` |
| ZeroCore runtime, both environments | `1.21.1-2.4.18`, `curse.maven:zerocore-247921:6648746` |
| ZeroCore JAR SHA-256, both environments | `69a12ceb3aa31e5d1075459b5fd28b121eb27fddb484a8be7ecdd68158e55003` |
| Minecraft / NeoForge | `1.21.1` / `21.1.252`; satisfies XR2's declared `21.1.152` minimum |
| Java | Eclipse Adoptium / Temurin `21.0.12.1+1-LTS` |
| Host | Windows 11 amd64, 24 logical processors |
| Server heap / tick rate | `-Xms2G -Xmx2G`, ordinary 20 TPS |
| Observation | Disposable formed-fixture warmup of 100 ticks; 100 ticks before each 200-tick idle sample; 20 consecutive ready ticks after assembly/restoration |
| World | Creative peaceful superflat; view/simulation distance 6; no structures, random ticks, daylight cycle or mob spawning; spawn chunk radius zero |

The runtime mods are NeoForge, ZeroCore, the benchmark mod and **either** MobVats
**or** XR2. Operator QoL mods are absent. Each workload unit is in one chunk, four
chunks apart from the next unit, away from spawn. Saves/configuration are in
`benchmarks/.cache/`; normal development and acceptance saves were not touched.

The XR2 binary is the [published 2.4.27 release](https://www.curseforge.com/minecraft/mc-mods/extreme-reactors/files/7293725).
Its correspondence to a reproducible build of the inspected source SHA has not been
proven; the recorded binary hash identifies the tested implementation. The local
ZeroCore source reference `64d892b1ba099192a35bfa4ee08465540a2f12e7` declares **2.4.17**;
it is a source reference, not the tested 2.4.18 binary. There is **no runtime ZeroCore
version/hash mismatch** between the environments.

Every row carries dependency hashes, runtime/JVM settings and a harness source hash.
Accepted data has three harness fingerprints: `a3a51292…` for the larger Mixer,
`bd926a1a…` for earlier validated extra/process fixtures, and `630874fa…` for the
final scale matrix, Reactor retry and controls. Later changes corrected unrelated
fixture setup and prevented chunk acquisition by unload diagnostics; the shared
callback probes and measurement contract remained the same. The reducer retains
fingerprints as separate groups rather than pooling different harness revisions.

## Results and reproduction

- [Runner and methodology](../../benchmarks/README.md)
- [Scenario/fixture definitions](../../benchmarks/scenarios/lifecycle.json)
- [Result schema](../../benchmarks/scenarios/result-schema.json)
- [All readable statistics](../../benchmarks/results/2026-10-11/SUMMARY.md)
- [Summary JSON](../../benchmarks/results/2026-10-11/summary.json)
- [Raw JSONL](../../benchmarks/results/2026-10-11/raw.jsonl) / [CSV](../../benchmarks/results/2026-10-11/raw.csv)
- [Accepted process directories](../../benchmarks/results/2026-10-11/accepted-runs.txt)
- [Vat JFR reduction](../../benchmarks/results/2026-10-11/jfr-vat-min-1.json) / [Reactor JFR reduction](../../benchmarks/results/2026-10-11/jfr-reactor-1.json)
- [16-Mixer JFR reduction](../../benchmarks/results/2026-10-11/jfr-mixer-16.json) / [16-Fluidizer JFR reduction](../../benchmarks/results/2026-10-11/jfr-fluidizer-16.json)

Binary JFR recordings, saved snapshots and logs remain in the ignored process
directories listed above. Raw data and reductions are retained in the repository.
The initial settings were `-Repetitions 3 -Warmup 100 -Measure 200`; the runner defaults
are deliberately longer. Reproduce using the README commands with those settings.
Use `-Cold` at count 1, and separate matched `-NoProbes -NoProfile` runs at count 16
for Mixer/Fluidizer controls. Run environments sequentially. Re-reduce the accepted
directories with `runners/summarize.py` and verify with `runners/validate.py`.

## Whole warm reload

All figures below are milliseconds, with **n=3** per entry. SD is sample standard
deviation; MAD is median absolute deviation. These operations include chunk work,
validation, derived-state readiness and the final settling window.

| Machine | Count 1 median [min, max] | Count 32 median [min, max] | Count 32 SD | Count 32 MAD |
|---|---:|---:|---:|---:|
| Minimum Mixer | 1892.08 [1833.09, 1894.31] | 1853.67 [1599.37, 1856.76] | 147.72 | 3.09 |
| Minimum Vat | 1839.27 [1799.24, 1845.38] | 1802.59 [1559.52, 1806.87] | 141.59 | 4.29 |
| Fluidizer | 1888.45 [1827.27, 1890.01] | 1851.97 [1584.54, 1856.51] | 155.73 | 4.55 |
| Reprocessor | 1892.05 [1850.83, 1895.72] | 1847.62 [1585.38, 1855.26] | 153.66 | 7.64 |

At count 32, median first-ready observations were 868–912 ms; roughly another
950 ms came from the required stable ticks. ZeroCore's validation cadence and the
settling rule dominate elapsed latency. These figures do **not** demonstrate exact
performance parity or a speedup as count increases. Retained min/max/SD expose the
shorter first repetitions that a median alone would conceal.

Median `onMachineAssembled` callback time during reload of all 32 machines was
2.91 ms for Mixer, 5.27 ms for Vat, 1.31 ms for Fluidizer and 1.66 ms for Reprocessor.
Inclusive ZeroCore validation time was 4.49, 7.14, 1.98 and 2.59 ms respectively.
The values are nested; do not add them. MobVats performs geometry/topology/specimen
rebinding that the XR2 process machines do not share. The extra callback work is
small in absolute terms and provides no evidence for changing production behavior.

## Pause and unload

At count 32, median whole unload duration was 145.02 ms for Mixer, 196.88 ms for
Vat, 145.17 ms for Fluidizer and 150.38 ms for Reprocessor. Median controller pause
callback totals for all 32 machines were approximately 0.11, 0.08, 0.01 and 0.01 ms.
World/chunk work dominates the full operation. The additional Vat/Mixer cleanup is
consistent with retiring their derived services; it is not disproportionate at this scale.

The valid Reactor trial had 5,058.54 ms median unload [5,055.09, 5,097.01], distinct
from the process-machine workloads. It completed actual eviction on every repetition.
Treat this as a qualified reference workload; investigate its chunk scheduling/save
path separately before interpreting the difference as controller cleanup cost.

## Inactive idle ticking

Each cell below is the median **total `updateServer` callback time over 200 ticks**,
in milliseconds, n=3. The last column is total Pre-to-Post server tick processing
time divided by 200, at count 32. It excludes tick pacing and work outside that interval.

| Machine | Parts per machine | Count 1 | Count 4 | Count 16 | Count 32 | Count 32 server ms/tick |
|---|---:|---:|---:|---:|---:|---:|
| Minimum Mixer | 27 | 1.201 | 1.567 | 1.480 | 2.811 | 0.687 |
| Minimum Vat | 36 | 3.206 | 4.042 | 5.466 | 8.813 | 0.723 |
| Fluidizer | 26 | 0.562 | 0.470 | 0.431 | 0.745 | 0.613 |
| Reprocessor | 58 | 0.687 | 0.338 | 0.448 | 0.893 | 0.610 |

All **60 profiled idle windows** had exactly one controller update and one ZeroCore
controller dispatch per machine per tick. No probed assembly, validation or part
attachment/removal callbacks occurred in these windows. This does not count every
unprobed topology routine. No resources were supplied; productive execution was disabled.

The per-machine callback cost decreases across scales, particularly in the XR2
process machines. A fixed tick warmup does not guarantee an identical JIT tier at
different invocation counts, and timer/probe overhead matters at these durations.
Do not interpret this as linear scaling or publish precise relative speedups.
Whole server processing remained well within the 50 ms tick interval in these empty workloads.

At count 1, the larger 67-part Mixer used 1.620 ms total controller time over 200 ticks;
the 90-part, four-specimen Vat used 4.070 ms. The connected pair has 152 attached parts
and two controllers per workload unit: its median combined controller time was
14.821 ms over 200 ticks. It has an active Vat fluid output facing a passive Mixer
input, local conduits and empty physical buffers. This is a separate routing workload,
not an XR2 process-machine performance contest; productive transfers were not benchmarked.
The basic Reactor used 6.486 ms at count 1 and is also a qualified comparison.

## Cold restores, startup and shutdown

Cold restoration used three **fresh processes and copies of the same saved snapshot**
per fixture, at count 1. Median acquisition-to-stable times were 2,236.73 ms Mixer,
2,227.94 ms Vat, 2,295.25 ms Fluidizer, 2,238.23 ms Reprocessor and 2,172.50 ms Reactor.
The larger Mixer, multi-specimen Vat and connected pair were 2,298.35, 2,262.73 and
2,355.01 ms. These intervals begin after ServerStarted, not at JVM launch.

The separate JVM-uptime row includes launcher/mod/world startup and benchmark
initialization. Shutdown is measured through ServerStopped, including actual saving
and unload. `process.jsonl` in each suite separately records ServerStopped-to-Gradle-exit,
including JFR export and Gradle overhead. Pure save-only and pure JVM-termination times
are not isolated. Cold startup/shutdown groups have three samples; higher-scale fresh
suite startup/shutdown groups have one and cannot establish variance.

## Instrumentation calibration

Matched 16-machine controls removed both JFR and callback mixins. Medians below
are totals over the same 200-tick idle window, n=3.

| Workload | Profiled tick-processing ms | Control ms | Profiled allocation bytes | Control bytes |
|---|---:|---:|---:|---:|
| Mixer ×16 | 81.752 | 76.063 | 39,600,992 | 38,800,712 |
| Fluidizer ×16 | 80.187 | 75.878 | 39,483,120 | 38,607,896 |

The observed allocation increment is about 0.8 MiB per window. JFR also samples
string concatenation in the injected probe exit handler. The tick-processing
increments are approximately 21–28 microseconds per server tick. These are combined
instrumentation estimates, not corrections applied to the published measurements.

Server-thread CPU medians changed from 234.375 to 156.250 ms for Mixer but from
140.625 to 218.750 ms for Fluidizer. The opposite signs, overlapping ranges, background
chunk work and Windows' 15.625 ms CPU-counter granularity preclude a reliable overhead
percentage. Unsupported measurements remain null; a supported zero CPU delta may
still be below counter resolution.

## Investigations, ranked by significance

1. **World/chunk save-future churn and measurement contamination.** The accepted
   minimum Vat first reload allocated about 3.80 GiB; the Reactor first reload about
   4.32 GiB. These are cumulative transient allocations, not retained heap. JFR captured
   524/538 allocation samples and 86/96 CPU samples in those windows. Leading stacks
   contain `CompletableFuture.uniRunNow`, `uniWhenCompleteStage`, `postComplete`,
   `ConcurrentLinkedQueue`, `ChunkHolder.isReadyForSaving` and `ChunkMap` scheduling.
   The pattern occurs in both implementations, outside machine callbacks. Add a
   matched empty-world/chunk control and inspect pending neighbouring-chunk save work
   before attributing full-operation allocations to MobVats. No machine fix is justified.
2. **Longer steady-state idle attribution.** Vanilla `PalettedContainer.pack` dominates
   sampled allocations in later idle windows, while individual machine CPU samples
   are sparse. Extend warmup/measurement, account for JIT tier, repeat controls and
   isolate background chunk serialization. Inspect MobVats-only stacks such as
   `ServiceStaging.save` only if they reproduce with meaningful attributable volume;
   one weighted sample is insufficient evidence of redundant persistence.
3. **Retained-controller restoration and deferred structural work.** The current
   single-chunk eviction destroys all controller parts. Reconnection commonly calls
   assembly rather than `onMachineRestored`. Add a separate cross-chunk partial-unload
   workload, with common chunk boundaries and controller identity checks, to measure
   retained paused-controller restoration. Structural dirty queues are checked now,
   but exact deferred-operation execution counts remain unmeasured/null.
4. **Qualified geometry and network expansion.** Repeat larger specimen/port/storage
   workloads at higher counts and sample assembly compilation stacks. Current minimum
   Vat/Mixer assembly differences are plausible given derived geometry/services;
   they are not evidence for an optimization. Keep productive network, biology,
   recipes and fuel simulation comparisons separate.

## Validation and limitations

`gradlew.bat build` passed; `gradlew.bat runGameTestServer` passed **721 required tests**
and saved all dimensions before normal exit; `git diff --check` passed. The result
validator checked all 578 rows, common runtime settings/ZeroCore hash and all 60
profiled idle call-count invariants. The production JAR was inspected and contains
no benchmark classes/mixins or XR2 classes. The benchmark is a separate Gradle project,
not a production dependency or source set. Existing packaging and AI-placeholder
safeguards are unchanged. No client was launched, and nothing was committed or published.

Failed/unaccepted pilot runs are omitted from accepted-runs.txt. Early fixtures used
an invalid Reprocessor side waste injector and an ambiguous central Sump for multiple
specimens; both were corrected to normal structure rules. A first connected fixture
failed geometry validation and was replaced with valid larger machines. An initial
Reactor eviction trial timed out with a diagnostic capable of chunk acquisition;
non-loading diagnostics and a successful repeated trial superseded it. Overlapping
early pilot runs were also excluded. No production correctness defect was established.

This is an initial baseline: three repetitions, short idle windows, sparse sampling,
JIT effects, background chunk work and probe overhead limit fine comparisons. The
larger/network/Reactor workloads and cold restores were measured at count 1 only.
Save-only timing, worker-thread CPU, exact deferred-job counts, partial unloads,
productive workloads and network packets are not measured. These limits prevent a
claim of full performance equivalence; they do not prevent reproducing the tested
operations and identifying the next measurement targets.
