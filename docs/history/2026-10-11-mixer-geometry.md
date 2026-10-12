> Historical record. Original: `docs/MIXER_GEOMETRY.md`. Current reference: [02-MULTIBLOCKS.md](../02-MULTIBLOCKS.md).
> Archive date basis: Git introduction author date 2026-10-11T06:54:12-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Mixer vessel geometry and clearance

`MixerVesselGeometry` is the immutable physical contract used by formation,
`MixerSweep`, `MixerAssembly`, rendering descriptors and generated placement charts.
Geometry is compiled on assembly and relevant changes, not on every frame or idle
tick. Buffer identity transitions enqueue a geometry recheck without rebuilding
fluid routing.

## Polygon and reference radius

For horizontal width D, N = 4(D - 1). Face normals point along the cardinal
directions; vertices are rotated by half a segment. Radius means apothem, and
vertex radius is apothem / cos(pi/N). Collision uses the convex polygon's face
planes, including its vertices, rather than an approximate circle.

The 5×5 reference reserves four corner buffers and eight edge fluid conduit
positions, each one cell sideways from a cardinal midpoint. Both casing and
glass cladding, vertical TRUNK collars, and TAP branches toward the adjoining
cardinal-face endpoint are included. Every occupied AABB is inflated horizontally
by 1/64 block. Its maximum admissible apothem is obtained by a separating-axis
test against the polygon face normals. The smallest bound across all reserved
devices and shell panels defines the reference radius.

The resulting reference apothem is **1.9365234375 blocks**. TAP elbow boxes are
the limiting volumes: their inner radial plane is 1.9521484375 blocks from the
shaft; subtracting the explicit safety clearance gives the reference apothem.
Without endpoint fittings, riser collars alone would permit 1.9506112774516602.
The production calculation derives the radius from actual `ConduitGeometry`
volumes; those numbers are explanatory results, not a substitute formula.

Other sizes use r(D) = r(5) × (D - 1) / 4. No per-size optimization occurs.
Physical boundaries are not rounded to pixels.

## Cardinal-face connections

![Generated reference geometry](../generated/MIXER_REFERENCE.svg)

Coordinates are measured from the shaft center. The enclosure outer face is
2.5 blocks away; its 3/16-deep panel ends at 2.3125. Pipe centers on that face
are 2.046875 blocks away. The generated top view projects the actual occupied
buffer and TAP volumes used in the radius calculation, including the branches
to midpoint devices.

The generated 5×5 grid is:

```text
B C X C B
C X X X C
X X S X X
C X X X C
B C X C B
```

The C cells are offset sideways from the cardinal midpoint. At a sideways offset
of one block the polygon boundary lies approximately 1.682 blocks from the shaft,
so a cladded riser can fit even though the cardinal apothem is approximately 1.937.
Shell location positions the pipe near the panel. Bare and clad pipes use
identical positions and occupied volumes; only structural legality differs.
TAP branches connect from the C cells to devices
at the cardinal face midpoint. A runtime GameTest installs and checks fittings
on all four cardinal faces using the actual compiled geometry.

A pipe installed directly at the cardinal midpoint occupies X space and requires
a radius reduction. An endpoint panel there can be served from the adjacent C
cell. Additional horizontal pipe runs, bridges, and endpoint arrangements are
checked against their actual snapshot; C is not permission for arbitrary geometry.

The 3×3 grid remains CXC/XSX/CXC for cladded vertical TRUNK risers. Its corner TAP
branches toward adjacent cardinal endpoints do not clear the canonical envelope.
The detailed tables explicitly report this difference. The 5×5 edge C cells clear
both reference TRUNK and TAP configurations.

## Device envelopes

`FluidBufferGeometry.BODY` is 10×10×13 pixels: local X/Z 3/16 through 13/16,
Y 2/16 through 15/16. A supported body has a centered 4×4-pixel foot down to Y=0.
Short casing mounts use a centered 4×4-pixel cross-section extending from the
body to the neighboring block boundary. Compatible neighbors reserve half-bridges
with the body's full cross-section; both halves form the continuous connection.

Bridge membership comes from `PhysicalFluidStorage` clustering. Filter equality
alone does not join incompatible stored fluid/component identities or allow an
empty intermediary to bridge two incompatible clusters. Storage capacities,
filters and routing rules are unchanged.

Conduits reuse `ConduitGeometry`: actual body segments, collars, bends and
endpoint fittings. Bare, casing-clad and glass-clad cells and TRUNK/TAP modes
are evaluated separately. Structural placement permission still comes from
`MixerPlacement`; physical clearance does not override it.

## Uniform structural reduction

If any occupied device intersects the canonical envelope or its safety margin,
formation tries r - 1, r - 2, and so on, keeping one uniform polygon along the
entire reached shaft depth. Every selected envelope clears every device. There
are **no local capacity deductions or gaps around obstructions**. If no radius
with shaft and rotor clearance remains, formation fails with an offending-device
diagnostic. Actual device envelopes and the canonical polygon remain authoritative
for formation, obstruction clearance and rendering boundaries.

## Approximate processing capacity

There is one authoritative calculation, in `MixerAssembly.slotCount()`:

