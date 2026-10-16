# MobVats

MobVats is a Minecraft mod for constructing biological processing Vats and mechanical Mixers. Capture a configured mob with a syringe, encode its essence into a Head, and build a serviced multiblock around its physical specimen geometry. Mixers produce and filter process fluids for these installations.

The repository is an active development implementation, version 0.1.0. No public release or compatibility beyond the configured Minecraft version is asserted here. Existing experimental worlds may need regeneration after registry changes.

Implemented features include multiple independently scheduled specimens in one Vat, configurable capture and processing definitions, tool interactions and harvesting, culture and feeding, clean/dirty medium, transactional item/fluid routing, physical Fluid Buffers, configurable Mixer recipes, and creative, combustion and FE Mixer Drives. Mechanical functions use universal inventory items with explicitly applied Vat or Mixer T1 casing/glass. T2/T3 are procedural art previews; they are not gameplay tiers.

Mixer [manual Item and Fluid Meters](docs/MIXER_MANUAL_METERS.md) constrain exact ingredient quantities using sampled filters.
Optional [Mixer Blockers](docs/MIXER_BLOCKER.md) exclude sampled output products, including distinct slurry compositions.

Requirements: Minecraft **1.21.1**, NeoForge **21.1.252** for the development build, and external **ZeroCore2 1.21.1-2.4.18**. ZeroCore2 is mandatory and is not bundled. Build with Java 21 using the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

The local JAR is written to `build/libs/`. Public distribution requires the separate release-art validation described in the developer reference; a successful build does not authorize publication.

Start with the [technical documentation index](docs/README.md), [development setup](docs/06-DEVELOPMENT.md), and [testing workflows](docs/07-TESTING.md). Server configuration samples are in [docs/examples](docs/examples/README.md). Proposed features are tracked separately in [future designs](docs/design/FUTURE-FEATURES.md).

See [LICENSE](LICENSE) and [attribution](ATTRIBUTION.md) for rights and upstream notices.
