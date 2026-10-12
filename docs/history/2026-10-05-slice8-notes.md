> Historical record. Original: `SLICE8_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Git introduction author date 2026-10-05T06:10:21-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
MobVats Slice 8 -- Generic resources and dynamic buffers
======================================================

1. Dynamic buffer model
   ProductBuffers owns a ResourceLocation-sorted map of independent FluidTanks.
   ProductBufferDefinition carries a shared fluid selector list, capacity and input
   direction. There is no Buffer enum or tank-count switch. Stable ordered lists
   are rebuilt only when definitions/storage change. New profiles expose only
   their configured buffers plus retained committed orphan contents. Default Cow
   IDs and compatibility helpers do not limit the generic runtime.

2. Profile/JSON schema
   Config directory and /mobvats reload_passive_mobs are retained. Files remain
   objects keyed by EntityType ID, replaced per entity in deterministic file order.
   See ../examples/RESOURCE_PROFILES.example.json for a complete data-only entity transform.
   Optional profile buffers:
     "buffers": {
       "pack:water": {"fluids": ["#minecraft:water"],
                      "capacity": 4000, "input": true},
       "pack:result": {"fluids": ["anothermod:magic_fluid"]}
     }
   Capacity defaults to 4000 mB. input defaults to false. fluids is a nonempty
   alternative list of registered exact IDs or syntactically valid #tag IDs.
   Optional profile processes:
     {"id":"pack:gold", "duration_ticks":600, "requires_stage":"ANY",
      "inputs":[{"type":"item","items":["#c:ingots/iron"],"count":1},
                {"type":"fluid","fluids":["minecraft:water"],"amount":500}],
      "outputs":[{"type":"item","item":"minecraft:gold_ingot","count":4},
                 {"type":"fluid","buffer":"pack:result",
                  "fluid":"anothermod:magic_fluid","amount":250},
                 {"type":"xp","amount":12}]}
   XP input uses type=xp and amount. inputs defaults to []. outputs must be
   nonempty. requires_stage defaults to ANY. Optional actor_use uses existing
   registered topology uses; empty/default uses ordinary specimen Item Input
   contacts. Named actor use restricts item acquisition to its resolved endpoints.
   Fluid requirements consume matching controller-owned tanks; an input-capable
   declaration enables Fluid Input ports to fill them. Intermediate product tanks
   can also serve later transforms. Generic fluid outputs require declared buffers.
   Existing secondary_outputs can deterministically provision a buffer for older
   definitions. PassiveMobDefinition naming is retained to avoid rename churn.
   Omitting feed/harvest/growth/slurry sections creates a profile without passive
   lifecycle. A partial passive lifecycle is rejected. All registered EntityTypes
   are representable; no MobCategory.CREATURE validation remains.

3. Persistence and migration
   New products NBT contains buffers keyed by ResourceLocation, each with tank
   contents and metadata, plus a ResourceLocation export cursor. New saves do not
   write the legacy controller fluidBuffer or fixed meat/waste/culture/feeding
   product fields. Old fields import once to mobvats:milk, mobvats:meat,
   mobvats:waste, mobvats:culture_slurry and mobvats:feeding_slurry. Contents are
   preserved even if they are incompatible with current definitions.
   Reconnection merges matching fluids including temporary over-capacity amounts.
   Conflicting definitions diagnose. Different committed fluids under the same ID
   are kept in separate deterministic /retained_N export-only tanks, never mixed
   or discarded. Conflict diagnostics persist. Production waits for export to
   restore capacity. Normal FluidStack int amount limits still apply.

4. Item selectors
   ResourceSelectors compiles exact Item and TagKey<Item> predicates once per
   immutable selector list. Feed delegates to this implementation. Generic inputs
   and Mixer ingredients use it too. Alternatives are ORed. Generic process
   consumption can span multiple eligible private staging endpoints and accounts
   for repeated requirements against the same slot before committing.

5. Fluid selectors
   Equivalent exact Fluid and TagKey<Fluid> predicates are supported for buffer
   acceptance, generic process inputs and Mixer ingredients. Cached selectors
   query live holder membership, so tag reload/rebinding changes matching without
   parsing definitions again. Unknown/empty tags diagnose at definition load and
   in controller status; they do not become permanent invalid cached membership.
   Malformed tag IDs reject the file. No registry scans occur on process ticks.

6. Fixed item/fluid/XP outputs and atomicity
   ResourceProcessRuntime compiles fixed outputs and selectors. A start check runs
   on the controller cadence; duration advances one tick at a time while eligible.
   Inputs are consumed together at completion, after all outputs are planned.
   Inputs are not reserved or consumed at start. If inputs disappear or outputs
   become blocked during the timer, the READY process waits and retries, retaining
   elapsed progress. Completion may wait up to the next 10-tick cadence boundary.
   Shadow plans aggregate repeated item/fluid/XP requirements and input/output
   changes to the same store into a single reversible transaction step. Stale plans
   fail before mutation. Item outputs split into legal stacks and use the existing
   controller-owned item backlog/export transaction path; a nonempty backlog blocks
   new configured item output. Mixed outputs consume nothing if any output is
   blocked. XP overflow blocks rather than losing reward. Output metrics record
   only committed production. Fixed outputs need no Java strategy.

7. Mixer reuse
   Mixer item and fluid requirements now use the same exact/tag alternative-list
   selectors as entity profiles. Fluid plans aggregate consumption without double
   spending. Existing recipes, batch duration, capacities, input tanks, XP handling
   and slurry outputs remain unchanged. Mixer recipes/storage remain the built-in
   Java-defined Mixer component; this slice does not introduce a Mixer config loader.

8. Cow migration
   Generated defaults and ../examples/PASSIVE_DEFAULTS.example.json declare ordinary buffer IDs.
   Historical uppercase buffer names are accepted only as legacy schema aliases.
   Existing files are never rewritten. Milk remains 1000 mB per 1200 adult ticks;
   Waste remains 250 mB per 1200 ticks with Drain actor requirement. Blender remains
   1000 mB MEAT with existing shared-feed/replacement semantics. Culture/Feeding
   costs, acceleration, sword vanilla death loot and durability remain unchanged.
   These optional lifecycle strategies retain their built-in resource IDs and
   explicit strategy whitelist; new fixed transforms use the generic data path.

9. Fluid ports
   IFluidHandler queries reflect the current dynamic set. Requested-fluid drain
   locates matching stored contents. Amount-only drain uses sorted buffer IDs and
   a persisted next-ID cursor. SIMULATE does not advance the cursor. Tank indices
   therefore differ from the historical enum order. Only configured input-capable,
   compatible buffers accept input. The Cow milk buffer now accepts its configured
   selector (milk), rather than arbitrary fluid through the former unfiltered tank;
   legacy non-milk contents still survive and can be exported.

10. Validation
    File-atomic validation covers registered entities/items/fluids, ID syntax,
    positive integer quantities/capacities, geometry, stage values, unique process
    IDs, duplicate JSON/canonical buffer IDs, undefined buffers, exact buffer/fluid
    incompatibility, and unsupported resource/strategy fields. Arbitrary registered
    resources need no whitelist additions. Tag-based output compatibility remains
    live: a tag can change after startup, so production validates current membership.
    Effective legacy default buffers can be overridden explicitly in profile data.

11. Live reload
    Publishing definitions changes registry revision; active engines rebuild their
    compiled profiles and apply metadata on the next server tick. Contents are never
    cleared for a capacity, resource-selector or removed-buffer change. Incompatible
    committed buffers diagnose, reject new production/input and remain exportable.
    Removed empty buffers are discarded; removed nonempty ones remain export-only.
    A missing profile disables processing and preserves contents. Geometry already
    stored in essence/Heads is unaffected by profile reload.

12. Validation results
    Final: .\gradlew.bat build and .\gradlew.bat runGameTestServer passed.
    All 155 required headless GameTests passed: 129 prior applicable tests (updated
    where old index/save assumptions changed) and 26 new dynamic-resource tests.
    Coverage includes arbitrary buffers/registered fluids, >5 buffers, explicit and
    sorted round-robin drains, cursor/metadata persistence, all five legacy imports,
    assimilation overflow/conflicts, incompatible/removed/capacity-reduced reloads,
    exact/vanilla/common/alternative selectors, live tag rebinding, specific invalid
    resource diagnostics, duplicate keys, generic hostile/no-lifecycle data,
    configured-duration mixed item+fluid+XP output, early/late capacity/backlog
    failures, stale plans, repeated/same-buffer transforms, multi-endpoint item
    input, real generic ports, arbitrary identity execution, item/XP-only output,
    shared Mixer exact/tag recipes, and unknown-tag diagnostics. Existing Cow,
    Sword, Blender, slurry, Mixer, topology and geometry regressions remain green.
    Packaged render-resource checks passed. No client task was run.

13. Remaining built-in assumptions
    Cow's optional replacement/slurry/Blender strategies, balance helpers, actor-use
    IDs, translations/metrics and Mixer recipe/storage setup remain explicit built-in
    content. Generic storage/transactions have no MILK/MEAT/WASTE switch or enum.
    Compatibility aliases and legacy NBT import name the old fields intentionally.
    There is still one active specimen, and existing geometry/Harness/Drain/port
    topology rules apply. FE and arbitrary scripts are not implemented. Declarative
    processes do not expose new arbitrary JSON executable effects; existing engine
    effects and passive lifecycle behavior remain code-registered.

14. Pack-maker capability and limits
    A registered entity, arbitrary exact/tagged item/fluid/XP inputs, positive duration,
    and arbitrary registered item/fluid/XP outputs can now be specified entirely in
    JSON. No species-specific Java or resource enum additions are needed. The test
    environment contains no Twilight Forest dependency, so tests use registered
    vanilla entities and MobVats fluid registrations as custom-resource fixtures.
    A supplied mod's registered entity/fluid/item IDs follow the identical path.
    Syringe capture currently requires a configured adult LivingEntity and a bottle;
    generic data does not impose that capture gate. Machine dimension/topology and
    one-specimen constraints still apply. The user performs client visual acceptance.

Nothing was published or released. No client, client smoke or client reload task ran.
```
