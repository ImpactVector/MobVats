# Extruder

The Extruder is a standalone block with one persisted output slot and no tank,
energy cost, GUI, or multiblock ownership. Place its rear against a fluid output;
the front plaque faces the placing player. Items leave through the bottom.
The front plaque depicts a portion or a building block. Empty-hand
sneak-right-click changes the form for future batches. Buffered items remain.

| Input | Portion | Block |
| --- | --- | --- |
| Meat | 250 mB → 1 Raw Meat | 1,000 mB → 1 Meat Block |
| Waste | 100 mB → 1 Fertilizer | 1,000 mB → 1 Waste Block |

Every ten server ticks it first attempts downward insertion, then produces at
most one item. Without a destination, output accumulates up to the item's stack
limit. A different product waits until the existing stack is removed. Bottom
automation can extract, including simulated extraction; insertion and other
faces are unavailable. No blocked-output item entities are spawned. Breaking
the machine drops buffered items normally; its block item retains form using
the standard vanilla block-state component and loot copy-state function.

## Capabilities and fluid identity

The rear neighbor is queried for NeoForge `Capabilities.FluidHandler.BLOCK` on
the side facing the Extruder (`FACING`). The block below is queried for
`Capabilities.ItemHandler.BLOCK` on `UP`. NeoForge capability caches track
neighbor invalidation; changing facing recreates the input cache. No controller
inspection or direct neighbor storage access is involved.

MobVats buffers now expose a small standard capability adapter over the same
filtered logical cluster used by their manual container interaction. Every
operation resolves the current cluster, so retained capabilities cannot access
removed or unloading members or bypass changed filters. Existing storage and
routing algorithms are unchanged. MobVats conduit blocks deliberately have no
capability of their own: use their configured passive output fluid interface.
Third-party pipes and tanks work directly when they expose `IFluidHandler`.

Candidates follow the handler's tank order. The first supported identity with
output space and a complete simulated drain is selected. Both simulation and
execution use a `FluidStack` request with the sample's complete data components;
amount-only drains are never used. Different components are never combined by
the Extruder. The handler remains responsible for honoring its stack-specific
drain contract, transport rules and filters.

Meat and Waste accept all their existing component-bearing identities. They
are ordinary product fluids, not the compound slurry identities to which the
culture fouling rules apply. Slurry and component fluids are rejected even if
their formulas mention Meat or Waste. Composition does not create additional
forming recipes or transfer to the ordinary solid product; metadata on the
remaining source fluid is unchanged.

If execution differs from a successful simulation, no item is produced and a
warning is logged once per loaded Extruder. A nonconforming external handler
can still lose extracted fluid: there is no safe general rollback across
external capabilities, and the machine does not attempt one.

## Assets and acceptance

The two ordinary JSON models reuse the existing casing, rim, inner surface,
Raw Meat and Meat Block textures. No new raster artwork, AI asset, custom
renderer or animation is introduced. Eight blockstate variants rotate the
front plaque and rear input together.

Manual client acceptance remains: front/rear readability, both form plaques,
horizontal placement/rotation, inventory appearance, and downward connection
appearance with a barrel or pipe. Functional verification uses autonomous
headless GameTests; no graphical client is launched.

## Validation

Validated October 16, 2026 against product-slice base
`e67b9adc087b843db4984ccd88888395f9b312e0` using the canonical Windows Java 21
launcher: `format`, followed by `validate` after repairs. Final
`spotlessCheck build runGameTestServer` completed successfully: **882 required
GameTests passed**, including **32 new Extruder tests** and all 850 existing
tests. Geometry, resource packaging, development-hook exclusion and CPU checks
also passed. The existing registry-count assertion now includes the single new
block and item. No client, demo publication or production publication was run.
