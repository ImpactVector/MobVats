# Procedural ports and Mixer frame visual correction

## Port regression and correction

The shared applied-cladding generator reduced each ordinary port to a centered
6-pixel socket cube. FixtureModel also omitted ordinary ports from its outward
orientation transform. The original procedural socket face therefore became a
small marking on a centered cube instead of readable exterior hardware.

Generated ports now comprise a neutral mechanical connector, a shallow exterior
socket faceplate, and (when clad) a casing-recipe mounting collar. The connector
uses the grayscale socket material; the fixed fluid blue `#168BFA` and item yellow
`#E3BC45` and input/output ticks remain palette-independent. Side and rear connector
surfaces use the neutral undirected socket material. Clad plates and collars use
the existing T1 verdigris palette, socket bevels and casing recesses/fasteners.

The north-authored connector spans Z=0..2.75 pixels, the faceplate 0.5..1,
and the collar 0.875..2. The plate occupies X/Y=2..14 and the collar 1..15,
leaving glass around the mount. The entire mechanism follows the actual outward
shell direction, including top/bottom faces. Its connector is flush with the casing; the plate and collar are recessed to
avoid overlap with attached pipes. Casing and glass skins use the existing
10-pixel service opening, avoiding coplanar shell/connector faces. It does not
acquire a rear mount.
Unformed and inventory presentations retain generated construction framing;
formed geometry removes that framing before adding the existing shell once.
Fixture model-data now carries the formed glass snapshot, avoiding fallback to
the complete construction model on the translucent pass.

## Mixer frame and height mapping

The existing two radial bars now form the upper members of two perpendicular
anchor frames: each has two descending outer members and a lower crossmember.
Very narrow reduced vessels retain one frame. Bars use the existing procedural
Mixer paddle metal; the six-pixel Shaft keeps its existing procedural material.
All frame meshes are cached with the Shaft and submitted under the same
`rotorAnimation().angle(partial)` pose. No animation clock, ticking entity or
lifecycle owner was added.

Coordinates are relative to the Impeller bottom. Upper bar centers remain at
Y=-0.5; the visual rim is Y=-0.625; maximum liquid is Y=-0.75. The existing
volume-to-pixel-layer cache is mapped proportionally between the interior floor
and this operating ceiling. The lowest bar has 1/128 block floor clearance.
Even the shallowest inset-floor vessel has lower frame vertices in the liquid
at full fill while its complete upper bars remain above both liquid and rim.

Frame reach retains the physical polygon apothem minus the existing arm
clearance and wall thickness. CPU tests check every actual mesh vertex's radial
envelope (which guarantees clearance for the continuous swept rotation), and
sample rotated vertices against the polygon every five degrees. Coverage includes
diameters 3/5/7/9, every supported radius reduction, depths 1/8, and both floor
insets. The physical polygon, obstruction rules, slot formula, storage, routing,
recipes, processing and formation calculations are unchanged.

## Validation and acceptance

Requested local build, deterministic procedural/provenance checks, shaft/frame
geometry checks, headless GameTests, AI-placeholder guard, release validation,
and whitespace verification were run. The GameTest suite passed all 766 required
tests. Public-release validation is a local check; nothing was published.

Run `gradlew.bat proceduralArtPreview` to regenerate
`build/procedural-art-preview/models.html`. Port comparisons show both directions,
bare and all four claddings, formed/construction geometry and front/rear views.
Mixer comparisons show diameters 3/5/9, empty/half/full liquid and 0/45-degree
frame angles using the actual exported renderer mesh.

The generated preview does not reproduce Minecraft lighting, glass blending or
fluid transparency sorting. In-game visual acceptance, including active/stopped
rotation and views through real glass, remains pending user client inspection.
No client was launched and no commit, push, release or deployment was performed.
