# Biological artwork replacement — October 12, 2026

The five recovered PNGs were inspected at native and enlarged resolution. The user's handoff establishes user authorship of the MobVats compositions and incorporation of Bone, Spider Eye and Rotten Flesh artwork; its addendum confirms Essence is a recolored Glass Bottle and identifies Syringe as suspected bottle-derived artwork. Attribution below combines that evidence with visual inspection, rather than claiming a forensic reconstruction of every borrowed pixel.

| Legacy production texture | Replaced region / material | Retained composition | Recipe seed |
| --- | --- | --- | --- |
| `src/main/resources/assets/mobvats/textures/blocks/vat_head.png` (retired) | Rotten Flesh background | Full repeating tissue field | 107 |
| `src/main/resources/assets/mobvats/textures/blocks/vat_face.png` (retired) | Rotten Flesh background; Spider Eye lenses | Tissue field with two eyes in the upper half | 107 |
| `src/main/resources/assets/mobvats/textures/blocks/vat_flesh.png` (retired) | Rotten Flesh background; Bone fragments | Tissue field with three ivory fragments, upper right / middle left / lower middle | 107 |
| `src/main/resources/assets/mobvats/textures/items/item_essence.png` (retired) | Glass Bottle entire recolored vessel | Small stopped glass vessel with red contents; entire silhouette independently redrawn | 211 |
| `src/main/resources/assets/mobvats/textures/items/item_syringe.png` (retired) | Suspected bottle-derived barrel/highlights; conservative redraw; Ivory needle; conservative redraw | Horizontal needle, glass barrel, finger flange and plunger; original rectangle/line geometry | 307 |

All three block textures share the legacy tissue field. Bone fragments occur in `vat_flesh`; the paired lenses occur in `vat_face`. No eyes or ivory fragments occur in `vat_head`. Essence requires an entire container redraw, since recoloring does not establish independent authorship. Syringe's precise historical bottle contribution is unconfirmed; its barrel/highlights and ivory needle were conservatively redrawn, retaining its horizontal tool composition. No borrowed silhouette was used as a mask.

The locally resolved Minecraft 1.21.1 resource JAR was inspected for the four named materials. A scan of every production source PNG found no exact fully opaque 3×3 match to those current vanilla images. Historical art may predate those versions; absence of such matches does not establish originality, nor was a distance/similarity threshold used as proof. The committed production PNG inventory consisted of the five recovered textures, two uniform tank materials and three casing materials. Visual inspection and fingerprint review found no additional occurrence of the recovered artwork among the remaining production PNGs. This is a bounded audit, not certification of every historical or third-party asset.

The source palettes and primitive row shapes are in `tools/procedural-art/biological.json`; generation is part of `gradle/procedural-art.gradle`, generator 1.13.0. Tissue uses a five-row authored lobe stamp with seed 107, fixed recessed/fatty/fibrous clusters and sparse gray-green decay marks. Ivory fragments use an irregular four-row chip with ridge/shadow colors. Organic eyes use a symmetric five-row lens with an independently arranged central reflection. Glassware uses authored vessel/barrel stamps, liquid, stopper and fixed glass thickness/highlight pixels, plus needle/flange/plunger lines. Seeds 211 and 307 identify the deterministic item recipes; those shapes have no randomized variation. No antialiasing, image-generation model, external artwork, historical image, PNG overlay, vanilla mask or network request supplies generator pixels.

| Fixed palette | Colors (dark to light, except tissue decay accent last) |
| --- | --- |
| tissue | `#542D2C`, `#743B32`, `#974F38`, `#B16B46`, `#CA8960`, `#73705A` |
| ivory | `#655F50`, `#ABA082`, `#D0C4A0`, `#EEE2BE` |
| eye | `#321B2B`, `#592439`, `#87324C`, `#B74B64`, `#E79B9E` |
| glass | `#455B61`, `#718C91`, `#AAC4C5`, `#E0ECE6` |
| liquid | `#49232D`, `#7A2B38`, `#AD3D49` |
| cap | `#4F4541`, `#89725B`, `#B99A73` |

The biological renderer writes fixed ARGB values; T1/T2/T3 conversion has no effect. Blocks remain opaque 16×16; items remain 16×16 with transparent backgrounds and independently specified silhouettes.

All output paths below are relative to `build/generated/resources/procedural-art/` (and become the same paths inside the JAR). Recipe IDs are the filenames without `.png`; all recipe/palette sources are `tools/procedural-art/biological.json`.

