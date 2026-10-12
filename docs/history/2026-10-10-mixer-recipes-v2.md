> Historical record. Original: `MIXER_RECIPES_V2.md`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Git introduction author date 2026-10-10T14:04:59-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Mixer operations

Server definitions remain in `config/mobvats/mixer/`. `_defaults.json` loads
first, then other JSON files in filename order; later definitions replace the
same recipe ID. A malformed file contributes no recipes or formula names.
Use `/mobvats reload_mixer` to reload.

The default culture recipe consumes 1,000 mB water + 250 mB XP and produces
1,000 mB culture slurry. Its compound composition derives from those inputs.
Startup and `/mobvats reload_mixer` create a bundled `_defaults.json` only when
missing; existing files, including historical defaults and authored overrides,
are never rewritten. Recipe-ID overrides and priority ordering remain unchanged.

Every normal development Gradle `build` runs `invalidateDevMixerDefaults`, even
when compilation and packaging are up-to-date. It deletes only the local
`run/config/mobvats/mixer/_defaults.json`; missing files are a successful no-op.
Linked paths (including junctions) and non-file targets are refused. Custom sibling
JSON files are preserved. The next startup recreates the missing bundled copy.
This housekeeping is not attached to startup or shipped with the mod and does
not apply to production installations.

```json
{
  "recipes": {
    "example:refine": {
      "priority": 100,
      "batch_ticks": 200,
      "strategies": [
        {
          "id": "preferred",
          "priority": 100,
          "enabled": true,
          "items": [{ "selector": "minecraft:raw_iron", "amount": 1 }],
          "fluids": [{ "selector": "minecraft:water", "amount": 250 }]
        },
        {
          "id": "alternate",
          "items": [{ "selector": "minecraft:iron_nugget", "amount": 9 }]
        }
      ],
      "outputs": {
        "items": [{ "item": "minecraft:iron_ingot", "amount": 1 }],
        "fluids": [{ "fluid": "mobvats:waste", "amount": 100 }]
      }
    }
  }
}
```

Recipe priority defaults to zero. Strategies require unique, nonempty IDs;
their priority defaults to zero and `enabled` defaults to true. Priorities are
signed 32-bit integers. Bundled recipes use -1000. Recipes sort by descending
priority, then ascending recipe ID; strategies sort by descending priority,
then ascending strategy ID. File order controls overrides only, not execution.
There is no player or machine priority override in this version.

Every requirement in a strategy must be satisfiable together. At least one
input and one output are required; item-only and fluid-only operations are
supported. Selectors retain native item/fluid ingredient codecs, including tags,
custom ingredients and alternatives. Fluid alternatives use native objects,
for example `[{ "fluid": "minecraft:water" }, { "fluid": "mobvats:waste" }]`.
Input `record_in_composition` defaults to true. Amounts and `batch_ticks` are
positive 32-bit integers.

Outputs execute as one bundle. Item outputs use four private output slots,
separate from the input inventory, and respect each item's stack limit. They
persist, survive controller assimilation, and are extracted through configured
item output ports. Input items cannot be extracted through those ports. Fluid
outputs use connected physical buffers first, then the producing endpoint's
bounded service tank for that fluid family. Outputs are reserved in declaration
order and tank order; incompatible identities cannot share a tank. This order
also defines deterministic backpressure. Capacity reservations accumulate
across repeated outputs. Any blocked output leaves all inputs, outputs and
production metrics unchanged. Simulation does not create service tanks.

The operation uses `ResourceBundle` demands and the same owned-resource
reservation implementation as `ResourceRouting`. Reservations are single-use
and reject changed item/fluid snapshots, changed capacity, changed output
routing/filters and a replaced registry snapshot. External third-party handlers
are not reversible transaction participants.

Each fluid output may specify `composition` or `transform`, using the existing
composition and transform fields. Without explicit composition, compound
outputs derive their formula from the selected strategy's recorded inputs.
A transform belongs to that output alone. Its `source_input` is a zero-based
fluid input index in every strategy; each strategy must have a recorded source
at that index. Required/excluded composition checks, removal selectors,
`restore_ordinary`, exact source identity and source components retain their
existing meaning. A source spanning several buffers must have one exact fluid
and component identity. Other outputs in the same operation are unaffected by
the transform. Restored ordinary fluid identities become exportable only after
successful production and remain persisted.

An active recipe keeps progress while its definition remains bound, including
temporary input or output blocking. The selected strategy is reevaluated in
authored order each cadence; progress belongs to the recipe, not a strategy.
Reloading with a newly parsed definition resets active progress on the next
cadence, even if its ID remains the same. Removing it also resets progress.
No input is consumed before successful completion. NBT stores an authored
signature so saved progress resets if the definition changed while unloaded;
unchanged definitions retain saved progress. Legacy NBT without a signature
rebinds to the current recipe for compatibility.

Existing top-level `items`, `fluids`, `output`, `output_amount`, `batch_ticks`,
`composition` and `transform` remain accepted and normalize to one enabled
`default` strategy and one fluid output. `priority` is also allowed on legacy
definitions. Mixing legacy fields with `strategies`/`outputs` is rejected.
Known untouched bundled v1 defaults upgrade to v2; authored defaults are
preserved. Mixer cadence, batch timing, drive behavior, spin-up and power
balance are unchanged.
