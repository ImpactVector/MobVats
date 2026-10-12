> Historical record. Original: `SLICE6B_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
MobVats Slice 6B — Conduit rendering and endpoint geometry
October 4, 2026

1. Formed rendering architecture
   ConduitCuboidController compiles an immutable ConduitGeometry.Snapshot after
   the existing 6A topology compiler. NetworkUpdate synchronizes it through the
   ZeroCore reference part. Clients bake geometry only when the snapshot changes.
   Vat Head and Mixer Controller BERs draw one coordinated network per machine.
   ConduitShellModel provides chunk-rendered skins, using cached controller bounds
   at chunk rebuild time. No conduit BE, tick, per-frame graph traversal, or new
   resource transport behavior was introduced. Static meshes use identity-keyed
   weak caching; immutable occupied volumes are also cached.

2. Pipe profile
   Main bodies are square pipes, 6/16 block across. Square hubs and short orthogonal
   elbow segments join runs. Item flanges are 8/16 across. Fluid cuffs have a
   rounded-square contour made from three disjoint rectangles, arranged in pairs.
   Small endpoint branches are 3/16 across and use 5/16 terminal cuffs.

3. Internal / glass / casing behavior
   Formed bare cells suppress their construction cube and show only plumbing.
   Glass cladding uses the same bounds-selected continuous skin as ordinary glass;
   casing cladding uses the same 3/16 shell/frame geometry as ordinary casing.
   Pipe centers shift inward on every outward face, including edges and corners.
   The largest cuff clears the 3/16 shell by at least 1/64 block.
   Unformed blocks and inventory items retain their existing simple models.
   Mixer ordinary and conduit-clad casing now share thin formed geometry while
   retaining the existing iron texture. Mixer ordinary and conduit-clad glass
   share the existing MobVats continuous tank material. Mixer functional faces
   become 3/16 panels so service anchors terminate at their actual back faces;
   their original face textures remain on both broad sides, with iron edge strips.
   No artwork files were added, replaced, or redesigned.

4. Item / Fluid distinction
   Item: native casing-inner surface and a single square flange.
   Fluid: native casing-edge surface and paired rounded-square cuffs.
   Both use the native casing-face material for collars. Shape and ring count
   distinguish the families in addition to texture treatment. All referenced
   materials are packaged assets; construction models from 6A remain available.

5. TRUNK / TAP
   Same-resource adjacent nodes connect regardless of cladding or mode.
   Different-resource neighbors never connect. TRUNK has conduit plumbing only.
   TAP adds a small junction collar and actual endpoint branches. Endpoint
   attachments come only from endpoints recognized by the 6A topology compiler.
   Existing loading, actor selection, reservation, and generic-output rules remain.

6. Endpoint abstraction
   EndpointGeometry holds position, endpoint kind, inward normal,
   serviceConnectionAnchor, optional interactionOrigin, and a local interaction
   corridor. Coordinates are world-space blocks. Kinds distinguish passive ports,
   Tool, Feed, Blender, and Sewer mounts. Only a single shell face is supported,
   consistent with the existing endpoint placement rules.

7. Service vs interaction origins
   Interaction origins preserve the existing central mount, 3/16 + 0.01 behind
   the outer plane. Service anchors sit 3/16 + 1/64 behind the outer plane and
   6/16 tangentially away from the central mechanism. The tangential direction
   follows the adjacent conduit; inward-adjacent conduits use a fixed side mount.
   VatPresentation uses the explicit interaction origin for existing tools/feeds.
   No specimen target, tool grip, head-facing, or feeding-tube behavior changed.

8. Fittings
   Short fixed orthogonal branches join the pipe to the service anchor. The final
   cuff ends exactly on that anchor and extends toward the chamber. An inward
   adjacent TAP branches from the hub side rather than through the centered actor
   origin. Tested fittings avoid the local centered corridor on all six faces for
   Tool, Feed, Blender, and Sewer mounts. No dynamic avoidance or rerouting occurs.

9. Geometry API for Slice 6C
   ConduitGeometry.Geometry exposes snapshot(), pipeSegments(), endpointFittings(),
   and occupied(). Volume records expose owner cell, resource, material, and AABB.
   Fitting records expose EndpointGeometry and their exact occupied volumes.
   The renderer converts those same AABBs into outward-wound quads; 6C can consume
   the volumes directly without reverse-engineering vertices. Snapshots retain
   cladding, mode, six-direction connectivity, and compatible endpoint descriptors.
   Geometry and NBT round-trips are deterministic. No whole-cell obstruction hull
   substitutes for the narrow mesh. Geometry currently has no validity consumer.

10. Render tiers
   FULL and REDUCED draw cached complete plumbing and fittings.
   MINIMAL and CULLED omit plumbing. Existing specimen/shaft and mechanism tier
   behavior is preserved. CULLED returns before conduit work. Full multiblock AABB,
   nearest-AABB camera distance, configured BER view distance, and off-screen
   rendering behavior remain intact. No server/player-distance state was added.

11. Verification
   Final command: gradlew.bat --offline build runGameTestServer
   GRADLE_USER_HOME: C:/Projects/MobVats/.codex/gradle
   BUILD SUCCESSFUL; dedicated server exited normally, October 4 at 17:37:00.
   All 78 required GameTests passed: 71 Slice 1–6A tests + 7 new geometry tests.
   New coverage includes bare dimensions, retained cladding metadata, connected
   resource rules, TRUNK/valid/invalid TAP branches, anchor termination, local
   corridor separation on all six faces, isolated and connected corner clearance,
   deterministic volumes/NBT, structural visual distinction, controller network
   snapshots, cache reuse, and mode-change invalidation.
   Packaged resource/model/atlas checks and seven preserved texture hashes passed.
   No conventional unit-test sources exist; geometry assertions run headlessly.
   One existing deprecated mock-player API compile warning remains.
   An earlier run passed all assertions but stalled during chunk unload. It was
   terminated after thread inspection. Lifecycle geometry clearing now discards
   transient data without notifying/fetching the reference chunk. The final run
   passed all assertions and completed normal save/shutdown.
   No client, smoke/reload task, mouse/keyboard, or screenshot acceptance ran.

12. Manual visual checks — user
   Internal: bare narrow pipes; coherent elbows/tees/crosses; distinguish Item from
   Fluid by flange/cuff shape and surface detail.
   Glass: uninterrupted continuous glass; pipe behind it; no replacement cube.
   Casing: intact casing/frame; pipe behind it; intentional face/edge/corner runs.
   TAP: valid endpoints only; fitting at the backside of each shallow face; Tool
   arm and feeding tube mounts remain clear, including inward-adjacent TAPs.
   Mixer: iron casing retained; continuous formed glass; thin ports with fittings.
   Lifecycle: assemble/disassemble, change TRUNK/TAP, reload chunks/resources,
   and reconnect to a server; skins and plumbing refresh without stale cubes.
   Tiers: FULL/REDUCED show plumbing; MINIMAL/CULLED omit it at configured distances.

13. Geometry limits before Slice 6C
   Visual acceptance remains pending. No known failed deterministic geometry
   assertion remains. Pipe bodies can still physically intersect actors, straps,
   specimen, or Mixer paddles: 6C must test actual occupied volumes against those
   mechanisms and reject routes where required. No blocking/route invalidation
   exists in 6B. interactionCorridor() is a local fixed mount envelope, not the full
   curved feeding tube or swept tool/Blender path; 6C must use their complete
   physical geometry. AABBs intentionally describe the square/rounded-square
   geometry used here, not a future smooth cylinder. Collision shapes remain the
   6A placement behavior; render volumes do not alter gameplay collisions.

Prepared for the user-requested commit/push. Manual visual acceptance remains
pending. No publication or release was performed.
```
