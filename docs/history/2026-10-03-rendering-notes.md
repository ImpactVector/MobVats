> Historical record. Original: `RENDERING_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
Formed Vat Rendering — October 3, 2026

Implementation
  Glass is still rendered in Minecraft's chunk mesh. VatGlassModel wraps the
  original baked model: absent/unformed VatGlassState uses the original cutout
  glass; a formed snapshot selects only the translucent layer and outward tank
  planes. Items keep the original model. Bake caches are recreated on resource
  reload and shared by original-model identity across ZeroCore2 facings variants.
  No neighbor-glass lookup, glass BER, custom packets, or structural changes.
  assets/minecraft/atlases/blocks.json adds seven explicit sprite entries to the
  vanilla block atlas. Minecraft's default sources cover singular block/item
  folders; the restored art retains its historical plural blocks/items paths.
  Startup found missing sprites at those paths; atlas entries fix that without
  modifying the original PNG bytes or model texture references.

Perimeter semantics
  Inclusive assembled bounds are min/max block coordinates. Each plane has
  presence plus TOP/BOTTOM/LEFT/RIGHT bits in immutable packed model data.
  North/south project U=X,V=Y; east/west project U=Z,V=Y; roof projects U=X,V=Z.
  Left/right denote low/high U; bottom/top denote low/high V, rather than camera
  orientation. This avoids direction-dependent reversal of mask and geometry.
  Side U spans min..max to include legal vertical glass frame edges. Side V spans
  minY+1..maxY-1 because mandatory casing rings occupy the two cap rows. Roof
  U/V span min+1..max-1 because its frame ring must be casing. Those insets come
  from existing geometry rules, not adjacent block types. Where a vertical frame
  is casing, that casing itself supplies the outer border; glass beside it does
  not invent an interior edge. Functional wall interruptions do not acquire rims.
  Vertical FrameUpDown glass has two independent planes and masks, never the
  four remaining cube faces. Central wall cells have zero perimeter bits.

Far wall, transparency, and geometry
  Surface and rim are procedural uniform materials, alpha 18 and 180 of 255.
  Every plane is partitioned into a 3x3 grid of non-overlapping rectangles; rim
  rectangles replace surface rectangles at selected perimeter edges. Adjacent
  cells use identical surface tint, with no cell-border pixels or border strips.
  Each rectangle has two opposite windings at the same coordinate, so vanilla
  backface culling draws one winding from each view. This makes the far wall's
  inward-facing perimeter visible. These are two views of a single surface, not
  offset inner/outer glass shells. No overlaid border quads or coplanar overlap.
  General quads bypass neighbor culling because at a vertical glass edge another
  glass block can touch a valid outward plane. Only bounds-selected planes exist,
  so this cannot reveal the unwanted interior block faces of ordinary cubes.

Dynamic interior
  VatHeadEntityRenderer remains the single existing Head anchor. It now owns a
  VisualState cache containing a VatRenderDescriptor and its render-only Cow.
  The descriptor contains vat AABB, interior AABB, specimen position, and harness
  and drain positions. Head assembly/break callbacks change its render revision;
  geometry is derived once per revision and reused by render/frustum queries.
  Cache values contain no part/controller references back to their weak keys.
  Disassembly/pause discards the visual on the next renderer query. Reformation
  or a newly loaded Head derives a new descriptor. Cow identity/persistence stay
  on the Head; no Cow is spawned, ticked, or registered in either entity manager.
  Stationary body, client head tracking, and construction suppression remain.
  Future liquid can be one prism over interiorBounds, with one top at
  minY + (maxY-minY)*(amount/capacity). The same anchor can coordinate specimen,
  fluid, and later tubes/tools without repeated per-frame geometry discovery.
  No fluid amount/capacity fields, tools, masks, cables, or multi-mob support added.

Reference classes inspected (existing local copies plus three files in examples)
  ZeroCore2: AbstractMultiblockEntity, GlassBlock, CuboidPartVariantsModelData,
    BlockVariantsModelBuilder, AbstractMultiblockModelBuilder.addGlass.
  ExtremeReactors2: ReactorGlassEntity, TurbineGlassEntity,
    ModelTransformers.getGlassVariantIndexFrom, RotorBearingEntityRenderer,
    RotorDescriptor, TurbineRotorBearingEntity, TurbineRotorModelBuilder.
  XR's same-glass neighbor variant was inspected but not used: its connectivity
  semantics do not represent a complete vat's perimeter or two outward edge
  planes. Its ZeroCore2 lifecycle and turbine anchor/descriptor pattern are reused.
  Minecraft/NeoForge: FaceBakery, BlockElementFace, BakedModelWrapper, ModelData,
    ChunkRenderTypeSet. Stock FaceBakery bakes the cached plane rectangles.

Changed/new files (relative to the mod repository)
  src/main/java/net/impactvector/mobvats/gamecontent/multiblock/vat/
    VatGlassState.java
    part/VatGlassEntity.java
    part/VatHeadEntity.java
    client/VatClient.java
    client/model/VatGlassModel.java
    client/render/VatHeadEntityRenderer.java
    client/render/VatRenderDescriptor.java
  src/main/java/net/impactvector/mobvats/gametest/VatGameTests.java
  src/main/java/net/impactvector/mobvats/gametest/client/VatClientSmokeTest.java
  src/main/resources/assets/mobvats/models/block/formed_glass_material.json
  src/main/resources/assets/mobvats/textures/blocks/tank_surface.png
  src/main/resources/assets/mobvats/textures/blocks/tank_rim.png
  src/main/resources/assets/minecraft/atlases/blocks.json
  tools/generate_resources.py
  2026-10-03-initial-readme.md, 2026-10-03-asset-provenance.md, RENDERING_NOTES.txt

Verification
  .\gradlew.bat --offline build runGameTestServer passed on the laptop.
  All original six GameTests passed; glassBoundsAndFormationLifecycle also passed.
  New headless coverage includes rectangular/tall bounds, central cells without
  borders, both corner planes, cap insets, wall interruptions, break/reform, and
  the existing server-entity isolation invariant. No separate unit test sources.
  Opt-in client smoke/reload infrastructure now asserts glass model-data changes,
  layer selection, general-only quads, both windings, descriptor reuse/full bounds,
  disassembly clearing, and reload formation. Those tasks have not been run.
  Existing restored PNG hashes remain unchanged. No release/publishing performed.
  Ordinary runClient startup completed. After atlas registration, the restarted
  client log contains no missing-texture/missing-sprite/atlas-parse warnings and
  reports assembly on both logical client and server. The user confirmed "it
  works now" on October 3, 2026, accepting the rendering in the current client.
  This does not establish every listed scenario or shader compatibility.
  An additional agent relaunch hit a locked Minecraft artifact
  while another client launched via the user's Gradle cache; that client was
  left running and untouched. Do not rebuild Minecraft artifacts concurrently
  with an active client using the same build directory.

Manual acceptance belongs to the user
  From the mod repository: .\gradlew.bat runClient
  Build a larger valid vat with glass across several side-wall cells and vertical
  glass edges; include roof glass and a controller/casing interruption. Compare
  unformed, formed, diagonal/far-wall view, disassembled, and repaired states.
  Verify tint/perimeter readability, visible stationary cow/head tracking, no
  cell grid, no lost corner plane, no z-fighting, and recovery after save/reload.
  The code's assertions do not establish subjective visual or shader acceptance.

Limitations and next rendering step
  Stock chunk translucency sorting can still show artifacts with overlapping
  transparent objects or across chunk boundaries. Shader/render replacement mods
  may alter culling, tint, or entity/translucent ordering; disabling backface culling
  could draw both windings. These environments have not been verified. Opaque
  service parts interrupt transparent walls by design. Planes have no refraction
  or simulated thickness. Frame art and fluid physics remain unchanged/unimplemented.
  First accept/tune the perimeter width and tint in the user's client, including
  a maximum-size vat across chunk boundaries. Then add a single-prism fluid visual
  prototype using the cached interior bounds, before wiring fluid storage/IO.
```
