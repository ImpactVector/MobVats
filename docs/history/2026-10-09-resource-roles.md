> Historical record. Original: `RESOURCE_ROLES.md`. Current reference: [03-RESOURCE-ROUTING.md](../03-RESOURCE-ROUTING.md).
> Archive date basis: Git introduction author date 2026-10-09T16:52:26-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Conduit resource roles

Conduit topology records connected endpoint positions for each resource. Producers,
Consumers, and Stores are independent capabilities; routing discovers inventories
through `ResourceEndpoint`. Item inventories and fluid tanks keep their native
NeoForge types. Private inventories must expose stable identities so shared port
views cannot reserve the same resources twice.

`ResourceRouting.prepare` tries the operation's ordered `ResourceBundle.Strategy`
list. Each strategy contains all its item and fluid requirements. Reservations
accumulate across requirements, use Stores before Producers, and commit only if
all captured contents still match. The operation supplies its mutation and undo.
External handlers are not eligible for these reversible transactions.

`DemandConfiguration` belongs to the machine or operation. It persists enabled
strategy IDs, order, and item/fluid selectors. Tool acquisition and combustion
fuel acquisition use this configuration. No feeding, fuel, culture, cooking, or
recipe names occur in routing infrastructure.

Producer completion tries Stores and then bounded local output escrow. A full
destination leaves the completion uncommitted. `publish` retries only producer
escrow; it never redistributes Store contents. Consumers request resources on
demand. Item output ports retain operation-announced output identities, or use
their configured selector, to avoid exporting unrelated recipe inputs. Fluid
output ports pull on external drain requests, including side-effect-free simulation.

## Migrated endpoints

- Fluid Buffers: fluid Store, with existing physical clusters and filters.
- Vat Item Input Ports: item Store in their existing private staging inventory.
- Vat Fluid Input Ports: filtered Store views over connected physical buffers;
  input never fills a Producer's output allowance.
- Vat Output Ports: item/fluid Consumer; outside direction language is unchanged.
- Tool Port: item/fluid Producer and Consumer, with private item results and
  service output escrow. Tool acquisition uses generic ordered demand.
- Blender and Sump: fluid Producers over their existing service output escrow.
- Combustion Drive: item Consumer. Connected Mixer Input Ports own a separate
  persisted fuel Store. Fuel remains separate from the existing recipe inventory.
- Mixer fluid ports/controller: Store/Consumer views and service Producer
  discovery needed by the migrated fluid boundary and drive plumbing.

Existing process transactions, specimen eligibility, recipes, and machine timing
remain operation-owned. Legacy `expand` projections are retained for processes
outside this slice, with Vat assignments restricted to actual boundary ports.
`PhysicalFluidStorage` depends on the generic `FluidSink` disposal contract, not
Vat boundary entities; explicit disposal remains separate from normal production.

`ResourceRoleGameTests` cover role composition, Store preference, direct Producer
supply, multi-resource reservation/completion, configuration persistence,
backpressure, stale reservations, rollback, filters/components, shared inventory
views, real endpoint mappings, and fuel isolation/portable persistence.

Environmental Tool Port interactions are registered as `mobvats:goat_horn`,
`mobvats:sniffer_dig`, and `mobvats:armadillo_brush`. Their definitions own
`duration_ticks`, `cooldown_ticks`, `requires_stage`, input selectors and
`actor_use`. `block_items` accepts exact block IDs or block tags, evaluated on
acquired BlockItems (for example `#minecraft:snaps_goat_horn` and
`#minecraft:sniffer_diggable_block`). Block tag membership remains live across
reloads. These inputs use the same ordered Tool Port resource acquisition and
persisted demand configuration as other interactions.

Ram targets and dig substrates are retained; brushing applies vanilla's
16-point durability cost to a copied brush. Progress is bound to the encoded
specimen, actor and held stack and persists across saves. Missing inputs pause
work; changed bindings restart it. Buffered results backpressure progress and
completion. Output, specimen state, held input, yield carry and completed
progress commit together. Cooldown starts after completion. Horn generation
uses vanilla's horn state/instrument contract; Sniffer seeds use vanilla's
`minecraft:gameplay/sniffer_digging` GIFT loot table. No environmental species
selection or timing lives on the Tool Port. Chicken eggs remain the existing
definition-authored passive resource process, avoiding a second egg producer.
