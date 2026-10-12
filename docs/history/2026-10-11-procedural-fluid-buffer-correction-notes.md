> Historical record. Original: `PROCEDURAL_FLUID_BUFFER_CORRECTION_NOTES.txt`. Current reference: [05-PROCEDURAL-ART.md](../05-PROCEDURAL-ART.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
FLUID BUFFER GEOMETRY / CONNECTION CORRECTION
October 11, 2026 — generator 1.5.1

1. MOUNTING HARDWARE REMOVED

Removed all six rotated 4x4-pixel mounting supports, which previously occupied
the three-pixel gap from the reservoir to the block boundary. Removed neighbor
casing/glass recognition, outward-shell mounts and sturdy-floor supports from
FluidBufferPresentation. It no longer scans structural neighbors for mounts.
Removed mount masks from renderer cache state and network tags. Legacy
visualMounts tags are ignored. Removed all four generated buffer_mount models;
generation and packaged-resource tests require their absence.

No standalone mounting orientation exists in authoritative gameplay state.
The standalone portable presentation therefore uses the plain symmetrical
reservoir. No placement direction, portable mechanics or storage redesign.
No mounting hardware can appear in bare, clad, formed or unformed buffers.

2. RESERVOIR AND JOINING GEOMETRY

The body remains exactly [3,3,3] through [13,13,13] pixels. Compatible joins
remain full 10x10 cross-section extensions to the shared boundary, identically
rotated across six directions. The surface generator operates on the union:
internal body/extension faces and shared boundary caps are omitted, including
their metal reinforcement. Exterior glass and edge reinforcement continue
across the boundary, rather than connecting isolated tanks with a small pipe.
Incompatible neighbors keep closed individual faces and the six-pixel gap.

Regression fixtures now cover all six pair directions, a three-buffer line,
L shapes, a vertical corner, and a 2x2 rectangular arrangement. Every emitted
cluster face is checked against the entire union: it must have reservoir
behind it and empty space in front, ruling out internal walls/reinforcement.

Mechanical discrepancy/limit: a 2x2 arrangement's reserved 10-pixel bodies and
face bridges do not reserve the center space between four block corners.
The artwork preserves that concave exterior gap. Filling it to create a solid
rectangular prism would exceed authoritative geometry. This correction does
not fill that space or change Mixer clearance. Likewise, the existing physical
10x13x10 buffer body and physical support/mount reservations remain unchanged;
they are conservative mechanical reservations, not required visible hardware.

3. AUTHORITATIVE COMPATIBILITY

FluidBufferPresentation still calls PhysicalFluidStorage.compile. Storage
owns deterministic cluster membership: equal filters plus compatible fluid
identity AND components, including its assignment of empty intermediaries.
No renderer compatibility algorithm was introduced. Formed controller ownership
continues to fence discovery into the applicable storage domain. Shared levels
and real fluid appearance remain derived from actual cluster contents/capacity.
Capacity, storage distribution, routing, transactions, filter semantics and
conduit connectivity are unchanged.

4. CLADDING AND RENDER OWNERSHIP

Unformed buffers retain the exact conduit Vat/Mixer open construction frame.
Formed buffers retain the existing bounds-selected casing/glass shell through
ConduitShellModel. FluidBufferRenderer owns the reservoir and real liquid in
both states; placed chunk models supply only the frame/shell. Inventory models
retain the empty reservoir and optional construction frame. There is no extra
reservoir in the placed static model. Fixed solid/translucent atlas buffers and
opposite windings of the same panel plane are unchanged.

5. INVALIDATION AND CACHING

Static geometry now has only the six-bit authoritative join mask as its key:
there are at most 64 reservoir shapes, with no mounting state. The renderer
reselects its cached static mesh when joins change, and rebuilds liquid mesh
when joins or liquid height change. Quantity-only changes preserve static mesh
identity. Server dirty queues still coalesce content/filter/neighbor changes,
assembly/disassembly and chunk lifecycle changes at LevelTick.Post; unchanged
snapshots do not send packets. The existing ZeroCore lifecycle hooks are intact.
Discovery checks loaded chunks; rendering performs no neighbor queries or
per-frame topology traversal. Disk storage remains authoritative and derived
presentation is rebuilt after load.

6. REGRESSION COVERAGE

Updated the prior mounting GameTest into no-hardware checks for all five buffer
variants, six directions, Vat/Mixer casing/glass, conduits and sturdy blocks.
Unequal-filter adjacent buffers remain separate. Added a storage save/load
round trip and legacy-mount-tag rejection. Formation/disassembly/reformation
coverage now asserts mount tags never appear. Existing tests still cover fluid
identity/components, incompatible intermediaries, shared liquid levels,
capacity/displacement, cache reuse and simulated chunk lifecycle recovery.

CPU geometry coverage preserves centered bounds, equal face areas, all 64 join
masks inside physical reservations, six-axis joins and liquid cap suppression;
irregular/rectangular scenes additionally check the whole-cluster exterior.
Generator tests preserve frame equality, fixed glass color/alpha, deterministic
outputs, model/texture hash checks, protected assets and AI-placeholder guards.

7. VALIDATION

Passed using the existing offline Gradle cache:
  gradlew proceduralArtPreview --offline
  gradlew build --offline
  gradlew runGameTestServer --offline — all 745 required tests passed
  gradlew buildTestWorld -PworldName=MobVatsAcceptance --offline
    Generated and verified run/saves/MobVatsAcceptance.
  git diff --check

The preview DOM/projection check passed for all tiers, fill states, colors and
rotations: 5908 canvases and 6931518 textured triangles, with no missing
materials or nonfinite projections. Generated output: 108 PNGs and 193 models.
No Minecraft client is launched. Generated JARs remain development-only under
the existing unrelated AI-placeholder release policy.

8. MANUAL VISUAL ACCEPTANCE

Open build/procedural-art-preview/models.html. Compare the five standalone
variants, formed shells, all six compatible pair orientations, incompatible
water/lava pair, line, L shape, rectangular arrangement and vertical corner.
Inspect casing/conduit neighbor scenes: no reservoir attachment is present.
Switch tiers and liquid levels/colors; inspect both Vat and Mixer open frames.

In MobVatsAcceptance or a separate creative world:
  - Place bare and all four clad variants beside casing, glass, conduits, ports,
    solid floors and incompatible buffers. Confirm no arms/brackets/pedestals.
  - Join compatible buffers on X/Y/Z. Inspect internal seam planes and corners
    for retained glass/metal partitions, flicker or small pipe-like connections.
  - Give adjacent members unlike fluids/components or filters; verify the gap
    and individual walls remain, including with an empty intermediary.
  - Form/disassemble/reform valid Vat and Mixer structures. Confirm frame/shell
    transitions, centered bodies, actual liquids and no duplicate geometry.
  - Save/reopen and leave/reload chunks. Confirm joins and contents recover.
  - Check permitted Mixer margins and near/far glass for vessel intersections,
    transparency ordering, lighting and visibility at distance.

Headless lifecycle tests simulate unload hooks, rather than client disk chunk
eviction. Preview shell/fluid illustrations retain their established limitations.
GPU translucent quality and client visual acceptance remain manual.
No commit, push, publication, release or deployment is performed for this slice.
```
