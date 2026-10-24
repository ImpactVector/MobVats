# Blood Iron casing manufacturing

The Survival Mixer recipe `mobvats:blood_iron_vat_casing` consumes four Iron Vat
Casings (`mobvats:vat_casing_t2`), one `minecraft:soul_sand`, and 100 mB of
`mobvats:strange_meat` to produce four Blood Iron Vat Casings
(`mobvats:vat_casing_t3`) in 200 processing ticks. It uses the existing Mixer
capacity checks, input reservations, atomic commit, and saved progress.

New configurations receive this recipe through the bundled Mixer defaults.
Existing `config/mobvats/mixer/_defaults.json` files are preserved. To add the
recipe to an existing installation, create
`config/mobvats/mixer/blood_iron.json` with:

```json
{
  "recipes": {
    "mobvats:blood_iron_vat_casing": {
      "batch_ticks": 200,
      "priority": -1000,
      "strategies": [{
        "id": "default",
        "priority": 0,
        "items": [
          {"selector": "mobvats:vat_casing_t2", "amount": 4},
          {"selector": "minecraft:soul_sand", "amount": 1}
        ],
        "fluids": [{"selector": "mobvats:strange_meat", "amount": 100}]
      }],
      "outputs": {
        "items": [{"item": "mobvats:vat_casing_t3", "amount": 4}]
      }
    }
  }
}
```

Run `/mobvats reload_mixer` with command permission, or restart the server.
The configuration recipe ID matches the bundled ID, keeping one manufacturing
route. Other Mixer definitions and crafting recipes are unchanged.
