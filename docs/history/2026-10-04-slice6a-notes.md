> Historical record. Original: `SLICE6A_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
MOBVATS SLICE 6A â€” CONDUIT BLOCKS AND TOPOLOGY SEMANTICS
Implemented October 4, 2026. No Minecraft client was run.

1. Registrations
---------------
Six ordinary blocks with matching BlockItems, registered by ConduitContent:
  mobvats:item_conduit
  mobvats:glass_item_conduit
  mobvats:casing_item_conduit
  mobvats:fluid_conduit
  mobvats:glass_fluid_conduit
  mobvats:casing_fluid_conduit
All appear in the MobVats creative tab. There are no conduit BE registrations,
inventories, fluid tanks, capability providers, scheduled ticks or transport code.
Shared code lives in gamecontent/multiblock/conduit. Vat and Mixer keep their
individual entity and controller classes.

2. Mode and interaction
-----------------------
ConduitMode implements StringRepresentable. ConduitBlock.MODE is an EnumProperty
with serialized values trunk / tap; newly placed blocks default to TRUNK.
Empty-hand right-click deterministically toggles TRUNK/TAP on the logical server,
syncs the blockstate, reports the mode and queues the containing controller rebuild.
Held-item interactions bypass the toggle so normal BlockItem placement can proceed.
No wrench dependency or GUI. Minecraft blockstate save/load preserves the mode;
breaking a conduit yields its ordinary BlockItem, subsequently placed in TRUNK mode.

3. Structural validation and ZeroCore integration
------------------------------------------------
Bare: valid only in interior cells. Glass cladding: exactly the containing machine's
ordinary glass rules. Casing cladding: exactly ordinary casing rules, including
horizontal edges and corners. Cladding is a registered block identity, not a renderer
inference; changing MODE never changes the structural variant.
Vat glass currently permits sides, vertical edges and non-edge top-face positions;
it does not permit the bottom face or corners. Mixer glass permits sides and vertical
edges. Cladded conduits are not allowed in the interior.

ConduitCuboidController extends the existing ZeroCore cuboid controller. Both
MultiblockVat and MultiblockMixer derive from it. Non-BE validation hooks accept
only the appropriate conduit identity at each position; all other requirements,
cuboid sizes and one-specimen Vat rules remain in force.
The old 34-part threshold counts passive conduit cells as well as real BEs; full
cuboid validation still checks every shell cell. Bounds include cladded structural
cells and are cached until structural changes. Minimum-size 3x4x3 Vat and Mixer
fixtures with almost entirely passive conduit shells are tested.

ZeroCore's default attachment registry callback and disconnection traversal use
six physical BE neighbors. Part attachment callbacks and the controller traversal
now also follow bounded passive structural bridges. Structural connectivity is
independent of ITEM/FLUID; resource topology remains strictly separate. There are
no synthetic BEs and no per-tick structure/path searches. Loaded-chunk checks and
maximum geometry bounds constrain structural traversal.

4. Graph compiler
-----------------
ConduitTopology.compile scans only the containing controller bounds. It walks
same-resource cardinally adjacent conduits with an iterative queue and visited set.
Both modes traverse same-resource neighbors; cladding does not change connectivity.
TRUNK never attaches endpoints. TAP attaches only explicitly supplied compatible
adjacent endpoints. The actor must also touch a TAP; an endpoint-side TAP alone
cannot turn an actor-side TRUNK into a connection. Loops and branches are legal.
Endpoint lists merge direct and conduit connections, deduplicate by BlockPos and
sort by X, then Y, then Z. No directional axes, pipe flow or per-tick pathfinding.
Visited nodes and queues are discarded after compilation. Retained component
summaries contain an origin, resource, mode counts and real endpoint/actor positions,
not node graphs or conduit paths.

5. Explicit compatibility
-------------------------
Vat Item network: Item Input/Output Ports; physically valid Tool Port relationships
as actor-side item endpoints, including empty tool-loading and displaced valid actors.
Vat Fluid network: Fluid Input/Output Ports; physically valid Tool/Blender/Sewer
relationships as actor-side fluid connections. Blender and Sewer never claim items.
Heads, Flesh, Harnesses, structural Drains, glass and casing are not endpoints.
Mixer Item network: plant Item Input and Controller actor-side connection.
Mixer Fluid network: Fluid Input/Output and Controller/Intake actor-side connections.
Descriptors come only from parts owned by that controller, and positions must lie
inside its bounds. An exterior conduit run cannot attach another machine's ports.
Existing exposed port capabilities remain the only cross-machine transfer mechanism.

6. Actor expansion and process behavior
--------------------------------------
VatTopology.Assignment now carries itemInputs, itemOutputs, fluidInputs and
fluidOutputs. Valid direct adjacency is preserved and conduit endpoints are added.
Empty Tool Ports receive expanded loading routes. CowProcesses uses those flattened
real item endpoints for tool acquisition and bucket output; it never visits conduits.
Current Vat fluids and Mixer inputs/outputs still use their controller-owned shared
stores. New fluid arrays describe actor endpoint connectivity and do not introduce
fluid movement or change Mixer recipe selection/transactions.
Tool/Blender specimen LOS, actor priority and Sewer's physical adjacent Drain
relationship remain required before actor TAP attachment. Conduits do not route
Tool-to-Cow, remote Sewer-to-Drain, Harness-to-Head or Item Input-to-Cow feeding.
Direct feed contacts and vat-level Feeding/Culture Slurry behavior are unchanged.

7. Reserved versus generic Item Outputs
--------------------------------------
Every physically valid actor-local item relationship reserves its expanded outputs,
including empty loading and actors displaced by a higher-priority claim. Reserved
outputs are excluded from generic controller kill-buffer draining. Unclaimed Item
Outputs retain their previous generic role. Mode changes rebuild classification.
An invalid physical actor cannot reserve outputs merely by touching a TAP.
No item output filters or port inventory expansion were added.

8. Rebuild triggers and lifecycle
--------------------------------
Assembly/restoration compiles graphs and active routes. Part/port/tool changes retain
their existing controller rebuild behavior. Conduit placement/removal or structural
replacement enqueues nearby containing controllers into ZeroCore's dirty-controller
revalidation lifecycle; invalid structures clear topology. Empty-hand mode changes
on formed machines recompile topology without revalidating the shell.
Notifications are coalesced per controller and processed at LevelTickEvent.Pre,
ahead of ZeroCore's normal registry update. The metadata queue is synchronized for
separate client/integrated-server threads; actual controller work stays on its level
thread. Weak references avoid retaining unloaded controllers/worlds.
Normal resource processing performs no conduit discovery or resource transfers.
ZeroCore revalidation is queued rather than synchronous inside block removal.

9. Recipes, models and diagnostics
----------------------------------
Six shaped/shapeless recipes, six self-drop loot tables, six two-mode blockstates,
twelve block models and six item models; translated block names and toggle feedback.
Bare recipes: two iron ingots around one hopper (Item) or copper ingot (Fluid),
yielding four conduits. Cladding recipes: one bare conduit plus one vat_glass or
vat_casing yields one matching cladded conduit. Costs are provisional. Self-drops
reclaim the whole variant; no separate uncladding recipe was added.
Placeholder cube models use built-in iron/copper/glass and existing native casing
materials. TRUNK caps use hopper/prismarine; TAP caps use gold. Glass-clad models
use cutout rendering. No new raster artwork or imported historical casing art.
Bare placeholders have no collision/occlusion to avoid adding conduit obstruction
before 6B/6C. Cladded placeholders retain ordinary full-block collision. Geometry,
formed casing/glass skin integration, pipe fittings and service anchors are deferred.

Vat controller Diagnostics lists Item/Fluid component counts, TAP/TRUNK totals,
per-component input/output/actor counts, actor item/fluid route counts and existing
reserved/generic output and unassigned/blocked explanations. Component summaries
are capped at 32 rows. Mixer empty-hand part status appends the same component
summary. No new GUI or retained full graph editor.

10. Verification
----------------
Required: gradlew.bat build and gradlew.bat runGameTestServer, using existing offline
Gradle cache. Packaged model/atlas/material verification also passes; seven preserved
artwork hashes remain unchanged. No client/smoke/reload tasks were launched.
Final run: build passed and all 71 required headless GameTests passed (57 retained
plus 14 new). Test log: run/logs/latest.log, October 4, 2026 at 08:50:02.177.

14 new autonomous GameTests in ConduitGameTests, covering the handoff's 30 checks
along with retained Slice 1â€“5 tests:
- All six variants form; bare Item/Fluid interior valid; no BEs or capabilities.
- Invalid bare wall / glass corner rejected; casing corner and BE-less repair valid.
- Exact Vat and Mixer cladding rules, including top/bottom and required edges.
- TRUNK/TAP traversal and endpoint attachment at both actor and endpoint sides.
- Resource-family isolation, loops, branches, deduplication, deterministic order,
  direct/conduit union, explicit endpoint compatibility and machine bounds.
- Real remote Item Input loads Bucket; remote Item Output receives Milk Bucket;
  actor Fluid Input/Output lists expand through separate Fluid conduits.
- Reserved output excludes kill drops; generic output still drains them.
- Mode interaction recompiles and reclassifies outputs; blockstate NBT roundtrip.
- Invalid tool reach cannot create routes, reservations or remote feed contacts.
- Blender still wins and valid displaced Sword retains reserved Item Output.
- Sewer expands fluid endpoints but still requires physical adjacent Drain.
- Minimum Mixer passive shell preserves bounds, plant/fluid endpoint compilation.
- Minimum Vat passive shell retains strict required Flesh validation.
- Solid specimen LOS remains mandatory; bare placeholder obstruction is deferred.
- Separate Vat/Mixer remain formed while exterior run cannot cross-connect endpoints.

The repair test awaits queued ZeroCore revalidation instead of assuming a fixed
short tick deadline. Existing Mixer/Slurry and all prior tests remain enabled.

11. Before 6B / manual acceptance
--------------------------------
No final narrow pipe geometry, glass/casing embedded pipe rendering, nipples,
elbows, serviceConnectionAnchor, interactionOrigin, collision/intersection,
Blender obstruction, output filters or multi-specimen changes are present.
6B should replace placeholder cubes/collision with the agreed service geometry,
reuse controller-compiled topology semantics and keep resource discovery out of
rendering and process ticks. Fluid routes must continue respecting shared controller
storage; introducing route-specific fluid transactions would be a separate change.

User manual checks (client acceptance was not performed):
- Craft each bare/cladded variant and inspect item/block models for missing textures.
- Place bare Item/Fluid conduits internally; verify shell use fails validation.
- Replace legal glass wall positions with glass-clad conduit; verify formation.
- Use casing cladding on edges/corners and a run turning bottom-to-side.
- Empty-hand toggle TRUNK/TAP and inspect changed cap/status feedback.
- Confirm TRUNK ignores adjacent endpoints; TAP at both actor/port ends claims them.
- Supply Bucket remotely, confirm Tool loads it and local reserved Output receives
  Milk Bucket. Confirm generic Output drains kill drops and reserved Output does not.
- Remove/restore a cladded shell cell; verify disassembly and queued recovery.
- Save/reload and verify mode state. Confirm direct feeding still works.
- Confirm unrelated neighboring machines do not gain conduit resource connections.
- Final visual/collision acceptance awaits 6B/6C geometry.

Changes remain local; no commit, push, publication or release was requested for 6A.
```
