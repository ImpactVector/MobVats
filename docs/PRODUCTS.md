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

## Extruder

- 250 mB Meat -> 1 Raw Meat.
- 1000 mB Meat -> 1 Meat Block.
- 100 mB Waste -> 1 Fertilizer (corrected product handoff).
- 1000 mB Waste -> 1 Waste Block.

The directional Extruder uses registered fluid identity to select Meat/Waste
conversions. Optional composition cannot substitute for that identity. Its
existing transfer/backpressure behavior and quantities are unchanged by Slice 2.

## Strange Meat fluid

`mobvats:strange_meat`, `mobvats:flowing_strange_meat`, its separate FluidType,
liquid world block and `mobvats:strange_meat_bucket` are registered alongside
ordinary `mobvats:meat`. The bucket is available in Creative. There is no Strange
Meat solid item, solid product block, extrusion recipe or Rotten Flesh conversion.

Strange Meat stays distinct through standard NeoForge handlers, buffers, buckets,
fluid-stack serialization, world placement, world flow and pickup. Like ordinary
product fluids, container transfers preserve optional metadata; world placement
does not promise composition preservation. Losing composition cannot make this
fluid edible Meat. It has no equivalence tags with ordinary Meat, and the Extruder
rejects it in both modes even if its composition mentions ordinary Meat.

The client uses fixed procedural rotten-flesh pixels from
`tools/procedural-art/drawing.json`: opaque murky red-brown with desaturated flesh
highlights, sparse gray-olive decay and dark brown shadows. The Gradle art generator
creates the texture; the standard dynamic bucket model uses the same fluid
appearance as tanks, buffers and world rendering. No hand-maintained PNG copy is
added. In-client visual acceptance remains an operator task.

Hostile-specimen Blender production is future work and must respect T2+ shells
and lightning-activated Heads. No hostile defaults, capture policies or activation
rules change here. Existing `harvest.blender` configuration accepts the explicit
`mobvats:strange_meat` output and execution honors that ID; compatible output
storage must accept it. Passive defaults still produce ordinary Meat. General
resource-process fluid outputs also resolve registered IDs normally. This does
not introduce an automatic hostile output rule or a new Mixer processing stage.
Blood Iron manufacturing, essence requirements and Strange Meat solid products
remain undecided.

`ProductGameTests` covers registration/order, eating, cooking recipe resolution,
fertilization and consumption, survival placement/mining/replacement and required
resources. Existing `ProductFluidGameTests` continues to cover fluid IDs,
capabilities, buckets, placement/pickup and process selectors unchanged.
