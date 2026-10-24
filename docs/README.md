# Technical documentation

These references describe the current source implementation. Read in order:

| Reference | Contents |
|---|---|
| [01 — Architecture](01-ARCHITECTURE.md) | Components, ownership, ZeroCore lifecycle, synchronization and inspection |
| [02 — Multiblocks](02-MULTIBLOCKS.md) | Vat structures, Mixer geometry, functional parts and applied materials |
| [03 — Resource Routing](03-RESOURCE-ROUTING.md) | Conduits, endpoint roles, physical stores, filters and atomic transactions |
| [04 — Processing](04-PROCESSING.md) | Capture, specimen operations, medium, feeding, tools and Mixer recipes |
| [05 — Procedural Art](05-PROCEDURAL-ART.md) | Semantic palettes, resource generation, models, provenance and release policy |
| [06 — Development](06-DEVELOPMENT.md) | Dependencies, build tasks, configuration and tooling |
| [07 — Testing](07-TESTING.md) | Automated tests, operator fixtures, visual acceptance and benchmarks |
| [Java code style](CODE_STYLE.md) | Pinned formatter, seven-file enforcement scope and staged migration |
| [Survival recipe audit](SURVIVAL_RECIPES.md) | T1/T2 construction, universal machinery costs and retained quantities |
| [Products and fluids](PRODUCTS.md) | Meat/Waste products, Extruder conversions and distinct Strange Meat fluid |
| [Mob profiles](MOB_PROFILES.md) | Individual species files, schema upgrades, registered-mob discovery, tier enforcement and reload revalidation |
| [Hostile specimens](HOSTILE_SPECIMENS.md) | T2+ dormancy, roof rods, independent Head activation, relocation and Strange Meat production |

[Configuration examples](examples/README.md), [future designs](design/FUTURE-FEATURES.md), [historical records](history/README.md), and [generated Mixer clearance tables](generated/MIXER_CLEARANCE_GENERATED.md) complement the current references. The [consolidation record](history/2026-10-12-documentation-consolidation.md) records the original inventory, moves, dates and validation.

## Maintenance conventions

- Give authoritative references two-digit reading-order prefixes and hierarchical section numbers. Keep one current authority per topic.
- Describe implemented behavior from source. Label proposals and partial implementations explicitly; place them under `design/`.
- Preserve substantial superseded designs and validation evidence in `history/`, using `YYYY-MM-DD-topic.md`. Use `YYYY-MM-DD-HHMM-topic.md` only when a reliable time and meaningful ordering exist. Record timezone when using times. Use `undated-topic.md` when no reliable date exists.
- Preserve original measurements and distinguish source-derived conclusions from measured results. Historical commands and paths are revision-specific evidence.
- Update living references when implementation changes; do not append chronological reports to them. A trivial documentation edit needs no historical report.
- Verify links, heading anchors, local file references and actual Gradle task names after moves. Change a generator rather than manually editing its generated output.
- Keep tool operating instructions next to tools when useful. Build-critical provenance must remain readable by its validator after any move.

Source code is authoritative when prose conflicts with implementation. Historical documents preserve original claims and may refer to removed registrations, old workstation paths and generated logs that are not distributed.
