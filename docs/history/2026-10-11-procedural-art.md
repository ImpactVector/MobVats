> Historical record. Original: `docs/PROCEDURAL_ART.md`. Current reference: [05-PROCEDURAL-ART.md](../05-PROCEDURAL-ART.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Production texture integration — October 11, 2026

The approved casing and sockets now drive production models. The sections below
“Historical Slice 1” describe the earlier prototype pipeline, not current bindings.

- Source-controlled binding authority: `tools/procedural-art/bindings.json`.
- PNG root: `build/generated/resources/procedural-art/assets/mobvats/textures/block/procedural/`.
- Resource locations: `mobvats:block/procedural/t1/casing_clean` and
  `mobvats:block/procedural/t1/socket_{item,fluid}_{inward,outward}`.
- `vat_casing` uses casing clean in `all`; `casing_material` uses it in
  particle/down/south/east/west. The formed casing wrapper consumes its particle
  sprite as FACE. Existing EDGE/INNER textures and all geometry/UV logic remain.
  Vat casing-clad conduits sharing this material also inherit the broad face.
- Directional consumers: `vat_item_port`, `vat_fluid_port`,
  `mixer_item_port`, `mixer_fluid_port`, and registered legacy
  `mixer_item_input`, `mixer_fluid_input`, `mixer_fluid_output`.
  For each, the generator writes the base inventory model, `_inward` and
  `_outward` models plus its existing blockstate path. Source JSON geometry is
  preserved. Items display the construction default (legacy fluid output outward).
- Render-only Boolean blockstate `port_output` selects the model. The endpoint
  remains authoritative: its change/load callback and onLoad project direction
  onto server blockstate, which Minecraft synchronizes normally. Filter and
  active/passive mode remain independent. No transfer or capability code changed.
- Gradle uses the existing generator task resource source directory and explicit
  processResources dependency. Recipes, palettes, bindings, overlays, generator
  script and source block models are inputs; unchanged inputs skip generation.
  Source resources overlapping generated models/blockstates are explicitly
  excluded; duplicatesStrategy FAIL rejects residual collisions. Existing PNGs
  stay preserved at their original paths. No runtime generator is shipped.
- The preview reads the same 44 generated PNGs packaged in the game. Grayscale,
  T1/T2/T3, clean/weathered casing, and all six sockets remain available.
  No new tiers or gameplay registrations were added.
- Weathering remains preview-only: there is no existing controller-position
  selector. Production casing uses clean, with no per-frame randomness.
- Mixer casing, glass, harness, fill/drain valves, pumps, voids, tool/feed ports,
  drives and controllers retain their existing materials. No ordinary port has
  neutral flow, so undirected sockets are generated but not assigned to a part.
- Replaced Mixer port AI overlays/PNGs are explicitly excluded. Source history
  stays retained; ledger entries record production replacement. Controller,
  impeller, shaft and source-only motors remain flagged. No release exemption.

Manual acceptance: in a user-launched development client, use an empty area with
12 x 5 x 9 blocks of space and permission level 2, then run
`/mobvatsArtFixture`. The fixture starts two blocks east/south of the command
position, places a 5 x 5 x 5 Vat with casing/glass, a 5 x 3 tiled casing wall,
Item Input/Output and Fluid Input/Output examples. A Pipez item pipe is placed
against the first item port if loaded; configure it with the Pipez wrench for
transfer testing and compare a smaller conduit connection manually.
Open port menus and toggle direction; the markings must move between inner
and outer endpoints, including after saving/reopening the world. Inspect formed
shell seams, corners, glass and pipe clearance. Weathered and undirected artwork
remain preview-only because their real-part selectors are unsupported.
The command/tests are development-only and excluded from the JAR. No client is
automatically launched, and no existing acceptance world needs rebuilding.

## Historical Slice 1

# Procedural art — Slice 1

The development generator uses authored integer-pixel recipes and semantic material
channels. It uses no image models, external dependencies, network access, Python,
or Minecraft client. Java ImageIO and Groovy ship with the existing Gradle runtime.
Generator classes and tooling are not compiled into the player JAR.

From the mod repository:

```powershell
.\gradlew.bat proceduralArtPreview --offline
# Optional: generate and open the local HTML in the default browser.
.\gradlew.bat proceduralArtPreview --offline -PopenArtPreview
```

Open `build/procedural-art-preview/index.html`. It displays native 16×16 images,
nearest-neighbor ×8 images, grayscale/T1/T2/T3 columns, all six port variants,
checkerboard transparency, and clean/weathered 3×3 and 7×7 casing walls at native
resolution and nearest-neighbor ×4. Reload after regeneration.
`build/procedural-art-preview/overview.png` is the complete initial contact sheet.
`build/procedural-art-preview/casing-walls.png` compares all twelve wall previews.
`generateProceduralArt` generates only resources; normal `processResources` and
`build` depend on it. Both generator and preview tasks skip unchanged inputs.

## Source and outputs

- `tools/procedural-art/recipes.json`: casing and concentric socket compositions.
- `tools/procedural-art/palettes.json`: exact five-channel grayscale master,
  three material finishes, exposed-metal wear, and fixed functional colors.
- `gradle/procedural-art.gradle`: bounded recipe dispatcher, authored primitives,
  preview, tests, and Gradle integration. Increment the generator version when
  changing its output contract; the manifest also records its source hash.
- `build/generated/resources/procedural-art/`: generated Minecraft resources and
  `META-INF/mobvats-procedural-art.json` provenance manifest.

The 44 PNGs use `assets/mobvats/textures/block/procedural/{finish}/...`, with
finishes `master`, `t1`, `t2`, and `t3`. There are four casing variants (clean and
seeds 17/43/89) and seven socket variants (clean and the six resource/direction
combinations) per finish. The singular `block` folder is discovered by Minecraft's
default atlas directory source; no competing atlas JSON is generated.

These are unbound prototypes, packaged for development inspection. T1/T2/T3 are
art finishes, not new gameplay registrations or claims that every machine supports
three tiers. Existing models, blockstates, mechanics, and renderers remain bound
to their current materials. Visual acceptance and explicit substitution are
required before assigning these textures to production models.

## Authoring the next texture

Add a recipe with a unique lowercase `id` and an ordered `layers` array. The
dispatcher accepts only named primitives; it never executes recipe expressions.
Coordinates are inclusive integer positions within 0..15. The canvas starts
transparent, so an unpainted pixel stays transparent.

```json
{"id":"service_panel","layers":[
  {"primitive":"panel","bounds":[0,0,15,15],"channel":"base"},
  {"primitive":"bevel","bounds":[1,1,14,14],"raised":false},
  {"primitive":"slot","bounds":[5,6,10,7]},
  {"primitive":"indicator","at":[12,3],"color":"status"}
]}
```

Primitives: filled `panel`, `fixed_panel`, raised/recessed `bevel`, Bresenham
`line`, subdued `seam`, two-pixel `rivet`, `slot`, fixed-color `indicator`,
concentric `socket` (list of `insets`), and optional `overlay`. Code-defined
`MobPixelCanvas.channels` reflects a single corner motif; `weather` places two
irregular scratches and a worn-edge cluster at seeded authored locations.
`recessed_border` draws a continuous subdued border; `fastener` adds a one-pixel
dark fastener; `material_variation` adds two quiet central material clusters.
Extend these methods or
add a named dispatcher case when a composition cannot be expressed clearly.

Material tokens are `highlight`, `light`, `base`, `shadow`, and `deep`, mapped
individually to each five-step palette. This preserves the authored contrast.
Subdued recesses and surface variation use explicit semantic material mixtures:
the border is three eighths of the way from base to shadow; quiet variation is
one eighth from base to light. These are derived from each existing tier palette,
without adding fixed structural colors or changing the established palettes.
`wear` is a separate material token: warm copper for T1 and exposed iron for
T2/T3. The T1 base is exactly `#5F8F78`; fixed fluid/item colors are Azure `#168BFA` and
`#E3BC45`. Red is reserved for status. Fixed pixels hold explicit RGBA values
and bypass every tier conversion, even when the RGB happens to be grayscale.

For ports, add `ports.resources` (`fluid`, `item`) and `ports.directions`
(`none`, `inward`, `outward`, corresponding to None/Input/Output). These independent
parameters share one socket drawing. A four-pixel diagonal from (1,1) to (4,4)
is mirrored into each corner. None uses diagonals only. Input adds two-pixel
cardinal arms meeting at the inner endpoint (4,4); Output adds them at the outer
endpoint (1,1). Arms include their endpoint, adding two new pixels per corner.
Cardinal runs never exceed two pixels, and neighboring corner motifs retain
visible horizontal and vertical gaps. Indicator pixels are neither tinted,
blended, nor weathered across the master and tier finishes.
All six variants retain identical diagonals. The markings remain inside the
corner 5×5 areas; the neutral 4×4 opening and middle edges remain unobstructed.
Endpoint geometry encodes direction independently of color. Rings
are illustrated on a flat texture, with no geometry recess or external-mod binding.

For casing wear, add `weathering: {"profile":"light","seeds":[17,43,89]}`.
The clean image is always generated. Each seed changes at most nine pixels;
there is no random full-surface noise. Seeds are explicit and the Java PRNG is
stable. Avoid expanding variant counts without a concrete need.
The casing has four balanced one-pixel fasteners, no bright bolt highlights, and
no lower horizontal bar. Weathering uses short connected/diagonally adjacent
scratch clusters with restrained exposed-metal pixels and a small worn edge.
Weathered wall previews mix the three finite seed variants using a stable hash
of preview tile coordinates. This is preview composition only; runtime texture
selection and controller behavior remain unchanged.

## Optional authored overlays

Place source sprites inside `tools/procedural-art/`; output never overwrites them.
An overlay must explicitly declare authorship and mode:

```json
{"primitive":"overlay","source":"overlays/mark.png",
 "authorship":"hand-authored","mode":"fixed"}
```

Fixed overlays preserve RGBA, skipping completely transparent pixels. For tier
material overlays use `mode: "material"` and an explicit ARGB-to-channel map,
for example `channelMap: {"#FF858585":"base"}`. Unmapped pixels fail; RGB alone
never determines tint eligibility. Use exact source pixels, without filtering,
resizing, antialiasing, or alpha blending.

PNG sources must be 16×16. Piskel sources support one selected layer (`layer`,
default 0), one frame, and one embedded PNG chunk. Export complex/multi-frame
Piskel sprites to single-frame PNG first. No Piskel source was found in the
current checkout, and the initial family uses no overlay assets.

Each generated asset records its recipe, palette source, finish, seed,
resource/direction, output hash, and overlay provenance/hashes. The manifest also
hashes the generator, recipe, and palette files. It has no timestamp or absolute
host path, so unchanged inputs produce byte-identical resources and provenance.

## Existing asset audit

| Classification | Current sources and consumers |
| --- | --- |
| Recovered source artwork | `vat_face`, `vat_flesh`, `vat_head`, `item_syringe`, and `item_essence` PNGs; original upstream revisions and hashes are in `2026-10-03-asset-provenance.md`. Preserved source artwork, not generator inputs. The ledger documents recovery, without independently proving the historical drawing process. |
| Authored procedural artwork | `tools/generate_resources.py` encodes the three existing blue-steel casing patterns and uniform RGBA tank surface/rim materials. Authored pixel patterns are described as hand-authored in the existing ledger; their PNG production is deterministic code. These inputs contain no AI placeholders. The script also writes models, recipes, blockstates, translations, and test structure; it is not automatically run by Gradle. |
| Vanilla placeholders | Base shell/controller/service models reference vanilla materials (for example Mixer casing uses `iron_block`; fluid input uses `dropper_front`). These are not AI-generated artwork. |
| AI development placeholders | Eight source PNGs in `dev/ai-placeholder-art/`, six copied unchanged by `generateAiPlaceholderResources` into the development overlay, and two source-only motors. Six overlay models and the merged development atlas retain AI provenance. Formed Mixer shaft/impeller renderers directly bind two of these PNGs. Copying or transforming them does not change their classification. |
| Runtime geometry/material selection | `VatCasingModel`/`VatCasingGeometry` select face/edge/inner patches from assembled state; `VatGlassModel` selects bounds-based planes and 16 edge masks per direction, without neighbor CTM artwork. Mixer vessel meshes use vanilla iron with verdigris/light/copper vertex colors. Fluid/specimen/harness/conduit geometry uses the existing renderer paths. |

Existing head blockstates rotate its model for four horizontal facings. Existing
casing geometry uses material patches, not a painted weathering variant family.
The inspected current casing and Mixer vessel rendering paths have no
controller-coordinate weathering texture selector to reuse. No runtime selector
or new rendering machinery is added in Slice 1. If that selector is introduced
in a later accepted art integration, it can choose the finite pre-generated
seed variants using stable controller coordinates. Current renderer behavior is
preserved.

`generateAiPlaceholderResources` already owns the development atlas overlay;
this task produces neither models nor another atlas. Gradle's resource source
directory registration carries the generation dependency through processing and
JAR packaging. Packaging verification checks every manifest PNG hash and confirms
that no model has acquired a procedural prototype binding.

## Validation and release safeguards

```powershell
.\gradlew.bat generateProceduralArt proceduralArtPreview testProceduralArt --offline
.\gradlew.bat build runGameTestServer --offline
.\gradlew.bat testAiPlaceholderGuard --offline --rerun-tasks
.\gradlew.bat verifyNoAiPlaceholderArt publicReleaseValidation --offline
git diff --check
```

`testProceduralArt` checks deterministic PNGs/provenance, dimensions, exact
brightness mapping, tier-independent blue/yellow/red and neutral center,
transparency, all three direction variants, shared diagonal geometry,
four-corner reflection/rotation symmetry, pipe clearance, unchanged socket
pixels outside the markings, sparse repeatable wear and seed variation,
continuous casing border, balanced fasteners, and deterministic wall previews,
primitive geometry, output collisions, and real PNG/Piskel overlay loading.
It tests contracts rather than freezing artistic taste with full-image goldens.
`verifyProceduralArtResources` checks packaged PNGs/provenance and unbound models.
Both are part of `check`. Existing guard regression fixtures continue testing
active ledger entries, retained AI sources, renamed PNG hashes, dangling
references, malformed ledgers, and clean historical ledgers.

The public guard is unchanged, with no exception for generated artwork. The
last guard command is expected to fail while current AI placeholders remain.
Local build output is development-only. No placeholder ledger row is reclassified,
no original artwork is replaced, and no public distribution is authorized by
this art-generation workflow.

Validation results for this slice are recorded in `2026-10-11-procedural-art-validation.md`.
