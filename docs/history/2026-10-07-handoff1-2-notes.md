> Historical record. Original: `HANDOFF1_2_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Git introduction author date 2026-10-07T13:55:29-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
Handoff 1.2 - Mixer visual vessel and first rotor
================================================

The controller renders an open vessel with a circular pixel-grid footprint,
transparent walls/base, and a distinct opaque lip below the top machinery layer.
The visual volume ends at the bottom of the actual contiguous placed Shaft chain.
One centered shaft and two crossing horizontal arms rotate together. Only the
first Shaft level below the Impeller has arms; no lower arms or vertical cage.

Geometry and lifecycle
----------------------
MixerSweep retains its structure-time obstruction BitSet as an immutable
MixerSweepMask. MixerAssembly persists/syncs that mask along with its existing
radius, depth, and volume count. Recipe capacity arithmetic is unchanged.
Legacy tags without a mask remain readable; normal server formation recompiles
the current physical geometry.

MixerRenderDescriptor derives tank boundaries from the cached circular mask,
excluding Shaft space, buffers, and conduit geometry. It merges vertical boundary
faces through depth and horizontal faces across rows. An open top keeps machinery
separate. Descriptor and GPU-facing meshes are rebuilt on geometry changes rather
than on individual frames. Identical network updates preserve descriptor identity.
Pause/disassembly clears presentation; reform builds a fresh descriptor even when
its dimensions match the previous structure.

Arms sit half a block below the Impeller. Their endpoints use the cached radius
minus square-section clearance. Cached obstacles crossing that first-level arm
band reduce the legal rotating reach; the vessel still depicts the full usable
mask. This conservative clearance prevents arms from sweeping through conduits,
buffers, shell panels, or ports. Ordinary unobstructed arms reach close to the
circular boundary with a small cross-section clearance. Formed static Shaft models are hidden to prevent
overlap with the rotating shaft; construction and inventory models are retained.

Animation and Handoff 2 seam
---------------------------
The presentation-only visualActive flag defaults to true on formed Mixers.
Client ticks integrate a smoothstep ramp over 40 ticks (2 seconds), starting at
zero angular velocity and reaching 1.8 degrees per tick. Operating speed then
stays at exactly 360 degrees per 200 ticks. Partial-tick interpolation and wrapped
phase keep rendering continuous. Animation advances independently of camera
distance and recipe progress.

MultiblockMixer.setVisualActive(false) stops rotation while leaving shaft and arms
deployed. This is a programmatic client/debug hook, not a player control or drive.
Handoff 2 should replace that temporary flag with actual drive/spin-up state.
No motor, energy requirement, slot processing, recipe parallelization, fluid
contents, or RPM-dependent chemistry was added.

Verification
------------
Gradle build and packaged render resources passed. All 443 required autonomous
headless GameTests passed, including six new MixerRenderingGameTests for cached
radius/depth/mask, machinery exclusion, short shafts, network persistence/cache
stability, break/reform/shortening, immutable mask identity, and rotation timing.
Existing Mixer capacity and processing tests remain in the passing suite.
Client rendering was not launched or manually tested.

Manual acceptance (user client session)
--------------------------------------
1. Form a glass-sided Mixer with a centered Impeller and one or more Shaft blocks.
   Confirm the circular vessel, visible lip, centered shaft, and first-level arms.
2. Compare a one-block Shaft chain with a deeper chain in the same-height shell.
   Confirm both vessel and shaft end at the placed depth; machinery stays above.
3. Place allowed conduits/buffers near the first arm level. Confirm vessel cutouts
   and rotor clearance; the arm reach can shorten to avoid an occupied volume.
4. Observe the two-second startup and full speed: one revolution takes ten seconds
   at 20 TPS. Arms remain deployed when debug activity is disabled.
5. Break the Impeller or a Shaft, then repair/reform. Confirm the coordinated
   rendering disappears while unformed and rebuilt geometry matches the new chain.
```
