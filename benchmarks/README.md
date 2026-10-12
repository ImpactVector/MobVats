# XR2 / MobVats headless lifecycle benchmark

This is a separate development mod/project, launched using the parent Gradle wrapper.
It never enters the MobVats source sets, dependency graph or distributable JAR.
It loads **one** machine implementation plus the same published ZeroCore2 artifact.
No acceptance saves, normal `run` configuration, graphical client or upstream checkout is modified.

## Run

From the MobVats mod repository root, Java 21:

```powershell
.\gradlew.bat build
.\benchmarks\runners\run.ps1 -Implementation mobvats -Machines mixer_min,vat_min -Counts 1,4,16 -Repetitions 5 -Warmup 200 -Measure 400 -Cold
.\benchmarks\runners\run.ps1 -Implementation xr2 -Machines fluidizer,reprocessor,reactor -Counts 1,4,16 -Repetitions 5 -Warmup 200 -Measure 400 -Cold
```

Additional MobVats fixtures: `mixer_5x5`, `vat_multi`, `connected_pair`.
Counts through 32 are supported; start below 32 and assess runtime/memory first.
The runner serializes environments with a named mutex. Do not concurrently run other
Minecraft instances, builds, tests or profilers when collecting accepted baselines.
The runner gives every suite a fresh directory and never recursively deletes a save.
Calling Gradle directly bypasses this isolation guard; use a fresh unique `runId`:

```powershell
.\gradlew.bat -p benchmarks runBenchmark -Pimplementation=xr2 -Pmachine=fluidizer -Pcount=1 -Prepetitions=3 -Pwarmup=100 -Pmeasure=200 -PrunId=unique-name
```

Unsupported machines, mismatched implementation names, unformed fixtures and failed
transitions fail the run rather than generating successful timing placeholders.
The Gradle task checks both completion and failure markers after normal server shutdown.

## Fixed environment

Minecraft 1.21.1; NeoForge 21.1.252; ZeroCore2 1.21.1-2.4.18
(`curse.maven:zerocore-247921:6648746`); Java toolchain 21; 2 GiB initial/maximum
server heap. XR2 is the published 1.21.1-2.4.27 artifact
(`curse.maven:extreme-reactors-250277:7293725`). The inspected source revision is
`f6cc65140a8abbfc87137f5c54fc641085f66048`, whose version bump is 2.4.27.
Source-to-published-binary reproducibility has not been established; binary SHA-256
is authoritative for these measurements. No optional operator mods are loaded.
MobVats loads the development artifact produced by the preceding parent build.
Every measurement carries the MobVats HEAD, mod binary hashes, harness source hash,
Java runtime/vendor, JVM arguments, OS, logical processor count and measurement settings.

Creative, peaceful superflat; no structures, random ticks, mob spawning or daylight
cycle. View/simulation distance 6; spawn chunk radius zero; ordinary **20 TPS**.
Each workload unit occupies one forced chunk at `(64 + i*4, 64)`, with origin
`(1024 + i*64, -60, 1024)`. Machines do not cross chunk boundaries. Neighbour chunks
acquired naturally by Minecraft are not artificially suppressed. This does not model
partially loaded machines spanning several chunks; add that as a distinct workload.

## Scenarios and reset

See [scenarios/lifecycle.json](scenarios/lifecycle.json). A disposable formed fixture
first warms the process for the configured warmup duration, then is cleared before
measured repetitions begin. Each suite repeats block
placement/assembly, warmup, inactive idle, a synchronous activate/deactivate pair,
shell break/repair, genuine unforce/unload, and reload. Before the next repetition,
the fixture volume is cleared and allowed to settle for 20 ticks, then rebuilt.
The workload contains no external third-party conduits and no supplied processing
resources. `connected_pair` is separately labelled and contains two connected machines
per workload unit; its count must not be interpreted as individual machine count.

Unload completion requires a Minecraft `ChunkEvent.Unload`, an absent full chunk
and removal of the old controller block entity. The benchmark does not manually call
ZeroCore pause/restore hooks. Reload begins before forcing/loading the chunk and ends
after 20 consecutive ticks of valid assembly. MobVats additionally requires its actual
derived assembly and empty structural dirty queues. `transition_observed_ms` records
the first ready observation; `settling_ms` separates the remaining settling window.
Readiness diagnostics during transitions are MobVats-only and add measurement cost.
Idle uses the same simple assembled/inactive checks in both implementations.

`-Cold` copies the saved suite world/config into a fresh directory **for every cold
repetition**, then launches a fresh server process with `phase=cold`; it does not reuse
the world changed by a previous cold run. `cold_restore` measures chunk acquisition
and persisted machine restoration after `ServerStartedEvent`. The separately recorded
`jvm_start_to_server_started` includes launcher/mod/world startup, uses JVM uptime,
and ends after benchmark setup/JFR initialization. It is not pure world load cost.
Its server CPU/allocation fields cover only the server thread's lifetime, whereas
startup includes main/worker-thread work. Profile recordings start at ServerStarted,
so early launcher/world startup is not in those JFR files.

