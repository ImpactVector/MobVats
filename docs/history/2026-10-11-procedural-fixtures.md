> Historical record. Original: `docs/PROCEDURAL_FIXTURES.md`. Current reference: [05-PROCEDURAL-ART.md](../05-PROCEDURAL-ART.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Procedural Valves, Pumps and Voids

Implemented October 11, 2026. Recipes: `tools/procedural-art/fixtures.json`;
generator: `gradle/procedural-art.gradle`; chunk/inventory integration:
`gamecontent/multiblock/common/client/FixtureModel.java`.

## Assets and bindings

The generator adds 40 native 16×16 PNGs: ten materials in each of grayscale
master, T1 verdigris copper, T2 iron and T3 bloody iron. It produces 150 fixture
model JSONs: six reusable cores and 27 family/configuration previews per palette,
plus nine production block and nine inventory parents. The separate
`assets/mobvats/fixture_mesh.json` contains the shared closed auger ribbon and
the nine supported production bindings. Models, mesh and PNGs have deterministic
hashes in `META-INF/mobvats-procedural-art.json`.

Production uses the established T1 palette. The nine existing block IDs are:

| Family | Production blocks |
| --- | --- |
| Vat | `vat_fill_valve`, `vat_drain_valve`, `vat_pull_pump`, `vat_push_pump`, `vat_fluid_void`, `vat_item_void` |
| Mixer | `mixer_pull_pump`, `mixer_push_pump`, `mixer_fluid_void` |

There are no Mixer Valve or Item Void bindings. Existing fixtures have no separate
bare, casing-clad or glass-clad registrations. Their production construction
models use the appropriate open family frame; formed boundary cells use the
existing bounds-selected casing skin. Bare and glass configurations are preview
compositions only. No block, item, cladding mechanic or tier registration was added.

## Geometry and presentation

- Valves have a conduit-sized central node and two square perforated distribution
  plates within a centered 10-pixel envelope. Fifteen dark holes remain after the
  corner markings overlap one perforation. Fill uses outward arrows and Drain uses
  inward arrows, relative to the Vat interior. Both use fixed `#168BFA`.
- Pumps have square flanges, an open polygonal chamber, shaft and a separate
  1¾-turn auger. Twenty-eight segments produce 228 planar triangles with outward
  normals, encoded as degenerate quads for Minecraft. The JSON seed cuboid supplies
  its atlas material and is replaced once at bake time; it is never rendered with
  the ribbon. The stationary mesh fits in the centered 10-pixel envelope. Flange
  UVs preserve the inward Pull and outward Push arrows. The separate auger mesh
  allows future independent rotation, but this pass adds no animation.
- Voids occupy nominal centered 10×10×10 bounds. Reinforced walls, an angular arch,
  thin front bars and a horizontal top grate expose a dark orange/red chamber.
  Four dots identify resource type: blue fluid or `#E3BC45` items, without arrows.
  The hot texture is static; there is no lava sprite, stored lava, light simulation
  or fire effect. The old pulse BER registrations are disconnected; disposal
  state and transaction behavior remain unchanged.

Fixtures have one chunk render owner. Unformed chunks and inventory items use
core plus open frame. Formed chunks remove all construction-frame quads and retain
one core plus the existing casing skin at boundary cells. Formed interior cells
retain only the core. Vat and Mixer frames and casing materials remain distinct.
Pump orientation comes from the existing assembled boundary face; these blocks
have no separate Pump-facing placement property. Unformed/items default north.

Void TAP coverage is sampled while obtaining chunk model data. Covered grate and
hot faces are suppressed; the reinforced chamber remains. Rendering performs no
per-frame neighbor scan. Core and casing share one solid chunk layer, with no
additional fixture render layer. Resource reload
recreates the mesh/model caches. The preview illustrates exposed mechanisms and
window contexts; Minecraft remains authoritative for lighting and occlusion.

No collision, physical clearance, placement legality, transport, storage,
clustering, Mixer/Vat geometry, resource semantics, activation or ZeroCore lifecycle
implementation changed. All authored visual geometry fits within the existing
block envelope; no physical calculations were enlarged to accommodate it.

The handoff described an arched-grate reference but supplied no image file. The
angular arch was constructed from that description, without tracing or image-model
artwork. Existing AI placeholder sources/ledger and the public release blocker
remain unchanged.

## Validation

- `gradlew.bat proceduralArtPreview --offline`: passed.
- `gradlew.bat build`: passed (also rerun offline after final generator changes).
- Procedural contracts: 148 total PNGs; deterministic PNG/model/mesh output;
  material mapping and fixed accents; Valve/Pump directions; arrow-free Voids;
  unique geometry; outward ribbon winding and nondegenerate normals; rotated
  chamber bounds; family frame composition; resource collisions and packaging.
- Actual baked-model CPU checks: all six Pump axes, packed normals, immutable
  geometry, assembly/disassembly transitions, interior rendering ownership,
  layer separation and six-direction TAP surface coverage.
- `gradlew.bat runGameTestServer`: all 745 required tests passed.
- `gradlew.bat buildTestWorld -PworldName=MobVatsDev`: passed; ten mirrored fixtures,
  nine operator Vats and 5,000 observation ticks verified.
- AI guard regression and development resource verification passed through `build`.
- Preview JavaScript exercised across three tiers and five rotations with local
  texture resolution and finite projection checks. This is a CPU execution check,
  not an assertion of visual acceptance.
- `git diff --check`: passed.

Concurrent existing JavaExec checks emitted shared-log rollover warnings; their
assertions and Gradle tasks passed. The initial build discovered the new CPU
helper as an empty conventional test suite; excluding it from that suite, consistent
with the existing JavaExec helpers, resolved the failure. It runs through
`conduitArtGeometryTest`, which is required by `check` and the preview task.

## Manual visual acceptance

Open `build/procedural-art-preview/models.html` for side-by-side fixtures,
bare/family frames, formed casing/window contexts, installed groups and all six
Pump orientations. Change T1/T2/T3 and rotate the scenes. Open `index.html` for
native 16×16 samples and nearest-neighbor enlargement.

In the manually launched client, use the generated `MobVatsDev` world and existing
registered items. Inspect inventory and unformed placement, then formed Vat/Mixer
boundary fixtures on side/top/bottom faces. Verify that formation removes frames,
disassembly restores them, and the screw remains visible from open interior views.
Check Fill outward, Drain inward, Pull toward mechanism, Push away, and Void
resource dots without arrows. Compare an uncovered Void grate with an actual
adjacent TAP and verify that visible openings retain their hot interior. Reload
resources once to check rebuilt mesh bindings. The browser's bare/glass fixture
concepts are not additional items to place. No client was launched automatically.

Validation did not publish, release or deploy an artifact.
