> Historical record. Original: `SLICE7B_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
Slice 7B — Passive mob definitions and lifecycle (October 4, 2026)
================================================================

1. Schema
PassiveMobDefinition is a typed Java record containing entity, Feed (item IDs or
#item tags plus count), juvenile_growth_ticks, SecondaryOutput list, Harvest,
and Culture/Feeding Slurry support/cost. SecondaryOutput contains process ID,
buffer, fluid ID, amount, interval_ticks, requires_stage (ADULT/JUVENILE/ANY),
actor_use, accelerated, metric, and optional Container input/output/amount/use
and acquisition priority. Harvest contains explicit strategy IDs, Blender amount/
XP, actor_use and acquisition priority. No Java class names or executable scripts.
Record lists and published registry maps are immutable. Validation checks required
fields/types, IDs/registrations, positive balances, supported passive category,
strategy/use IDs, unique process IDs and matching fluid/buffer pairs.

2. Default serialization
FMLPaths.CONFIGDIR/mobvats/passive_mobs/_defaults.json, on the logical server.
In this development run: run/config/mobvats/passive_mobs/_defaults.json.
PassiveDefaults constructs generic DATA solely for generation. CREATE_NEW writes
pretty UTF-8 JSON only when missing. Runtime always deserializes the resulting
file. ../examples/PASSIVE_DEFAULTS.example.json is a checked-in readable example, not another
runtime source. Existing defaults and sibling files are never regenerated/rewritten.

3. Loading and reload
ServerAboutToStartEvent loads files after registry availability, before world use.
_defaults.json loads first, then regular *.json siblings in lexical filename order.
Permission-level-2 command: /mobvats reload_passive_mobs.
Reload runs on the server thread, publishes the validated immutable definition set,
and increments profile registry revision. Existing active controllers rebuild
claims/topology and process contexts on their next engine tick. Standard /reload
is not the config reload command. Rejected files log filename and parse/validation
reason; other valid files continue. Missing valid minecraft:cow logs explicitly.
No silent Java-default fallback or destructive repair of malformed defaults.

4. Merge semantics
Whole definition replacement by entity ID. Later valid files win. Additional IDs
are additive. No recursive patches. Each file validates completely before any
entries from that file are merged; a partially invalid file applies no entries.

5. Cow defaults
See PASSIVE_DEFAULTS.example.json. Root key and entity are minecraft:cow.
Feed: #minecraft:cow_food or minecraft:wheat, count 1.
Juvenile growth: 24000 active ticks (vanilla AgeableMob BABY_START_AGE baseline).
Milk: minecraft:milk into MILK, 1000 mB/1200 active ticks, ADULT only, retains
existing Culture acceleration. Bucket container: minecraft:bucket ->
minecraft:milk_bucket for 1000 mB, ADULT only.
Waste: mobvats:waste into WASTE, 250 mB/1200 active ticks, ANY, valid Sewer/Drain
claim required; not Culture-accelerated.
Sword: mobvats:vanilla_entity_death_loot. Blender: mobvats:meat, 1000 mB, 0 XP.
Culture supported: 10 mB per paid 20-active-tick budget, 4x.
Feeding supported: 250 mB per one replacement-feed equivalent.

6. Generic lifecycle
MultiblockVat owns VatProcessEngine and SpecimenProcessState. PassiveProcessCompiler
compiles loaded data into typed process requirements/timers and PassiveOperations.
LifeStage is ADULT/JUVENILE; StageRequirement is ADULT/JUVENILE/ANY. Reusable stage,
reset/advance growth effects and lifecycle transaction steps are species-neutral.
ADULT plus valid feed and selected harvest actor commits immediately on the next
10-tick process cadence: harvest output + XP + tool mutation + feed consumption +
JUVENILE stage/growth start. There is one in-place specimen and no breeding cooldown.
Growth timer finishes to ADULT and resets growth progress. No live production entity.

7. Stage behavior and presentation
JUVENILE growth runs once per active tick, with optional paid Culture multiplier.
Adult-only milk timer pauses in place; resident buckets do not milk while juvenile.
Milk resumes after maturity; Waste remains stage-independent in the default.
The controller publishes life stage to Head as cosmetic transport. Renderer creates
an unregistered client Mob and sets AgeableMob baby state. Full/reduced/minimal
specimen renders show the same stage; render tier/culling and full vat AABB remain.
Follow-up (October 4, 2026): reach claims retain the adult substrate envelope,
while attachment geometry follows life stage. Calves use half-sized body bounds
for straps/tool targets and CowModel's separate (0,10,4)-pixel head translation
for feed targets. High tool ports aim down at the calf; cached arm rotation follows
that target. Controller obstruction checks use the same adjusted paths. Stage
publication precedes topology compilation on reload; maturity restores adult ends.
No collision checks against the visual specimen were added.

8. Topology specialization and acquisition
No selected harvest actor: adult dairy continues; Wheat and Feeding Slurry remain
untouched. Sword: existing death loot, enchantments, durability, XP and shared kill
buffer. Blender: independent MEAT buffer/capacity, still higher topology priority
than Sword; Sword backlog does not block Blender. Invalid/blocked routes cannot
consume feed. Generic acquisition policies sort by configured priority/use;
harvest acquisition is eligible only with feed, adult stage and no item backlog.
Container acquisition requires available output fluid and its configured stage.
Blender/Sewer capability and Tool use descriptors read loaded definitions; the
controller topology compiler remains the claim/priority authority. Legacy use-key
strings cowHarvest/cowMilk/cowDrain are retained as stable compatibility identifiers.
No GUI selection, player-distance state or actor-local processing timers.

9. Slurries
Feeding Slurry is preferred over direct feed when supported and sufficient; one
configured dose is committed in the same replacement transaction. No double feed.
Culture support/cost comes from data; its existing shared paid budget persists.
Insufficient configured payment cannot grant acceleration. Unsupported definitions
receive rate 1 and do not pay Culture. Existing slurry recipes and Mixer are unchanged.

10. Remaining Cow-specific Java
CowProcessDefinitions and CowProcessOperations were removed. VanillaEntityLoot is
the reusable identity-based loot/combat strategy; runtime has no Cow switch.
PassiveDefaults contains only canonical generic Cow data. CowBalance retains
historical balance constants/convenience fixture APIs. CowProcesses is temporary
migration/status snapshot compatibility only. CowHarvestLoot is a deprecated test
facade delegating to VanillaEntityLoot; runtime production uses the generic strategy.
Capture/Essence remain limited to Cow, as adding species was explicitly out of scope.

11. Persistence/migration
New saves: passiveStateVersion=1, processXp, specimenProcesses keyed by head,
lifeStage plus generic process state (growth, output timers, pending loot/tools).
Versioned generic state wins over stale cowProcesses/cowXp. Existing controller
fluid buffers, kill buffer, XP, tool slots, metrics and paid Culture credit persist.
Old legacy IDLE/READY/BLOCKED -> ADULT with zero juvenile progress; old GROWING ->
JUVENILE with floor(clamp(old_ticks,0,1200)*configured_growth_ticks/1200).
Default example: old 400/1200 -> new 8000/24000. A fully elapsed old GROWING state
is imported RUNNING at its configured boundary, so the next active tick matures it.
Old generic Cow process IDs are translated, with unrelated process IDs preserved.
Prepared old READY/BLOCKED loot remains pending until a valid new feed/harvest
transaction commits. Existing committed output is never re-harvested by migration.
New saves stop writing old lifecycle records; compact legacy status transport remains.

12. GUI/diagnostics
Specimen page now shows loaded definition source filename, Stage, Growth progress/
configured duration, Harvest actor, Replacement cycle, last feed source and each
secondary process stage/timer. Dairy reports Disabled by topology. Juvenile reports
Adult secondary outputs: Paused (juvenile). Tool labels say Specimen rather than Cow.
Existing obstruction, buffered output, fluid capacity and performance diagnostics
remain. Unsupported definitions diagnose cleanly and disable processing.

13. Validation
Gradle offline build and runGameTestServer passed. All 111 required headless
GameTests passed at 19:54:49 on October 4, 2026; server shut down normally.
Existing validation/topology/geometry/render/config/Mixer/routing/loot/durability/
buffer/metrics tests remain active. Obsolete feed-then-wait-then-harvest fixtures
were updated to immediate harvest, 24000 juvenile duration and paused milk.
New tests cover missing defaults/directory, deserialize existing edits, override
order, additive fixture IDs, whole replacement, file-level rejection/preservation,
unsupported strategies/entities/partial definitions, dairy feed/slurry retention,
instant Sword replacement, juvenile milk/bucket pause, configured maturity/resume,
generic stage predicates/lifecycle rollback/save, active reload/profile revision,
legacy stages/boundary/proportional migration, authoritative new saves and configured
Culture payment. Existing tests also cover Blender priority/capacity/backlog
independence, slurry substitution/4x/paid credit, direct feed, Waste and full buffers.
Build's packaged resource/provenance verification passed. git diff --check passed.
No runClient, client smoke/reload, manual UI control, screenshots, publish or release.

14. Future limits
Only the existing private named fluid buffers, container conversion, vanilla death
loot and MEAT strategies are supported. New strategy/buffer kinds need typed code.
Passive capture/Essence acquisition and model-specific head attachment metadata
need extension before additional species are a finished gameplay feature. Pig/
sheep are loader test fixtures only. Current formation enforces one specimen;
future multi-specimen work must address shared support accounting/feed-source status,
claims and fair scheduling. No recursive patching, scripts or automatic config
version migration. Missing Cow definitions disable processing; recover the edited
file or remove _defaults.json deliberately to request canonical generation, then
use the explicit config reload command.

Manual acceptance (user only)
- Fresh config directory: verify readable defaults appear; never edit the example
  as a substitute for the actual runtime config directory.
- Copy a full Cow definition into 90_server_overrides.json, adjust growth duration,
  use /mobvats reload_passive_mobs, verify source/duration in controller status.
- Add an additive passive fixture file and verify the loaded-definition count/log;
  capture/Essence still supports Cow only. Introduce malformed JSON and verify its
  filename/reason while the file is left unchanged and valid files still load.
- No Sword/Blender: verify indefinite milk, Adult status, Disabled by topology and
  untouched feed/slurry. Sword plus feed: immediate drops/XP/tool use and calf;
  milk/bucket pause until Adult. Confirm last source and paused timer in the GUI.
- Culture: verify shorter juvenile downtime with paid consumption; remove support
  and verify base rate. Blender: verify immediate MEAT/calf, priority over Sword,
  full MEAT tank leaves feed untouched, and Sword backlog does not block Blender.
- Confirm juvenile/adult visuals and stable straps/feed anchors in all render tiers;
  save/reload and break/reform while juvenile to verify stage/progress persistence.

Architecture reference: AnimalPen's documented interaction-format separation of
conditions, consumers and effects informed the typed data/strategy boundary.
https://github.com/BONNePlayground/AnimalPen/wiki/Animal-Interaction-Format
No AnimalPen source/assets were imported.
```
