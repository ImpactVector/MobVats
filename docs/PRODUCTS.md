# Meat and Waste products

Five standalone products are registered in the existing MobVats creative tab,
in this order: Raw Meat (`raw_meat`), Cooked Meat (`cooked_meat`), Fertilizer
(`fertilizer`), Meat Block (`meat_block`), Waste Block (`waste_block`). All stack
to 64. They have separate identities; no beef or bone-meal equivalence tags are
added.

Raw Meat uses vanilla beef food properties (3 hunger, 1.8 saturation points).
Cooked Meat uses vanilla steak properties (8 hunger, 12.8 saturation points).
Furnace, smoker and campfire recipes cook one Raw Meat into one Cooked Meat in
200, 100 and 600 ticks respectively, each awarding 0.35 experience.

Fertilizer is a distinct registered `BoneMealItem`. Its inherited use path
handles vanilla growables, underwater plants, growth events, particles and
consumption. Standard NeoForge bone-meal hooks receive the Fertilizer stack.
Valid targets consume one even if their probabilistic growth attempt fails;
invalid targets pass without consumption. Integrations that explicitly require
the vanilla item identity or a tag rather than the standard behavior may not
accept it.

The two decorative blocks are full cubes with hardness/resistance 0.6, slime
sounds for Meat Block and mud sounds for Waste Block. They can be mined by hand
and drop themselves. They have no block entities, tick behavior or food effects.

`tools/generate_product_resources.py` regenerates the five original 16x16
procedural pixel textures, models, blockstates, loot, translations, atlas entries
and cooking recipes. It uses fixed palettes and integer pixel patterns, without
image inputs, overlays, animation or custom renderers. Visual acceptance in the
Minecraft client remains a user check.

## Future Extruder contract (not implemented)

- 250 mB Meat -> 1 Raw Meat.
- 1000 mB Meat -> 1 Meat Block.
- 100 mB Waste -> 1 Fertilizer (corrected product handoff).
- Waste -> Waste Block quantity remains undecided.

There are no fluid-to-item recipes, block compression/decompression recipes,
Extruder registration or changes to existing Blender, Mixer, Vat or fluid logic
in this slice. The future directional Extruder and its transfer/backpressure
behavior remain a separate implementation.

`ProductGameTests` covers registration/order, eating, cooking recipe resolution,
fertilization and consumption, survival placement/mining/replacement and required
resources. Existing `ProductFluidGameTests` continues to cover fluid IDs,
capabilities, buckets, placement/pickup and process selectors unchanged.