`slots = floor((effectiveDiameter - 1)^2 * shaftLength / 8)`

`effectiveDiameter = nominalDiameter - 2 * radiusReduction`. Each radius step
removes one block from both sides. Existing geometry validation permits effective
diameters of at least 3. Shaft length counts actual contiguous installed mixing
blocks, from 1 through 8. Full-depth installation has shaftLength = H - 1; shorter
Shafts receive capacity only for installed depth. Integer arithmetic yields 64
slots naturally at 9×9×9, with no separate clamp.

| Footprint | Height 3 | Height 5 | Height 7 | Height 9 |
|---|---:|---:|---:|---:|
| 3×3 | 1 | 2 | 3 | 4 |
| 5×5 | 4 | 8 | 12 | 16 |
| 7×7 | 9 | 18 | 27 | 36 |
| 9×9 | 16 | 32 | 48 | 64 |

At depth 8, nominal diameter 9 reduced by 0, 1, 2 or 3 radius steps has
effective diameter 9, 7, 5 or 3 and 64, 36, 16 or 4 slots. At diameter 5,
depth 3 gives 6 slots; one reduction gives 1 slot. A one-block Shaft at diameter
3 yields zero slots; the minimum full-depth 3×3×3 Mixer has one slot.

Capacity approximates vessel footprint and mixing depth. Shaft thickness, rotor
artwork, individual pixels and floor-shell inset do not deduct slots. The
3-pixel bottom shell inset still constrains physical clearance and rendering.
Machinery outside installed depth cannot shrink the vessel.

The centered square Shaft is exactly 6×6 pixels (0.375 blocks), local X/Z
5..11 pixels. Physical shape, construction/inventory models, formed rendering,
central liquid exclusion and the generated reference diagram use that dimension.
Mixer Shafts are vertical; the square is invariant under cardinal rotations and
mirrors. The formed Shaft ends at the inner vessel floor. Crossing paddles still
attach through its center at the first usable level, within the canonical vessel.

Pixel columns remain for liquid presentation and explicitly geometric
`geometricSweptVoxels()` / `geometricSweptVolume()` diagnostics. They do not feed
slots. The old pixel-capacity denominator and division have been removed.

Drive/process relationships are unchanged: startup takes slots × 200 ticks;
FE startup costs slots × configured FE per cycle; running FE costs one cycle
per 200 ticks. The executor retains one active recipe, existing batch cadence
and atomic transactions. Slots do not introduce additional recipe concurrency.
At 64 slots startup is 12,800 ticks, safely within integer arithmetic.

Save version 3 stores diameter, radius-reduction steps, shaft depth and floor inset;
geometric volume and slots are derived. Version-3 assemblies recompute slots from
dimensions on load. Legacy obstruction masks are rejected and rebuilt through
the normal structure lifecycle.

## Charts, fixtures and presentation

[Generated clearance tables](../generated/MIXER_CLEARANCE_GENERATED.md) contain grids for all
four widths and per-cell structural legality, canonical fit, actual intersection,
and required whole-vessel reduction for each device variant. Regenerate them with
`MixerVesselGeometryGameTests.generatedClearanceReport`; the GameTest run writes
`.cache/gametest/mixer-clearance.md`. Copy that output to the tracked document.

The historical dense storage fixture remains available unchanged and fails the
physical clearance check. Runtime operating regressions use peripheral buffers
and safe conduit paths; they retain storage/filter counts and test behavior.
The generated acceptance world has not been redesigned. Acceptance tests check
zero intersections and produce `mixer-acceptance-clearance.txt` and
`mixer-annex-clearance.txt` with actual reductions and canonical conflicts.

The unchanged full-height acceptance enclosure selects the canonical radius,
shaft depth 3 and 6 slots. The culture annex adds a midpoint
conduit at enclosure-relative (0,2,2); its body, collars and connecting run from
(0,2,1) intersect the canonical envelope. It selects one radius step, apothem
0.9365234375 and 1 slot. Both selected vessels have zero physical
intersections. These results are measured by the acceptance GameTests.

`MixerRenderDescriptor` consumes the selected polygon's exact vertices. Each
edge has one exterior wall and an inset interior wall, extending through the
reached shaft depth. A continuous inset rim leaves the vessel open; the bottom
uses center fans with one triangle per edge on each visible side. The inner wall
and bottom are separated from the exterior by 1/32 block. Paddle reach includes
this presentation thickness without changing the physical clearance contract.
The client bakes these meshes only when the assembled descriptor changes.

The working-liquid fan uses the same polygon with a small additional inset.
One bucket per block of geometric swept volume sets its height, quantized
to pixel layers and clamped 1/8 block below the rim. Fluid identity and tint are
independent of the verdigris material. Presentation selects the active or next
executable recipe and its executable strategy, excluding unrelated outputs and
additives. Ambiguous fluid identities remain empty. The current executor commits
consumption and production together at batch completion; inputs remain physical
storage during processing, so no consumed-batch snapshot or extra fluid tank is
needed. Network updates send the selected FluidStack and its components.
Connected-buffer artwork and rotor animation remain independent follow-up work.
