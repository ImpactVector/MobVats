> Historical record. Original: `PROCEDURAL_FLUID_BUFFER_ART_NOTES.txt`. Current reference: [05-PROCEDURAL-ART.md](../05-PROCEDURAL-ART.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
PROCEDURAL FLUID BUFFER ART — implementation and acceptance report
October 11, 2026

Historical generator 1.5.0 report. The mounting design and associated preview
and validation descriptions below are superseded by
2026-10-11-procedural-fluid-buffer-correction-notes.md (generator 1.5.1).

1. GENERATED RESERVOIR MATERIALS AND MODELS

Generator 1.5.0 adds eight deterministic 16x16 PNGs: buffer_glass and
buffer_metal in master/T1/T2/T3. Glass remains #B8D9CB at alpha 48/255;
metal uses the existing verdigris copper, iron and bloody-iron palettes with
seed 29 weathering. Existing registered blocks bind T1, as with conduits;
T2/T3 are art/preview variants, not new gameplay registrations.

Added 62 model resources: 52 palette-specific block models (reservoir,
canonical extension, canonical mount, five complete variants and five frame
variants per palette), five existing block aliases and five item models.
The output now contains 108 PNGs and 197 model JSONs.

Generated resources:
  build/generated/resources/procedural-art/assets/mobvats/
  build/generated/resources/procedural-art/META-INF/mobvats-procedural-art.json
Preview:
  build/procedural-art-preview/models.html

2. CONNECTIONS, EXTENSIONS AND SIX-WAY SYMMETRY

FluidBufferArt owns presentation-only geometry. Body bounds are exactly
[3,3,3] through [13,13,13] pixels. Each closed face has an 8x8 translucent
pane surrounded by 1px metal reinforcement. All six faces have equal total
and glass areas. A canonical extension reaches from 13 to 16 with the same
10x10 cross-section; runtime rotation applies it along any cardinal axis.
Mounts use a 4x4 cross-section from the body to the attachment face.

Connected bodies are unions of body and extension volumes. Exterior surfaces
are merged into rectangles, and internal body/extension walls and block-boundary
end caps are suppressed. Reinforcement continues across joined boundaries
without a seam ring. Concave L/cross clusters retain their actual exterior
outline. Mounts appear only on actual structural neighbors, reserved downward
support, or a formed clad block's outward shell face. A standalone block has
no permanent pedestal, top, lid or privileged face.

3. GROUPING AND FLUID COMPATIBILITY

FluidBufferPresentation calls PhysicalFluidStorage.compile rather than
duplicating its rules. Equal filters are necessary but insufficient: the
compiler requires equal fluid identity AND components for nonempty members.
Its deterministic ordering also owns assignment of empty intermediaries;
water -> empty -> lava cannot become a single visual cluster.

Formed components are fenced by ConduitStructure.containing, matching the
controller's bounded storage domain. Unformed loaded components use the same
storage compiler as interactionCluster. Different formed owners cannot merge.
No storage, compatibility, capacity, routing or transaction logic was changed.

4. CLADDING REUSE AND RENDER OWNERSHIP

All buffer construction models call MobProceduralArt.frame directly. They
reuse the conduit frame's eight trihedral corner fittings, twelve 1px rails,
4px corner reach, and the existing Vat/Mixer casing/glass differences.
Bare block models contain no construction frame.

Placed buffers use ConduitShellModel: unformed chunk geometry is only the
open frame; formed chunk geometry is only the existing bounds-selected
casing/glass shell. The FluidBufferRenderer draws the reservoir and actual
fluid once in either state. Inventory models contain the complete empty body
and optional frame because no block entity renderer runs for inventory items.

Both faces of each reservoir panel use opposite windings on one plane.
Backface culling chooses the applicable winding. Glass and liquid share the
existing fixed translucent atlas buffer used by VatMediumRenderer, preventing
an intervening opaque BER from prematurely flushing transparency. Metal uses
the solid block atlas sheet. No extra translucent offset wall is added.

5. ACTUAL CONTENTS AND LEVELS

Snapshots contain the cluster's real FluidStack, including all components,
aggregate amount, join/mount masks, and a shared absolute liquid level.
Configured filters never invent fluid. Empty snapshots render no liquid.
The real amount/capacity fraction is mapped onto the connected reservoir's
visible volume, including bridge volumes. Horizontal members show a common
level; vertical clusters fill lower regions first. This is presentation only:
persisted member tanks retain their established distribution and 4000 mB
capacity. Insets keep liquid off the glass plane and below the closed ceiling.

The renderer uses the fluid's client still texture, stack tint/alpha and
emission. Composed/dirty tint comes directly from the existing
MixerLiquidPresentation.compoundTint implementation. Machine tier never
colors the fluid. Unlike identity/components remain distinct volumes.

6. PERFORMANCE, SYNCHRONIZATION AND LIFECYCLE

Server content/filter/neighbor and lifecycle events enqueue presentation work.
LevelTick.Post coalesces these changes; unchanged snapshots send no packet.
Controller compile/assembly/clear hooks enqueue bounded structural regions.
Chunk-unload hooks enqueue positions without fetching chunks. Discovery at
the subsequent level tick checks hasChunkAt and never intentionally loads an
unloaded neighbor. No renderer scans the world or polls adjacent blocks.

Vanilla BE update packets/chunk update tags deliver the presentation snapshot
for this ordinary BE; ZeroCore remains responsible for controller assembly
and bounds. No new custom channel, ticking buffer entity or gameplay state
is introduced. Presentation data is derived on load, not persisted as storage.

Static panel geometry is held in a bounded 256-entry cache, with shared baked
renderer meshes and weak per-entity presentation state. Contents changes
rebuild only liquid geometry when the level changes; identity/tint reads use
the authoritative snapshot. Joins/mounts select/rebuild static geometry only
when relevant presentation state changes.

7. MECHANICAL ENVELOPE AND MIXER CLEARANCE

The existing physical contract remains 10x13x10 pixels, Y=2..15, plus its
separate downward support. The artwork is the smaller centered 10px cube,
Y=3..13. Vertical artwork extends across the recessed bands inside the old
body before entering the reserved bridge. Thus it fits without changing
FluidBufferGeometry, MixerVesselGeometry, reference radius, clearance charts,
collision, capability behavior, or Vat displacement/comfort mechanics.

CPU checks cover all 64 join masks against the UNION of unchanged mechanical
volumes, not just individual bridge boxes. Every rotated mount has the same
dimensions. Existing polygon clearance and physical-storage regressions pass.

Within Mixers, mounting recognition follows the existing Mixer casing/glass
and outward-shell physical rules; unrelated Vat casing does not add an
unreserved Mixer support. Vat attachment rendering is available outside a
formed Mixer. Generic downward sturdy supports remain within the pre-existing
reserved support/body volumes.

8. PROVENANCE AND PACKAGING

generateProceduralArt remains the single producer; normal processResources
selects generated models over the old static buffer placeholders. No copying,
resource-path duplication, static combination explosion or production art-tool
dependency was introduced. Generated model and texture references resolve.
The manifest records hashes for generated models as well as textures; checks
compare these hashes against processed resources and JAR contents. All seven
protected PNG hashes pass. AI-placeholder guards/ledger are unchanged; local
JARs remain development-only while unrelated placeholders are present.

9. VALIDATION

Passed with the existing offline Gradle cache:
  gradlew proceduralArtPreview --offline
  gradlew build --offline
  gradlew runGameTestServer --offline — all 744 required tests passed
  gradlew buildTestWorld --offline — MobVatsTest generated and verified
  git diff --check

Seven new GameTests cover empty/half/full presentation, all six join axes,
unchanged persistent distribution/capacity/displacement, incompatible content
and filters, metadata/composition plus network-tag round trips, six-way mounts,
stable revisions/cache reuse, lifecycle hooks, removal, and actual Mixer
formation/disassembly/reformation with casing/glass-clad buffers.

CPU tests cover exact body dimensions, equal face areas, all connection masks,
physical-envelope containment, internal-face removal, static-cache identity,
shared horizontal/vertical levels and preview geometry from production code.
Generator tests compare buffer frames directly with conduit frames and verify
fixed glass tint/alpha across palettes. Existing shell ownership and deferred
transparency tests also pass. The preview DOM/projection check exercised all
three tiers, three fill states, three colors and five rotations without missing
materials or nonfinite projections (4816 canvases, 4105808 textured triangles).

Lifecycle GameTests simulate unload/removal/reload hooks; they do not force
actual client chunk disk eviction. GPU sorting, client assembly transitions,
lighting, distance visibility and visual acceptance remain manual checks.
Preview fluids are explicitly colored fixtures, not live Minecraft contents;
formed shell illustrations retain the preview's established simplification.

10. MANUAL ACCEPTANCE

Open build/procedural-art-preview/models.html. Compare all tiers and all five
variants. Rotate through all six faces; select empty/half/full and the color
fixtures. Inspect side/vertical/depth pairs, three members, L shapes and the
larger cluster with multiple mounting faces.

In the generated MobVatsTest world or a separate creative world:
  - Place each of the five buffer variants alone. Inspect all faces and items.
    Confirm centered geometry, no permanent lid/pedestal, and distinct framing.
  - Attach casing on each axis; remove it and confirm the support disappears.
  - Join buffers on X/Y/Z, in a line and an L. Fill/drain with water and lava.
    Check levels, continuous glass/metal, open internal joins, and empty state.
  - Put unlike fluids/components or unequal filters in adjacent members.
    Confirm separate reservoirs; repeat with an empty intermediary.
  - Complete a valid Vat/Mixer using matching clad buffers in supported shell
    positions. Confirm open frames disappear, formed skins match nearby parts,
    and glass reveals exactly one reservoir/fluid presentation.
  - Break/rebuild a structural wall, leave/reload chunks, and save/reopen.
    Confirm ownership, joins, supports and liquid recover without block toggles.
  - Inspect permitted Mixer margin positions for polygon-vessel intersection.
    Check near/far glass, corner panes, water/lava/composed carriers, opaque
    machinery and specimens during camera movement for sorting/flicker.

No Minecraft client was launched. No commit, publication, release or deployment
was performed. Manual client acceptance is not claimed.
```
