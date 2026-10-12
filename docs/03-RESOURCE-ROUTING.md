# 03 — Resource Routing

## 3.1 Topology and resource networks

`ConduitTopology` compiles independent item and fluid components from the validated machine. Cardinal adjacent conduits of the same resource connect. TRUNK provides network continuation; TAP also discovers adjacent endpoints and their attachment constraints. Topology records endpoint positions/roles and compiled geometry, then serves cached queries. It does not execute specimen rules or recipes.

Conduit mode is a blockstate; TRUNK and TAP inventory items share one block per resource. Installed casing/glass determines structural family and placement permission, independently of resource/mode. Item and fluid networks do not merge. The mechanical cladding contract is in [multiblocks](02-MULTIBLOCKS.md).

## 3.2 Physical storage, filters and boundaries

Fluid Buffers hold resources in physical tanks. Compatible adjacent members form logical clusters; stored identity and components constrain clustering, so an empty intermediary cannot bridge incompatible fluid identities. Exact filters and fluid components remain distinct constraints. A cluster preserves its filter when drained; an empty container interaction can clear an empty filtered cluster's filter. Native buckets transfer 1,000 mB atomically and other fluid-capability containers follow their transfer policy.

Input/Output describe the outside boundary, while Producer/Consumer/Store describe operation roles. Configured ports preserve direction, selectors and active/passive state. Pumps explicitly move world fluid; Fill/Drain Valves move Vat medium. Voids dispose only through explicit disposal policy after compatible storage/staging capacity is exhausted. Sump is the specimen service collector; it is not an environment extraction valve. No standalone item-buffer block is registered.

Active boundary transfer is controller-scheduled in deterministic rotating part order with a per-port fluid budget. Adapters use ZeroCore neighbor sources and NeoForge simulation. Direct machine adjacency assigns transfer to active output-side push; input-side pull can serve passive outputs, avoiding duplicate active-to-active ownership. Passive capability requests remain distinct from scheduled transfer.

## 3.3 Reservations and endpoint roles


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

### 3.3.1 Endpoint mappings

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

## 3.4 Invalidation and limits

Structural edits, cladding, endpoint configuration and relevant buffer identity changes invalidate compiled services through controller callbacks and coalesced ConduitStructure notifications. Paused controllers reject queued delivery. Restoration rebuilds local services; unchanged geometry does not repeatedly dirty persistent resource state. Physical device clearance and routing reach are separate constraints. External third-party handlers remain capability boundaries, not reversible owned transaction participants.
