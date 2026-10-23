# Culture XP depletion and restoration — design decisions

Status: agreed behavior, not necessarily implemented. Last updated: 2026-10-23.

## Goal

Enriched culture should have **ongoing XP upkeep**, not merely a one-time XP creation cost. Biological work fouls portions of medium; culture restoration consumes fresh XP to replace the lost XP enrichment. Keep the existing separate clean-mB / dirty-mB quantities in Vat storage, and preserve compound-fluid formulas, real fluid amounts, fill/drain behavior and selective draining.

## Discrete conversion, not continuous dilution

A fouling event converts X mB of *clean* culture into X mB of *dirty* culture. For the dirty portion's **normalized composition formula**, remove the entire XP component and replace it with an equal amount of Waste. Do not reduce the clean portion's XP concentration or progressively mutate one shared formula to a ladder of partially depleted values.

Other formula components (water and unrelated additives) remain unchanged. Existing physical volume is preserved. The dirty culture retains a distinct formula from the clean culture, while clean/dirty Vat amounts remain separately tracked.

Example recipe ratio (illustrative): 1,000 mB water + 100 mB XP produces 1,000 mB culture. With 900 mB clean and 100 mB fouled:
- 900 mB clean fluid has its original normalized formula: water 1,000 + XP 100.
- 100 mB dirty fluid has normalized formula: water 1,000 + Waste 100.
- Restoring the dirty 100 mB requires **10 mB actual XP fluid**, not 100 mB.

For the *currently bundled* enrichment recipe of 250 mB XP per 1,000 mB culture, restoring 100 mB fouled medium instead requires **25 mB XP**. Recipe ratios should remain data-driven and tunable.

## Separate Mixer operations: filtration then enrichment

Culture **always remains Culture** throughout fouling, filtering, and re-enrichment. Filtering must never turn it into ordinary water merely because its Waste and XP components have been removed. The fluid identity is separate from its normalized compound formula.

Example normalized formulas per 1,000 mB of Culture (using an illustrative 100 mB XP enrichment ratio):

| State | Fluid identity | Normalized formula |
| --- | --- | --- |
| Fresh | Culture | Water 1,000 + XP 100 |
| Fouled | Culture | Water 1,000 + Waste 100 |
| Filtered | Culture | Water 1,000 |
| Re-enriched | Culture | Water 1,000 + XP 100 |

**Stage 1 — generic filtration:** Charcoal removes Waste from a compatible mixture, leaving other formula ingredients and fluid identity unchanged. The filter should operate against Waste concentration/quantity in the normalized composition, with removal capacity proportional to actual processed volume (equivalent mB per 1,000 mB), rather than being a special recipe that restores enriched culture wholesale. The exact charcoal capacity and batch economics remain to be tuned. Existing lava-specific filtering can remain a separate material rule.

**Stage 2 — enrichment:** A distinct Mixer operation adds actual XP fluid to filtered Culture, restoring the XP formula component at the intended ratio. For 100 mB of filtered Culture derived from a 100-XP-per-1,000 recipe, consume 10 mB XP; with the currently bundled 250-XP-per-1,000 recipe, consume 25 mB. Do not simultaneously remove Waste in the enrichment stage.

These are separate recipe stages, **not a requirement to own two Mixers**. One programmable Mixer may process both at different times; two dedicated Mixers and intermediate fluid storage enable a continuous automated filtration → enrichment pipeline. Neither stage creates or destroys physical Culture volume; don't discard other composition additives.

Generic filtration must not silently restore XP for free. Preserve the distinction between contaminated culture, filtered XP-free culture, and enriched culture for matching and routing. The exact handling of incidental Waste already present in a mixture, fractional restoration remainders, and filter material capacities must be specified and tested before implementation.

Avoid creating many distinct partially enriched formulas. The chosen clean/dirty formula pair and separate physical quantities should remain stable, even for small fouling and partial drains/filtration. Ensure fractional XP requirements across arbitrary partial batch sizes are accurate and conserved (using appropriate integer-mB/remainder handling, not exploitable rounding).

## No hidden upkeep

Fouling occurs on successfully committed recognized biological work, not continuously per tick or on failed/blocked operations. XP consumption happens at restoration in the Mixer, not secretly inside the Vat. Culture can be run dirty at reduced performance or maintained using a filtration/XP supply loop.

## Current implementation versus target

As documented in the existing 2026-10-05 Slice 12.2 behavior, current `CultureFouling.dirty` **adds** Waste and `filter_general`/`filter_lava` **remove** Waste without replacing XP. This is superseded as the intended *future* enrichment behavior, but should not be described as already implemented. The bundled recipe currently uses 1,000 mB water + 250 mB XP -> 1,000 mB culture.

## Tests to add

- Complete XP-to-Waste normalized formula replacement, with all other components unchanged.
- Independent clean/dirty mB conservation during repeated fouling, fill/drain and world reload.
- 100 mB dirty / 100 XP-per-1000 ratio => 10 mB XP to restore.
- 100 mB dirty / 250 XP-per-1000 ratio => 25 mB XP to restore.
- Partial batches and remainders never create or destroy XP through rounding.
- Missing XP stops restoration; filtering alone does not restore enrichment.
- Existing non-XP cultures and lava-specific filtration continue behaving sensibly.