| Generated resource | Historical SHA-256 | Replacement SHA-256 |
| --- | --- | --- |
| `assets/mobvats/textures/blocks/vat_head.png` | `7f5c3eca50911af70fb016746e0ec4f194f0969db444025bc2ddbef645e277a8` | `b75f38b3a50f3ef8295a670d1c3a7310816bcb05ee4206b965a9d989da9810fd` |
| `assets/mobvats/textures/blocks/vat_face.png` | `d37882750907af5705c22c0edb9991e1dbfc7c7275b395e356e955cf690d7459` | `8a0738576c1048d5d5b55a122532e71881f9b4160faefb0bf66fc110c904ec9e` |
| `assets/mobvats/textures/blocks/vat_flesh.png` | `bfc7d66595dfb99a66aceec33c75d59942fa57ec1c1b3cdeaae7fcdbb9ce94e2` | `a49a19c01109dd6856fc8196cc2f3bd8c11832eab4ddc116b1926543ed409513` |
| `assets/mobvats/textures/items/item_essence.png` | `b3a03ae739efcfa80ad6e9d9049be8d0ee5deb33e02b6071e1444ac542acab02` | `241c992e35669626cef113ae7bafc6a483410d561db4cd909175c39881676cca` |
| `assets/mobvats/textures/items/item_syringe.png` | `d1401985de5d10c9382113ee960215daf090382d5304cacc2d6e21e44c161cbb` | `69ead9477b43a6aa5b34928c3477dc96b4144a51e776262a5550281c8a05a81b` |

`processResources` consumes the existing generated source directory automatically, with duplicate paths rejected. No manual PNG installation or runtime texture generation is required. Models and atlas names remain stable: `vat_head` uses Face on north, Head on the other five faces and particles; `vat_flesh` uses Flesh on all six faces; `hidden` uses Head particles; Syringe and Essence use their existing `minecraft:item/generated` layer0 textures. The explicit single-sprite entries in `assets/minecraft/atlases/blocks.json` remain valid. Tool-vise procedural previews now load the generated Syringe rather than the removed source PNG. No gameplay code, geometry, behavior, recipe or license changed.

The existing provenance manifest records biological recipe SHA-256, fixed finish, individual seeds, replaced materials, enabled model-slot bindings, original SHA-256 and output SHA-256. The old binaries are absent from production and are not added elsewhere in the repository; Git history retains their historical revisions. Temporary ignored copies under `.cache/legacy-biological/` are only for visual comparison and are not generator inputs or packaged resources.

Preview: run `gradlew.bat proceduralArtPreview --offline`, then inspect `build/procedural-art-preview/biological.png`, individual native and ×8 PNGs under `biological/`, and the 3D Tool-vise Syringe context in `models.html`. The cube/item sheet reflects current texture slots and full-cube UVs / flat generated-item layer context. It does not reproduce Minecraft's lighting or held-item rendering. An ignored local old/new contact sheet is `.cache/biological-comparison.png`. Manual in-game visual acceptance remains with the user; no client was launched.

Validation checks include two complete byte-identical generation runs, reviewed PNG hash pins, dimensions, binary transparency, unchanged output under tier palettes, no obsolete production source paths, model/atlas resolution, duplicate-free JAR paths, manifest completeness and rejection of all five historical fingerprints anywhere in the packaged PNGs. Existing AI-placeholder guard code is unchanged. Required commands and final outcomes are recorded below after validation.

MIT LICENSE and required third-party notices are unchanged. Vanilla asset references used elsewhere remain Minecraft-owned resources supplied by the game; this task does not relicense them. Syringe's exact historical input boundary remains uncertain, and this record does not legally certify every repository asset. Visual approval of the replacements remains pending.

## Validation outcome

Final command from the current checkout, `D:\backup\dev\git\MobVats\src`:

```powershell
.\gradlew.bat build testProceduralArt verifyProceduralArtResources verifyNoAiPlaceholderArt publicReleaseValidation proceduralArtPreview --offline -I .cache/art-validation.init.gradle
git diff --check
```

PASS: build, proceduralArtPreview, testProceduralArt (233 textures, two complete deterministic runs), verifyProceduralArtResources (PNG/manifest/model/atlas/hash checks), verifyRenderResources, verifyNoAiPlaceholderArt, publicReleaseValidation, existing AI-placeholder rejection fixtures and development-hook exclusion checks. CPU Conduit, Fixture, Controller, Feeder, Buffer, Mixer Shaft and render-buffer ordering checks passed. No gameplay or shared runtime changes warranted headless GameTests. No client was launched.

The initial normal preview invocation generated the textures, but Temurin 21.0.12's JVM crashed with EXCEPTION_ACCESS_VIOLATION inside `jvm.dll` during the existing Mixer Shaft geometry test. The final run uses an ignored local Gradle init script adding `-XX:TieredStopAtLevel=1` to JavaExec test processes, disabling C2 JIT; no production JVM settings were changed. A subsequent Groovy primitive-double preview closure verifier error was corrected by using untyped integer-coordinate closure arguments. An intermediate packaging run saw a recipe hash mismatch because binding metadata was edited during that run; the complete final run regenerated and verified the finalized metadata successfully.

`git diff --check` passed. The MIT license, third-party attribution and AI-placeholder guard source remain unchanged. No commit, push, publication, release or repository visibility change was performed.
