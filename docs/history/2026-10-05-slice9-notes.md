> Historical record. Original: `SLICE9_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Git introduction author date 2026-10-05T06:50:21-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
SLICE 9 — Generic capture and vanilla default catalog

Implementation
--------------
PassiveMobDefinition retains its compatibility name. A profile may contain capture,
simulacrum, buffers and fixed processes without feed, growth, harvest or slurries.
No CREATURE-category restriction is applied by definition validation.

Capture schema (all fields except cost members are optional):
  "capture": {
    "enabled": true,
    "tool": "mobvats:syringe",
    "cost": { "items": ["minecraft:glass_bottle"], "count": 1 },
    "health_cost": 0,
    "requires_alive": true,
    "allow_hostile": true,
    "allow_boss": true,
    "adult_only": true,
    "encoding_enabled": true,
    "status": "configured"
  }

Tool is one exact-item or #item-tag selector. Cost items are alternatives, evaluated
live through the existing selector system. Counts can be paid across inventory
slots. health_cost removes player health directly, never kills the player, and
uses no damage hooks. Creative bypasses item and health costs, but still obeys
capture gates and requires an output slot. An explicit capture object with omitted
cost is free. An omitted capture section uses the legacy one-bottle adult syringe
policy, with hostile/boss allowance disabled. Changing capture.enabled does not
invalidate existing Essence or prevent encoding; encoding_enabled is separate.

ItemSyringe delegates to CaptureTransaction. Capture validates policy and geometry,
filters state, plans costs and output capacity, then commits reversible inventory
and health steps. Full inventory now fails without charge instead of dropping
Essence into the world. No Cow branch exists in this path. Only the actual syringe
invokes this interaction; other tools can invoke the same transaction API through
their integration. A tool selector does not automatically install an interaction
handler on another mod's item.

Specimen format and migration
----------------------------
Essence CUSTOM_DATA and Head NBT contain entity, geometry, specimen_version=1,
and specimen_state. Geometry has its existing independent version. Heads resolve
behavior through the current loaded entity profile; behavior is never serialized
into the specimen. ItemEssence validates version, identity, geometry, registration,
loaded definition and encoding policy before encoding/consumption.

Captured state is an explicit allowlist of scalar vanilla presentation fields:
Color, Sheared, Type, RabbitType, Variant, variant, IsScreamingGoat, HasLeftHorn,
HasRightHorn, Size, PuffState, MainGene, HiddenGene, CollarColor, Baby, IsBaby;
plus CustomName and filtered VillagerData (type/profession/level). No equipment,
owner UUID, AI, navigation, position, active combat, inventory or Brain is copied.
Fresh render/loot entities retain their defaults, then read this filtered state.
Goat screaming uses vanilla's IsScreamingGoat key. Ageable rendering uses Head
lifeStage. Explicit adult_only=false preserves a captured juvenile's initial stage;
a lifecycle profile starts its growth process from that stage.

Old Cow Essence with entity only uses the historical 7C geometry, excluding current
config overrides. Existing stored geometry is used exactly. Old Heads follow the
same migration. No unknown identity silently becomes Cow. A missing profile blocks
new encoding/capture, and disables processing of existing Heads while preserving
identity, state, geometry and stored buffers. Diagnostics are interaction messages,
Essence identity/geometry tooltips, and controller profile/capture status; no tick
log spam. /mobvats reload_passive_mobs still reloads all generic profiles.

Vanilla catalog
---------------
The generator walks the vanilla EntityType registry. It uses registry identity,
category and dimensions; no reflection or full handwritten entity catalog.
All non-MISC vanilla categories are mobs in 1.21.1. Centralized MISC exceptions
include iron_golem, snow_golem and villager. Tests independently instantiate every
vanilla type and compare the catalog with all Mob instances. Player, armor stand,
projectiles, vehicles, displays and other non-mob entities are excluded.

82 entries:
  19 passive lifecycle profiles — capture enabled
  22 passive/special representation profiles — capture enabled, no replacement lifecycle
  37 hostile/undead profiles — capture disabled
   4 boss/special profiles — capture disabled and boss allowance disabled

Boss exceptions: wither, ender_dragon, elder_guardian and warden. Undead horse
exceptions: skeleton_horse and zombie_horse are gated despite CREATURE category.
All MONSTER-category defaults are gated, including neutral monsters. The runtime
hostile gate also recognizes Enemy. Modded bosses are not globally prohibited;
an explicit enabled profile permits them without species Java.

Centralized vanilla food-tag table drives cow, sheep, pig, chicken, rabbit, goat,
horse, donkey, camel, llama, trader_llama, wolf, cat, ocelot, fox, armadillo, strider,
sniffer and mooshroom. Shared tags are used for donkey/horse, trader_llama/llama
and mooshroom/cow. Cow retains its exact wheat fallback. Juvenile duration is
24000 ticks except sniffer (48000, confirmed in local vanilla source).
Mule, turtle, bee, frog/tadpole, fish, axolotl, panda, villagers and other special
reproduction entities omit replacement lifecycle. Egg-laying, environmental or
otherwise conditional reproduction is not simulated as ordinary breeding.

Cow retains its prior milk, Waste, harvest, Blender and slurry behavior. Goat and
mooshroom share generic milk data. Lifecycle profiles share generic Waste, vanilla
death-loot strategy, Blender and slurry primitives. Chicken additionally uses a
fixed adult egg process every 9000 ticks, the mean of vanilla's 6000–12000 range;
this is a deterministic configured approximation. Wool/shearing, armadillo brushing
or natural scute timing, honey/honeycomb, turtle eggs/scutes, mooshroom stew,
sniffer digging and other special interactions remain unimplemented. Their profile
status identifies unavailable special interactions; empty processes are explicit.

_defaults.json is written with CREATE_NEW only when missing, then that exact file
is deserialized. Existing files are never regenerated. Entity and buffer keys are
sorted for stable output. Simulacrum {} means procedural capture-time geometry.
Sibling files load lexically after defaults and replace whole definitions. Invalid
files are rejected atomically; unrelated files/defaults remain available. Existing
Cow-only defaults therefore remain Cow-only until the user deliberately manages
that file; a generated reference is saved as VANILLA_DEFAULTS.example.json.

Pack override example: copy the whole zombie profile to 90_server_overrides.json,
set capture.enabled=true (allow_hostile is already true in generated templates),
and add a fixed item process. Boss templates also require allow_boss=true.
An arbitrary registered entity can instead use a minimal profile:
  {
    "somepack:entity": {
      "entity": "somepack:entity",
      "capture": { "enabled": true },
      "simulacrum": {},
      "buffers": {},
      "processes": [{
        "id": "pack:gold", "duration_ticks": 600,
        "inputs": [{ "type": "item", "items": ["#c:ingots/iron"], "count": 1 }],
        "outputs": [{ "type": "item", "item": "minecraft:gold_ingot", "count": 4 }]
      }]
    }
  }

Validation and limits
---------------------
Required verification: gradlew.bat build and gradlew.bat runGameTestServer.
Tests exercise actual syringe interactions, creative/cost/health/full-inventory
behavior, failed geometry/profile transactions, custom registered MONSTER fixture,
hostile JSON enablement/fixed transforms, generic encoding, stored geometry after
reload, variants/names, legacy Cow, catalog completeness and invalid selectors.
The registered custom fixture and all GameTests are excluded from the jar.
All 168 required headless GameTests passed; build passed, including packaged resource checks. All prior applicable regressions remain in the suite. Final results are reported
in the chat and build/slice9-verification.log.

No Java is needed for a registered living mob to enter through a valid configured
profile with finite positive supported geometry and the syringe. Fixed processes
remain pure JSON. Client rendering still requires an entity compatible with the
existing Mob renderer and its vanilla/mod renderer; non-Mob LivingEntity rendering
and unusual boss geometry/animations need manual acceptance. Custom mod-specific
variant fields outside the vanilla allowlist require future safe state adapters.
No hostile progression, boss balancing or generalized entity cloning was added.

Manual acceptance (not performed)
---------------------------------
Inspect the generated reference and a deliberately fresh config; confirm all
82 entries and the capture gates. Capture Cow, encode it and verify prior lifecycle.
Capture a non-Cow passive, inspect Essence tooltip and verify its encoded specimen
renders with its variant. Copy zombie into a sibling override, enable capture,
add iron-to-gold, reload and verify production. Inspect disabled Wither/Dragon;
only enable and allow bosses deliberately. Existing config files remain untouched.
No client task, release or publication was performed.
```
