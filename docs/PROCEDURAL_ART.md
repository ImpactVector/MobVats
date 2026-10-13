# Procedural artwork data and build contracts

Current architecture and resource ownership are documented in
[05-PROCEDURAL-ART.md](05-PROCEDURAL-ART.md). This page records the October 13,
2026 JSON extraction and the authoring contract.

## Where definitions belong

| Category | Owner | Extracted/retained values |
| --- | --- | --- |
| Artistic data | `tools/procedural-art/*.json` | Five-channel grayscale and tier palettes; wear and functional colors; phosphor/background; glass ARGB; skull/rotor grids; port motifs; borders, fasteners, material clusters; scratches and candidate positions; layer order, offsets and seeds; all industrial and biological PNG compositions |
| Generic algorithms | `gradle/procedural-art.gradle` | Integer lines/panels, symbol-grid rasterization, corner reflection, seeded grid scatter, seeded scratch placement/rotation, compositing, semantic palette interpolation, PNG encoding, previews and validation |
| Authoritative geometry | Existing Java geometry classes | Conduit physical diameter, occupied TRUNK/TAP envelopes, buffer surfaces, vessel/specimen clearance, structure and collision/connection rules; unchanged by this extraction |
| Build/resource integration | Gradle, `bindings.json`, existing model definitions | Output paths, model/atlas consumers, generation dependencies, protected resource ownership, packaging and provenance |

JSON describes what to draw; code determines how to draw it. Gameplay dimensions
must never become artwork settings. Existing 3D modeling algorithms and their
presentation contracts remain in code; this refactor does not introduce a model
description language.

## Source layout

* `palettes.json`: semantic grayscale/T1/T2/T3, wear, fixed RGB/ARGB colors.
  Six digits mean opaque RGB; eight digits mean **ARGB**, including glass alpha.
  Azure remains `#168BFA`, item yellow remains `#E3BC45`.
* `drawing.json`: shared named patterns, weathering profiles and industrial texture
  layers. `patterns.controller_skull.pixels` and `controller_rotor.pixels` contain
  the original 16-row glyphs. `port_none`, `port_inward`, `port_outward` contain the
  original motifs. Texture ids generate the existing named PNGs for every finish.
* `recipes.json`: casing/socket layers and output variants. Casing border tokens,
  fastener positions and weather seeds are editable here. Port resource colors
  and direction-to-pattern references are explicit data.
* `biological.json`: fixed biological palettes, pixel grids, scatter parameters,
  layers, bindings and existing reviewed/historical fingerprints. The original
  five output hashes are preserved.
* `geometry.json`, `fixtures.json`, `machinery.json`: retained model/presentation
  parameters and production bindings. PNG colors and sprites were removed from
  machinery/geometry definitions. `nodeWidth` and `bufferWidth` are exported
  from Java instead of maintained here.
* `baseline/pre-extraction.json`: immutable historical SHA-256 evidence from
  generator 1.13.0 before extraction; 233 PNGs and 453 model/resource files.

No AI source was used for extraction. Definition hashes and explicit source paths
are included in `META-INF/mobvats-procedural-art.json`. Existing biological source
provenance, historical fingerprints, protected resources and the public release
guard are retained. Changing storage format does not change asset authorship.

## Raster vocabulary (schema version 1)

All coordinates and widths/heights are texture pixels on the existing 16×16
canvas. Bounds are inclusive. Layers execute in array order; a null grid token
skips drawing and preserves the underlying layer. An untouched pixel is transparent.

| Primitive | Required fields | Operation |
| --- | --- | --- |
| `panel` | `bounds: [x0,y0,x1,y1]`, `token` | Filled rectangle |
| `line` | `from`, `to`, `token` | Integer raster line |
| `points` | `points`, `token` | Pixel list; optional `reflectCorners: true` reflects into all four corners |
| `pixel_grid` | `width`, `height`, `at`, `palette`, `pixels` | Symbol grid with exact row lengths and symbol mapping |
| `reference` | `recipe` | Named pattern/texture composition; optional `parameters` |
| `weather` | `profile`, `seed` | Seeded configured scratches and rotating edge cluster |
| `scatter_grid` | `pixels`, `palette`, `gridMin`, `gridMax`, `spacing`, `jitter`, `seed` | Seeded wrapped stamping; row width/height derived from the grid |
| `overlay` | `source`, `authorship`, `mode` | Existing PNG/Piskel overlay support in `recipes.json` only; explicit hand-authored provenance and semantic map required |

