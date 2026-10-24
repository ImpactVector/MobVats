# Inter-machine logistics baseline
> Historical October 23 baseline. The assembled chunk-retention finding below is
> superseded by [the October 24 lifecycle follow-up](ASSEMBLED_CHUNK_LIFECYCLE.md),
> which records actual XR2 controls, the retention mechanism, the correction and
> native intact-assembly unload/reload acceptance. The old BUG characterization
> is not the current acceptance condition; the disassembled reload regressions remain.

Production baseline: `385beb82b6017fe8123f151dfcdd45b273475468` (`origin/main` at task start).
Runtime: NeoForge 1.21.1 / 21.1.252, Zero CORE 2 2.4.18, Java 21.
Scope: automated diagnostic fixtures only; no assembly or transport implementation changes.

## Questions answered

| Question | Result | Evidence |
| --- | --- | --- |
| Can same-family machines remain independent with one intervening block? | PASS | All four ordered Vat/Mixer combinations, five gap contents, three formation orders; exact bounds and every real part's controller checked. |
| Can a bare Fluid Buffer occupy that gap without joining either machine? | PASS | Buffer is outside both discovered envelopes and both assembled owners; shell/intermediate replacement and chunk reload coverage. |
| Can existing active ports transfer fluid through it? | PASS, with Mixer recipe restrictions | Scheduled A → Buffer → B in all four family combinations, conserving accepted fluids and components. |
| Can existing ports transfer items through a chest? | GAP | Scheduled active output works; active input extraction is absent. A real hopper can insert into passive/Both input. |

## Discovery versus validation

`touchingVatsDiscoveryBaseline` and `touchingMixersDiscoveryBaseline` place both
T1 machines before formation. Both sets of parts acquire **one controller**, with a
combined 10×4×5 envelope, and formation fails. This is not two independently
assembled machines, and rejection must not be mistaken for isolated discovery.
The tests assert this observed baseline and print the actual validation message.

**ZEROCORE LIMITATION:** ZeroCore's `MultiblockWorldRegistry.processMultiblockChanges`
calls `attachToNeighbors`, collects merge pools, and invokes
`assimilateController` before `recalculateCoords` / `checkIfMachineIsWhole`.
The pinned dependency's bytecode was inspected using Java 21 `javap -p -c`;
the default registry candidate search checks controller class, compatibility,
and neighboring part positions. This supports attribution of the merge-before-
validation mechanism, not an unconditional claim that all adjacent layouts fail.

MobVats overrides discovery in `AbstractVatEntity` and `AbstractMixerEntity`.
Their `attachToNeighbors` uses `getNeighboringParts`, including
`ConduitStructure.neighbors`. `sameEnvelope` fences previously established
envelopes; `continuation` walks clad Buffer/Conduit cells of the matching family.
That discovery filter does not check Vat tier; final validation checks material
and tier separately.
Bare shared blocks do not continue shell discovery. Formation order therefore
matters for touching layouts: A-first and B-first both retain independent,
assembled controllers in the four ordered touching tests. Simultaneous placement
merges and rejects both same-family layouts. The rejection is specifically the
combined x dimension (10) exceeding the configured maximum (9). Neither result
should be generalized to other dimensions or treated as an ownership guarantee
after an untested simultaneous disk reload of touching machines.
Do not remove these MobVats fences or infer a ZeroCore defect from a cladding
validation error.

For a one-block gap, the 60 `isolation*` tests cover:

- Vat→Vat, Mixer→Mixer, Vat→Mixer, Mixer→Vat.
- Air, vanilla chest, bare Buffer, bare Item Conduit, bare Fluid Conduit.
- Same-tick placement, A formed before B, B formed before A.
- A shell removal with B remaining assembled, shell repair, intermediate removal
  and replacement, and repeated exact-envelope/part-owner assertions.

The 20 `reload*` characterizations cover all four family combinations and all
five gap contents, using distant chunks with separate ticket/dependency radii.

