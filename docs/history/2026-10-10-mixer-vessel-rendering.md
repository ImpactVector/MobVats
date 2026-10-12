> Historical record. Original: `MIXER_VESSEL_RENDERING.md`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Mixer vessel rendering

The controller renders an open, opaque, pixel-segmented verdigris copper vessel
around the cached `MixerAssembly` sweep. A neutral vanilla iron-block texture
supplies panel detail under copper-green vertex colors: medium exterior, lighter
rim with occasional exposed copper, darker reinforcing bands, subdued seams and
a darker green interior. All vessel faces remain opaque. Interior faces are inset
by half a pixel. Palette and band divisions are cached with the assembly mesh.
No new artwork or physical storage is introduced. Vessel colors never alter
the liquid's composition-derived color.

Mixer Glass may occupy ordinary TopFace positions, including around the centered
Impeller and adjacent Drive. Existing vertical glass-frame permissions remain;
roof frame/corner and bottom restrictions are unchanged. Glass-clad Mixer conduits
and buffers use the same part-type permission through the existing validator.
A 3x3 minimum roof has only the centered Impeller cell inside its frame; roof
windows require a larger horizontal size.

The Impeller bottom center anchors the mesh. Radius retains the assembly's
four-pixel outside clearance; depth is exactly the installed usable Shaft depth.
The machinery layer is excluded. Existing rotor position, animation and speed
are unchanged. Walls around the central Shaft opening are omitted, avoiding
intersection with the physical rod.

Physical buffers and conduit occupied volumes are authoritative sweep cutouts.
Bare interior buffers are legitimate placements and require this rendering
treatment, rather than fixture changes. Clad buffers are valid shell placements,
not interior placements. Production fixture buffers are in the floor layer below
the mixing depth. Acceptance-world builders are unchanged.

## Working liquid

The server selects actual connected fluid stacks through a read-only accessor.
For a selected recipe, filtration uses its transform source; other recipes use
recorded fluid inputs. Waste and XP are treated as additives when a different
carrier exists. All enabled strategies must yield one exact fluid/component
identity; ambiguity produces an empty vessel. With no selected recipe, only a
single exact stored identity is eligible. No outputs/additives are blended.
Amounts from split buffers of that same identity are summed; all components are
copied intact. The selection is sent in network presentation NBT. It is recomputed
from saved physical storage after reload, not persisted as an additional tank.

Native still sprites and ordinary stack tint are used. Compound appearance uses
muted water blue/teal, lava orange, and End/chorus purple. Known exact composition
entries establish these colors; unrecognized selectors retain the fluid type's
native tint. The current shared client fluid types have fixed colors, so this
composition palette is scoped to Mixer presentation. Waste shifts RGB toward the
existing waste color by no more than 10%. Only the liquid top uses translucency.

There is no separate working-volume measurement. The conservative visual rule is
1,000 mB of the selected connected stored fluid per usable swept block. The level
counts unblocked sweep voxels from the bottom, rounds up to a pixel layer and caps
two pixels below the rim. This is a storage-derived proxy, not fluid physically
transferred into a vessel. Large stored amounts may saturate this display. Empty
or ambiguous selection has no surface. Surface cells exclude physical machinery
at their level and leave clearance around the Shaft and inset wall faces.

Vessel/rotor geometry is cached by descriptor identity; existing rebuild and
network invalidation paths apply. The liquid mesh changes only when the quantized
level changes. Identity/composition tint updates independently of geometry.

## Verification and manual acceptance

`gradlew.bat build`, `gradlew.bat runGameTestServer`, and `git diff --check` cover
compilation, rendering resources, descriptor lifecycle, physical cutouts, ordinary
and compound identities, recipe selection, waste tint, level bounds and unchanged
processing capacity. Opt-in client smoke assertions also bake the vessel and
check its wall/rim/surface geometry; the graphical client is not launched by this
change.

Validation on October 10, 2026: the standalone build and `git diff --check` passed;
all 622 required headless GameTests passed, including six new vessel/liquid tests.
The GameTest JVM stalled during chunk unloading after reporting success, including
on a fresh generated world. Only the identified completed test JVM was stopped to
release the world lock. Consequently the test launcher did not exit normally;
the success claim applies to the test results, not its shutdown. Opt-in graphical
smoke assertions were added and compiled, but not run.

Top-glass/verdigris addendum validation: the build and diff check passed, and all
632 required headless tests passed. Ten additional tests cover ordinary roof
glass, all three adjacent Drive types, full/short Shaft geometry, larger roofs,
clad conduits/buffers, unchanged frame rules, required machinery and casing-to-glass
compatibility. The prior conduit-placement matrix now expects Mixer TopFace
glass to be legal; Vat expectations are unchanged. The known post-test server
shutdown stall required cleanup of only the completed test JVM. `runClient` was
launched as requested; fixture visual acceptance remains manual.

Manual visual acceptance remains required for the existing minimum, larger/full
Shaft, shorter Shaft, feeding-slurry, culture-slurry, general-filtration and
lava-filtration fixtures. Check the open rim from above and through exterior
glass, rotor clearance, obstruction cutouts, empty/ambiguous buffers, save/chunk
reload, structural changes and machine resizing. No fixtures, recipes, storage,
routing, drives, Vat rendering, or placeholder release safeguards are changed.

Structural-shell handoff audit on October 10, 2026: existing shared placement
policy, solid Impeller/Drive models and full glass enclosure support were retained.
Additional headless checks cover every cell for all infrastructure families,
all Vat-clad conduit/buffer variants at a Mixer corner, and bare infrastructure
rejection at shell faces, edges and corners with recovery. All 644 required
GameTests passed. The launcher encountered the documented post-test chunk-unload
stall; its thread dump was captured and only the completed test JVM was stopped.
In-game visual acceptance remains pending and operator-controlled.
