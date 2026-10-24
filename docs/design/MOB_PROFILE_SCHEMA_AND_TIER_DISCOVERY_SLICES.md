# Mob profile schema and tier discovery — two implementation slices

Status: **Slices 1 and 2 implemented and validated.** Recorded 2026-10-24. See [profile configuration](../MOB_PROFILES.md) for the implemented storage and schema contract. Slice 1 validation passed 1,171 required autonomous headless GameTests. Final Slice 2 validation: Java 21 spotlessCheck/build and all 1,178 required autonomous headless GameTests passed on 2026-10-24.

## Boundaries

These are deliberately separate, ordered slices:

1. **Slice 1 — per-species persistent configuration and schema contracts.** Refactor the existing mob definition loader/storage and preserve current game behavior. Include a minimum Vat tier field but **do not implement tier discovery or new enforcement here**.
2. **Slice 2 — tier assignment and modded-mob discovery.** Classify registered species, generate one-time profiles and apply the data-defined tier gate. No boss support in this phase.

Existing MobVats pre-release profiles may be **discarded and regenerated**, including the current `config/mobvats/passive_mobs/` bundle/override arrangement. Do not build a compatibility migration for that old pre-release layout. Keep save-world/specimen safety and tests where applicable.

## Slice 1 — individual mob files, versioned schema, and centralized policy

**On-disk authority:** `config/mobvats/mobs/<namespace>/<path>.json` for entity `namespace:path`; nested entity paths must be mapped safely. One **complete, independently editable** JSON document per entity ID, not a monolithic `_defaults.json` plus override files. Every file contains `schema_version` (integer) and `entity` (exact registry ID), with all existing mob profile behavior/configuration retained. One file is one definition; enforce entity/path agreement, reject duplicates/path traversal, report file-specific errors.

**Schema version is independent of the MobVats mod version.** New files use current schema. Implement explicit in-code stepwise migration contracts, e.g. v1→v2, invoked only when an older file schema is found. Migrations **add missing fields** and preserve author-chosen values; never refresh inferred defaults or silently reset a file. Validate and atomically persist migrated files, with original backup on upgrade; a bad/too-new file remains unchanged and disabled with a clear diagnostic. Unknown fields should survive schema upgrade (e.g. use JSON tree-preserving operations), even where runtime deserialization remains strict. No backward migrations. Mod version alone must not trigger rewrites.

**Tier data field:** include `minimum_vat_tier` (1, 2, 3) in the per-mob schema now, as author-controlled data. It is **not an enforcement or discovery feature in this slice**. Existing known built-in species can have explicit initial values; final assignment and automatic inference belong to Slice 2. Do not let tier become an unrelated `capture` permission or actor-specific setting.

**Remove T1/passive eligibility hardcoding:** consolidate tier eligibility as a data-driven mob rule rather than scattering tests for passive/monster categories or implicitly assigning all passive specimens T1 in formation code. For Slice 1, preserve the existing T2+ hostile safety restrictions using a narrowly isolated compatibility check until Slice 2 wires tier gating to `minimum_vat_tier`; do not accidentally allow hostiles in T1 while refactoring. Other species-specific processing defaults belong in the generated/configured profiles, not tier-specific hardcoded Java branches. No new speculative processing behavior.

**Loading:** parse/validate the per-file catalog and publish it atomically, retaining the current efficient in-memory snapshot/cache for per-tick processing. Disk is the durable source, not an extra inference database. Reload command `/mobvats reload_passive_mobs` may retain its legacy name temporarily but must load the new location; a friendly rename/alias is optional. On invalid files, surface diagnostics and never auto-overwrite admin edits. Keep absent-mod definitions on disk; do not destructively delete on missing entity registration. Document how invalid/unavailable files affect processing and existing Heads; no silent regeneration of an existing file.

**Slice 1 acceptance:**
- Files live at one per-species path, include schema version and minimum tier, and load exactly the same processing/capture/tool behavior as before.
- Existing pre-release config can be blown away without carrying old override semantics; new output is editable and reloadable.
- A schema migration adds a new field to an older document without losing authored/unknown fields, safely and atomically. Current/newer/bad documents are left untouched as appropriate.
- Entire runtime catalog is published consistently; ordinary operations don't parse files each tick.
- T1 passive and T2 hostile behavior does not regress while tier enforcement is deferred.
- Individual mob edits survive restart and are not overwritten by generation.

## Slice 2 — discover registered mobs and assign tiers

**Trigger:** on server start at a point where entity registration, entity-type tags, and relevant datapack/biome spawn information are ready; also rescan on appropriate registry/datapack refresh. **Never infer at syringe capture** or each Vat construction. Construct the disk catalog once per previously unknown registered entity type; newly discovered IDs get a complete profile in the Slice 1 schema. Existing files are authoritative and **never re-inferred**, regenerated or changed because mod versions/spawn lists changed. Missing mods do not cause their files to be deleted.

**Default policy:**
- T1 Copper: ordinary passive/non-hostile mobs.
- T2 Iron: ordinary Overworld hostile mobs.
- T3 Blood Iron: Nether/End-affiliated, teleporting/warping or other supernatural containment exceptions; **Enderman always T3 by built-in initial data**, regardless of capture location.
- Bosses **excluded entirely** from automatic eligibility and generic farming; revisit boss costs/Nether Star separately.
- Unknowns conservatively classified with diagnostics; do not claim universal detection of magical abilities, native dimensions, boss status, special tools or unknown lifecycle processes. Entity capture location does **not** determine species tier.
- Source classification signals may include entity category, recognized tags, spawn affinities, explicit known exceptions. Tag/group policies may help **generate defaults for new species**, but must not override already authored per-species values. Show classification provenance/ambiguity for editors and inspection.

**Runtime:** formation and existing-specimen revalidation consult `minimum_vat_tier` in the loaded species profile, not separate hardcoded passive/T2/Enderman checks. Config edit/reload applies immediately to **existing** specimens and Vats, even when it makes an assembly invalid; operator is responsible. No protective grandfathering. Avoid implementing future probabilistic containment breaches in this slice.

**Compatibility:** preserve explicit capture policies, lightning activation of hostile Heads, existing processing executors, and unsupported-mod diagnostics. Generated profile is not a promise that every unknown species has valid breeding, special interactions or renderer compatibility; no automatic tool probing or synthetic specimen capture-time classification.

**Slice 2 acceptance:**
- First server startup discovers new registered modded passive, Overworld hostile, Nether/End types and generates per-entity files in the current schema.
- Reboot/refresh does not overwrite a customized mob file, including manually edited tier; a new entity generates a new file. Removed-mod files persist.
- Enderman receives T3, conventional Overworld hostile T2, ordinary passive T1, ambiguous specimen conservative handling, and bosses excluded.
- T1/T2/T3 placement and revalidation use the config field; changing a tier on disk and reloading changes eligibility of **already encoded** specimens without changing their identities.
- Unit/GameTests exercise malformed config, schema migration, mod removal, unknown tags, respawn/reload and no T1 hostile regression.

## Explicitly out of scope

- Boss progression, special containment requirements, Nether Star recipes and under-tier breaches.
- Blood Iron manufacturing.
- Automatic discovery of unknown right/left-click tool interactions and Tool Port learning; tracked separately in [Tool Port design](TOOL_PORT_FALLBACK_AND_LEARNING.md).
- Reconstructing arbitrary modded mob AI or special abilities for profile generation.

See [Vat tiers](VAT_TIERS_AND_CONSTRUCTION.md) for the broader tier design.
