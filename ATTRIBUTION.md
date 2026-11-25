# Third-party attribution

## Research journal PoC

The journal selectively adapts the MIT-licensed page renderer factory, display
lifecycle, spread navigation, formatted component wrapping, and crafting layout
from [Modonomicon](https://github.com/klikli-dev/modonomicon/tree/324c92724bb3a5805216660c71f1a452f29f4c90),
branch `version/1.21.1`, commit `324c92724bb3a5805216660c71f1a452f29f4c90`.
Copyright 2022/2024 klikli-dev; paginated screen copyright 2024 DaFuqs.
Original SPDX notices remain in the adapted client classes. The complete MIT
permission notice ships in `META-INF/LICENSE-Modonomicon.txt`.

No upstream textures, fonts, sounds, Markdown implementation, platform adapters,
JEI integration, or third-party libraries are copied or bundled by this feature.
Its prototype PNGs are original algorithmic pixel primitives, reproducible with
`tools/journal/generate-prototypes.mjs`, under MobVats' MIT license. The item uses
Minecraft's built-in book sprite by resource reference, without copying it.
Dependency and asset scope: [journal implementation](docs/08-RESEARCH-JOURNAL.md).

## Multiblock infrastructure

Portions of MobVats' multiblock and I/O architecture are derived from or closely
modeled on Extreme Reactors 2 and ZeroCore2 by ZeroNoRyouki under the MIT License.

- Extreme Reactors 2: https://github.com/ZeroNoRyouki/ExtremeReactors2/tree/1.21
  Reference revision: f6cc65140a8abbfc87137f5c54fc641085f66048
- ZeroCore2: https://github.com/ZeroNoRyouki/ZeroCore2/tree/1.21
  Reference revision: 64d892b1ba099192a35bfa4ee08465540a2f12e7

The shared port lifecycle follows their boundary-entity/handler/controller
ownership split and lifecycle/neighbor refresh pattern. MobVats uses ZeroCore's
IoDirection, IoMode, FluidPortHandlerForge, FluidHandlerForwarder, and
IOPortBlockCapabilitySource directly as an external dependency. Resource roles,
selectors, staging, and machine transfer policy remain MobVats implementations.

Active boundary transfer follows XR2's controller-scheduled fluid distribution
and solid access-port ejection. MobVats rotates deterministic position order
with a per-port fluid budget. Its fluid adapter uses ZeroCore neighbor sources
and NeoForge simulated transfer because upstream active helpers reject `Both`.
Direct machine adjacency assigns transport to active output-side push, allowing
input-side pull from passive outputs. These adaptations are documented in
[resource routing](docs/03-RESOURCE-ROUTING.md); no upstream source is bundled or forked.

The following upstream multiblock MIT copyright and permission notice is
preserved. This third-party notice does not change MobVats' own license metadata.

MULTIBLOCK API LICENSE:

The MIT License (MIT)

Copyright (c) 2016-2017 "ZeroNoRyouki"

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