`shutdown_to_server_stopped` includes the actual server save/unload sequence through
`ServerStoppedEvent`, before JFR dumping. `process.jsonl` separately records the time
from that event to Gradle completion, including JFR dumping/JVM exit/Gradle overhead;
it is explicitly **not** a pure JVM-termination measurement. Save-only duration is
not isolated in this slice; consult profiler/log evidence before attributing it.

## Measurements and overhead

Whole-operation wall time, server-thread CPU/allocated bytes, aggregated Pre-to-Post
server tick processing time, global GC count/time and callback counts/time are recorded.
Idle's wall time includes deliberate tick pacing; use CPU, tick processing, allocations
and controller callback totals to investigate idle overhead. Normalize callback totals
by measured ticks and workload count, with connected-part counts as structural context.
Windows thread CPU counters are quantized; zero may mean below counter resolution.
Allocation volume includes framework, vanilla world work, probes and the runner's
verification; it is not an exact attribution to machine callbacks.

Shared development mixins time controller `onMachineAssembled`, `onMachineRestored`,
`onMachinePaused`, `onMachineDisassembled`, `onPartAdded`, `onPartRemoved`, `updateServer`
and ZeroCore `attachPart`, `detachPart`, `checkIfMachineIsWhole`, `updateMultiblockEntity`.
They observe ordinary invocation and do not substitute controller implementations or
change machine state. Callback durations are **inclusive and nested**; do not add them
together or subtract them from full operations as though they were disjoint.
Normal-return callbacks are measured; exceptional exits invalidate the suite.
The same hook set is installed in both environments, with absent implementation
classes expected in mixin warnings. JFR's `profile` configuration provides common CPU
and allocation sampling. Exact asynchronous/deferred operation counts remain nullable;
queue emptiness is checked but must not be presented as an execution count. Whole-chunk
eviction can destroy every controller part; the subsequent controller may assemble
instead of restoring a retained paused controller. Actual callback counts distinguish
these paths. A cross-chunk partial-unload workload is still needed for that latter path.

Repeat matched runs with `-NoProbes -NoProfile` to estimate combined instrumentation
cost. Separate `-NoProfile` and `-NoProbes` runs can distinguish components. Never
compare a probed build directly against an unprobed one. Probes themselves allocate,
and JIT/GC/background variability may exceed their measured effect at small scales.

## Outputs

Each run lives under `.cache/<implementation>/<runId>/`: `results.jsonl`, `profile.jfr`,
save/config, completion marker, server logs; the runner retains the Gradle log beside it.
Generated worlds and recordings are ignored. Reduce only accepted, completed runs:
The row contract is [scenarios/result-schema.json](scenarios/result-schema.json);
unmeasured network and deferred-operation fields remain null.

```powershell
python benchmarks/runners/summarize.py benchmarks/.cache/mobvats/SELECTED-RUN benchmarks/.cache/xr2/SELECTED-RUN --output benchmarks/results/baseline
jfr print --json --events jdk.ExecutionSample,jdk.ObjectAllocationSample benchmarks/.cache/mobvats/SELECTED-RUN/profile.jfr > benchmarks/.cache/samples.json
```

Use a real Python 3 executable, rather than the Windows Store alias, if necessary.
The reducer writes raw JSONL/CSV, summary JSON and Markdown; groups instrumentation
settings and harness hashes separately; reports n, median, min, max, sample standard
deviation and median absolute deviation. Missing values remain unavailable, not zero.
Do not pool pilot runs that overlapped or differed in settings/harness revisions.
Recordings/logs remain local and are referenced by the baseline report.

Chunk/world operations share underlying mechanisms but include structural differences.
Assembly/reassembly/idle/attachment require part-count and geometry qualifications.
Connected routing and productive Vat/Reactor or Mixer/Turbine simulations are not
like-for-like contests. Packet counts, payload bytes, recipients/coalescing and client
interpolation are reserved for a later slice; the schema's `network` field is null.
No callback/request count is a wire packet count.

Production validation remains `gradlew.bat build`, `gradlew.bat runGameTestServer`,
and `git diff --check` from the parent repository. The separate benchmark project cannot
be included in the parent JAR without an explicit build change. Existing AI-placeholder
release safeguards remain intact; these are local development artifacts only.

The `connected_pair` fixture now places universal Fluid Ports, Fluid Conduits and Fluid Buffers with explicit `vat_t1_casing`/`mixer_t1_casing` material states. Geometry, TAP mode, endpoint settings and lifecycle measurement boundaries are unchanged. Old material-specific block IDs are not used.
