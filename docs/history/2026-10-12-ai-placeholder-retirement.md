# DEVELOPMENT-ONLY AI PLACEHOLDER ASSETS

> Historical record. Original: `AI_PLACEHOLDER_ASSETS.md`. Current reference: [05-PROCEDURAL-ART.md](../05-PROCEDURAL-ART.md).
> Archive date basis: Explicit retirement date in ledger: October 12, 2026. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

HARD PUBLIC RELEASE BLOCKER:
Public distribution must contain no active AI sources, references or original fingerprints.

Historical pack: user-supplied `mobvats_placeholder_art_20261009.zip`, received October 9, 2026.
The supplied pack declared AI provenance. Original SHA-256 history is retained below.
All eight original PNGs and all development model overlays were removed October 12, 2026.
The original pack README is retained as provenance text only; it is not shipped.
No procedural material was derived from these images.

Shaft, bearing/Impeller and Drive artwork is authored from semantic pixel primitives and
cuboids in `gradle/procedural-art.gradle` with `tools/procedural-art/machinery.json`.
Grayscale masters and T1 verdigris copper / T2 iron / T3 bloody iron finishes use the
existing palettes. Structural colors are tier materials; functional indicators are fixed.
The formed renderer uses the generated T1 Shaft and paddle PNGs; higher finishes remain
art variants, without registering gameplay tiers.

| Source placeholder | Runtime texture | Consumers / derived model | Game element | Status | Replacement status | Original SHA-256 |
| --- | --- | --- | --- | --- | --- | --- |
| `dev/ai-placeholder-art/mixer_controller.png` | None | `assets/mobvats/models/block/mixer_controller.json` | mixer_controller | REPLACED | Original procedural replacement; AI source and overlay removed | 4eb7872e49dd2faba56df4a1d2b3cfe9076251cd5c4ae976ef865ab15a198633 |
| `dev/ai-placeholder-art/mixer_impeller.png` | None | `assets/mobvats/models/block/mixer_impeller.json`; `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/client/render/MixerControllerRenderer.java` (formed rotor direct PNG binding) | mixer_impeller | REPLACED | Original procedural replacement; AI source and overlay removed | 4d86e1d4ca5334458dbbfbd1d076752c6eea3f797781963e49ce9198a7b9bf12 |
| `dev/ai-placeholder-art/mixer_shaft.png` | None | `assets/mobvats/models/block/mixer_shaft.json`; `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/client/render/MixerControllerRenderer.java` (formed rotor direct PNG binding) | mixer_shaft | REPLACED | Original procedural replacement; AI source and overlay removed | a252d7cea5609fc8b3236216db87a7c859b82e9bf1ad6e461e1c3353c99f6e07 |
| `dev/ai-placeholder-art/fluid_input_port.png` | None | `assets/mobvats/models/block/mixer_fluid_input.json` | mixer_fluid_input | REPLACED | Original procedural replacement; AI source and overlay removed | 1c74d2990c04a97fe6cc40360a31831b789db0005794d2484fc165b1b8ebf4c9 |
| `dev/ai-placeholder-art/fluid_output_port.png` | None | `assets/mobvats/models/block/mixer_fluid_output.json` | mixer_fluid_output | REPLACED | Original procedural replacement; AI source and overlay removed | a53b6d316c191ec64cad7228e8cde6c11a8d33cc8bee3851acf3144b077e2b15 |
| `dev/ai-placeholder-art/item_input_port.png` | None | `assets/mobvats/models/block/mixer_item_input.json` | mixer_item_input | REPLACED | Original procedural replacement; AI source and overlay removed | e5298046fe593f56b80f69bb0c1efa26c2f4d644b847a3730d2ba5393ecb7ca0 |
| `dev/ai-placeholder-art/combustion_motor.png` | None | None | combustion_motor (not implemented) | REMOVED | Unused AI motor concept removed; no derivative shipped | 0cb7200eb9c681c12c40cb0f8754c270362712f8ef02ffc7cb005e3b335dcd9c |
| `dev/ai-placeholder-art/fe_motor.png` | None | None | fe_motor (not implemented) | REMOVED | Unused AI motor concept removed; no derivative shipped | 3d7fc90dbc409cb7d123db52fe764f0ab5364cbcb0ce3dd5b1584c70686ea075 |

All former runtime atlas and direct renderer bindings were removed. Normal generated
procedural resources supply block and 3D inventory models. The development overlay
resource pipeline is retired; no AI source or derivative is included in local builds.

The release safeguard is unchanged: `verifyNoAiPlaceholderArt` checks ledger status,
source PNGs, shipped references and historical hashes, including renamed images in
JARs. Public entry points still depend on the guard and revalidate before acting.
`testAiPlaceholderGuard` exercises active-ledger, retained-source, renamed-fingerprint,
dangling-reference, malformed-ledger and clean-ledger cases with isolated synthetic
fixtures. `verifyAiPlaceholderDevResources` now verifies procedural replacements and
absence of packaged placeholder paths, while preserving checks of original base PNGs.

Run `gradlew clean build`, then `gradlew verifyNoAiPlaceholderArt publicReleaseValidation`
to eliminate stale placeholder artifacts and validate. Validation does not authorize publication.
