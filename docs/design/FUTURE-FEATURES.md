# Future features and unresolved design work

This file distinguishes unimplemented proposals from completed implementation. It is not a release roadmap or a promise of compatibility.

| Topic | Status | Current boundary / unresolved decision |
|---|---|---|
| T2/T3 structural materials and machine tiers | Proposed; artwork implemented | Only T1 casing/glass is registered. Define real structural items, installed-material policy, progression and mixed-tier formation before claiming gameplay support. |
| Conduit mode inventory consolidation | Partially implemented | One block per resource exists; distinct TRUNK/TAP inventory items remain. Mode is functional state. Further item consolidation requires its own design. |
| Cross-machine fluid bridge networks | Proposed | Direct boundary transfers, within-machine conduits and compatible buffer bridges exist. They do not establish a general bridge-network feature. |
| Broader hostile/boss processing progression | Partially implemented | Non-boss monster defaults now support T2+ formation, per-Head vanilla lightning activation, Sword loot/XP and Strange Meat Blender output. Boss and Blood Iron progression remain unresolved. Renderer/state compatibility for unusual modded entities needs acceptance/adapters. |
| Pump auger animation and further mechanism animation | Proposed | Auger mesh is separate but stationary; Mixer rotor animation already exists. Define animation/ownership without confusing current physical geometry with future art. |
| Very large Vat status pagination | Proposed | Existing bounded text pages can truncate verbose large-machine diagnostics. Define GUI pagination separately from per-Head processing. |
| Additional captured state and special interactions | Partially implemented | Vanilla allowlist and registered state/executor adapters exist; arbitrary entity cloning or unrestricted environmental effects are unsupported. Goat horn, Sniffer digging and Armadillo brushing are implemented. |
| Runtime weathered material selection | Proposed | Finite seed weathering and preview wall composition exist. No controller-coordinate production selector is implied. |
| Further synchronization batching/performance changes | Investigation | Parity corrections and comparative harness are implemented. Original benchmark evidence does not justify a production change; actual packet/coalescing, large-state transport and workload costs need evidence. |

Multiple independent specimens, FE/combustion drives, configurable Mixer bundles, culture exposure/happiness, fouling/filtration and explicit applied cladding are implemented, so older proposals for them remain historical records rather than open work.

Historical records retain unresolved revision-specific questions and rationale; consult the [history index](../history/README.md) before adopting an old proposal. Future changes must update the current numbered reference and preserve significant superseded rationale.
