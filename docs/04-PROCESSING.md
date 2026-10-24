# 04 — Processing

## 4.1 Capture, encoding and configuration

Server entity profiles live individually at `config/mobvats/mobs/<namespace>/<path>.json`. Existing files are authoritative; there are no bundle overrides. See [mob profiles](MOB_PROFILES.md) for schema versions, migration backups, validation and unavailable-species behavior. /mobvats reload_passive_mobs reloads profiles and culture configuration. Configuration samples are in [examples](examples/README.md).

`CaptureTransaction` checks configured capture policy, costs, geometry and output capacity before committing. Creative bypasses configured item/health payment but still obeys capture gates and output capacity. The default legacy policy requires an adult configured mob, syringe and bottle. Hostile/boss capture is gated by explicit profile policy; declaring a tool selector does not install a handler on another mod's item.

Essence/Head store identity, versioned geometry and allowlisted specimen fields, not a live entity or serialized behavior. Profile reload changes behavior, not already captured geometry. Missing profiles disable processing while retaining identity and contents. Head UUID bindings prevent a new Head at the same position from inheriting the previous specimen's progress.

## 4.2 Scheduling, feeding and output

Each Head owns independent stage, growth, timers, pending work and feed provenance through the process engine. Contexts compile on topology/profile revisions. Stable X/Y/Z Head order rotates its start after each 10-tick cadence; cursor/offset persist. Actor claims are local to Head and use. Shared stores and destinations commit in that order with atomic reservations.

Item feed uses compiled physical contacts, and conduit expansion extends selected actors' reachable endpoints. Feeding Slurry is paid per committed replacement. Tools remain mounted, can wear/transform and retain result buffers. Committed Sword loot enters a controller-wide kill-output backlog; that backlog blocks further item-producing Sword harvests until drained. Blender fluid output can continue independently when its destination has capacity.

Experience quantities are stored/transferred as exact millibuckets. `ExperienceFluid` supplies the common configurable XP conversion (default 20 mB per point, range 1..1000); recipe amounts and fluid tails are not rounded to whole XP points. Native reward scaling plans and fractional carry commit only with their output transaction.

## 4.3 Environment, culture and fouling

Culture frequency scales biological timers; happiness scales native output amounts. Default frequency spans 0.5..5× and happiness yield spans 0.5..2×. Eligibility still requires feed, tools, routes and destination capacity. Mechanical Tool strikes retain native timing/durability. Accelerated generic resource timers and yield-scaled outputs opt in through definition flags.

Vat medium belongs to the controller, separately from Fluid Buffers and endpoint service staging. Its capacity uses interior cell volume minus numeric displacement; each level fills uniformly from below. Comfort-shell exposure contributes culture frequency; physical obstruction, neighboring specimens and shared comfort claims determine happiness. A buffer containing culture does not accelerate specimens until a Fill Valve delivers it into the environment. The former paid culture-credit model is superseded.

Clean and dirty amounts share one selected fluid identity/profile and common capacity. Recognized committed biological work fouls clean volume (default base 50 mB), preserving total volume. Each operation multiplies its configured base by `1 + (M - 1) * clamp((2 - H) / 1.5, 0, 1)`, using that specimen's current happiness yield `H`. `fouling.max_multiplier` in the existing culture JSON defaults to `M = 5.0` (finite, at least 1); perfect happiness always gives 1×. At happiness 2.0/1.25/0.5, the default operation fouls 50/150/250 mB. Definition overrides and zero-fouling opt-outs retain their bases. Fractional mB carry persists per specimen/action and commits with the operation, including rollback; a changed specimen, definition, base or maximum cannot inherit the previous configuration's fraction. Dry, unrecognized and fully dirty medium do not accrue fouling debt. Failed/blocked work and ordinary mechanical transfers do not foul it. Drain exports dirty first, then clean, including partial tails. Only the selected clean/dirty pair refills nonempty medium; incompatible reconnection contents are preserved separately, not blended. Fill refuses excess capacity; Drain can recover stored excess. Mixer filtration recipes transform dirty culture through definition-owned composition checks.

Ordinary water receives the same happiness scaling. Drain or void dirty water and refill with fresh water; enriched Culture, charcoal and XP restoration are optional. In enriched Vats the added dirty volume increases filtration and re-enrichment demand. The Vat never consumes XP directly.

