# Configuration examples

These source-controlled samples illustrate server JSON schemas. They are documentation, not automatically loaded runtime data. Copy/adapt deliberately; preserve authored server configuration. For the current individual mob file contract, use [mob profiles](../MOB_PROFILES.md) and the generated species file as the authority.

| Sample | Intended use |
|---|---|
| [MOB_COW.example.json](MOB_COW.example.json) | Complete current-schema file for config/mobvats/mobs/minecraft/cow.json |
| [CULTURE_PROFILES.example.json](CULTURE_PROFILES.example.json) | Culture configuration under config/mobvats/culture/ |
| [MIXER_RECIPES.example.json](MIXER_RECIPES.example.json) | Mixer definitions under config/mobvats/mixer/ |
| [PASSIVE_DEFAULTS.example.json](PASSIVE_DEFAULTS.example.json) | Historical bundle example; unwrap one species and add schema_version: 2 and minimum_vat_tier: 1–3 |
| [RESOURCE_PROFILES.example.json](RESOURCE_PROFILES.example.json) | Historical bundle of generic resource examples; unwrap one species and add schema/tier fields |
| [VANILLA_DEFAULTS.example.json](VANILLA_DEFAULTS.example.json) | Historical vanilla catalog reference; the current loader requires individual species files |

See [processing](../04-PROCESSING.md) for current behavior. Some samples record earlier defaults; samples are not a claim that every illustrated policy is the current bundled value. Compare with bundled resources/parser validation when authoring overrides.
