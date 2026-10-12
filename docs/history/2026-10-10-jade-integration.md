> Historical record. Original: `JADE_INTEGRATION.md`. Current reference: [01-ARCHITECTURE.md](../01-ARCHITECTURE.md).
> Archive date basis: Git introduction author date 2026-10-10T14:39:21-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Optional Jade inspection

Jade 15.10.6 for NeoForge 1.21.1 is a compile-only API dependency. It is installed
only in the operator-controlled `runClient` runtime. Normal runtime, dedicated
server and GameTest classpaths remain Jade-free. Published mod metadata has no
Jade dependency, and the JAR does not bundle Jade. Jade discovers
`MobVatsJadePlugin` through `@WailaPlugin`; MobVats initialization never loads it.
On multiplayer servers, these detailed snapshots require Jade on the server too.

The plugin only transports and renders `MachineInspection` snapshots. These read
existing formation errors, endpoint configuration, evaluated process results and
local inventory getters. Compound fluids reuse their hover names and the existing
bucket composition display. Casing and glass add no inspection text. Conduit
membership is retained during the normal `ConduitTopology` compilation; inspection
never walks or recompiles routes. Lists show at most six entries followed by a
count of omitted entries.

Fluid buffers report the hovered member's physical tank and capacity. Item ports
report their staged items; Mixer item ports show the resources exposed by that
port. Tool Ports show a current assigned specimen, definition-selected operation
and its last evaluated status, plus held input and local results/service tanks.
Other actors show their assignment, operation and local service contents. There
is currently no standalone item-buffer block to register.

Combustion drives retain a presentation-only copy of the consumed fuel and its
original burn duration across reloads. Neither field influences ignition or
processing. Older saves with burning fuel cannot recover its identity; inspection
labels that fuel unknown until the next ignition.

Automated validation:

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
git diff --check
```

`MachineInspectionGameTests` exercise real server blocks without linking Jade:
contents and non-mutation, compound display, validator errors, quiet glass,
compiled conduit membership, assigned Tool Port operations, and consumed fuel
presentation after reload.

Manual acceptance remains operator-controlled:

```powershell
.\gradlew.bat runClient
```

Verify normal vanilla Jade tooltips, then inspect buffer contents/filter/capacity,
both conduit modes and component counts, Tool Port target/input/operation/status,
port direction/filter, all three drives, formed Vat/Mixer controllers and an
unformed controller's existing formation reason. Check casing/glass remain quiet.
Use a large component to check endpoint truncation and multiple specimens to
check local assignments. No GUI or machine mechanics are added by this integration.
