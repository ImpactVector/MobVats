> Historical record. Original: `SLICE6C_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
MobVats Slice 6C — Physical Obstruction and Route Validity
October 4, 2026

1. Shared route intersection
PhysicalRoute stores an immutable polyline and conservative thickness radius.
RouteIntersection tests every polyline leg against inflated conduit-volume AABBs.
The first intersection returns conduit resource, owner coordinates, and whether
it came from an endpoint fitting. Stable geometry ordering selects the blocker.
The intersection layer has no world queries or renderer dependencies.

2. Geometry representation
Checks use Slice 6B ConduitGeometry pipeSegments and endpoint-fitting volumes.
They never substitute the whole conduit block cell or its glass/casing shell.
The controller adopts the exact geometry baked by the topology compiler, avoiding
a second geometry/world scan. Bare, glass and casing use their compiled volumes.
No conduit-vs-conduit invalidation or specimen-body collision was introduced.
Head/Flesh occupancy remains enforced by existing structure validation.

3. Harness behavior
VatPhysicalState uses the existing HarnessStraps curves, anchors, sag, segment
count and thickness. Any required strap intersection marks that head inactive.
The vat stays formed. Milk, replacement growth, feed, harvest, waste progress,
and actor execution pause for the affected head; persisted process state survives.
Controller support-fluid consumption stops when every head is inactive.
Already committed machine-wide kill-buffer draining remains available.
Straps remain visible; the specimen does not tick or track the camera while inactive.

Current geometry limitation: all present strap paths lie within the mandatory
Harness/Head/Flesh column. Ordinary conduit placement there fails existing structure
validation before route compilation. Validation was not weakened. The obstruction
and process-pause cases therefore use explicit narrow-geometry fixtures in tests;
nearby actual world conduits are also tested. A practical manual strap-crossing
fixture requires a future layout with straps extending outside that column.

4. Tool behavior
Cardinal orientation, compatibility and structural reach/LOS remain prerequisites.
The interaction origin to specimen contact corridor then undergoes the shared
physical test. Blocked tools retain service plumbing but lose claims, loading
relationships and route-specific output reservations. Their presentation remains
unassigned and cannot animate as an operating tool.

5. Feed behavior
VatInteractionGeometry supplies the same origin, facing-dependent head target,
12-segment curve, 0.08 sag and 0.09 width to rendering and obstruction compilation.
Blocked direct Item Input relationships are omitted from itemContacts, so wheat
cannot be consumed through them. Their feed tube is omitted from presentation.
Unblocked relationships resume after rebuild.

Slice 5 Feeding Slurry is currently a controller-wide budget with no distinct
physical endpoint or tube. This slice preserves that existing behavior instead
of inventing a mandatory Item Input for slurry. Harness-inactive heads cannot
consume slurry. A future slurry delivery origin needs its own compiled route.

6. Blender behavior
Blender uses its centered interaction origin and specimen contact corridor.
Blocked Blender candidates are removed before HARVEST claim resolution. Clear
Sword candidates can win; clearing the Blender corridor restores its priority.
Blender currently has no separate extension animation to suppress.

7. Candidate ordering
Compatibility and structural reach/LOS select candidates and service endpoints.
Conduit topology and occupied geometry compile next, then harness validity and
actor physical clearance filter candidates. Priority resolution runs only on
physically valid candidates. Outputs are reserved for surviving valid relationships,
including valid displaced actors and loading relationships. Blocked actors may
still have valid service connections; service connectivity does not imply a claim.
No rejected assignment/alternate endpoint arrays are retained for display.

8. Self-connection exception
Only endpoint-fitting geometry whose endpoint position equals the relationship's
own endpoint is skipped. Its pipe bodies are still tested. Foreign fitting volumes
are always tested, including nearby service fittings of the same resource.
Harness routes have no service-fitting ownership exemption.

9. Diagnostics/status
VatPhysicalState caches compact per-part/head reasons and an inactive-head set.
Reasons include Strap / Tool Port / Feed route / Blender route, part coordinates,
Item or Fluid Conduit, blocker coordinates, and associated head coordinates.
Controller snapshots and Tool Port status expose blocked and inactive states
alongside existing assignment, waiting, tool/input/output statuses. Presentation
carries the server-authoritative operational flag and valid relationship links.

10. Rebuild triggers and performance
Existing controller assembly/revalidation and conduit dirty queues rebuild after
conduit addition/removal, cladding/state or TAP/TRUNK edits, actor/endpoint edits,
and relevant structure edits. An in-place Head-facing edit schedules one block
tick after the edit, then requests controller compilation if the compiled facing
changed. This allows vanilla to finish updating the retained block entity first.
No recurring route checks, tick world scans, renderer intersections, or rerouting
were added. Process execution consults cached operational/assignment state.

11. Tests/results
Commands: gradlew.bat --offline build; gradlew.bat --offline runGameTestServer.
Build includes packaged render-resource checks and seven preserved texture hashes.
Full headless suite: 88 required tests (78 existing, 10 new), all passed.
Server shutdown completed normally. No client tasks were run.

PhysicalRouteGameTests covers:
- narrow crossings versus nearby clearance for all three claddings;
- own-fitting exemption, foreign fittings, and non-exempt pipe bodies;
- actual Tool blocker addition/removal, diagnostics and inactive presentation;
- blocked Blender fallback to Sword, clear priority recovery and real harvesting;
- curved feed clearance, blocker addition/removal and real wheat consumption;
- real Tool/Feed/Blender TAP fittings remaining clear;
- TAP/TRUNK changes rebuilding geometry and physical results;
- rendered harness anchors, crossing/nearby fixtures for all three claddings;
- Head-facing edits rebuilding target geometry and cached obstruction;
- cached harness obstruction pausing milk/growth/wheat/slurry/harvest, retaining
  progress, then restoring operation after recompiling actual clear world geometry.
The harness process fixture injects a cached test topology via test-only reflection;
production code has no test hooks. One old 6A placeholder assertion was updated:
a conduit intersecting the Tool arm now must reject it rather than remain ignored.

12. Render/obstruction approximation
Conduit volumes and cable curves are shared with rendering. Square cable thickness
uses its circumradius; inflated AABBs conservatively approximate swept volume and
can reject marginal corner clearance. Tool and Blender use a fixed 0.075-radius
corridor across the full extension path, conservatively covering the arm. Individual
held-item meshes and animation poses are not tested. No specimen body, rendered
mob, player, or entity collision checks are part of this slice.
Existing solid-block structural LOS checks remain separate and unchanged.

13. Before multi-simulacrum work
Define any separate slurry delivery origin and compile that physical relationship.
Provide strap layouts outside the mandatory specimen column if real conduit/strap
intersections need to be constructible. Replace current Cow dimensions with per-
specimen dimensions when additional entities are supported. Decide whether future
moving tools need a larger swept envelope than the current straight corridor.
Validate diagnostic ordering/display capacity with multiple heads; no scheduling,
rerouting, alternate anchors, filters or external pipe network features were added.

Manual acceptance checklist (user only)
- Tool: place a narrow conduit across the arm corridor; verify blocked status,
  no active animation/claim, and recovery after removal or moving it aside.
- Feed: block the curved tube; verify no wheat consumption and omitted tube;
  clear it and verify feeding resumes. Check different Head facings.
- Blender: obstruct Blender while Sword stays clear; verify Sword harvests.
  Clear Blender and verify it regains HARVEST priority.
- Geometry: compare visible narrow pipes/fittings with diagnostics; nearby pipes
  should remain valid. Check bare/glass/casing variants and TAP/TRUNK changes.
- Harness: nearby conduits should leave the specimen operational. The current
  mandatory column prevents a valid direct crossing placement; do not replace
  Head/Flesh merely to force this case. Crossing/pause behavior is fixture-tested.

No publication, release, or client launch. Manual client acceptance remains pending.
```
