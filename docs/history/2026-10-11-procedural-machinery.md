> Historical record. Original: `docs/PROCEDURAL_MACHINERY.md`. Current reference: [05-PROCEDURAL-ART.md](../05-PROCEDURAL-ART.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Procedural Vat Machinery

Implemented October 11, 2026. Source recipe: `tools/procedural-art/machinery.json`.
Generator: `gradle/procedural-art.gradle`, version 1.8.1. No generative image artwork.

## Generated assets and production bindings

This extension adds 32 deterministic 16x16 PNGs and 24 model JSONs. Each of the
grayscale master, T1 verdigris copper, T2 iron and T3 bloody iron has six shared
machinery materials (metal, recess, bezel, fastener, channels, controls), two green
screen textures (skull and rotor), and four block models. Four production block
parents and four item models bind the existing registrations without changing
blockstates. Production retains the established T1 palette; the other palettes
are generated variants and preview selections, not new machine tier mechanics.

Generated resource root: `build/generated/resources/procedural-art/`.

| Registration | Generated block parent | Inventory parent |
| --- | --- | --- |
| `vat_controller` | `mobvats:block/procedural/t1/vat_controller` | `mobvats:block/vat_controller` |
| `mixer_controller` | `mobvats:block/procedural/t1/mixer_controller` | `mobvats:block/mixer_controller` |
| `vat_harness` | `mobvats:block/procedural/t1/vat_harness` | `mobvats:block/vat_harness` |
| `vat_drain` (Sump) | `mobvats:block/procedural/t1/vat_drain` | `mobvats:block/vat_drain` |

Model paths are `assets/mobvats/models/block/procedural/<finish>/<registration>.json`,
plus `models/block/<registration>.json` and `models/item/<registration>.json` for
the production bindings. PNGs are under
`assets/mobvats/textures/block/procedural/<finish>/`.
The existing provenance manifest `META-INF/mobvats-procedural-art.json` records
all generated texture/model hashes, generator hash, palette hash and the new
machinery recipe hash. Sources and historical placeholder fingerprints are retained.

## Shared Controller geometry and displays

Both Controllers use the same nine authored elements: substantial full-width
housing, raised perimeter, recessed front panel, screen bezel, lower controls,
and a single front-facing screen plane. There are no port arrows or socket faces.
Housing, control arrangement, indicator, display dimensions and inventory
transforms are identical. Only the screen texture selection differs.

The Vat display contains a centered, species-independent skull with no horns,
ears or crossed bones. The Mixer display contains three blades around a central
rotor hub. The screen backgrounds are fixed `#071C12`; the glyphs are fixed
`#65F28B`. Both are authored pixel masks without text, gradients, antialiasing,
animation, recipe state, specimen state or live metrics. The common lower control
panel has three controls and one fixed red status marking.

All four items inherit their actual 3D block machinery models. GUI transforms are
rotation `[30,225,0]`, scale `[0.85,0.85,0.85]`; ground and fixed transforms are
also shared. No item uses `item/generated`.

## Harness and strap alignment

The Harness uses an overhead mounting plate, recessed central mechanism, retaining
collar, structural corner arms and four underside fittings. All authored geometry
lies between y=11 and y=16 model pixels. The central mounting housing extends
one pixel below the ceiling inner surface at y=13, and the retaining collar
extends two pixels below it. Corner strap fittings stay at the original y=13 plane. The visible corner fittings include
(x,z)=(0,0), (16,0), (0,16), (16,16) at y=13, matching `HarnessStraps.corners`.
The same fittings support `HarnessStraps.origins` for larger specimen footprints.

Existing specimen placement, strap curves, thickness, segment count and body
attachment definition remain authoritative. No decorative cables or second strap
renderer were added. The preview exports four actual curves and their meshes from
`HarnessStraps` and `CableRenderer.bake`; it also submits the neutral adult vanilla
CowModel to export specimen geometry. The cow has solid preview shading rather
than its in-game texture. The preview does not simulate head tracking or lighting.

## Sump appearance and orientation

The Sump retains the existing `vat_drain` ID and floor placement. Its basin lies
between y=0 and y=3 pixels, with a dark recessed bottom, reinforced metal perimeter,
four shallow collection-channel plates and five grate bars around/over the central
opening. It has no Void glow, valve arrows or buffer reservoir. Its orientation
stays bottom-mounted in every assembly state; Harness orientation stays ceiling-mounted.

## Formed and unformed rendering

The existing stationary `FixtureModel` now includes these four registrations.
They bypass generic wall-panel substitution. Their own housing/ceiling plate/floor
basin supplies the installed structure, so no additional opaque casing model is
layered over them. The Controllers face outward according to existing assembled
bounds. Assembled Controllers remove all six faces of the rear housing cuboid,
retaining the closed 3-pixel front panel, greenscreen, bezel and controls. Unformed
and inventory views retain the full north-facing construction housing.
Harness and Sump remain in their ceiling/floor orientations, including interior
placements. Each model owns one solid chunk layer and recovers its construction
appearance when disassembled. Existing snapshot and resource-reload caches are reused.

Changes are limited to art generation, client model registration/wrapping,
preview exports, tests and placeholder exclusions. No block collision or physical
shape, specimen geometry implementation, gameplay, GUI, activation, processing,
routing, Harness assignment, Sump collection, tier registration, ZeroCore lifecycle,
conduit/buffer/valve/pump geometry or dynamic specimen/strap renderer changed.
The existing Pump exterior correction is preserved.

## Previews and validation

- `build/procedural-art-preview/models.html`: side-by-side Controllers in all three
  palettes, formed/unformed shell context, 32px/16px inventory views, enlarged
  greenscreens, Harness underside and ceiling/strap scenes, Sump standalone/floor/
  top-down/inventory views, and an assembled Vat machinery/specimen scene.
- `build/procedural-art-preview/index.html`: texture contact sheets and comparison pages.
- `proceduralArtPreview --offline`: passed, including authoritative geometry exports.
- `build`: passed. The generator contracts verify byte-for-byte determinism, shared
  hardware, glyph selection, fixed phosphor colors across all palettes, item
  parents/transforms, exposed screen plane, anchor coordinates, Sump/grate extent,
  resource bindings and collision-free paths. Renderer tests cover face rotation,
  fixed floor/ceiling orientation, no duplicate casing, and repeated assembly transitions.
  JAR checks verify processed hashes, blockstates/items, unique paths and exclusion
  of the replaced Mixer Controller AI PNG. Existing release-guard regression tests pass.
- `runGameTestServer`: passed, all 753 required tests.
- Preview execution with a recording Canvas/DOM: passed for T1/T2/T3; all local
  texture paths resolve and all scene builders/render paths execute. This checks
  execution and bindings, not browser pixels or Minecraft visual acceptance.
- `buildTestWorld`: default `MobVatsTest` was locked by a running Minecraft session;
  the generator stopped before modifying it. The same standard generator succeeded
  with `-PworldName=MobVatsMachineryArt`, under `run/saves/MobVatsMachineryArt/`.
- `git diff --check`: passed.

Local validation logs: `build/machinery-build.log`, `build/machinery-gametest.log`,
`build/machinery-preview-final.log`, `build/machinery-world-final.log`.
The final world run also regenerated the completed preview.
No Minecraft client was launched and no commit, publication, release or deployment occurred.

## Manual visual acceptance

Inspect the generated previews and the separate acceptance world in a user-launched
client. Verify glyph readability at normal inventory scale, Controller front visibility
on each formed wall and after dismantling/reforming, ceiling fittings at single and
multi-Harness strap origins, specimen clearance, and floor basin/grate visibility.
Check resource reload and Minecraft lighting/occlusion, especially adjacent glass.
The browser preview uses production machinery/strap geometry and neutral CowModel
geometry, but does not reproduce Minecraft lighting, culling or transparency sorting.

The Mixer Controller placeholder is explicitly replaced and excluded from shipping.
Its original source/hash remains historical. Shaft, impeller and source-only motor
placeholders still block public release; the existing guard has no exception or bypass.

## Controller rear housing and Harness ceiling correction

The assembled Controller rear cuboid is excluded before rotating the retained
panel toward the machine boundary. Its front cap at z=3 is also excluded;
the inward-facing backs of the front-panel elements remain, closing the panel.
Unformed blocks and inventory retain the complete Controller housing. Renderer
regression tests read both generated Controller models, verify removal of exactly
six rear-housing faces, preserve panel closure, and test repeated assembly and
disassembly on all four wall orientations.

The Harness central housing now occupies y=12..15 and its retaining collar and
mechanism occupy y=11..12. The ceiling inner surface is y=13, so the housing and
collar visibly project one and two pixels below the casing, respectively. Corner
strap fittings remain at y=13. Strap origins, curves and specimen placement are
unchanged. Generator tests verify the protruding housing/collar and original
corner attachment coordinates in every palette.

The assembled Controller comparison and assembled Vat preview use the same rear
exclusion as production; unformed and inventory comparisons retain full housing.
Build and procedural preview generation passed, the preview execution check passed
for T1/T2/T3, and all 753 required headless GameTests passed. Correction logs:
`build/machinery-correction-build.log` and
`build/machinery-correction-acceptance.log`.

Standard acceptance-world generation also passed in
`run/saves/MobVatsMachineryCorrection/`, verifying 10 mirrored fixtures and 9
operator Vats. `git diff --check` passed. Manual visual acceptance remains pending;
no client was launched for this correction.

## Feeder, Blender and Tool Port

Generator version 1.9.0 extends the existing recipe with `vat_feed_port`,
`vat_blender`, `vat_tool_port` and `tool_vise`. This adds eight 16x16 PNGs
(`machinery_vise_metal` and `machinery_vise_pad` in four finishes) and 22 model
JSONs: four models per finish, three production block parents and three item
bindings. Shared machinery materials are reused. The subsequent Feeder terminal
correction brings the complete resource family to 196 procedural textures.

The Feeder is a centered 10x10x10 hose reel with a conduit-aligned spindle,
open metal end frames and five dark hose laps. It has no wall panel or perforated
nozzle. The original per-mob feeding hoses are rendered again from the unchanged
interaction anchors and physical feed curves. No particular food/fluid color is drawn.

The Blender has a 12x12 reinforced square housing, recessed chamber, four-sided
retaining collar, transverse cutting drum and eight stationary teeth. Geometry
stays between z=3 and z=9 in the north-wall mount, inside the existing operating
envelope. No previous independent cutter renderer existed. The new cutter is
chunk-owned stationary geometry; actor priority, reach, line of sight and output
transactions are unchanged.

The Tool Port uses a fixed **7x5x5** vise. In arm-local coordinates, width runs
along Z (native item X), height along Y, and depth along X (native item thickness).
Bounds are x=5.5..10.5, y=5.5..10.5, z=4.5..11.5 before model centering.
Two solid jaws have 2.5px-high inward dark gripping faces, a substantial rear housing and
a tightening screw. Dark faces share the jaws' surfaces; there are no overlapping
pad cuboids or tool-specific jaw models.

### Item transform and grip

The item renderer still resolves the actual stack/model and inherits the
`THIRD_PERSON_RIGHT_HAND` scale. Existing sword and generic item translations,
Y mount rotation (-90 degrees, +90 for the syringe), and final `NONE` display
context are unchanged. No vanilla display rotation is applied a second time.
Vanilla generated item thickness is 1px; inherited scales produce 0.85px for
the iron sword and 0.55px for shears/buckets. The fixed 0.45px gap gives 0.20px
overlap per sword jaw and 0.05px per shears/bucket jaw.

`ToolViseArt.center` places the decorative head at the tool's mounting end,
scaled using the actual model transform. Native centered item coordinates are
(-5/16,-5/16) for swords, (-3/16,-3/16) for generic handles including shears,
(-3/16,+3/16) for bucket/container rims, and (+1/2,0) for the reversed syringe's
rear edge. The entire 7x5x5 vise remains unscaled.
An existing cached rod mesh supplies a decorative stem from the original arm tip
to the rear housing. The equipped tool is still submitted once by the existing
item renderer. Interaction origins, assignment, durability and tool retention
are unchanged.

### Ownership, inventory and previews

All three parts bypass generic formed wall-panel substitution. Blender/Tool Port
retain their 3px mounting plates and wall orientation. The Feeder has no panel:
its reel stays centered and rotates its spindle toward the actual adjacent
conduit, in both wall and interior positions. Interior Tool Port mounts retain
their existing origin translation and interaction direction. Construction
and inventory Tool Ports retain the empty vise; formed chunk geometry suppresses
its vise and short decorative arm so the existing Head renderer owns one moving
arm/vise. Resource reload recreates baked models and chunk caches. No new
block updates, synchronization, model generation during frames, or animation
were introduced.

Each item's parent is `mobvats:block/<registration>`, which binds the generated
T1 model. All three use GUI rotation `[30,45,0]`, scale `[.85,.85,.85]` to expose
the inward mechanism; ground/fixed transforms retain existing conventions.
Other finishes are generated art/preview variants, not new registrations.

`build/procedural-art-preview/models.html` includes all tier finishes, standalone
and installed machinery beside the exported vanilla CowModel, actual conduit-to-
spindle connections and per-mob feeding hoses, an idle arm/vise using the production mount calculation, and empty,
diamond sword, shears, bucket and syringe vises in front/side/three-quarter views. Tool meshes extrude
actual opaque vanilla item pixels by 1px and use inherited hand scales and the
same mounting rotation. Static overlap checks require real opaque pixels inside
the grip and less than half the silhouette covered. The preview approximates
lighting, and does not reproduce model overrides/glint or resource-pack tools.

### Validation and manual acceptance

`proceduralArtPreview --offline` and `build` pass, including deterministic
generation, palette/provenance/resource checks, exact vise dimensions, jaw gap,
opaque tool overlap, inventory bindings, wall/interior orientation, repeated
formation/disassembly ownership, render-buffer tests and placeholder guard tests.
`runGameTestServer` passed all 753 required tests. `buildTestWorld
-PworldName=MobVatsAcceptance` passed: 10 mirrored fixtures, 9 operator Vats and
5000 observation ticks. `git diff --check` passes. Client smoke checks were
extended to resolve the baked vise, 3D machinery inventory models, atlas materials
and representative vanilla hand scales; they were not launched.

Manual acceptance remains pending. Open the generated HTML locally and inspect
all three finishes and tool silhouettes. Browser-tool inspection was unavailable
because its URL policy rejects local `file:` pages. In the manually launched
Minecraft client, open `MobVatsAcceptance`; use the operator row's Shears,
Milk/container, Kill/harvest, Blender and Feed fixtures. Inspect inventory and
unformed blocks, all four formed wall orientations, interior feeder mounts,
active/idle jaw attachment, acquisition/removal/replacement and reassignment,
adult/juvenile transitions, disassembly/reformation, resource reload and saved
chunk reload. Confirm one equipped item and one feeding hose, visible machinery,
and no gaps, excessive tool occlusion or casing intersections. Minecraft visual
acceptance has not been performed. No client was automatically launched, and no
commit, publish, release or deployment was performed.

## Tool Port mounting-end alignment correction

Generator/preview version 1.9.1 replaces the rightmost-third rule. The previous
(.25,.125) offset selected the sword's upper-right blade: MobVats deliberately
maps native item +X to arm +Z using its -90-degree Y mount. The model's vanilla
handheld display rotations/translations are not applied; only hand scale is used,
followed by `ItemDisplayContext.NONE`. Consequently there was no unexpected
sword rotation or mirror to correct. Its existing item translation already pins
the native (3,3) hilt to the authoritative arm tip.

Only the decorative vise center changes. For swords the corrected center is
exactly `(0,0,armLength)` in the arm's local frame. Shears use their lower-left
handles at native (5,5). A bucket/container exception uses the upper-left rim at
(5,11); the upper rim and lower shears handles cannot share a vertical offset
without gripping the wrong region. This covers `BucketItem` and `MilkBucketItem`
while using the same clamp. The existing reversed syringe branch grips its rear
edge at (16,8), with its needle still facing arm +Z. Sword and syringe branches
already existed in item rendering; container placement is the only new exception.
No claim is made about recognizable handles on arbitrary modded/resource-pack
items. Empty ports use the deterministic generic mounting pose.

The 7x5x5 envelope, 0.45px gap, jaws, rear housing, screw, textures, tier mapping
and all production model bindings are unchanged. The item translation, scale,
rotation, item submission, arm origin/extension/aim, authoritative target,
assignment data, physical routes and synchronization are unchanged. The existing
decorative connector follows the relocated vise; cached arm rods are reused.

Actual Minecraft diamond-sword/shears/bucket PNGs and inherited model hand scales
plus MobVats' syringe asset drive the preview. Conservative front-view projected
coverage is 10/84 opaque pixels for the sword, 24/77 for shears, 36/146 for the
bucket and 17/40 for the syringe. Tests require real opaque mounting-region pixels
inside the vise projection, real working-end pixels outside it, and less than
half the silhouette covered. Additional CPU checks cover the sword hilt at the
original tip, reverse mount, container rim distinction, scale immutability,
replacement without stale placement, saved/restored presentation anchors, exact
recipe/runtime offsets and unchanged single item submission/mount operations.

Manual visual checks remain necessary: inspect the updated preview's front,
side and three-quarter views, then the retained diamond sword's hilt and exposed
blade in `MobVatsAcceptance`. Check shears handles, bucket rim/body and syringe
rear barrel/needle, empty ports, tool replacement, assignments, formation,
restoration and chunk reload. Preview lighting is approximate; Minecraft client
visual acceptance and client smoke execution were not performed.

Correction validation passed: `proceduralArtPreview --offline`, `build`, all 753
required `runGameTestServer` tests, `buildTestWorld -PworldName=MobVatsAcceptance`
and `git diff --check`. Acceptance generation completed after its existing
XP/growth observation wait and verified 10 mirrored fixtures and 9 operator Vats.
Logs are retained under `.cache/tool-grip/gametest.log` and
`.cache/tool-grip/acceptance.log`. No commit, push, publish, release, deployment
or automatic Minecraft client launch was performed for this correction.

## Superseded Feeder conduit-terminal correction (1.10.0)

This version incorrectly retained a wall-panel appearance and removed the mob
feeding hoses. The 1.11.0 hose-reel correction below supersedes this design.

Generator version 1.10.0 replaces the old manifold and its multiple dispensing
outlets. One 3px tube extends inward from the existing 3px Vat mounting plate,
with the same partitioned rounded-square twin-ring cuff style as formed fluid
conduits. The tube reaches z=11; one 5x5x1 nozzle occupies z=11..12 and has nine
dark perforations. Wall mounts retain their existing outward-face rotation;
interior mounts retain the existing center-origin translation/orientation.

All Feeder materials are neutral tier channels. Pipe, housing and collar reuse
`vat_frame`/`vat_trim`; the two new texture recipes are `feeder_connection` and
`feeder_nozzle` in grayscale, copper, iron and bloody iron. No fixed azure-blue,
food, slurry or resource indicator is used in the Feeder. Existing resource
markings on unrelated network pipes are unchanged. Inventory remains a 3D model
bound to the registered T1 Feeder and its existing camera transform.

Construction/inventory has one short inlet mating to the native conduit arm at
the adjacent cell face. During normal chunk model-data collection it rotates
toward an adjacent fluid conduit, with an item-conduit fallback; only that inlet
rotates. No controller/routing work or neighbor discovery occurs during frames.
Formation suppresses this construction inlet. `FeederArt` reads the existing
immutable `ConduitGeometry` endpoint fittings and joins their exact service
anchors to the unchanged feeding origin. All associated fluid/item fittings
merge into one terminal, including a shared connection leg where applicable.
The bridges stay short (at most the existing 6px wall service offset) and neutral.

`ConduitRenderer` owns these cached bridges alongside the existing physical TAP
fittings. The chunk model owns one inward tube/nozzle per block. The existing
coordinated renderer still renders conduits only from the first Vat Head, and
the visual bridge grouping is per Feed Port rather than per specimen. Four feed
assignments therefore retain one terminal and do not produce four long hoses.
`VatMechanismRenderer` no longer submits the obsolete feeding hoses. It retains
the existing Tool Port path without modifying arms, items or the vise.

Decorative bridge boxes are never added to `ConduitGeometry.occupied()`, physical
snapshots, route intersections or collision. `EndpointGeometry`,
`VatInteractionGeometry`, `VatFeedPortEntity.RANGE`, topology/assignment rules,
feeding curves, item/slurry consumption and specimen mechanics are unchanged.
Formation, disassembly, pause/restoration and resource reload use the existing
model-data and immutable conduit-cache infrastructure. No extra synchronization
or forced decorative block updates were introduced.

The procedural preview includes standalone/inventory, unformed conduit mating,
four formed wall orientations and a shared central Feeder serving four exported
CowModel specimens, in all three tier finishes. Mesh data for network fittings
and terminal bridges comes from the actual production geometry helpers. The
preview shows the real network's existing fluid marking outside the neutral
terminal, and contains no obsolete per-cow feeding hose.

Focused CPU tests verify neutral materials/perforations, one nozzle/inlet,
matching TAP diameter, all six construction inlet directions, formed inlet
suppression, cached-quad reuse, exact service/interaction-anchor continuity on
four walls, item/slurry branches sharing one nozzle, restoration, one terminal
for four cow assignments, absence of hose rendering and unchanged physical
snapshot/occupied volumes. Tests use production geometry and generated resources.
Manual Minecraft visual acceptance remains pending: inspect conduit-to-Feeder
joins in construction and formed Vats, four-cow views, all wall orientations,
interior fixtures, lighting/culling, disassembly/reformation and chunk reload.

Validation passed: `proceduralArtPreview --offline`, `build`, all 753 required
headless GameTests, `buildTestWorld -PworldName=MobVatsAcceptance` and
`git diff --check`. Acceptance generation verified 10 mirrored fixtures and 9
operator Vats over 5000 observation ticks. Logs are in `.cache/feeder-build.log`,
`.cache/feeder-gametest.log` and `.cache/feeder-acceptance.log`. No client was
launched and no commit, push, publish, release or deployment was performed.

## Feeder hose reel (1.11.0, end geometry superseded below)

The Feeder is a centered **10x10x10** reel body at model coordinates `(3,3,3)`
through `(13,13,13)`. There is no full-block panel, wall skin, perforated nozzle
or perforated texture. Open metal end frames and four corner supports enclose
five clipped-corner hose laps around a single 3px spindle. Coil material is dark
neutral tier channels; end frames/spindle retain the existing neutral tier metals.
`feeder_hose` replaces `feeder_nozzle` in all four palettes.

The spindle and wound hose rotate together toward the actual adjacent conduit,
instead of toward the wall's outward face. The entire reel stays at the block
center, including interior placements: no wall-panel or interior-offset transform
is applied. In construction/inventory, the spindle extends from the 10px body
face to the cell boundary. While formed, the existing physical service anchor
connects to the reel's spindle face through a cached neutral fitting; its last
segment follows the spindle axis. Multiple existing item/fluid attachments share
one reel, with the fluid connection selecting its primary spindle direction.
All connections remain decorative and never enter collision or occupied geometry.

**The original feeding hoses are restored.** `VatMechanismRenderer` again bakes
and renders one hose for each active feed presentation link, using the original
`VatInteractionGeometry.feedCurve`, sag, segment count, thickness and anchors.
One Feeder serving four cows therefore has one reel and four mob hoses. Geometry
is cached using the existing presentation revision; no new synchronization,
per-frame geometry generation, assignments or gameplay changes were introduced.
The feeding origin overlaps the reel's coil or hub in every inlet orientation.

CPU tests check exact generated 10x10x10 body bounds, absence of any panel/nozzle,
five hose laps and one spindle, all six production reel rotations, hose-root
attachment, conduit-to-spindle continuity, centered interior mounting, four-cow
hoses matching the authoritative production curves, unchanged physical snapshots,
item/slurry attachments, restoration and model-data cache reuse. The procedural
preview exports actual rotated reel quads and restored production feeding hoses.
Standalone, four wall placements, four-cow and all tier views are included.

`build/procedural-art-preview/feeder-reel.png` is a static software-shaded view
of the actual generated geometry, rather than a Minecraft screenshot. Its local
helper is `.cache/render-feeder-reel.ps1`; the repeatable full scene preview is
`build/procedural-art-preview/models.html`. Manual Minecraft lighting, culling,
attachment and four-cow visual acceptance remain necessary. No client was launched
and no commit or push was performed for this correction.

Validation passed: `proceduralArtPreview --offline`, `build`, all 753 required
headless GameTests, separate `MobVatsFeederReel` acceptance-world generation
(10 mirrored fixtures, 9 operator Vats, 5000 observation ticks), and
`git diff --check`. Logs are retained in `.cache/feeder-reel-build.log`,
`.cache/feeder-reel-gametest.log` and `.cache/feeder-reel-acceptance.log`.

## Current opaque Feeder hose reel (1.11.1)

Both spindle ends now have full opaque 1px end plates. Their outer faces use
the tier metal finish with an opaque black central square representing the
conduit socket. The central winding is a solid cuboid with repeated dark ribs
in its opaque texture; no gaps or transparent pixels remain between hose turns.
The body still occupies exactly `(3,3,3)` through `(13,13,13)` (10x10x10).
The existing 3px conduit spindle, six-axis orientation, formed fitting bridges,
cladding behavior and original per-mob feeding hoses are unchanged. This is a
rendering-only correction; no routing, reach, consumption, assignments or
collision changes were made.

CPU checks now verify both closed plates and their outer socket faces, the
solid winding's full cross-section and exact adjoining bounds, opaque rib and
socket textures in all four palettes, and the existing conduit/hose continuity
and four-cow geometry contracts. The optional client smoke assertion accepts
the simpler four-cuboid model. No client smoke task was launched.

Validation passed: `proceduralArtPreview --offline build` (200 generated assets,
rendering geometry checks and normal build checks) and `git diff --check`.
Build output is retained in `.cache/feeder-opaque-build.log`. The static
`build/procedural-art-preview/feeder-reel.png` was regenerated from the actual
model and pixel textures and inspected. Minecraft visual acceptance remains
pending. No Minecraft client was launched for this correction.
