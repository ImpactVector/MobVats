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
Automatic discovery of modded mobs is deferred to slice 2; a manually supplied,
valid registered entity profile can still load.

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

Slice 1 stores the minimum tier but does not enforce it. Built-in initial data
uses T1 for non-hostiles, T2 for hostiles and T3 for Enderman. The isolated legacy
containment gate still prevents hostile specimens from forming in T1; hostile
Heads still require their existing lightning activation. Other tier assignments,
registered-mob discovery and applying tier edits to existing Vats belong to
slice 2.
