# 05 — Procedural Art

## 5.1 Source contract and palettes

The development generator authors integer-pixel textures and cuboid/mesh models using semantic material channels. It uses Gradle/Groovy and Java ImageIO, with no generative image model or runtime texture generation. Generator classes/tooling are not shipped as gameplay code.

The grayscale master maps five channels (`highlight`, `light`, `base`, `shadow`, `deep`) to T1 verdigris copper, T2 iron and T3 bloody iron finishes. Separate wear tokens describe exposed material. T1 base is `#5F8F78`. Fixed functional RGBA pixels bypass material conversion: fluid blue `#168BFA`, item yellow `#E3BC45`, red status, and Controller phosphor greens. T2/T3 remain art/preview variants; current gameplay structural material is T1.

Recipes are declarative named primitives, with integer coordinates in 0..15, explicit seed weathering and exact transparency. No recipe expression is executed. Layers can use panel, fixed_panel, bevel, line, seam, rivet, slot, indicator, socket, overlay and generator-defined material/geometry primitives. Fixed overlays preserve RGBA; material overlays require an explicit ARGB-to-channel map. PNG inputs must be 16×16; supported Piskel inputs select one layer/frame/chunk. Authorship must be declared; an overlay does not acquire valid provenance by being recolored.

## 5.2 Generator inputs and resource integration

| Input | Responsibility |
|---|---|
| [recipes.json](../tools/procedural-art/recipes.json) | Casing and socket recipes |
| [palettes.json](../tools/procedural-art/palettes.json) | Semantic materials and fixed colors |
| [bindings.json](../tools/procedural-art/bindings.json) | Production model consumers |
| [geometry.json](../tools/procedural-art/geometry.json) | Conduit/buffer dimensions and preview parameters |
| [fixtures.json](../tools/procedural-art/fixtures.json) | Valve, pump and void cores |
| [machinery.json](../tools/procedural-art/machinery.json) | Controllers, services, Shaft, Impeller and Drives |
| [biological.json](../tools/procedural-art/biological.json) | Fixed tissue, ivory, eyes and glassware; legacy resource bindings and historical/output fingerprints |
| [procedural-art.gradle](../gradle/procedural-art.gradle) | Deterministic generation, tests, manifest and preview |

`generateProceduralArt` writes `build/generated/resources/procedural-art/`. `processResources` includes this directory and depends on generation. Source/generated collisions are explicitly excluded and residual duplicate paths fail packaging. No second atlas or runtime generator is introduced. Increment the generator contract version when output changes; provenance also hashes its source.

Textures use `assets/mobvats/textures/block/procedural/<finish>/`; models use `assets/mobvats/models/block/procedural/<finish>/`. The generated `META-INF/mobvats-procedural-art.json` records deterministic input/binding/model/texture hashes and overlay provenance without host paths or timestamps. Current counts come from the manifest, not the obsolete counts in historical slice reports.

Biological recipes retain `textures/blocks/vat_{head,face,flesh}.png` and `textures/items/item_{essence,syringe}.png`. Gradle generates these directly; obsolete recovered PNGs are removed from production sources. `MobBiologicalArt` uses authored cluster stamps, glassware silhouettes, lines and explicit seeds. Dedicated tissue/ivory/eye/glass/liquid/cap palettes produce fixed ARGB pixels and never receive metal tier tinting. The biological renderer has no image loader, overlay, historical pixel mask or network input. Its recipe hash, enabled bindings, historical fingerprints and generated PNG hashes are recorded in the existing provenance manifest.

## 5.3 Models and inventory presentation

Production bindings supply T1 casing, directional sockets, conduits, glass, buffers, fixtures and machinery. Universal mechanical inventories use bare functional models; installed cladding selects family shells. Unformed clad blocks show construction frames; formed blocks remove construction frames and retain mechanisms and appropriate casing/glass treatment. Material, endpoint direction and topology remain independent. Models share geometry with runtime presentation where exported by the existing CPU tests.

