# Vat Controller GUI slice 2: specimen diorama

The selected specimen's existing 88-pixel observation window now contains a
schematic scene. The list, independent detail scrolling, biological/process
information, all actor assignments and tool icons, operator control, and
Diagnostics navigation retain their existing paths.

## Rendering and ownership

- Reuses `VatSpecimenPreview`, the appearance allowlist in `CapturedSpecimen`,
  and the snapshot's existing appearance string. No network or persistence
  schema change and no full entity NBT synchronization.
- The cached detached Mob is never registered with a level or ticked. UUID,
  species, adult flag, appearance, and level govern invalidation. Unchanged
  snapshots reuse it; closing the screen clears it.
- Uses the registered entity renderer, with fixed 145-degree head/body yaw,
  zero head pitch and a 12-degree observation pitch. No mouse input or clock
  drives the pose. Shadows and debug hitboxes are not submitted.
- `VatDioramaLayout` fits conservative projected dimensions into the specimen
  region. Adult dimensions cap juvenile enlargement, preserving smaller bodies
  and native juvenile proportions. General scale limit is 64 pixels per block.
- Two screen-space cables and small attachment tabs approximate upper-torso
  suspension. These are illustrative supports, without Harness geometry,
  world anchors, topology compilation or suspension simulation.
- Primary actor selection considers production Tool Port and Blender assignments,
  excludes tool-loading-only routes, and sorts by type priority (Tool Port first),
  packed position, operation and item identity. Position only breaks selection
  ties; it never affects the scene transform. Other assignments remain text.
- The actor uses the existing unclad baked construction block model through
  `renderSingleBlock`, with a separate scale capped at 30 pixels per block.
  Its north functional face points toward the viewer and the specimen at
  45 degrees, with a 20-degree observation pitch. Its reserved region cannot
  overlap the specimen's conservative projected region.
- Private pose stacks isolate both renderers. Lighting and dispatcher camera
  orientation are restored. Entity and actor failures are caught separately;
  an actor failure does not remove a working mob. Failed actor rendering is
  suppressed until the selected actor type changes or the screen reopens.
  Missing/invalid mob information uses the existing preview fallback while
  permitting the actor to render. Normal GUI text contains no exception traces.

## Automated coverage

`VatSpecimenMenuGameTests` adds assignment-order/primary-actor checks and pure
layout assertions for three viewport widths, chicken/cow/sheep dimensions,
very tall, wide and tiny dimensions, adults/juveniles, with/without actors.
These check positive bounded scales, containment, separate actor bounds,
juvenile relative size, and invalid-dimension rejection.

Existing menu GameTests cover UUID selection, species identity, sheep color and
shearing, juvenile/adult transitions, detached construction, unchanged snapshot
reuse, replacement and appearance invalidation, empty selection, close/reopen,
disconnection, snapshot transport and diagnostics.

Validation on October 16, 2026 used the canonical Windows Java 21 launcher:
`-Mode format`, formatting-diff review, then `-Mode validate`
(`spotlessCheck build runGameTestServer`). The full run succeeded in 7m 40s;
all 927 required GameTests passed, including the new diorama tests and existing
preview/menu lifecycle tests. CPU geometry/cache/buffer and packaged-resource
checks passed as part of the build. `git diff --check` passed. Existing deprecated
mock-player API warnings and log-rollover notices did not fail validation.

## Client acceptance (outstanding; user performed)

No Minecraft client was launched for this slice. Headless checks cannot verify
final model appearance, lighting, third-party renderer behavior or readability.

- [ ] Cow, sheep, chicken and other available supported specimens have the correct
      captured species and variants; only the selected specimen appears.
- [ ] Juveniles retain their native proportions and smaller size; maturation
      updates the preview. Red/other wool colors and sheared sheep match capture.
- [ ] Small, large, tall and wide specimens fit the observation window without
      clipping; both specimen and actor remain recognizable.
- [ ] Tool Port and Blender use their recognizable existing machinery models;
      the functional surface faces the viewer/specimen. Neither intersects or
      hides behind the mob. Tool icons remain available in assignment details.
- [ ] Multiple assignments display one stable primary actor. Missing production
      assignments display no invented actor; all assignments remain in text.
- [ ] Cables suggest suspension without covering the head or important features.
- [ ] Moving the cursor and waiting does not turn the body/head or rotate the
      scene. There are no player camera, rotation or zoom controls.
- [ ] Switching specimens, replacing one, refreshing snapshots, changing
      appearance, breaking/reforming the Vat and closing/reopening the menu
      update or clear the scene without stale mobs.
- [ ] At normal supported GUI scales and window sizes, list and detail scrolling
      remain independent. The observation window clips cleanly as it scrolls;
      all growth, production, happiness/yield, feeding/culture and assignments
      remain reachable. Operator control and every Diagnostics page still work.

## Limits

Suspension attachment is approximate. Model extents beyond an entity's declared
bounds (especially third-party appendages) may need visual follow-up; scissoring
always confines submissions to the observation window. Actor tools remain the
existing text/detail icons rather than added 3D equipment. The miniature uses
unclad construction geometry, independently of actual installation/cladding.
No world interior, Harness, Sump, animation, or physical actor layout is rendered.
This is a local development change; nothing is published.
