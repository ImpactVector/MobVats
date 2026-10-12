> Historical record. Original: `MIXER_FE_DRIVE_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
Mixer FE Drive
==============
Survival recipe: copper ingots around redstone / Mixer casing / redstone.
Uses vanilla copper-block texture. No new AI art.

Install on the top face, horizontally adjacent to the impeller. Exactly one
drive (of any kind) is allowed. All attachment checks use the shared drive rules.
Native NeoForge Capabilities.EnergyStorage.BLOCK accepts FE on all sides,
including an unsided query, into a receive-only 1,000,000 FE internal buffer.
It does not participate in item/fluid conduits or add energy conduits.
Buffer and fractional prepaid FE survive saves and portable block drops.

Server config: mobvats-mixer-energy.toml, mixerFePerCycle (1..100,000,000).
Default: 2,500 FE per 200 powered ticks. Current coal burns for 1,600 ticks,
so this initial default corresponds to 20,000 FE/coal; no coal conversion is
used by the FE implementation. Cycle means a 200-tick operating interval,
independent of recipe duration, activity and slot count.

Startup uses the existing STOPPED -> SPINNING_UP -> RUNNING state machine.
It costs slotCount * 200 powered ticks. Startup never advances recipes.
Running, including idle running, keeps paying the same configured rate.
Fractional costs are rounded up with prepaid credit carried forward, so each
200 successful drive ticks costs exactly mixerFePerCycle even for odd values.
Insufficient energy never partially pays a tick and resets startup; refilling
requires full startup again while preserving recipe progress.
No item/fluid routing changes and no Create integration.

Validation (October 9, 2026): build and packaged resource checks passed.
All 514 required headless GameTests passed, including 15 FE drive tests.
The full test server stalled afterward while saving/unloading chunks during
shutdown (Minecraft ChunkMap.scheduleUnload); test completion was verified
from the server log. The stalled development test processes were stopped.
```