Tokens are semantic material names (`highlight`, `light`, `base`, `shadow`,
`deep`, `wear`), a material interpolation object (`material`, `toward`,
`eighths`), `{ "colorRef": "fixed.fluid" }`, explicit `{ "rgba": "#AARRGGBB" }`,
or `{ "parameter": "name" }`. Color references traverse the applicable palette
object; biological references can address array entries, such as `glass.3`.
Patterns may declare explicit `defaults` for parameters. References override those
defaults. These are data substitutions, not expressions or executable scripts.
The only implicit defaults are no reflection, no reference arguments, overlay
layer zero, and no variant weathering/markings when none are requested.

Validation reports the source filename and property path for unsupported versions,
unknown primitives/colors/symbols, missing fields, negative/reversed dimensions,
out-of-bounds coordinates, bad references/cycles and conflicting PNG output paths.
Artwork and planned PNG paths are validated before PNGs are written. Resource
ownership and model output collision checks remain separate existing safeguards.

## Compiled geometry and task ordering

`compileJava → compileArtGeometryExport → exportArtGeometry → generateProceduralArt
→ processResources → jar` is the dependency chain. The build-only
`tools/procedural-art/GeometryExport.java` reads compiled `ConduitGeometry.DIAMETER`
and `FluidBufferArt.BODY`, writes `build/tmp/procedural-art-geometry/geometry.json`,
and declares schema version 1, `model_pixels` units, and 16 model pixels per block.
It combines those dimensions with existing cosmetic preview fields and palette
glass values to preserve the old generated resource bytes.

The exporter classpath uses compiled main classes and dependency JARs, excluding
resource output and `classes`/`testClasses` task dependencies. This avoids a
resource-generation cycle. Java source declarations are never parsed. The
existing conduit geometry CPU test independently checks exported dimensions
against runtime geometry and checks descriptor units/version.

JSON files are Gradle inputs. A skull edit regenerates PNGs and provenance without
recompiling gameplay or exporter classes; palette changes may rerun the exporter
to update glass preview fields, also without compilation. Previews read generated
production PNGs and the resolved geometry descriptor. No copying, player-side
JSON parsing, added runtime dependency, or additional network tool is required.

## Verification and deliberate future changes

```powershell
.\gradlew.bat proceduralArtPreview --offline
.\gradlew.bat testProceduralArt compareProceduralArtBaseline --offline
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat buildTestWorld
git diff --check
```

`testProceduralArt` retains existing regression/packaging contracts and adds
temporary JSON edits proving that one skull pixel changes exactly that pixel in
all four skull textures, T1 palette edits affect only T1 material textures, a
functional color changes exactly its color masks, and a casing border edit changes
only casing variants. Every unrelated PNG is compared byte-for-byte. Invalid
definitions and recipe cycles are rejected in isolated fixtures. Source artwork
is never edited by tests.

`compareProceduralArtBaseline` checks the complete generated asset set against the
pre-extraction manifest. It is an explicit historical comparison, not an ordinary
`check` dependency: future deliberate artwork changes should not be blocked by a
historical extraction baseline. Never regenerate this baseline to hide a refactor
regression. Existing biological golden fingerprints remain ordinary test guards.

The metadata manifest necessarily changes to identify the new JSON sources; it is
excluded from artwork/model byte comparisons. No encoder exception was needed.
`testAiPlaceholderGuard` verifies expected release rejection using active-ledger,
retained-source, renamed-PNG and dangling-reference fixtures. The actual ledger
currently records retired placeholders; no artificial active entry is introduced.
No client, commit, push or public distribution is part of this work.
