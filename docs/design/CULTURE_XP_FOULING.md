# Culture XP depletion and restoration — design decisions

Status: implemented recovery slice; see [production benchmark](CULTURE_PRODUCTION_BENCHMARK.md) for validation and provisional economics. Last updated: 2026-10-23.

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

**Stage 1 — ordinary recipe filtration:** The bundled `mobvats:filter_general` recipe consumes one charcoal and 1,000 mB of water-based dirty Culture. Its existing removal transform removes the Waste entry, preserves other formula entries and metadata, and returns exactly 1,000 mB of Culture without XP restoration. The recipe requires Culture identity and at least Water 1,000 in its formula, and excludes lava. This supersedes the proposed universal quantitative/PPMB charcoal filter: there is no removal capacity, partial component removal, or arbitrary-fluid charcoal recipe. The separate lava/sand recipe retains its existing behavior.

**Stage 2 — enrichment:** The ordinary `mobvats:enrich_culture` recipe consumes 1,000 mB of XP-free water-based Culture and 250 mB of actual `mobvats:experience`, returning 1,000 mB of Culture with XP 250 added to the existing formula. It preserves other ingredients, including any Waste: filtration is a distinct operation. Already XP-bearing formulas do not match. Both operations take 200 base Mixer ticks per batch. Smaller batches are optional authored recipes, not a fractional-XP accounting subsystem.

These are separate recipe stages, **not a requirement to own two Mixers**. One programmable Mixer may process both at different times; two dedicated Mixers and intermediate fluid storage enable a continuous automated filtration → enrichment pipeline. Neither stage creates or destroys physical Culture volume; don't discard other composition additives.

Filtering never restores XP for free. Preserve the distinction between contaminated culture, filtered XP-free culture, and enriched culture for matching and routing. Fouling adds the replaced XP to any pre-existing Waste; it preserves all unrelated entries. The recipe removes the whole Waste entry, including incidental Waste. It does not distinguish Waste provenance. Filtered Culture does not satisfy the enriched culture profile and currently falls back to frequency 0.5.

The selected clean/dirty formula pair and separate physical quantities remain stable through small fouling events and partial transfers. Processing uses whole configured batches with exact integer input/output quantities. A 100 mB transfer can wait in a buffer until enough accumulates for a batch; arbitrary 1 mB recovery is not implemented. Recipe authors must specify exact ratios and the intended formula basis for other batch sizes.

## No hidden upkeep

Fouling occurs on successfully committed recognized biological work, not continuously per tick or on failed/blocked operations. XP consumption happens at restoration in the Mixer, not secretly inside the Vat. Culture can be run dirty at reduced performance or maintained using a filtration/XP supply loop.

## Current implementation versus target

`CultureFouling.dirty` now replaces the entire XP entry with equal Waste. Media without XP retain the previous configured Waste increment. Storage still saves both prototypes and physical fractions. On load, an exact legacy pair (the clean prototype plus an added Waste increment, still carrying XP) migrates to the new dirty formula without changing either volume. The saved clean prototype identifies unrelated pre-existing Waste, which is preserved. Unrecognized/custom pairs retain their exact stored identities. An imported dirty stack without its original clean prototype cannot establish that it formerly contained XP; inverse legacy pairing never invents XP. Normal recovery returns enriched Culture to the original selected pair.

The Mixer previously could derive the additive formula using an ordinary output, but could not retain exact source metadata and apply required/excluded composition matching without also removing an entry. The only engine extension permits `transform.remove: []`; existing source checks and recorded-input composition derivation then perform enrichment. Ratios remain JSON data. Existing authored Mixer configuration is never overwritten: installations with an old `_defaults.json` must explicitly adopt the updated recipes or provide overrides.

## Automated coverage

- Complete XP-to-Waste normalized formula replacement, with all other components unchanged.
- Independent clean/dirty mB conservation during repeated fouling, fill/drain and world reload.
- Configurable 100 and bundled 250 mB XP inputs produce the corresponding formula entries.
- Partial transfers preserve exact fluid identity; repeated whole batches conserve Culture and pay actual XP.
- Missing XP stops restoration; filtering alone does not restore enrichment.
- Existing non-XP cultures and lava-specific filtration continue behaving sensibly.
- Atomic input/output reservations, full destinations, stale plans, exact meters, and single-/two-Mixer sequences.