For enriched Culture, fouling replaces the entire XP formula entry with equal Waste in the dirty fraction only, adding to pre-existing Waste and preserving other ingredients. Non-XP media retain the configured Waste increment. Filtration and enrichment are separate ordinary Mixer recipes: one charcoal filters 1,000 mB of water-based dirty Culture without restoring XP; 250 mB of actual XP then enriches 1,000 mB of XP-free Culture. Both return 1,000 mB of Culture. Filtered Culture remains a compound fluid and does not match the enriched performance profile. See [recovery decisions](design/CULTURE_XP_FOULING.md) and [production measurements](design/HAPPINESS_SCALED_FOULING.md).

Culture JSON under `config/mobvats/culture/` assigns ordered medium rules through specific mobs, groups and a default profile. Required composition, selectors and exact components determine matching. Existing defaults remain authored data; [examples/CULTURE_PROFILES.example.json](examples/CULTURE_PROFILES.example.json) illustrates the schema.

## 4.4 Tool interaction definitions

Tool operations use registered executors and definition-owned stages, selectors, priorities, cooldowns and actor uses. The following configuration/API contract retains the detailed current interaction reference. Environmental Goat horn, Sniffer digging and Armadillo brushing adapters are now implemented; they use the same demand configuration and transactional output path. Buffered results backpressure completion, and cooldown begins after commit.


Each species file in `config/mobvats/mobs/<namespace>/<path>.json` can contain an
"interactions" array. The root entity ID is the specimen selector. Example:

  "interactions": [
    {
      "id": "example:collect",
      "executor": "mobvats:entity_item_interaction",
      "items": ["minecraft:bucket"],
      "actor_use": "example:collect",
      "requires_stage": "ADULT",
      "cooldown_ticks": 100,
      "priority": 30,
      "output_policy": "retain"
    }
  ]

This example invokes the actual entity interaction; a configured cow returns a
milk bucket. It replaces the Tool interactions for that entity. To preserve
other operations, retain their entries in the array as well.

id and executor are required resource IDs. items accepts exact IDs or #item
tags. abilities optionally contains NeoForge ItemAbility names, such as
"shears_harvest". An input matches any item selector OR all listed abilities.
At least one item selector or ability is required. Unknown executor IDs and
unknown JSON fields reject the entire file, with a diagnostic.

actor_use defaults to id and groups exclusive operations on a specimen.
requires_stage defaults to ANY (ADULT and JUVENILE are also accepted).
cooldown_ticks defaults to zero and must be nonnegative. The persisted process
clock advances only after a changed, committed operation. Tool animation also
imposes its existing 20-tick availability interval. Regrowth timers run
independently of interaction cooldown. priority defaults to zero; larger
priorities are selected first, with IDs as deterministic tie-breakers.
output_policy currently accepts only "retain": products remain in the Tool's
result buffer until routed or collected, and an occupied buffer backpressures
the next interaction. Input consumption, retention, damage and replacement
are determined by the executor's public interaction contract.

Tool demand strategies now use interaction IDs. Ordered persisted
DemandConfiguration options can override acquisition order and narrow item
selectors. Existing options named "tool" retain their combined legacy filter.
Acquisition still reserves routed inputs atomically and validates stale plans.

### 4.4.1 Built-in executors

  mobvats:entity_item_interaction
    Reconstructs detached specimen/player objects and mirrors the public
    survival Player.interactOn dispatch contracts:
    the NeoForge EntityInteract event, entity interaction, then item interaction.
    Captures persisted specimen fields, held-item changes, player inventory
    replacements and spawned ItemEntities. Standard IShearable interactions
    supply their own drops and state; no species output table is used.

  mobvats:regrowing_entity_item_interaction
    Uses the generic executor plus the existing registered regrowth strategies
    and transactional feed payment. Requires a lifecycle definition.

  mobvats:weapon_harvest
    Preserves the existing weapon-hit, death-loot, XP and replacement lifecycle
    transaction machinery. Requires lifecycle and its matching actor_use.

  mobvats:stored_container
    Preserves physical-fluid-to-container transactions. source_process must
    name a secondary_outputs entry with a container and matching actor_use.
    This adapter retains storage accounting instead of generating free fluid.
    Acquisition requests empty inputs only; filled held containers can still
    be routed out without reacquiring produced containers from the network.

Fresh generated defaults explicitly include these executor definitions.
Existing files are not overwritten. Omitting interactions imports the legacy
harvest, ability-based interaction and container definitions. An explicit []
disables Tool interactions. Existing harvest and secondary state IDs are
retained for saved progress, prepared loot and regrowth compatibility.

### 4.4.2 Code integration

  ToolInteractionExecutorRegistry.register(ResourceLocation, executor)

