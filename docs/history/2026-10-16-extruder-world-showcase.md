# Extruder manual test-world showcase

The generated creative world now includes four Extruder demonstrations west of spawn, separate from the existing rows that extend east. Implementation: `ExtruderWorldFixtures`, invoked from `ManualTestWorld`.

- Portion Meat: Extruder at (-24, -60, 0), rear buffer west, barrel below.
- Block Meat: Extruder at (-24, -60, 5).
- Portion Waste/Fertilizer: Extruder at (-24, -60, 10).
- Block Waste: Extruder at (-24, -60, 15).

Each has a 4,000 mB compatible fluid buffer behind its west face, an east-facing Extruder, and a vanilla barrel beneath. These are deterministic finite demonstrations rather than infinite creative providers. They exercise the actual buffer fluid capability and the Extruder's active downward insertion. World generation checks that every barrel receives an item. Refilling the source buffer allows replaying a demonstration.

Two small themed keeps are farther west, clear of the eastward machine-row extension:

- Meat Castle: center (-53, -60, 30), Meat Block walls, placed Meat fluid moat.
- Waste Castle: center (-82, -60, 30), Waste Block walls, placed Waste fluid moat.

Both keeps have four corner towers, crenellations, a bridge, and a stone-lined two-block-wide contained moat. The block moats use real MobVats fluid source blocks. They are decorative fixtures, not resource production rigs.

Regenerate with `./gradlew buildTestWorld` from `src/`. As with other manual-world generation, this resets the named generated save; client visual acceptance is still manual. Verify physical orientation, flowing fluid display, bridge clearance, and output contents in-game.
