> Historical record. Original: `PROCEDURAL_CONDUIT_ART_NOTES.txt`. Current reference: [05-PROCEDURAL-ART.md](../05-PROCEDURAL-ART.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
PROCEDURAL CONDUITS, CLADDING AND GLASS — implementation report
October 11, 2026

1. GENERATED TEXTURES AND MODELS

Extended gradle/procedural-art.gradle, generator version 1.4.0. New dimensional
parameters are in tools/procedural-art/geometry.json. Added 56 deterministic
16x16 PNGs: four conduit materials plus five materials for each of Vat and Mixer,
across grayscale master and T1/T2/T3. The pipeline now produces 100 PNGs total.

Added 112 model resources: 80 palette-specific conduit previews, eight glass
models, 20 established conduit model aliases, two established glass aliases,
and two formed-glass material models. Together with the previous bindings,
the generated output contains 135 block model JSON files. Existing blockstates
and item parents continue to select the same TRUNK/TAP resource paths.

Outputs:
  build/generated/resources/procedural-art/assets/mobvats/
  build/generated/resources/procedural-art/META-INF/mobvats-procedural-art.json
  build/procedural-art-preview/index.html
  build/procedural-art-preview/models.html

2. VAT / MIXER CLADDING

Both use eight trihedral corner fittings and twelve 1px rails. Vat fittings have
three single edge arms per corner, recessed trim and quiet fasteners. Mixer
fittings add three shortened, offset flange strips per corner, paired panel
seams and different fasteners. All fitting volumes are disjoint; no solid
4x4x4 corner cubes are used. Glass variants add inset translucent edge accents.
Metal colors use the existing copper/verdigris, iron and bloody-iron palettes.
Existing gameplay blocks bind T1; other tiers remain generated, unregistered
art variants. Existing full Mixer casing artwork remains intact.

3. UNFORMED / FORMED RENDER PATH

Unformed: ConduitShellModel explicitly returns the generated construction
model, containing the node, six branches and optional skeletal frame.
ConduitPreviewModel converts only the dedicated TAP pipe-material quads into
square frusta once at bake time, including inventory models. Ordinary model
JSON supplies the cuboids, textures and material assignments; the small bake
wrapper supplies the sloping faces that vanilla cuboid JSON cannot express.

Formed: ConduitShellModel returns only the existing bounds-selected casing or
glass shell. Bare formed conduits return no chunk quads or render layers.
The existing controller-owned ConduitRenderer draws the authoritative occupied
volumes once. Its body materials now use the fixed-color procedural Item/Fluid
pipe textures; collar material and physical geometry remain unchanged.

Assembly ownership still comes from ConduitStructure.containing. Placement,
disassembly, paused controllers and restoration use existing controller state,
ZeroCore notifications and model-data refresh paths. No visual blockstate,
per-tick visual updates, extra packets, endpoint discovery or unformed network
compilation were added. ZeroCore lifecycle methods were not changed.

4. GEOMETRY MEASUREMENTS

Node: [5,5,5] through [11,11,11], a 6px cube.
TRUNK: six 5px-long branches, constant 6x6px cross-section.
TAP: the same six branches, narrowing linearly from 6x6px at the node to
3x3px at each exposed endpoint. This changes vertices, not painted shading.
TAP endpoint ratio: 0.5. Normals are recomputed for sloping faces.
Frame corner reach: 4px along each incident edge. Rails: 1px thick, spanning
coordinates 4 through 12 between fittings. Mixer flange details stop short of
the corner to prevent overlapping fitting volumes.

The generator checks node width against ConduitGeometry.DIAMETER (6/16).
Runtime taper and the HTML preview read the same generated dimensions.
ConduitGeometry.java, collision/clearance calculations and Mixer vessel
geometry were not modified. Preview taper stays inside the TRUNK envelope.

5. GLASS TINT AND TRANSPARENCY

Pane RGB: #B8D9CB for every tier and both families.
Pane alpha: 32/255 = 12.55%. Edge accents: 48/255 = 18.82%.
Metal trim stays opaque and tier-colored. No reflection or diagonal-glare
motifs were added. Glass-clad construction models use the translucent render
layer so low-alpha accents remain translucent.

6. CONNECTED GLASS

VatGlassState, its Mixer bounds projection, and VatGlassModel's existing
96 face/edge-mask planes remain unchanged. Formed materials have a uniform
pane surface: no repeated per-block green or metal interior borders. The
existing assembled envelope chooses perimeter metal rims and shell faces,
including glass-clad conduits. Mixer now has its own formed-glass material
instead of borrowing Vat's material. Structural permissions and formation
rules remain unchanged. Unformed standalone glass retains a family-specific
full-cube frame until assembly.

7. GRADLE / RESOURCE INTEGRATION

generateProceduralArt remains the single resource producer and runs before
processResources. Existing generated-over-static precedence and duplicate-path
failure remain active. No manual copying or runtime dependency was added.
The generator removes stale files only from its dedicated output directory.
Texture provenance includes deterministic hashes, generator/recipe/palette
sources and the geometry-source hash. Generated parent and texture references
are checked, as are processed-resource and JAR hashes and duplicate entries.

proceduralArtPreview also runs a CPU Java fixture to export representative
actual formed meshes through ConduitGeometry.bake. It provides straight,
elbow, T-junction and TAP-endpoint examples for both resources and shell types.
The HTML has selectable tiers, rotation, side-by-side states, both glass
families and the requested four-cell assembly joins. Shell illustrations do
not reproduce Minecraft lighting, full casing detail or transparency sorting.
The browser preview source is tracked as a Gradle input.

8. REGRESSION RESULTS

Passed, using the existing offline Gradle cache:
  gradlew proceduralArtPreview --offline
  gradlew build --offline
  gradlew runGameTestServer --offline — all 737 required tests passed
  gradlew buildTestWorld --offline — MobVatsTest generated and verified
  git diff --check

New coverage: byte-identical generation, palette/fixed-color preservation,
16x16 materials, tier-independent pane alpha/RGB, different family textures
and profiles, eight corners/twelve rails, non-overlapping fittings, uniform
TRUNK sections, six-axis 6-to-3px frusta, unchanged input vertices, generated
resource references, bare formed preview/layer suppression, assembly /
disassembly / reassembly ownership, and Vat/Mixer chunk-lifecycle restoration.
Existing formed connectivity, physical geometry and AI-placeholder safeguards
also passed their existing checks. Seven protected texture hashes passed.

CPU Java tests execute through conduitArtGeometryTest, rather than JUnit.
The dedicated-server lifecycle tests invoke the existing chunk-unload and
clearRemoved hooks; these are lifecycle simulations, not forced disk eviction.
Actual client assembly synchronization and GPU transparency remain manual
acceptance items. A temporary Node check validated every preview material,
all tiers and rotation projections (400 canvases, 182754 textured triangles).
Local-file browser inspection was blocked by the browser URL policy; no
alternate browser surface or policy workaround was used.

9. PRESERVED IMPLEMENTATION AND ASSETS

No changes to routing, endpoint discovery, physical occupancy, structural
permissions, activation, fluid buffers/composition, recipes or specimens.
Existing polygon Mixer geometry, casing/glass geometry builders, machine
renderers and authored base texture files remain intact. The old tank PNGs
remain packaged but are no longer the selected formed-glass material.
Existing AI-placeholder manifest, development guards and public-release
blocker remain active. No generative-image output was used.

Pre-existing local build.gradle, Vat renderer, diagnostics and test changes
were retained. No commit, publication, release, deployment or Minecraft
client task was performed.

10. MANUAL VISUAL ACCEPTANCE

Open build/procedural-art-preview/models.html in a browser. Compare all three
tiers, both resources, both roles and both cladding families. Rotate the
models to inspect the six TAP endpoints and the open corner fittings.

In the generated MobVatsTest world, or a separate creative test world:
  - Place bare Item/Fluid TRUNK/TAP blocks and all Vat/Mixer cladding variants.
    Confirm six-way construction previews, open faces, cubic node and taper.
  - Complete a valid Vat and Mixer using their matching clad shell parts.
    Confirm preview/frame disappearance and actual connectivity only.
  - Break and restore a structural wall; confirm preview recovery and formed
    recovery without replacing the conduit.
  - Leave/reload the chunks, then save/reopen the world; verify ownership and
    appearance recover without toggling block states.
  - Inspect casing -> glass -> glass-clad conduit -> glass joins in each family.
    Confirm continuous panes, perimeter trim and readable interior machinery.
  - Check near/far glass walls, corners, overlapping specimens and camera
    movement for translucency ordering, flicker or duplicate pipe geometry.
  - Check both inventory/held TAP models and in-world TAP geometry.

No manual client visual acceptance is claimed by this report.
```