**BUG: assembled chunk retention / unsafe unload reads.** After releasing each
forced ticket, the assembled pair retains a level-33 holder and the original
loaded chunk. The tests assert and report that limitation rather than claiming
successful live-assembly eviction. The Vat source path is
`MultiblockVat.updateServer → PhysicalFluidStorage.evacuate → enabled predicate →
Level.getBlockEntity`: it performs a world read for service endpoints before
checking whether their staging tanks are empty. ZeroCore's registry calls
`updateMultiblockEntity` for its nonempty controllers each level tick. Mixer-only
fixtures also retain level 33; this document does not claim a uniquely traced
Mixer reacquisition call or attribute the defect to ZeroCore ownership.

An exploratory, premature level-33 eviction produced `No chunk holder after
ticket has been added`, with that Vat call path in the server crash report.
That unsafe trigger is **not retained** in the harness. The existing scheduler
helper's level-34 / zero-generation-reference precondition remains mandatory.

To test persistence separately, the fixtures then remove one ordinary shell cell
from **both** machines, wait for real disassembly, and release their ticket again.
After the holder settles at level 34, they reuse `ServerChunkEviction.request`.
Its only guard extension permits a dedicated `GameTestServer`; ordinary servers
still need the existing lifecycle-acceptance flag. Minecraft performs saving and
unloading; tests wait for complete eviction including pending unloads, load a
different chunk instance, verify new controller objects and stale-capability
rejection, and repair the saved shell cells. Both machines must independently
reform. Buffer amount/name/composition and intermediate block/cladding/mode must
survive. No callback, ownership assignment, saved data, or lifecycle flag is
manually forged. This is native reload **after shell quiescence**, not proof of
safe automatic unload of assembled machines. Ordered touching fixtures also
verify shell break/repair; touching layouts have no native-reload guarantee.

## Exterior cladding

The 16 `cladding*` tests exercise Vat T1/T2/T3 and Mixer T1 with Buffer,
Item Conduit, Fluid Conduit, and Fluid Port substitutions. Each follows bare
exterior rejection → compatible material acceptance → wrong tier/family rejection
→ repair with a bare Buffer in a clear interior cell. The other machine must
remain assembled. Tier-specific Controllers are present throughout the valid
stages. Invalid shell cases independently inspect discovered bounds and controller
separation, rather than relying on validation failure as evidence of isolation.

**PASS:** compatible exterior cladding is required; a bare interior Buffer is
allowed when physical placement is otherwise valid. Mixer has only T1, so its
negative material test uses Vat-family cladding; Vat tests use another Vat tier.
Existing `VatTierGameTests`, `ShellFamilyGameTests`, and `ConduitGameTests` retain
their more detailed material, bridge, physical-clearance, and topology contracts.

## Standalone Buffer and fluid transfers

**PASS:** `FluidBufferCapabilities.register` already exposes a sided
`Capabilities.FluidHandler.BLOCK` adapter. Its live delegate checks the original
entity, routing availability, loaded chunk, and world identity before forwarding
to `interactionCluster().tank()`. A standalone Buffer has 4,000 mB capacity.
There is no missing capability integration point in this baseline.

The standalone tests use Strange Meat and component-bearing culture slurry.
They check fill/drain simulation, execution, rejected filters, component-exact
typed draining, saturation, NBT reconstruction, filter persistence, unload
callback gating, removal, and stale capability rejection after replacement.
The paired native reload tests complement the serialization/callback tests.

The four `*Buffer*Scheduled` tests seed only A, then rely on controller ticks.
Vat→Vat uses named Strange Meat; configurations involving a Mixer use named
compound culture containing water (1,000) and waste (250), an accepted recipe
input:

| Stage | A | Standalone Buffer | B |
| --- | ---: | ---: | ---: |
| Initial | 2,500 | 0 | 0 |
| Output filter rejects | 2,500 | 0 | 0 |
| Active output enabled | 0 | 2,500 | 0 |
| Input filter rejects | 0 | 2,500 | 0 |
| Active input enabled | 0 | 0 | 2,500 |

Quantities count exact fluid plus components, not just fluid registry ID.
Separate stages assert that a full Buffer retains A's fluid and a full B retains
the Buffer's fluid. Initial seeding/full-capacity setup is explicit fixture
setup; the handoff stages never fill B directly to simulate successful transfer.
Every stage checks independent assembly and that neither machine owns the Buffer.
`MachineFluidPort.transfer` discovers the external capability and selects source
and destination from the port's direction. `ActiveBoundaryPort.tickOrdered`
provides the actual scheduled calls. Existing `ActiveBoundaryGameTests` continue
to cover direct Active/Passive/Both mode combinations and capability simulation.

