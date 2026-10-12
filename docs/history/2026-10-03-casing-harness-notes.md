> Historical record. Original: `CASING_HARNESS_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
Native Casing, Formed Interior Rims, and Harness Straps — October 3, 2026

Assets and art direction
  New original 16x16 RGBA pixel patterns, with no inherited shell/IE art:
    C:\Projects\MobVats\src\src\main\resources\assets\mobvats\textures\blocks\vat_casing_face.png
    C:\Projects\MobVats\src\src\main\resources\assets\mobvats\textures\blocks\vat_casing_edge.png
    C:\Projects\MobVats\src\src\main\resources\assets\mobvats\textures\blocks\vat_casing_inner.png
  Authored as deterministic pixel rules in tools/generate_resources.py. FACE is
  subdued blue-steel (roughly RGB 91/106/114) with sparse fasteners and a small
  muted olive lab marking. EDGE uses darker steel (48/65/77), a cool V=0 highlight,
  and a shadow edge. INNER uses dark subdued steel (42/55/63) with restrained seams.
  Colors harmonize with the existing blue-white tank glass; geometry supplies
  most of the detail. Palette/contrast are provisional pending user acceptance.
  Head, Face, Flesh, Syringe, Essence, tank_surface, and tank_rim remain unchanged.
  Controller/drain/harness keep their existing temporary broad-face textures,
  but share the formed 3px wall-panel geometry described below.
  Full hashes are in ASSET_PROVENANCE.txt. All material IDs have explicit atlas
  entries. casing_material.json loads the three semantic sprites for model baking.

Recessed 3-pixel formed shell geometry
  The first trim implementation floated one block inside the glass footprint.
  The following correction restored full cubes but raised perimeter lips into
  the glass area. The user's clarification replaces both with a hollow shell.

  Inclusive assembled bounds are min/max; outer rendered planes are min/max+1.
  Logical interior AABB [min+1,max] remains unchanged for validation/specimens.
  Formed casing thickness is measured inward from the actual OUTER shell plane,
  not from that logical interior. For each outward face VatCasingGeometry fills
  only a 3px panel: negative direction local axis [0,3]; positive [13,16].
  Perpendicular panels union into connected L/corner walls. No full cube backing
  or raised lip is added. All geometry stays inside its original casing cell.

  Bottom floor lies at local Y=3, recessed 13/16 below the glass-area boundary
  Y=16. Top ceiling lies at local Y=13, recessed 13/16 above its glass-area
  boundary Y=0. Outer perimeter walls retain the full casing-cell height, only
  3/16 thick, so their ends meet the unchanged glass boundary flush. A bottom
  corner's flush L section covers 87 pixels; 31 use EDGE and 56 use FACE body.
  Its recessed floor has 169 pixels (13x13). Center cap cells have complete
  16x16 recessed floors/ceilings and no separate rim.

  Side casing uses the same thin outer-plane panels. Every exposed cavity surface
  is 3/16 from its corresponding external plane. Solid-union surface extraction
  emits each exposed local pixel face once and merges same-material rectangles.
  No floating inner rectangle and no casing geometry in neighboring glass cells.
  Unformed casing and items remain full cubes. Glass, textures, collision, light occlusion, production and validity rules
  are unchanged. Functional wall components now follow the same thin-shell rule.

Semantic and directional materials
  Existing PNGs are unchanged: FACE on bracket body and ordinary exterior;
  EDGE on the exposed one-pixel directional perimeter; INNER on chamber-facing
  shell and recessed surfaces. North/west corner strips retain NORTH/WEST
  directions. The EDGE sprite V=0 highlight points to the corresponding world
  edge. All geometry and material UVs remain within the local 0..16 range.
  Each patch has one material, without overlaid local coplanar quads.

Functional wall panels
  VatPartType.usesWallPanel defaults to every type except casing, glass and the
  interior Head/Flesh. Future wall components inherit the same rule without an
  individual renderer. AbstractVatEntity supplies a VatWallPanelState snapshot
  derived from assembled bounds and actual adjacent glass entities. Unformed
  blocks/items retain their construction cube; break/repair follows ZeroCore
  model-data lifecycle. The model cache key includes both shell and glass-edge mask.

  VatWallPanelModel uses the common VatCasingGeometry 3px panels. Controller side
  orientation follows vat bounds, harness uses local Y=13..16, and drain Y=0..3.
  Both broad faces use the original outward block-face sprite. Four possible
  thickness sides use vat_casing_edge, the existing lip material. Those sides are
  baked/emitted only if the adjacent shell block is glass. Casing-adjacent sides
  are geometrically calculated but hidden and receive no textured quads. Current
  cap harness/drain have casing on all four sides, so only two broad faces render.
  Controller glass adjacency selects its exposed thickness edges independently.
  No new PNGs, gameplay behavior, collision geometry, or separate inner/outer art.

Harness straps and attachment definitions
  The existing Head remains the only dynamic render anchor. Four starts are the
  actual panel underside corners at descriptor.harnessPosition with
  Y offset 13/16: (x,y+13/16,z), (x+1,y+13/16,z), (x,y+13/16,z+1),
  (x+1,y+13/16,z+1). HarnessStraps derives four independent CableCurve objects.
  MobRenderDefinition.DEFAULT derives the endpoint from the visual Cow's body
  AABB: center X, minY + 0.55*height, center Z. Fractions lie strictly inside (0,1),
  so the attachment penetrates bounding-box volume rather than ending on its skin.
  Cow uses the default; registerOverride(entityId, definition) accepts optional
  per-entity fraction overrides during setup. No full mob lexicon or future
  head/tool anchor fields were introduced. Mesh intersection is not performed.
  Head yaw/pitch never enter body-anchor or cable geometry calculations.

Curve and reusable rendering
  For t in [0,1]: P(t)=lerp(start,end,t) - (0, 4*sag*t*(1-t), 0).
  Sag is 0.10 block at the midpoint, zero at endpoints. This is a restrained
  parabolic suspension approximation, not an IE implementation or physical
  catenary solver. Each curve uses 16 segments/17 points. CableCurve supports
  2..64 segments and validates finite, distinct points.
  CableRenderer.bake builds square-section geometry with tangent-aligned rings,
  four side quads per segment and end caps. Initial thickness is 3/64 block
  (0.046875), so cables remain smaller than casing trim. A stable reference axis
  prevents frame flips on these short curves. Cached meshes have no world/entity
  references and require only vertex submission during ordinary render frames.
  renderCable(mesh, origin, material, pose, buffers, light) is the small reusable
  rendering entry point. The dark vat_casing_inner PNG supplies the strap material
  through an ordinary opaque/cutout entity render layer. No IE code/assets copied.
  Cow depth rendering hides endpoints inside its torso. The AABB attachment can
  require later per-mob tuning because it does not guarantee intersection with
  every animated mesh.

Lifecycle and patterns reused
  ZeroCore2: AbstractMultiblockEntity.getUpdatedModelData, assembly/break callbacks,
  requestModelDataUpdate/notifyBlockUpdate, and markMultiblockForRenderUpdate.
  ExtremeReactors2: RotorBearingEntityRenderer, RotorDescriptor, and
  TurbineRotorBearingEntity's cached-descriptor reset on assembly/break. The
  existing VatRenderDescriptor/Head render-revision cache follows that pattern.
  Straps join the same VisualState as the render-only Cow and are rebuilt once per
  formation revision. Disassembly/pause clears visuals on the next renderer query.
  No new render anchors, ticking entities, packets, or ZeroCore lifecycle duplication.
  Cow construction suppression, stationary body, head tracking, NBT persistence,
  and server/client entity-manager isolation retain their existing implementations.

Verification
  .\gradlew.bat --offline build runGameTestServer: build, packaged resource checks,
  and all eleven autonomous headless GameTests passed after geometry correction.
  Casing checks cover full exterior area, recessed 3px panels in all six directions,
  directional materials, L union area, local duplicate faces, and break/repair.
  Regression checks cover minimum 3x4x3 and rectangular 7x6x5 bounds, including
  translated world coordinates. Every flush cap-rim pixel must lie within 3px
  of an actual glass wall; former inset cells cannot acquire a lip. Geometry cannot
  extend outside its casing cell or push into the glass area.
  Functional-panel integration checks cover thickness, matching broad-face material
  selection, glass-only exposed sides, glass-to-casing replacement and break/repair.
  Harness checks additionally enforce the new actual panel-underside starts;
  sag, segmentation, body endpoints and glass lifecycle behavior remain unchanged.
  verifyRenderResources checks packaged model/texture/atlas references, 16x16
  casing materials and the seven preserved restored/glass texture hashes.
  No Minecraft client, client smoke/reload task, or input automation was used.

Manual checks for the user
  1. No detached/floating inner rectangle remains.
  2. Bottom floor is recessed into the base, with a 3px perimeter wall.
  3. Top ceiling is recessed into the cap, mirrored upward.
  4. Side/corner channels join continuously where casing is present.
  5. Shell thickness is 3/16 block; no raised projection enters the glass area.
  6. Glass touches metal without an air gap.
  7. Exterior casing is intact while its chamber side appears hollow/thin.
  8. Continuous glass rendering and borders remain unchanged.
  9. Four sagging harness straps retain their torso attachment.
  10. No visible z-fighting or duplicate geometry at seams/corners.
  11. Controller, harness and drain appear 3px thick and show matching broad faces.
  12. Only edges beside glass show the casing lip texture.
  13. Harness straps start on the thin panel underside without a floating gap.

Remaining limitations
  Client visual acceptance is pending. Adjacent block meshes retain hidden opposing
  internal faces; the union removes duplicates within each casing block. Functional
  controller/drain/harness art remains provisional; their formed geometry is 3px.
  Functional panel edges beside casing are culled.
  Shell thickness is visual only; collision and light occlusion remain full-block.
  Straps remain static, square-section meshes using the Head's light value and
  body AABB anchor, with no animated mesh intersection calculation. Existing
  translucent chunk sorting/shader behavior remains. No fluid implementation,
  art redesign, validation changes, publication, release, commit, or push in this
  correction pass.

Head facing addition
  VatHeadBlock adds persisted horizontal facing opposite the placing player's
  look direction. The north Face texture rotates via four blockstate variants.
  VatRenderDescriptor caches the validated Head's yaw; the client specimen body
  uses that yaw, while gaze remains clamped relative to its body. Bounds and
  world-down scan are independent. A four-direction item-placement GameTest
  checks formation, yaw, block-state serialization and break/reform persistence.
  Packaged resource checks also enforce all four model rotation mappings.
```
