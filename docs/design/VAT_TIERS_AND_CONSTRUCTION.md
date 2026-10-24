# Vat tiers and construction — design decisions

Status: Slices 1–3 implemented: structural tiers, T1/T2 Survival construction, and per-Head hostile activation.
Last updated: 2026-10-23.

## Principle

Vats are purpose-built mob-production installations, not a mandatory upgrade ladder. An existing copper cow Vat remains useful indefinitely. Players expand or repurpose production by changing functional machinery (for example, adding a Blender instead of a sword Tool Port), not necessarily rebuilding the Vat in a higher tier. Higher tiers enable more demanding specimens rather than inherently multiplying speed or yield.

## Structural tiers

| Vat tier | Structural material | Maximum exterior dimensions | Intended access |
| --- | --- | --- | --- |
| T1 | Copper | 9 x 7 x 9 | Passive specimens |
| T2 | Iron | 9 x 9 x 9 | Passive and eligible hostile specimens |
| T3 | Blood Iron | 12 x 12 x 12 | Larger/advanced specimens; future progression |

These are per-axis maxima, not required cubic footprints. The minimum exterior size is derived from the geometry and machinery needed for the installed specimen(s), with a valid 3 x 3 x 3 Vat possible for a one-cell chicken. Do not retain an arbitrary global 3 x 4 x 3 minimum.

A Vat's structural shell must use a single tier throughout. Tier-mixed shell casing, glass and clad exterior service cells are invalid. There is no intrinsic higher-tier throughput or efficiency bonus.

## Structural and mechanical items

Casing is the base tier-specific construction material. Vat Glass is crafted using matching Vat Casing and glass. Controllers may be tier-specific via crafting recipes containing their tier's casing; controller/shell tier agreement is required. No controller upgrade pathway is required.

Tool Ports, Blenders, item/fluid ports, pumps/valves, conduits, buffers, Heads, Harnesses, Sumps and other functional mechanisms are *universal functional items*. Shell-mounted machinery can be clad with casing or glass of the appropriate family/tier, charging the higher-tier material cost without duplicating the functional item registry or recipe catalog. A bare interior mechanism remains subject to its existing placement rules. Structural cladding does not confer an independent capability upgrade. Particular future components or upgrades may have explicit minimum-tier compatibility requirements.

Preserve the current installed-material/cladding architecture and its material drop/replacement behavior. Extend material registration, appearance and shell validation rather than introducing per-tier copies of every actor.

## Implemented construction (Slice 1)

Creative exposes Copper casing/glass/Controller under the established IDs `vat_casing`, `vat_glass`, `vat_controller`; Iron uses the corresponding `_t2` suffix and Blood Iron uses `_t3`. All Controllers share the existing entity, menu, management APIs and process engine. Tier is derived from the Controller block; it adds no process multiplier. T2+ permits hostile specimens subject to per-Head lightning activation.

Installed materials `vat_t2_casing`, `vat_t2_glass`, `vat_t3_casing`, `vat_t3_glass` extend the existing `cladding_material` property. Sneak-installation/replacement consumes and recovers the corresponding structural item. Functional item registrations remain universal. Harness/Sump shell mounts also require installed matching material; their existing IDs, interior support geometry and association rules remain intact.

Validation checks only the actual exterior shell for tier agreement and reports the first offending coordinate with expected/actual tiers and materials. Controller dimensions are independent per axis; X/Z limits are equal, preserving horizontal rotation. The base geometric floor is 3×3×3 (one interior cell); actual specimen ownership and Harness/Sump rules determine whether it forms. A cow still needs its two-cell vertical substrate. One-block comfort-shell and happiness rules are unchanged.

Tier comes from current block state on formation/revalidation/reload; no saved tier migration is needed. Existing structural/cladding change notifications trigger revalidation. Disposable development worlds may be regenerated rather than migrated.

## Survival construction (Slice 2)

T1 Copper and T2 Iron are Survival-craftable. One Copper/Iron ingot plus ordinary Glass makes four matching casings. One matching casing plus Glass makes four matching Vat Glass; one matching casing plus Redstone makes one matching Controller. No hostile drops, XP, lightning or Strange Meat are needed for T2 construction. T3 Blood Iron casing, Glass and Controller remain registered in Creative with no Survival recipes or manufacturing chain.

Universal machinery uses Copper for fluid transport/storage, ports, pumps, valves, disposal, feed delivery and Sumps; Iron for item transport, ports, tools and motion; Iron plus Leather for Harnesses; and Iron, a Stonecutter and a Diamond for the Blender. These are crafting costs, not mechanical tiers. Conduits still craft in batches of four, and Mixer Shafts in batches of three. Existing pump/void mode switches and conduit trunk/tap conversions remain reversible. Matching structural cladding is still charged separately for exterior mechanisms, including Harnesses and Sumps. No functional tier variants, transfer/actor speed changes or inventory changes are introduced. See [recipe audit](../SURVIVAL_RECIPES.md).

`mobvats:strange_meat` is a distinct registered fluid with its own source, flowing state, FluidType and bucket. It is available in Creative for fluid-handling checks. It cannot enter ordinary Meat extrusion even if optional composition is absent or mentions Meat. Activated hostile Blenders produce Strange Meat; passive defaults remain ordinary Meat. No Strange Meat solids or extrusion recipes are implemented. See [hostile specimens](../HOSTILE_SPECIMENS.md) for formation, rod placement, dormancy and persistence rules.

