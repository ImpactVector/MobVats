# Survival recipe audit — Slice 2

The audit started from `dd1511bb`. The existing catalog uses static JSON crafting
recipes, reversible boundary-mode recipes, conduit trunk/tap conversions and
ordinary Meat cooking. No new intermediate items or machinery variants are added.

| Recipe | Before | Slice 2 |
| --- | --- | --- |
| T1 Casing | Iron + Glass → 4 | Copper + Glass → 4 |
| T1 Glass / Controller | T1 Casing + Glass → 4 / Redstone → 1 | Preserved |
| T2 Casing | Absent | Iron + Glass → 4 |
| T2 Glass / Controller | Absent | T2 Casing + Glass → 4 / Redstone → 1 |
| T3 structure | Creative only | Preserved; no Survival recipes |
| Fluid Conduit | 2 Iron + Copper → 4 | 3 Copper → 4; shape preserved |
| Fluid Buffer | Glass + Iron + Bucket | Glass + Copper + Bucket |
| Fluid Port | T1 Casing + Dropper | T1 Casing + Bucket |
| Fill / Drain Valve | 4 Iron around Bucket / Hopper | 4 Copper around existing Bucket / Hopper |
| Pull Pump | 4 Iron around Piston | 4 Copper around Piston |
| Fluid Void | 3 Iron + Mixer Casing + Lava Bucket | 3 Copper + existing casing and bucket |
| Feed Port | T1 Casing + Hopper + Iron | T1 Casing + Hopper + Copper |
| Sump (`vat_drain`) | T1 Casing + Hopper | T1 Casing + Bucket |
| Item Port | T1 Casing + Hopper | Iron + Hopper |
| Tool Port | T1 Casing + Iron + Piston | 2 Iron + Piston |
| Harness | T1 Casing + Chain | Iron + Leather |
| Blender | T1 Casing + Stonecutter | Iron + Stonecutter + Diamond |
| Mixer Fluid Meter | Mixer Casing + Comparator + Bucket | Copper + Comparator + Bucket |
| Mixer Item Meter | Mixer Casing + Comparator + Hopper | Iron + Comparator + Hopper |
| Mixer Intake | T1 Casing + Kelp | Copper + Kelp |

All quantities not explicitly shown are one. The Intake's primary function is
fluid admission, so Copper is appropriate. Feed Ports retain the existing Hopper
for feed handling; Drain Valves retain their Hopper center. Item Conduits (four),
Item Void, Mixer Shafts (three), Impellers, combustion drives and the Extruder
already use Iron for mechanical/item work and retain their recipes. The FE drive
retains its established Copper windings and Redstone. Mixer structure/control,
Blocker, Head, Flesh, Syringe and cooking recipes retain their identities/costs.
No Item Buffer is registered, so no Item Buffer recipe is introduced.

Existing Push/Pull Pump and Item/Fluid Void switches remain one-for-one mode
conversions, preserving their configuration; they are alternate acquisition
paths. Conduit trunk/tap conversions remain one-for-one. No new duplicate base
recipes are added. T1 structural costs changing to Copper also affect recipes
that already use T1 Casing; matching exterior cladding is still installed and
paid for separately, regardless of a functional component's crafting material.

`tools/generate_resources.py` retains the historical base structural and port
recipe generator and now emits these T1/T2/port costs. Other crafting JSONs remain
their existing authored sources. Do not run this historical broad generator to
regenerate only recipes: it also rewrites legacy models/translations.

`SurvivalRecipeGameTests` checks loaded recipes, actual T1/T2 crafting grids,
T3 exclusion, material requirements, batch quantities, universal registrations
and resolvable ingredients/results. Structural capability does not activate
hostile specimens. Blood Iron manufacturing and all Strange Meat solid products
remain future work.
