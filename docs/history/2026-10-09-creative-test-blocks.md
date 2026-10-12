> Historical record. Original: `CREATIVE_TEST_BLOCKS.txt`. Current reference: [07-TESTING.md](../07-TESTING.md).
> Archive date basis: Git introduction author date 2026-10-09T14:37:09-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
Creative / operator test blocks
==============================
All five blocks are in the MobVats creative tab and available by /give using
mobvats:creative_drive, creative_item_source, creative_fluid_source,
creative_item_sink, and creative_fluid_sink. They have no survival recipes.
Their simple models reuse vanilla block textures; no new AI art is introduced.

Mount Creative Drive immediately above the Mixer Impeller. It supplies full
drive to a formed Mixer through MixerDrive, without resources or a GUI.
Recipe cadence and batch timing are unchanged. There is currently no functional
motor/spin-up implementation: undriven Mixers retain their existing processing
baseline until survival combustion/FE providers arrive. MixerDrive and the
controller's drive lookup are the shared extension point for those providers.

For all sources and sinks:
  Shift-right-click with an item / filled fluid container to sample it.
  Shift-right-click with an empty hand to clear.
  Ordinary right-click reports status without changing configuration.
Sampling does not consume the held item/container. Item count and fluid volume
are ignored; all identity components, including compound compositions, remain.
An invalid fluid sample leaves the previous configuration in place.

Sources are empty until configured, then supply indefinitely. Sinks accept all
when cleared and otherwise accept only the exact sampled item/fluid identity.
Accepted resources disappear; simulation changes nothing. They expose standard
NeoForge item/fluid handlers on every face. Use normal external transport to
connect to Vat/Mixer ports; internal conduits retain their existing endpoint and
physical storage rules. These blocks do not add a transport network or tick.

Configuration survives save/load, pick-block, drops, and replacement placement
through the existing versioned EndpointState item component.
CreativeDeviceGameTests covers sampling, infinite extraction/disposal, exact
component matching, clearing, portable persistence, Mixer drive attachment,
normal recipe timing, and standard capability transfer into/out of Mixer storage.
```