## Future Blood Iron manufacturing

Blood Iron is intended to be made using the Mixer from T2 Iron Casing and material derived from hostile specimens (provisionally Strange Meat), potentially with XP or another reagent. Favor direct production of Blood Iron Casing over inventing an unnecessary intermediate. Craft T3 glass from T3 casing. **No final Blood Iron recipe or precise Strange Meat form has been agreed yet.**

## Planned specimen tier requirements and discoverability

**Status: agreed design direction, not implemented. Boss containment is excluded from this phase.** See the ordered [mob-profile schema (Slice 1) and tier discovery (Slice 2) implementation plan](MOB_PROFILE_SCHEMA_AND_TIER_DISCOVERY_SLICES.md). Slice 1 records the minimum-tier field without enforcing it; Slice 2 supplies classification and tier enforcement.

Containment requirements must be **configurable per entity type**, including third-party modded entities. A default tier must also be discoverable for a newly installed mod's mobs without requiring a prewritten MobVats config file.

- **T1 Copper:** ordinary passive/non-hostile mobs.
- **T2 Iron:** conventional Overworld hostile mobs.
- **T3 Blood Iron:** Nether and End mobs, teleporting/warping species and other supernatural containment exceptions. **Endermen are explicitly T3**, even in the Overworld, because their warping requires Blood Iron.
- **Bosses:** do not assign automatic T3 permission, implement boss farming, or include them in the current progression pass. Boss eligibility and resource costs require a separate future design (possibly involving a Nether Star and further components).

Treat these as **one-time generated classifications**, not immutable rules. Once written to an individual per-species config file, values are operator-owned and never automatically re-inferred. An operator-authored exact entity-ID override wins over inferred/default tier. Support data-driven tags/group overrides where practical, so modpacks can adjust multiple species. Integrate with the existing capture policy and profile configuration instead of creating a conflicting second whitelist.

### Unknown modded mobs

Provide a documented *best-effort* discovery/classification path for registered mobs that lack explicit profiles. Use trustworthy metadata when present (entity type category, data-pack entity tags, known or configured dimension affinities and explicit special-case overrides), with clear provenance. Do not infer an entity's native dimension solely from where one individual happened to spawn, and do not assume the registry namespace or hitbox size determines magical containment. Not all modded mob abilities, including teleportation, are reliably introspectable; an exact override or tag is the correct fallback.

A generated/inspected entry should expose entity ID, inferred minimum tier, reason/source for the inference, capture support status and any unresolved classification. Discoverability should not imply every modded entity is safely capturable or processable: species missing supported geometry, representation, lifecycle or handlers need explicit diagnostics and must not silently bypass safety gates. Unknown or ambiguous supernatural properties must not accidentally grant risky T1 containment; settle the conservative fallback policy in implementation design and test it.

Tests should demonstrate an unconfigured ordinary modded passive, an Overworld hostile, a Nether/End creature, the Enderman exception, explicit overrides, ambiguous/unknown species and boss rejection. Expose the effective tier in the relevant specimen/Head inspection interface.

## Future option: unstable under-tier containment and breaches

**Status: speculative / not implemented.** Current tier eligibility remains a hard requirement. In particular, Endermen require T3 Blood Iron because their teleportation must be contained; a T2 Iron Vat cannot currently process them.

A possible later system would allow *selected* T3 specimens in T2 Iron Vats with a nonzero chance of containment failure. This should be explicit per-specimen eligibility, not blanket permission for every T3 mob or boss. Blood Iron remains the safe, reliable way to contain the same specimen.

On a failure, the encoded specimen escapes as an actual mob outside the Vat, and its Head/Flesh representation is removed or cleared from the machine. This is a real loss of the captured specimen, not merely a temporary paused state. A more severe breach may additionally destroy a contiguous patch of the Vat's own exterior shell, approximately matching the specimen's captured width and height on the affected wall. The resulting structural opening naturally invalidates/disassembles the Vat until repaired; other specimens may be affected by that loss of formation.

Possible balancing direction: roll on committed biological events (e.g. growth/harvest), not continuously per tick or while unloaded, to make risk track operation without idle-time punishment. Exact chance, eligible mobs, trigger events, whether shell damage is probabilistic or always occurs, wall selection, geometry constraints, material drops, fluid-spill handling and configuration are **undecided**.

Safety/design constraints for any implementation:
- Confine optional structural destruction to eligible blocks belonging to the breached Vat; never damage unrelated buildings.
- Respect Minecraft protection and mob-griefing expectations, and check for a safe real-world spawn location.
- Clear the correct Head/Flesh ownership and process state atomically to avoid duplicate escapes or drops, including across save/reload.
- Never attempt escape rolls or load world chunks while the Vat is unloaded.
- Test multi-specimen assemblies and cases where a wall patch intersects ports, glass, cladding or controller blocks.

This is deliberately deferred until after normal T3 containment/progression design. Do not implement or relax today's tier gates as part of documenting the idea.

## Implementation acceptance

- Tier-compatible shells only, including clad exterior machinery; universal functional items preserved.
- Rectangular vats and geometry-derived minima; T1 maximum 9 x 7 x 9.
- No accidental bonus for higher tier alone.
- Hostile T2+ formation and independent lightning activation are covered by headless tests.
- Validate actual specimen/Tool Port line of sight and construction in GameTests; do not assume illustrative layouts are formable.