**GAP (unrestricted Mixer intake):** a Mixer is not a generic destination tank.
`MixerPortEntity.delegatedFluids` requires both the endpoint filter and
`MixerRecipeRegistry.acceptsFluid`. That registry calls
`MixerRecipeDefinition.matchesInput`, including compound-transform constraints.
`gapStrangeMeatMixerRecipeInput` and `gapWaterOnlyCultureMixerRecipeInput` assert
that scheduled output fills the standalone Buffer, while B stays empty and its
present passive capability simulates zero acceptance. All 1,000 mB remain in
the Buffer. The exact failing boundary is the existing Mixer recipe-input
predicate, not Buffer capabilities, ownership, direction, or scheduling.
Water-only culture fails the relevant transform requirements; adding waste makes
the compound eligible. This rejection honors the current recipe contract and is
not classified as BUG. No recipe is changed to make a test pass.

## Chest and the expected extraction gap

**PASS:** the four `gap*Chest*ActiveExtraction` characterizations stage 20 named
wheat items, demonstrate full-chest backpressure and output filtering, then
observe scheduled A→Chest output with unchanged item components and an empty A.
Input filters reject incompatible insertion. Replacing the intermediate chest
with an east-facing hopper demonstrates real scheduled hopper insertion into B
in Both mode, preserving all 20 items and their components.

**GAP:** while B's Item Input is Active, the chest retains all 20 items and B
remains empty. `VatItemPortEntity.transferBoundary` and
`MixerItemPortEntity.transferBoundary` call `PortLifecycle.ejectItems`, whose
guard requires Output direction. No extraction-from-neighbor branch exists.
The missing operation is chest→active Item Input extraction, not item capability
exposure, output pushing, cladding, machine ownership, or scheduling. The GAP
tests assert this absence explicitly and stay green; adding pulling in a future
slice must deliberately update these characterizations.

The test moves the chest's stack into the hopper only for the separate passive
input check. It does not report that setup action as an implemented chest→B
handoff. No new pulling behavior, capability, pipe, ownership workaround, or
production mechanic is introduced.

## Diagnostics and validation

`PairedMachineFixtures` reuses `MachineFixtures` and `Slice11Fixtures`. It exposes
controllers, ports, storage, exact resource totals, intermediate positions and
capabilities; configuration includes families, Vat tiers, gap length, per-cell
gap states, facing port positions, cladding, direction and mode. `seed` provides
explicit initial fluid contents. Item setup reuses the existing staging/NBT
conventions. It does not mock ZeroCore ownership.
Successful paired fixtures remove their own blocks after assertions and records,
so completed controllers and active ports do not keep processing during later
batches. The existing hostile-head native persistence test now reuses the same
settled level-34 scheduler trigger, retains all original eviction/activation
assertions, runs early, and retires its local source fixture after success.
The existing cached-harness recovery test retains its operational, harvest,
milk, and tool-damage assertions, but polls within a 260-tick bound instead of
requiring all scheduled effects at tick 140. Failure diagnostics report each
value; its successful source fixture is also retired.

Headless server logs contain `INTER_MACHINE` records with family/tier, separation,
intermediate state, formation, bounds, actual validation reason, controller
identity equality, intermediate owner and external capability presence. Transfer
records include checked before/after amounts and actual direction, or the named
unsupported operation. Assertion failures include the configuration diagnostics.

The assembled chunk-retention finding is categorized as BUG, with the native
reload test's quiescence precondition stated above. Missing active item extraction
and unrestricted Mixer intake are GAPs; neither is inferred to be a ZeroCore bug.

Canonical validation uses `tools/gradle-java21.ps1`: `format`, then `validate`
(`spotlessCheck build runGameTestServer`). The build includes CPU geometry/cache/
procedural-art checks and packaged resource verification. GameTest development
classes remain excluded from the JAR. No client run or release is performed.

Validation receipt, 2026-10-23: Java 21 formatting and canonical `validate`
completed successfully; **all 1,151 required GameTests passed**, including
**114 new tests**. CPU geometry/cache/procedural-art checks and packaged-resource
checks passed. GAP/BUG characterizations verify the recorded limitations; a green
suite does not mean those missing operations or unload limitations were fixed.
