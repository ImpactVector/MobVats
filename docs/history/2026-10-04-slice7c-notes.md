> Historical record. Original: `SLICE7C_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Git introduction author date 2026-10-04T23:06:09-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
MobVats Slice 7C -- Geometry-owned simulacra
==========================================

Implementation
--------------
1. SimulacrumGeometry is a version-1 persisted filled cuboid. It saves width,
   height, depth, one local Head offset, horizontal FRONT, and three captured
   body extents. The filled-cuboid rule reconstructs every Head/Flesh cell exactly.
   Derived data includes world occupied bounds, footprint corners, body bounds,
   render position, and a one-block comfort shell. No live entity is needed later.

2. Capture reads runtime bounding-box X/Y/Z extents, converts X/Z to local
   width/depth for EAST/WEST facing, then uses ceil independently (minimum 1).
   Runtime horizontal entity direction establishes FRONT. The generic path has
   no species branch. All new captures apply optional loaded definition overrides.

3. Local X is specimen left-to-right, local Z front-to-back, local Y upward.
   Default Head offset is ((width-1)/2, height-1, 0): center for odd width,
   left-of-center for even width, always top/front. Rotation maps local right to
   FRONT.clockwise and local back to FRONT.opposite. Saved facing governs visuals;
   explicit Head block rotation rotates the complete resolved geometry around Head.

4. The controller discovers encoded Heads, reads saved geometry, creates exact
   cell claims, verifies expected Head/Flesh, then associates services. Flesh never
   expands a claim. Adjacent extra Flesh is diagnosed as unowned. Low-level
   ownership supports multiple touching Heads and rejects intersecting claims;
   runtime formation still permits only one active specimen.

5. Geometry lives in Essence custom data and Head full/network NBT. Loading never
   reapplies current definitions or current dimensions. Legacy Cow uses a frozen
   historical 1x2x1 profile with 0.9x1.4x0.9 body extents, without recapture.
   Generic legacy identities use registered EntityType dimensions, excluding config
   overrides. Unknown identities and malformed/version-unknown geometry diagnose
   invalid data. Malformed saved geometry is preserved, preventing a subsequent
   rewrite from silently turning corruption into legacy/default geometry.
   Extra Flesh from old oversized rods remains unowned; remove those extra cells
   or encode an explicitly larger geometry. It cannot safely infer old rod height
   without reintroducing the forbidden service/Flesh-based ownership rule.

6. Harness coordinates are horizontal bounding-box corners, deduplicated: 1x1 ->
   1, 1x2 -> 2, 1x5 -> 2, 3x3 -> 4, 2x4 -> 4. One complete common plane is required.
   Harness bottom block coordinate is specimen exclusive top Y + 0..4, meaning
   0..4 intervening spacer blocks. Multiple complete planes, incomplete corners,
   and unassociated extras fail. Harnesses may occupy the top face or interior.

7. A collapsed 1x1 footprint uses the four panel underside corners. Larger
   footprints take one outer attachment from each required Harness corner. The
   underside is Y+13/16. Both renderer and physical validation call the same
   HarnessStraps origins/curves implementation. Endpoints follow stage-specific
   saved body bounds and body-anchor fractions. Diagnostics identify the actual
   Harness from which an obstructed strap originates.

8. Drain candidates must be strictly below the occupied volume, from minY-1 to
   minY-5 inclusive, and within a horizontal footprint expanded by 2 cells on
   each side. Exactly one candidate is required; ambiguity fails explicitly.
   Drain may be off-center and offset from Head. Extra out-of-envelope Drains
   fail as unassociated. Drain may be bottom-shell or interior. Sewer remains a
   neighboring, same-height waste-service actor and follows the selected Drain.
   It may be bottom-shell or interior. Drain exposes no general fluid capability;
   WASTE production/output stays distinct from controller Culture buffers.

9. Interior validation allows air, collision-free water, collision-free Culture
   Slurry fluid type, recognized legal conduit blocks, and position-valid vat
   entities. Arbitrary solid blocks, unowned Flesh, and unapproved fluids fail.
   Product slurries currently have no placeable blocks; accepting Culture fluid
   type prepares validation without adding medium simulation. Bounded axial
   registration across acceptable medium connects suspended semantic parts to
   ZeroCore2; this is connectivity only, never ownership/service association.
   Visibility stops at the first non-medium cell and does not cross a shell.

10. Hard validity enforces occupied non-overlap, substrate correctness, service
    requirements, existing actor routes, and existing narrow conduit intersections.
    Wall/floor contact or zero comfort space is allowed. No productivity penalty,
    happiness score, medium chemistry, or multi-specimen scheduling was added.

11. SpecimenClearance exposes each independent shell, solid intrusion cells,
    actual formed conduit/service AABBs, neighboring saved body AABBs, and shared
    shell cells. One intervening cell produces shared shells; two cells separate
    them. This data supports later comfort scoring without a vat-wide spacing value.

12. MobAssembly now contains all associated Harnesses, Head, and selected Drain.
    Flesh count, render origin, body bounds, and attachments derive from Head
    geometry. Generic interior validation and part-position rules recognize spacer
    layouts and interior service endpoints. Interior Harness panels use UP and
    Drain/Sewer panels DOWN so their thin rendering matches resolved origins.

13. VatRenderDescriptor transports all Harness positions and resolved bounds.
    Head renderer uses shared strap curves and scales visuals to saved body extents
    if registered dimensions change. Juvenile body scaling and the existing Cow
    juvenile head-rig correction are preserved. Other entities use generic relative
    head anchors. Tool/feed/Blender eligibility and endpoints use resolved body
    bounds, not the old Head column or a Drain-derived feet position. Actual narrow
    conduit route intersection rules remain authoritative.

14. Final validation: .\gradlew.bat build and .\gradlew.bat runGameTestServer
    both passed. All 129 required headless GameTests passed (113 prior tests plus
    16 new geometry tests). Packaged render-resource checks passed. New
    SimulacrumGeometryGameTests group procedural/override cases, schema rejection,
    exact/touching/overlapping ownership, missing Flesh, persistence/migration,
    actual capture, definition-change stability, corner counts, service ambiguity,
    independent clearance, spacer formation, Sewer association, medium rejection,
    narrow route obstruction, resolved actor bounds, and interior endpoint panels.
    Existing passive lifecycle, Milk/Waste/Sword/Blender/Slurry, conduit/topology,
    Mixer, and juvenile geometry regression tests remain applicable.

15. No production continuous-column discovery remains. MobAssembly.harness() and
    VatRenderDescriptor.harnessPosition remain representative-endpoint compatibility
    accessors; straps consume the complete Harness list. The one-Harness curves
    overload is retained for low-level legacy 1x1 tests. Historical slice notes and
    unused localization keys retain old terminology; they no longer describe
    current ownership. Sewer remains a same-height neighboring waste tap; that is
    service reach, not a Head/Flesh/Drain column requirement.

Optional definition schema
--------------------------
  "simulacrum": {
    "dimensions": [2, 3, 1],
    "head_offset": [0, 2, 0]
  }
Either field may be omitted independently. Dimensions must be positive integers;
Head offset must be inside resolved dimensions. Arrays require exactly 3 integers.
There is exactly one Head because the cuboid's root offset is singular. Runtime
capture also checks Head-only overrides against actual captured coarse dimensions.

Manual acceptance (user-run only)
---------------------------------
A. Minimum Cow: Harness / Head / Flesh / Drain.
B. Spaced Cow: Harness / Air / Head / Flesh / Air / Drain.
Confirm both form, with no generic interior error. Test offset Drain with neighboring
Sewer; confirm WASTE transfer. Use a configured wider capture to confirm multiple
corner Harnesses and spread strap mounts. Confirm resolved render position,
juvenile/adult endpoints, and save/reload persistence. Also inspect interior
Harness/Drain panels if using a service plane inside the shell.

No client, smoke, or reload task was run. Nothing was published or released.
```