Controller greenscreens use fixed skull/rotor pixel masks, not live metrics. Harness and Sump retain ceiling/floor anchors. Feeder hoses and Tool arms use the existing specimen render path; Blender teeth and fixture augers are stationary chunk geometry. Mixer Shaft and paddles use generated T1 materials and the canonical 6-pixel Shaft contract. Drives couple at the top Impeller's cardinal neighbors. Current registered block models supply 3D inventory presentation; old direction-only Mixer block registrations were removed by unified cladding.

Buffer connected surfaces and conduit fittings derive from validated geometry and compatible storage clusters. Production runtime owners cache meshes by descriptors/model data and clear them on lifecycle/resource changes. Preview-only weathering is finite and deterministic; no controller-position runtime selector is implied. Preview palette selection does not create tiers.

## 5.4 Asset origin and release policy

Recovered MobVats Head/Flesh/Syringe/Essence textures retain upstream revisions and SHA-256 evidence in the [historical provenance record](history/2026-10-03-asset-provenance.md). Their user-authored compositions incorporated vanilla artwork; the [October 12 biological replacement record](history/2026-10-12-biological-art-replacement.md) documents the affected regions, audit limits, independent recipes and new hashes. Authored procedural replacements use integer primitives and explicit model geometry. Vanilla material references remain vanilla assets. [Attribution](../ATTRIBUTION.md) and [LICENSE](../LICENSE) retain upstream notices and rights. This targeted cleanup does not legally certify all repository assets.

All eight original AI development images were replaced or removed on October 12, 2026. Their names, original hashes, six replacements and two source-only removals remain in the [historical AI ledger](history/2026-10-12-ai-placeholder-retirement.md). Former atlas/direct renderer bindings and the development overlay pipeline are retired. The retained supplied README is text provenance only and is not shipped.

AI-generated visuals are allowed only as explicit development placeholders. Record every source/derivative and retain its fingerprint after removal. Never relabel an AI placeholder as final art. Any future introduction of placeholders must restore active ledger tracking and remain blocked from public distribution until explicitly replaced/removed by suitable authored/licensed art.

`verifyNoAiPlaceholderArt` always reads the historical ledger. It rejects active rows, retained source PNGs, renamed images matching historical fingerprints, shipped placeholder references, and missing/invalid provenance. Moving the ledger is not an exemption. Public publish/release/upload/distribution tasks depend on the guard and revalidate immediately before acting. Custom distribution tasks outside the naming convention must explicitly depend on it. Normal local builds remain separate from publication authorization.

## 5.5 Preview and validation

```powershell
.\gradlew.bat proceduralArtPreview
.\gradlew.bat testProceduralArt verifyProceduralArtResources verifyAiPlaceholderDevResources
.\gradlew.bat testAiPlaceholderGuard
.\gradlew.bat verifyNoAiPlaceholderArt publicReleaseValidation
```

`--offline` may be appended when required dependencies are already cached. Open `build/procedural-art-preview/index.html` for contact sheets and `models.html` for geometry comparisons. `-PopenArtPreview` optionally opens the local HTML; this is an operator action. Previews use exported production geometry but do not establish Minecraft lighting, culling or visual acceptance.

Art contract checks cover determinism, material/fixed-color mapping, geometry, generated hashes, model/item bindings, output collisions, protected base PNGs and placeholder rejection fixtures. Build `check` includes existing art/provenance verification. Minecraft visual acceptance remains operator-controlled; never launch a client as part of routine documentation work.

`biological.png` adds native 16px, 32px inventory, nearest-neighbor ×8 and model UV context views. Individual PNGs and ×8 images are under `biological/`. Cube views use the existing full-cube UV arrangement; flat item views use the existing `item/generated` layer. These offline previews do not reproduce Minecraft lighting. Biological tests pin five output hashes, compare two complete generation runs, check fixed palettes and binary alpha, validate packaged model/atlas bindings and reject historical PNG fingerprints anywhere in the JAR. The AI-placeholder release guard remains intact.