Register before passive definitions load, on the initialization/server thread.
Duplicate IDs are rejected. A ToolInteractionExecutor supplies selection
support, optional acquisition readiness, and an execute adapter which plans
and commits through ProcessTransaction. The compiler dispatches solely by ID.
No definition can specify a Java class, method name or reflective invocation.
Code adapters remain responsible for reversible mutations, stale checks and
output backpressure; the registry cannot make arbitrary Java code reversible.

CapturedSpecimen.registerInteractionStateField("YourPersistedField") lets code
adapters opt additional NBT fields into reconstruction and persistence.
Generic execution rejects changes to unpersisted fields. State registration
must precede capture/loading and targets the entity's additional-save-data
contract; JSON cannot expand the persistence contract.

### 4.4.3 Compatibility boundary

The interaction level never exposes live chunks, entities, mutable level data
or the live server. It records item spawns, suppresses cosmetic sound/particle/
game-event notifications, and rejects block/fluid access, environmental writes,
scheduled ticks, map changes and non-item entity spawns. Rejected paths discard
their detached changes. Planning never relies on rolling back the real world.

The synthetic player is a survival Player, not a ServerPlayer. ServerLevel-only
and ServerPlayer-only mechanics, advancements, AI/environmental interactions,
and arbitrary damageable-item behavior require dedicated registered adapters.
Standard shears use their one-point durability contract with damageItem and
enchantment hooks on copied stacks, since normal LivingEntity durability calls
only execute in a ServerLevel. These limits are deliberate; this is not a full
virtual Minecraft server or a security sandbox for malicious Java listeners.
Dedicated Goat ram, Sniffer dig and Armadillo brush adapters are implemented; their definition-owned inputs/timing use the same transaction path.


## 4.5 Mixer recipe definitions and lifecycle


Server definitions remain in `config/mobvats/mixer/`. `_defaults.json` loads
first, then other JSON files in filename order; later definitions replace the
same recipe ID. A malformed file contributes no recipes or formula names.
Use `/mobvats reload_mixer` to reload.

The default culture recipe consumes 1,000 mB water + 250 mB XP and produces
1,000 mB culture slurry. Its compound composition derives from those inputs.
Startup and `/mobvats reload_mixer` create a bundled `_defaults.json` only when
missing; existing files, including historical defaults and authored overrides,
are never rewritten. Recipe-ID overrides remain unchanged.

Every normal development Gradle `build` runs `invalidateDevMixerDefaults`, even
when compilation and packaging are up-to-date. It deletes only the local
`run/config/mobvats/mixer/_defaults.json`; missing files are a successful no-op.
Linked paths (including junctions) and non-file targets are refused. Custom sibling
JSON files are preserved. The next startup recreates the missing bundled copy.
This housekeeping is not attached to startup or shipped with the mod and does
not apply to production installations.

```json
{
  "recipes": {
    "example:refine": {
      "batch_ticks": 200,
      "strategies": [
        {
          "id": "primary",
          "enabled": true,
          "items": [{ "selector": "minecraft:raw_iron", "amount": 1 }],
          "fluids": [{ "selector": "minecraft:water", "amount": 250 }]
        },
        {
          "id": "alternate",
          "items": [{ "selector": "minecraft:iron_nugget", "amount": 9 }]
        }
      ],
      "outputs": {
        "items": [{ "item": "minecraft:iron_ingot", "amount": 1 }],
        "fluids": [{ "fluid": "mobvats:waste", "amount": 100 }]
      }
    }
  }
}
```

At each batch boundary, the Mixer ranks executable strategies by requirement
slot count descending, normalized consumption descending, then recipe ID
ascending. Repeated selectors count as separate slots. Item consumption is the
allocated count divided by that actual stack's maximum size (including components);
fluid consumption is mB divided by 1,000. These fractions are summed and compared
exactly with integer arithmetic. Alternatives use the existing integral allocation,
not an assumed stack size or a search for the highest-scoring possible allocation.
Each recipe competes using its highest-ranked executable strategy. Equal strategies
within the same recipe break ties by ascending strategy ID. Strategies require
unique, nonempty IDs and `enabled` defaults to true. Existing signed-integer
`priority` fields remain readable for compatibility but have no selection effect.
File order controls overrides only, not execution. Output capacity and transform
validation remain part of executability, preserving existing backpressure/fallback.

