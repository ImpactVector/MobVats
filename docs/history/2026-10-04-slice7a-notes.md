> Historical record. Original: `SLICE7A_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
MobVats Slice 7A — Generic Processing Engine Refactor
October 4, 2026

Scope
Behavior-preserving refactor of the current vat process subsystem. No new species,
juvenile/adult lifecycle, dairy specialization, scheduling expansion, gameplay
balance, actor priority, topology, rendering geometry, or conduit rules were added.
Authored code is under src/main/java/net/impactvector/mobvats/gamecontent/multiblock/
vat/process. Animal Pen was an architecture reference only; no code was copied:
https://github.com/BONNePlayground/AnimalPen/tree/1.21
https://github.com/BONNePlayground/AnimalPen/wiki/Animal-Interaction-Format
The useful separation is definition, requirements, consumption, timing and effects.
Animal Pen population rules, formulas, feeding and attack timing were not imported.

1. Generic abstractions
ProcessDefinition: immutable stable ID/state ID, species predicate, execution stage,
requirements, progress policy, backpressure policy, operation and start/finish hooks.
ProcessRequirement: typed state/phase, item/tag predicate, actor input, fluid amount,
XP, active actor use and held-tool predicate checks. Failed checks never consume.
ProcessInput/ProcessOutput: private item/fluid/XP consumption and item/fluid/buffered
output plans. ProcessTools: atomic acquisition and copied tool transformation.
ProcessEffect: state start/reset, metrics, XP and cosmetic tool interaction hooks.
ProcessResult: eligibility/waiting/blocked/ready/running/committed reasons plus a
changed flag. ProcessState and SpecimenProcessState provide persisted generic state.
ProcessContext provides runtime objects and cached already-resolved relationships.
ProcessTransaction composes small reversible resource steps.

2. Scheduler ownership and execution
MultiblockVat owns VatProcessEngine; updateServer invokes this single executor.
CowProcesses no longer has tick, acquisition, production or harvest execution.
The engine iterates specimens in stable BlockPos order and definitions by stable ID.
Timers run each active tick. Production/actions retain the ten-tick cadence.
Machine-wide kill-buffer export still precedes per-specimen cadence actions.
Physical-invalid heads pause through cached topology.operational; unformed vats
still do not advance. The controller support budget runs once per active registered
profile, preserving the single Cow profile's shared Culture Slurry budget.

The engine consumes compiled actor assignments; it does not resolve actor priority.
Optional definition use exclusivity prevents duplicate successful operations for
one use during one stage. A blocked/ineligible operation does not reserve the use.
This is process execution exclusivity, not a second topology arbitration system.

Tool acquisition/transformation can legitimately change topology during a cadence.
The engine refreshes runtime contexts only when the topology revision changes, then
continues the remaining definitions with current claims. This preserves immediate
same-cadence use of newly loaded tools rather than adding a ten-tick delay.

3. Registry/provider
MobProcessRegistry is an in-code keyed provider using mob ResourceLocation identity.
Immutable profiles sort and partition definitions into TICK/CADENCE lists once.
Duplicate species/process IDs and registration after freeze are rejected.
The default registry registers CowProcessDefinitions. No datapack/config loader,
expression language, reflective discovery or per-tick scan of unrelated mobs exists.
The controller has no new species switch for executing processes. Existing Cow-only
specimen construction/validation remains outside this processing refactor.

4. Runtime and persisted state
SpecimenProcessState holds a sorted map from stable process ID to ProcessState.
ProcessState supports IDLE, RUNNING, READY and BLOCKED; elapsed/remaining progress;
lightweight CompoundTag data; and prepared output/tool snapshots for pending work.
Instant actions do not need to enter a timed lifecycle. Passive timers, replacement
progress and cooldown-like waits use the same small timing policy. Definitions can
share a state ID while separating start, ticking and completion operations.
No live world, entity, controller, handler, profile or runtime context is serialized.

5. Atomic transaction model
ProcessTransaction validates every step before applying any changes. Steps carry
resource identity, precondition, apply and undo. Failed application rolls back in
reverse order, including the failing step; an exception rolls back before rethrow.
Transactions are single-use. Multiple changes to one resource must be combined into
one step so independently prepared plans cannot overwrite one another.
Supported owned-resource steps cover one-slot items, named fluid tanks, XP, tool
mutation, process phase/progress and authoritative kill buffering.

Sword loot/tool hooks are prepared on copies before buffer/XP/tool/state commit.
Generic item acquisition, direct wheat consumption, slurry feeding, passive milk,
Waste, Blender and bucket conversion use these resource steps. Effects/metrics run
after successful commit. Generic failed commits do not record completion effects.
Existing VatItemTransaction retains its tested multi-output staging plan for bucket
routing. Milk-bucket routing failure intentionally leaves the converted bucket in
the Tool Port; that is its existing successful transformation/backpressure policy.

Boundary: these transactions support private controller-owned resources on the
server thread. They do not promise rollback for arbitrary external capabilities,
third-party event side effects or arbitrary user callbacks. Existing combat hooks
still execute during copy preparation, preserving existing behavior. Effects should
remain non-failing post-commit state changes/notifications. External transport and
Mixer recipe execution remain on their existing transaction paths.

6. Actor/tool/input/output abstractions
ProcessContext caches assembly/head identity, per-use actor lists, loading claims,
resolved item endpoints, fluid endpoints and tool entities from compiled topology.
It exposes server level, controller buffer facade, metrics, engine XP and process
state without serialization. Item/feed requirements use valid direct feed endpoints;
actorItem requirements address a selected use's resolved actor inputs. Fluid recipes
refer to Fluid identities and ProductBuffers.Buffer keys, not scheduler branches.
Tool predicates and transformation strategies support reusable tools, containers,
durability/breakage and retained results. Definitions own their specific predicates.
Loot is a prepared-output strategy; the scheduler does not know the victim species.
The current CowHarvestLoot helper and its loot-table/combat behavior are unchanged.

7. Backpressure
Definition policies are NONE, OUTPUT_CAPACITY, SHARED_ITEM_BUFFER and
RETAIN_TOOL_RESULT. Shared-item backpressure is checked by the engine; capacity
and retained-container policies are realized by their transactional output plans.
Sword remains gated by the machine-wide kill backlog. Blender remains independent
of that backlog and requires MEAT capacity. Milk production/Waste wait on capacity.
Milk buckets can remain resident when outputs are blocked. Existing Mixer output
backpressure and input-consumption rules were not changed.
Generic output plans reject incompatible/full storage and reconnection overflow
before addition, preserving committed contents without integer wraparound.

8. Cow migration and preserved ordering
Eleven definitions express nine existing behaviors:
00 replacement progress: 1200 ticks, only RUNNING, Culture acceleration retained;
01 passive milk timer: 1200 ticks, independently accelerated by Culture;
02 Waste timer: 1200 ticks, requires Drain/Sewer claim, unaccelerated;
10 passive milk output: 1000 mB, resets only after successful production;
20 Feeding Slurry: unchanged cost/budget, starts one replacement before wheat;
21 direct wheat feed: one wheat, starts replacement atomically, same metrics/source;
30 Sewer/Waste output: 250 mB, claim required, resets only after successful output;
40 tool acquisition: READY weapon precedence, then independent bucket acquisition;
50 Blender: topology-selected HARVEST, 1000 mB MEAT, unchanged XP/cycle reset;
51 Sword: topology-selected weapon HARVEST, same loot/durability/kill buffer semantics;
60 bucket: same milk cost, tool conversion, animation and output routing.

Culture Slurry's existing controller support-credit helper is called by the Cow
profile support policy. Credit persistence, cost, budget and 4x rate are unchanged.
Feeding Slurry retains the existing portless controller-budget semantics; it does
not gain a new physical delivery requirement in 7A. Harness-invalid heads cannot
consume either feed route or make progress. No second Cow execution path exists.

9. Remaining Cow-specific code
CowProcessDefinitions: balance, IDs, use requirements and intentional action order.
CowProcessOperations: original loot/combat hooks, bucket strategy and acquisition
precedence. These are species/process strategies rather than scheduler branches.
CowHarvestLoot and CowBalance remain unchanged. CowProcesses is now a thin legacy
NBT and GUI snapshot adapter. ProductBuffers retains the existing named products and
export arbitration, while exposing typed tank keys for generic recipes.
The existing controller fluid/support compatibility helpers remain callable, but
production execution uses the generic definitions. Existing GUI labels/status logic
consume the generic engine's compatible state snapshots; no wholesale GUI rewrite.
Generic evaluation reasons are available through processEngine.status(head).

10. Save/reload migration
FullSync adds specimenProcesses: ordered head entries with a processes compound
keyed by stable process ID, plus processXp. Existing cowProcesses/cowXp are also
written for compatibility. Cow replacement RUNNING maps to legacy GROWING.
Milk/Waste/replacement progress and pending loot/tool snapshots round-trip,
including blocked/pending process state through the compatibility adapter.
When both formats exist, legacy Cow channels remain authoritative on load; additional
generic process IDs for retained heads survive. This preserves existing world fields
and intentional legacy-field edits used by existing GameTests. Legacy-only saves
migrate cleanly; generic-only saves also load. No current-world incompatibility is
introduced. Future definitions should not edit only the generic Cow channels while
leaving conflicting legacy values; retire the dual-write adapter deliberately later.

Milk/product buffers, Culture credit, Feeding Slurry, kill outputs, metrics and Tool
Port persistence remain in their existing controller/entity save paths. Compact
network cowStatus snapshots and GUI labels remain compatible; no large generic
runtime contexts are sent to clients. XP remains controller-wide, saturating on gain.

11. Performance
Species lookup, definition partitioning, endpoint caching and strategy creation occur
on topology revision/assembly/load changes. Normal ticks traverse cached contexts
and relevant stage lists, perform scalar timer work and read cached assignments.
There is no reflective runtime discovery, topology graph traversal, conduit test or
external capability scan in normal scheduler execution. The support result map and
process-use set are reused. Common unchanged result objects are cached. Preparation
copies/transaction plans are allocated only for cadence actions that need them;
changed timer results and status updates remain small bounded runtime objects.
No multi-simulacrum scheduling features were introduced.

12. Verification
Commands: gradlew.bat --offline build; gradlew.bat --offline runGameTestServer.
Build passed, including model/atlas references and seven preserved texture hashes.
All 100 required autonomous headless GameTests passed: 88 existing + 12 new.
Server shutdown completed normally. Existing behavioral assertions were preserved.
One 6C feed-obstruction fixture intermittently checked during queued revalidation;
it now waits for formed route states between edits and retains its blockage,
20-tick no-consumption, presentation and recovery assertions.
No runClient, runClientSmokeTest, runClientReloadTest or manual UI testing ran.

GenericProcessGameTests covers all fifteen requested engine topics across twelve tests:
- species registration/resolution, frozen registry and deterministic definition order;
- failed condition, exact/tag items, fluids, XP, actor and held-tool requirements;
- timed progression, start/finish hooks, instantaneous actions and generic metrics;
- successful atomic item/fluid/XP/state commit and single-use behavior;
- false-return rollback, stale-plan rejection and no consumption on blocked output;
- thrown-operation rollback and omission of completion metrics;
- generic phase/progress/data/pending-item/tool state persistence;
- generic-only engine/XP reload and READY-at-duration transition dirty marking;
- legacy Cow migration while retaining extra generic IDs, and legacy-only reload;
- normal ticks preserving topology revision/geometry identity/context compilation count;
- process-use exclusivity without redoing actor priority;
- full/incompatible output and committed reconnection-overflow preservation.

The unchanged 88-test suite covers Cow milk/buckets/wheat/slurries, replacement and
Culture 4x timing, Sword loot/durability, kill-buffer semantics, Blender priority/MEAT,
Sewer/Waste, fluid routing/arbitration, metrics, status, save/reload/reconnection,
physical obstruction and previous Slice 1–6C structure/conduit behavior.

13. Before Slice 7B
No blocker identified for changing the current Cow lifecycle through definitions and
state transitions. Preserve the atomic state/output boundaries when adding the next
lifecycle. The present replacement interval intentionally still includes its old
semantics; milk is not suppressed based on juvenile/adult state in this slice.
Plan retirement of dual Cow/generic save fields explicitly when lifecycle keys change.
New species later require extending current specimen validation/render dimensions
and product definitions separately; registry extensibility does not add those mobs.
Conduit/strap and separate slurry-origin limitations from 6C remain unchanged.

Manual checks (user only)
Compare against the current world: passive milk timing, bucket conversion/routing
and retained results; wheat versus Feeding Slurry precedence; Culture acceleration;
Sword loot, durability and buffered-output backpressure; Blender priority/MEAT;
Sewer/Waste and fluid exports; controller statuses, tool pulses and conduit validity.
No new gameplay or visible rendering behavior is intended. Visual acceptance remains
pending. No client was launched and nothing was published or released.
```
