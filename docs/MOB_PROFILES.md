# Individual mob profiles (schema 2)

Mob profiles are server configuration at
`config/mobvats/mobs/<namespace>/<path>.json`. For example,
`minecraft:cow` uses `minecraft/cow.json`; `example:animals/small_mob` uses
`example/animals/small_mob.json`. Each file is one complete definition with an
exact `entity` ID, integer `schema_version`, and integer `minimum_vat_tier`
(1–3), alongside the existing capture, lifecycle, outputs, buffers, process,
geometry and Tool interaction data. Copy the generated species file as an editing
reference. Capture permissions and the minimum tier are independent.

Startup and `/mobvats reload_passive_mobs` (permission level 2) load this directory.
Missing built-in vanilla profiles are generated once. Existing files are never
refreshed from inferred defaults, even after a mod update. The old
`config/mobvats/passive_mobs/` bundle and override layout is no longer loaded;
it may be discarded. There is no compatibility migration or override precedence.
To deliberately regenerate a built-in profile, remove that species file and reload.
Startup and server datapack/tag refresh discover previously unknown registered mobs.
The reload command also scans for missing profiles. Existing documents are never
reclassified because tags, biome spawns or mod versions changed.

Schema versions are independent of the mod version. Schema 1 upgrades to schema 2
by adding `minimum_vat_tier: 1` only if absent. An authored tier is preserved.
The loader validates and compiles the upgraded document before writing it.
A successful upgrade retains the exact original in a sibling
`<mob>.json.v1.<unique-id>.bak`, then atomically replaces the JSON file from a
temporary file in the same directory. If atomic replacement is unsupported or
fails, the species is disabled and the original remains; no non-atomic fallback
is attempted. Backups are ignored by the loader. Current schema documents are not
rewritten. Newer, malformed or invalid documents remain untouched and disabled.

Unknown top-level fields (including nested editor metadata inside those fields)
survive upgrades and are ignored by runtime deserialization. Known behavior
sections retain strict field/type validation: unknown executor/method fields do
not become executable behavior. A rejected document, including one with unknown
behavior fields, remains unchanged. Schema steps operate on the original JSON
tree rather than serializing a runtime object, so extensions are not discarded.

Diagnostics identify the relative file. An entity/path mismatch, duplicate ID,
duplicate JSON key, unsafe path or linked file is rejected. Duplicate species
are disabled across all their declarations; there is no winning file.
Definitions for absent mods remain on disk and are disabled until that entity
registration returns. Other valid species continue to load. The full validated
catalog, source labels and compiled processing profiles are published as one
immutable snapshot; processing does not parse disk files per tick.

Invalid/unavailable profiles disable new captures/encoding and processing for
that species. Existing Heads keep their specimen identity, geometry, saved
process state and lightning charge; the loader does not delete or re-encode
them. Server formation rejects a Head with no loaded definition. Repairing the
file or restoring the mod and reloading allows normal revalidation.

The loaded `minimum_vat_tier` controls formation for every encoded specimen.
A server reload immediately revalidates loaded Vats, including previously
disassembled Vats. Raising a species tier can disassemble an existing Vat;
lowering it or repairing the profile can allow it to form again. There is no
grandfathering and no per-capture classification. Unloaded structures use the new
catalog when they load. Heads keep their identities, geometry and saved state.
Capture permissions and lightning activation remain independent. A hostile Head
still requires a real strike on its roof Harness rod in a valid formed Vat.
Authored tier reductions are honored, including T1, without a second hidden T2 gate.

## Discovery and initial tiers

Discovery runs at ServerStartingEvent, after worlds, registry tags, biome data
and NeoForge biome modifiers are ready. Server-side tag refresh queues another
scan after datapack replacement completes; client tag packets do not trigger it.
Entity factories are inspected once for missing species files to establish
whether the type is a Mob. These temporary instances are never spawned, ticked
or captured. Factory/geometry failures produce diagnostics and no replacement
file. Non-Mob entities do not receive profiles.

Initial classification uses these signals, in order:

- Known vanilla bosses and the entity-type tag `c:bosses` are excluded. Tagged
  modded bosses receive no automatically enabled profile. New built-in boss records
  remain disabled, with no lifecycle/farming and no new Essence encoding.
- Explicit vanilla ordinary-tier assignments are Villager (T1), Iron Golem,
  Skeleton and Snow Golem (T2). These override category and biome inference.
- Explicit vanilla otherworldly/containment exceptions are Evoker, Breeze,
  Guardian, Illusioner, Phantom, Enderman, Endermite, Shulker,
  Blaze, Ghast, Magma Cube, Piglin, Piglin Brute, Zombified Piglin, Hoglin, Zoglin,
  Strider, Wither Skeleton and Vex. Their initial tier is T3.
- Entity-type tags `mobvats:nether_mobs`, `mobvats:end_mobs`,
  `mobvats:teleporting_mobs` and `mobvats:supernatural_mobs` request T3.
- Spawn entries in biomes tagged as Nether or End by Minecraft or the common
  biome tags request T3 for species without an explicit assignment, even if the
  species also spawns in the Overworld.
- Ordinary Overworld monsters start at T2. A modded monster with no recognized
  Overworld affinity starts conservatively at T3 with an ambiguity diagnostic.
- Other non-MISC mob categories start at T1. MISC mobs start at T3 with an
  ambiguity diagnostic.

Enderman’s initial T3 comes from species data, independent of capture location.
The profile remains authoritative afterward; an editor may deliberately change
its tier. Existing Slice 1 files keep their previous assignments. To adopt newly
classified defaults, explicitly back up and remove only the desired species files
before reloading; there is no automatic upgrade of inferred values.

New files include a `discovery` metadata object with their initial tier,
ambiguity flag and the category, observed tags, biome affinities and explicit
exceptions used. Source inspection includes this provenance and the currently
configured minimum tier. Metadata describes initial generation, not a live
classification override. Unknown tags are recorded as unclassified signals;
their abilities or meaning are not guessed.

Generic modded profiles enable configured capture/encoding and representation
but have empty processes and Tool interactions, without inferred breeding,
feed, loot or special abilities. Vanilla profiles retain their supported
processing defaults. Boss recognition is limited to known species and the boss
tag; magical abilities, boss status, special interactions and renderer
compatibility cannot be universally detected. Review ambiguous or unsupported
species explicitly. Missing-mod files persist and are never deleted by a scan.
