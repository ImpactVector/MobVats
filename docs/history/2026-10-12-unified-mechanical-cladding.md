> Historical record. Original: `UNIFIED_MECHANICAL_CLADDING.md`. Current reference: [02-MULTIBLOCKS.md](../02-MULTIBLOCKS.md).
> Archive date basis: Git introduction author date 2026-10-12T11:54:42-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Unified mechanical blocks and applied cladding

Mechanical inventory items represent functions. Casing and glass are materials installed on placed machinery, not mechanical item variants.

## Registrations

| Scope | Before blocks | After blocks | Before items | After items |
|---|---:|---:|---:|---:|
| Conduits | 10 | 2 | 20 | 4 |
| Fluid Buffer | 5 | 1 | 5 | 1 |
| Ports, pumps, valves, voids and specimen service functions | 19 | 11 | 19 | 11 |
| All MobVats registrations, including structural parts, creative devices, fluids and buckets | 60 | 40 | 72 | 44 |

There are two resource-specific conduit blocks, one buffer, and one block/item for each of `item_port`, `fluid_port`, `tool_port`, `feed_port`, `blender`, `pull_pump`, `push_pump`, `fill_valve`, `drain_valve`, `item_void` and `fluid_void`. Item/Fluid Conduit TRUNK and TAP items remain temporarily, with one shared block per resource. Mode remains the `mode` blockstate property. Mixer input/output direction-only registrations were removed; port direction remains functional endpoint configuration.

Vat/Mixer controller, casing, glass, rotor, drives, specimen substrate and other genuinely distinct structural functions retain their existing registrations. Existing family-specific functional Block Entity types remain ZeroCore adapters; they are not compatibility block/item entries.

## Installed material and interactions

The single `cladding_material` enum property supports `none`, `vat_t1_casing`, `vat_t1_glass`, `mixer_t1_casing` and `mixer_t1_glass`. It determines the exact material item, shell family and glass/casing treatment. No additional entity exists solely to store cladding, and no NBT field duplicates installed material.

Place the bare function item, then shift-right-click it with an actual casing/glass item. The casing/glass item's `useOn` handles Minecraft's sneaking placement bypass and invokes the same shared policy as the block interaction. Survival installation consumes one item. Creative installation consumes none. Identical material does nothing. Replacement returns the old material to inventory, dropping it if insertion fails. Unsupported materials are rejected. Vat-only service functions reject Mixer material; the five shared port/pump/void functions support both families.

Only the targeted block changes. Connectivity, neighbors, controllers and assembly never select or propagate material. TRUNK/TAP mode is preserved. There is no removal tool in this slice; replace material or break the block.

## Resource preservation and drops

Installed materials are recovered exclusively through block loot tables, using material-state conditions and normal explosion survival rules. No removal callback also emits cladding. Bare machinery drops its universal function item; clad machinery additionally drops its exact material item. Silk Touch uses the same path. Normal creative destruction suppresses both drops. Pick Block returns the universal function item (including the selected conduit mode), with the existing portable functional state where applicable.

Buffers retain the same entity, tank, fluid components and filter through every material replacement. Same-family fixture changes retain their entity. Explicit cross-family changes select the existing Vat or Mixer ZeroCore adapter and transfer whitelisted functional data through ZeroCore's public sync contract. Ownership and assembly snapshots are excluded. Resources belonging to the inactive adapter, such as a Vat staging slot versus a Mixer fuel store, remain retained and restore if the family is changed back. Portable machinery drops preserve this data. Port staging and specimen tool resources are retained in the machinery item rather than emitted a second time by a removal callback.

## Structural and physical behavior

Conduit and buffer structural permission, continuation, bounds and passive part counts read installed material instead of constructor fields. Vat and Mixer envelopes stay separate. Casing/glass position permissions remain family-specific. Bare passive machinery retains its interior permissions. Mechanical entities must satisfy both their existing functional placement rule and the installed material's shell rule; existing interior service permissions remain available.

Only passive conduits and buffers contribute to passive bridge counts. Functional entities remain ordinary ZeroCore parts and are counted once. Cladding changes enqueue structural revalidation, topology invalidation and buffer presentation refresh. Controller ownership continues to use ZeroCore. No framework modifications were made.

Bare conduit collision is empty; clad conduit collision is full-block. Existing routing, TAP clearance, occupied geometry, buffer displacement and compatible-buffer joining remain unchanged. Mechanical shells use non-occluding models to support glass and open construction presentation. No new waterlogging capability was added.

Only T1 structural materials exist. T2/T3 gameplay and mixed-tier formation tests are outside the current registered content. Future materials extend the installed-material enum, actual structural items/policies and procedural palettes; they do not add mechanical block/item registrations or tiers to mechanical functions.

## Rendering

Material blockstates select generated procedural construction models. Universal inventories use bare machinery. Clad, unformed models add the existing family construction frame; formed chunk models select the material shell and remove that frame. Stationary fixture rendering retains mechanisms, pump orientation/sleeves, void treatments and service fixture mounting. Installed shells replace the old exterior service backing plate to avoid coincident geometry. Glass shells retain their translucent layer. Conduit formed meshes and buffer reservoirs keep their existing render owners and joining geometry. No runtime textures are generated. Art-only T2/T3 palette previews remain available.

## Direct consolidation and disposable fixtures

No legacy world migration, aliases, data fixers or compatibility blocks/items were added or retained. Old material-specific conduit/buffer blocks and items, duplicate Mixer mechanical blocks/items, direction-only Mixer ports, their item models, blockstates, recipes and stale model aliases were removed. Material appearance models remain because they render installed states.

Disposable worlds must be regenerated. Every fixture builder uses explicit final unified material states, including standard acceptance, creative production, four-cow production, operator Vats, Mixer fixtures and minimum-XP P-06. World generation never simulates player cladding. Historical placement GameTests that exercise gameplay place bare conduits and then use the material interaction.

`tools/verify_unified_world.py` reads Anvil block palettes and item inventories, rejects obsolete IDs, and checks material/mode properties. It deliberately permits the existing functional Block Entity IDs.

## Tests and validation

Nine `AppliedCladdingGameTests` cover the function/material interaction matrix, survival and creative consumption, replacement and identical-material behavior, invalid/non-sneaking inputs, no propagation, collision, bare/clad loot, Pick Block, buffer fluid/components/filter preservation, cross-family port settings and dormant resources, player-triggered formation/disassembly, exact blockstate/mode serialization, registry counts, creative destruction, and Silk Touch/explosion loot. Existing ownership, topology, lifecycle, routing, buffer mesh and procedural geometry regressions remain in the suite. A CPU rendering regression checks that formed bare fixtures add neither a shell nor a construction frame.

Final command results and the reproduced baseline failures are recorded in `2026-10-12-unified-cladding-validation.md`. Client visual acceptance remains manual. Nothing was committed, published, released or deployed.
