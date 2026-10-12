> Historical record. Original: `TOOL_INTERACTIONS.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Git introduction author date 2026-10-10T12:39:15-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
Tool interaction definitions (Minecraft 1.21.1 / NeoForge)

Each entity entry in config/mobvats/passive_mobs/*.json can contain an
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

Built-in executors:

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

Code integration:

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

Compatibility boundary:

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
Goat ram and sniffer digging adapters are outside this slice.

Verification:

  gradlew.bat compileJava
  gradlew.bat runGameTestServer

GameTests use .cache/gametest, separate from operator-authored run/config.
ToolInteractionGameTests covers registry/definition rejection, acquisition and
demand ordering, normal/public-hook interactions, alternate IShearable state,
damage/replacement, transaction rollback, stale reservations and backpressure.
Existing suites retain shearing, weapon harvest and stored-container coverage.
```
