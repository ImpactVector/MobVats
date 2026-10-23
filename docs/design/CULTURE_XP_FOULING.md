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

## Mixer restoration

Filtration transforms a matching quantity of dirty culture to its original clean culture:
1. Remove the Waste entry that replaced XP in its normalized formula.
2. Consume fresh XP fluid proportional to the batch's *actual culture mB* and the original recipe's XP per 1,000 mB.
3. Restore the XP formula component in equal amount and preserve other components and fluid volume.

A generic contamination-only filter must **not** silently restore XP for free. Existing filter materials (charcoal for general culture, sand for lava-specific culture) may remain additional filtration inputs; their exact costs are balance knobs. Ensure restoration can distinguish replacement Waste from pre-existing unrelated waste/additives where relevant, and preserve original composition identity for reliable filter matching.

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
