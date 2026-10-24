# Hostile specimens and lightning activation

T2 Iron and T3 Blood Iron Vats accept encoded hostile specimens with complete
captured Flesh geometry, their normal Harness corner plane, Sump, and valid shell.
T1 Copper rejects hostile specimens. An uncharged hostile specimen can form; its
inspection status is **Awaiting Lightning**. Its physical Head/Flesh stays visible,
its mob replacement is hidden, and none of its processes run. Passive specimens
and already activated hostile specimens in the same Vat continue independently.

## Rod placement

Mount an upright **vanilla lightning rod directly on an owned roof Harness**:

```text
           vanilla rod (FACING=UP)       outside Vat
roof       matching-clad Harness        shell top face
           existing support gap         existing geometry rules
           encoded Head / Flesh         complete captured geometry
           Sump                         existing association rules
```

The Harness must occupy the top face of the formed Vat, directly below the rod.
Interior Harnesses still work structurally but cannot receive charging through
intervening roof casing. There is no remote conduction or nearby-Head search.
For a specimen with several corner Harnesses, a rod on any of its owned roof
Harnesses serves that same Head. Each Harness has exactly one structural owner.
Rods are optional external attachments, with no tier or formation requirement.

Natural thunderstorm strikes and Channeling lightning both use vanilla targeting.
Only a strike on the particular rod activates its associated hostile Head. One
strike cannot activate another Head. Redstone, manually powered rod states, and
unrelated nearby strikes do nothing. The server consumes NeoForge's
`VanillaGameEvent` for `LIGHTNING_STRIKE`, verifies a `LightningBolt` source, and
matches vanilla's exact strike cell after the bolt has powered the rod. It
revalidates the entire Vat and specimen before activating the Head.

## Persistent state and operation

Activation is a one-time Boolean stored with the Head's encoded specimen. There
is no charge meter or upkeep. Saving/reloading and collecting/replacing an encoded
Head retain activation, including relocation to another valid T2/T3 Vat. Normal
Head drops now preserve encoded specimen data. Creative copies preserve activation
but receive a separate process identity. Re-encoding, including the same species,
resets activation and process identity; clearing the specimen clears activation.
Use Essence on an empty, unassembled Head to encode it. To replace an existing
encoding, remove the Head from a formed Vat, place it outside a formed structure,
and sneak-use Essence on it. This consumes Essence normally and resets activation,
including when encoding the same species. Ordinary use does not overwrite an encoding.

Activation uses the existing Head update packet and per-Head render revision.
Clients refresh model data for that specimen's occupied cells and enable the same
mob replacement renderer, orientation, scale, and animation used by passive Heads.
No world reload is required.

Default non-boss monsters can be captured and use the existing harvest lifecycle:
one Rotten Flesh or the existing configured Feeding Slurry cost per replacement,
24000 base growth ticks, ordinary actor scheduling, durability, loot, XP, yield,
and output backpressure. They have no milk, wool, or passive secondary outputs.
Sword harvesting uses the existing entity death loot/XP path. Blender profiles
select `mobvats:strange_meat`; passive profiles retain `mobvats:meat`. Both use the
same transactional output path and separate service tanks. Strange Meat has no
solid products or Extruder recipes. Boss capture and undead-horse progression
remain gated. Blood Iron manufacturing and T3 Survival recipes remain unresolved.

Profiles remain operator-owned at config/mobvats/mobs/<namespace>/<path>.json. Existing species files are preserved. The old passive_mobs bundle/override directory is no longer loaded. Edit an individual hostile profile and reload with /mobvats reload_passive_mobs. See [mob profiles](MOB_PROFILES.md) for schema upgrades and disabled-profile handling. No tier recipes change.

## Manual client acceptance (outstanding)

No client was launched for this slice. The user performs visual acceptance:

1. Build a valid T2 Vat on a roof with complete geometry, Harnesses and Sumps for
   two encoded hostile Heads. Confirm formation with both Flesh constructions visible.
2. Mount upright vanilla rods directly on the corresponding roof Harnesses.
3. Use a legitimate lightning strike on the first rod, then on the second. Confirm
   each independently switches to its mob replacement, hiding only its own Flesh.
4. Collect a charged Head and place it in another valid T2 Vat. Confirm it stays active.
5. Re-encode through the supported encoding lifecycle and confirm dormancy resets.
6. Assign a Blender, supply feed and fluid output capacity, and confirm Strange Meat
   enters the expected output path. Confirm a passive Blender still makes Meat.

Headless tests cover server state, model selection and network payloads; they do
not establish GPU rendering or visual acceptance.
