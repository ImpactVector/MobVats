# Mixer Blocker — Slice 3

The optional `mobvats:mixer_blocker` is a Mixer shell component. Craft it from
Mixer Casing, a comparator, and a redstone torch. It has no inventory or resource
capability. Any number permitted by the existing shell placement rules may be
installed; their samples form a union of exclusions.

Shift-use with an acquired item or filled fluid container assigns one product
sample without consuming it. A fluid container samples its contents. Shift-use
with an empty hand clears the sample. An empty Blocker excludes nothing. There
is no mode, recipe selector, tag selector, quantity setting, or new screen.

## Matching and planning

`MixerBlockerSample` uses the existing ItemStack/FluidStack codecs and physical
sample interaction pattern. Item matching uses `ItemStack.isSameItemSameComponents`,
ignoring stack count. Fluid matching requires the same registered fluid and equal
`CompoundFluids.COMPOSITION` values. `CompoundComposition` already sorts entries,
merges duplicate selectors, and canonicalizes selector JSON. Formula entry amounts
remain meaningful; containing fluid volume does not. Custom names, lore, and other
presentation components on fluid stacks do not enter this identity. An unknown
legacy composition is distinct from every known formula.

The existing `MixerRecipeDefinition.result(strategy, output, consumed)` predicts
every fluid output from the actual proposed allocation, including explicit,
derived, transformed, and restored ordinary outputs. Item outputs use the existing
`ItemOutput.stack()`. No processing engine was duplicated. A match on any output
rejects the entire candidate before preparing resource changes or output routes.
Temporary input snapshots stay local; no executable reservation is returned for a
blocked candidate and no buffer changes occur. Remaining candidates use the
unchanged Complexity → Normalized Consumption → Recipe ID ordering.

Installed samples are resolved at selection time. A prepared reservation rechecks
the current samples before committing. Existing in-flight processing bypasses new
Blocker checks, including after process-state reload; subsequent batches read the
new samples. Existing input/output replanning and manual-meter behavior remain.

## Lifecycle and diagnostics

Configuration belongs to each physical entity and uses ZeroCore syncDataTo/From
for save/load and client update tags. EndpointState captures and restores it for
block drops, pick-block, and placement. Disassembly does not clear configuration.
Chunk reload uses the same block-entity serialization path.

Mixer status reports whether samples are installed and when all compatible
candidates are blocked. Messages contain no predicted product names or formulas.
There is no per-tick logging.

## Validation and boundaries

`MixerBlockerGameTests` adds 23 headless tests covering registration, interaction,
matching, normalized formulas, volume, presentation metadata, item components,
derived and allocation-dependent transformed outputs, fallback and ranking,
multiple outputs and Blockers, idle/no-consumption behavior, stale reservations,
save/load/update tags/portable state, reformation, in-flight edits, manual meters,
and item/fluid backpressure. New-entity deserialization exercises the chunk-load
serialization path; tests do not force-unload the shared GameTest chunks.

Current recipes predict all supported outputs safely. No unsupported prediction
case was found. Existing allocation chooses one proposal; the Blocker does not
search for a different ingredient allocation within that proposal. Item recipes
currently author item IDs and amounts; exact sample component matching does not
add component-bearing item output authoring. Future mechanical fluid metadata
must be explicitly included in product identity if added to the composition model.

The block reuses a vanilla piston texture. Client visual and functional acceptance
of both Manual Meters and Mixer Blockers remains a user-run next step. No client
was launched and nothing was published.

### Validation result (October 16, 2026)

Canonical Java 21 `format` and `validate` modes passed, including
`spotlessCheck build runGameTestServer`, CPU geometry/render checks, and packaged
resource checks. All 839 required headless GameTests passed: 23 new Blocker tests
and the existing 816. The registry-count assertion now expects 43 blocks and 47
items. `git diff --cached --check` passed. No checks were waived.

## Changed files

- `README.md` and `docs/MIXER_BLOCKER.md`: feature usage and slice record.
- `src/main/java/net/impactvector/mobvats/gamecontent/item/EndpointState.java`:
  portable part configuration.
- `src/main/java/net/impactvector/mobvats/gamecontent/multiblock/mixer/`:
  `MixerPartType.java`, `MultiblockMixer.java`, `part/MixerPartBlock.java`,
  `part/MixerBlockerEntity.java`, `process/MixerBlockerSample.java`, and
  `process/MixerState.java`: registration, physical control, matching, eligibility.
- `src/main/java/net/impactvector/mobvats/gametest/MixerBlockerGameTests.java`:
  23 added tests.
- `src/main/java/net/impactvector/mobvats/gametest/AppliedCladdingGameTests.java`:
  expected registry count increases by one block and one item.
- `src/main/resources/assets/mobvats/lang/en_us.json`: names and assignment message.
- `src/main/resources/assets/mobvats/blockstates/mixer_blocker.json`,
  `models/block/mixer_blocker.json`, and `models/item/mixer_blocker.json` under the
  same asset namespace: block and inventory models.
- `src/main/resources/data/mobvats/loot_table/blocks/mixer_blocker.json` and
  `src/main/resources/data/mobvats/recipe/mixer_blocker.json`: drops and crafting.