Every requirement in a strategy must be satisfiable together. At least one
input and one output are required; item-only and fluid-only operations are
supported. Selectors retain native item/fluid ingredient codecs, including tags,
custom ingredients and alternatives. Fluid alternatives use native objects,
for example `[{ "fluid": "minecraft:water" }, { "fluid": "mobvats:waste" }]`.
Input `record_in_composition` defaults to true. Amounts and `batch_ticks` are
positive 32-bit integers.

Item eligibility uses the existing four recipe input staging slots filled by
item ports; selection does not pull directly from connected item buffers.
Fluid eligibility uses currently connected physical buffers and service tanks.

All current input requirements are consumed. There is no non-consumed tool or
catalyst requirement in the schema, definition, or resource bundle;
`record_in_composition: false` only omits an input from the derived formula.
Non-consumed-input ranking and its integration test therefore require a later
processing change. No arbitrary consumption value is assigned to hypothetical tools.

Outputs execute as one bundle. Item outputs use four private output slots,
separate from the input inventory, and respect each item's stack limit. They
persist, survive controller assimilation, and are extracted through configured
item output ports. Input items cannot be extracted through those ports. Fluid
outputs use connected physical buffers first, then the producing endpoint's
bounded service tank for that fluid family. Outputs are reserved in declaration
order and tank order; incompatible identities cannot share a tank. This order
also defines deterministic backpressure. Capacity reservations accumulate
across repeated outputs. Any blocked output leaves all inputs, outputs and
production metrics unchanged. Simulation does not create service tanks.

The operation uses `ResourceBundle` demands and the same owned-resource
reservation implementation as `ResourceRouting`. Reservations are single-use
and reject changed item/fluid snapshots, changed capacity, changed output
routing/filters and a replaced registry snapshot. External third-party handlers
are not reversible transaction participants.

Each fluid output may specify `composition` or `transform`, using the existing
composition and transform fields. Without explicit composition, compound
outputs derive their formula from the selected strategy's recorded inputs.
A transform belongs to that output alone. Its `source_input` is a zero-based
fluid input index in every strategy; each strategy must have a recorded source
at that index. Required/excluded composition checks, removal selectors,
`restore_ordinary`, exact source identity and source components retain their
existing meaning. A source spanning several buffers must have one exact fluid
and component identity. Other outputs in the same operation are unaffected by
the transform. Restored ordinary fluid identities become exportable only after
successful production and remain persisted.

An empty `transform.remove` list retains the source while incorporating the other recorded inputs. The bundled enrichment recipe uses this with XP input 250, required Water 1,000 and excluded XP 1. It does not remove Waste. The charcoal recipe uses a nonempty Waste removal list and `restore_ordinary: false`, so filtered Culture stays Culture. There is no quantitative/PPMB filtering framework. Legacy authored general transforms remain valid; the bundled charcoal recipe is restricted to water-based Culture. Empty-removal transforms record `compoundTransformedMb`, while removal transforms retain `cultureFilteredMb` metrics.

An executable active batch keeps its recipe and progress until completion;
its best executable strategy is reevaluated each cadence. Progress belongs to
the recipe, not a strategy. If that recipe becomes unavailable, another executable
recipe can replace it, resetting progress. If none can run, the Mixer idles and
retains the blocked batch's progress. After completion, all recipes compete again
without affinity for the previously successful recipe.
Reloading with a newly parsed definition resets active progress on the next
cadence, even if its ID remains the same. Removing it also resets progress.
No input is consumed before successful completion. NBT stores an authored
signature so saved progress resets if the definition changed while unloaded;
unchanged definitions retain saved progress. Legacy NBT without a signature
rebinds to the current recipe for compatibility.

Existing top-level `items`, `fluids`, `output`, `output_amount`, `batch_ticks`,
`composition` and `transform` remain accepted and normalize to one enabled
`default` strategy and one fluid output. `priority` is also allowed on legacy
definitions. Mixing legacy fields with `strategies`/`outputs` is rejected.
Known untouched bundled v1 defaults upgrade to v2; authored defaults are
preserved. Mixer cadence, batch timing, drive behavior, spin-up and power
balance are unchanged.

## Hostile activation (Slice 3)

Complete hostile specimens form in T2/T3 while dormant. Per-Head activation gates every process before actor execution, including custom processes. A genuine strike on the vanilla rod directly above an owned roof Harness activates only that Head. Encoding resets activation; disk/network serialization and portable Head drops retain it. Default hostile Blenders produce Strange Meat through the existing fluid transaction path; Sword processing retains entity loot and XP. See [gameplay and placement](HOSTILE_SPECIMENS.md).
