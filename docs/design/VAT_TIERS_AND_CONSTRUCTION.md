# Vat tiers and construction — design decisions

Status: agreed design; implementation may lag this document.
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

## T1 recipe transition

The current T1 Vat Casing recipe uses iron; change the tier-one structural ingredient to copper. Add T2 Iron and T3 Blood Iron casing and associated glass recipes. Controller recipes should incorporate the appropriate casing. Specific quantities/recipe arrangements remain subject to recipe inspection and tuning.

## Hostile Head progression

A hostile specimen requires **both** a T2-or-higher Vat and an encoded hostile Head activated once by lightning. Lightning activation is a persistent Boolean on the Head associated with the current encoded specimen. Re-encoding/replacing the specimen resets activation; relocating the Head while preserving its encoded specimen retains activation. No FE, fuel, charge budget or repeated lightning upkeep is intended.

A vanilla lightning rod at/adjacent to the Head is a possible activation interface, but exact placement and whether an unactivated hostile Head prevents formation versus merely preventing processing are not yet settled.

## Blood Iron

Blood Iron is intended to be made using the Mixer from T2 Iron Casing and material derived from hostile specimens (provisionally Strange Meat), potentially with XP or another reagent. Favor direct production of Blood Iron Casing over inventing an unnecessary intermediate. Craft T3 glass from T3 casing. **No final Blood Iron recipe or precise Strange Meat form has been agreed yet.**

## Implementation acceptance

- Tier-compatible shells only, including clad exterior machinery; universal functional items preserved.
- Rectangular vats and geometry-derived minima; T1 maximum 9 x 7 x 9.
- No accidental bonus for higher tier alone.
- Hostile gating and lightning behavior documented separately from current implementation.
- Validate actual specimen/Tool Port line of sight and construction in GameTests; do not assume illustrative layouts are formable.
