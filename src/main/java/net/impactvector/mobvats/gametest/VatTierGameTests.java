package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.*;

/** Actual assembled shells, including passive bridges; no synthetic validator substitutes. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class VatTierGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1), H = O.offset(1, 2, 1), WALL = O.offset(0, 1, 1);

    private static VatHeadEntity head(GameTestHelper h) {
        return (VatHeadEntity) h.getBlockEntity(H);
    }

    private static MultiblockVat vat(GameTestHelper h) {
        return head(h).getMultiblockController().orElseThrow();
    }

    private static void build(GameTestHelper h, VatTier tier, int x, int y, int z, boolean chicken) {
        for (int a = 0; a < x; a++)
            for (int b = 0; b < y; b++)
                for (int c = 0; c < z; c++) {
                    boolean shell = a == 0 || a == x - 1 || b == 0 || b == y - 1 || c == 0 || c == z - 1;
                    h.setBlock(
                            O.offset(a, b, c),
                            shell
                                    ? Content.block(tier, VatPartType.Casing).defaultBlockState()
                                    : Blocks.AIR.defaultBlockState());
                }
        h.setBlock(O.offset(1, 1, 0), Content.block(tier, VatPartType.Controller));
        h.setBlock(WALL, Content.block(tier, VatPartType.Glass));
        var hp = chicken ? H.below() : H;
        h.setBlock(hp, Content.state(VatPartType.Head));
        h.setBlock(
                hp.above(),
                Content.state(VatPartType.Harness).setValue(CladdingMaterial.PROPERTY, tier.cladding(false)));
        h.setBlock(
                O.offset(1, 0, 1),
                Content.state(VatPartType.Sump).setValue(CladdingMaterial.PROPERTY, tier.cladding(false)));
        if (!chicken) h.setBlock(hp.below(), Content.state(VatPartType.Flesh));
        ((VatHeadEntity) h.getBlockEntity(hp))
                .encode(ResourceLocation.withDefaultNamespace(chicken ? "chicken" : "cow"));
    }

    private static void size(GameTestHelper h, VatTier tier, int x, int y, int z, boolean allowed) {
        build(h, tier, x, y, z, false);
        h.runAtTickTime(40, () -> {
            h.assertTrue(
                    vat(h).isAssembled() == allowed,
                    "Size " + tier + " " + x + "x" + y + "x" + z + ": " + vat(h).getLastError());
            if (allowed) h.assertTrue(vat(h).structuralTier() == tier, "Controller tier lost");
            h.succeed();
        });
    }

    private static void mismatch(GameTestHelper h, BlockPos pos, BlockState wrong) {
        build(h, VatTier.T2, 5, 4, 5, false);
        var correct = h.getBlockState(pos);
        h.setBlock(pos, wrong);
        h.runAtTickTime(40, () -> {
            var c = vat(h);
            var error = c.getLastError().orElseThrow();
            h.assertTrue(!c.isAssembled(), "Mixed shell formed");
            var controllerPos = O.offset(1, 1, 0);
            var expected = pos.equals(controllerPos) ? O : pos;
            h.assertTrue(error.getPosition().equals(h.absolutePos(expected)), "Wrong diagnostic position: " + error);
            h.assertTrue(
                    error.getChatMessage()
                            .getString()
                            .contains(pos.equals(controllerPos) ? "expected T3 (Blood Iron)" : "expected T2 (Iron)"),
                    "Expected tier/material omitted: " + error);
            h.setBlock(pos, correct);
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(vat(h).isAssembled(), "Repair failed: " + vat(h).getLastError());
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void maximumT1(GameTestHelper h) {
        size(h, VatTier.T1, 9, 7, 9, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rectangularT1(GameTestHelper h) {
        size(h, VatTier.T1, 3, 4, 9, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100, rotationSteps = 1)
    public static void rotatedRectangleT1(GameTestHelper h) {
        size(h, VatTier.T1, 9, 4, 3, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tooWideT1(GameTestHelper h) {
        size(h, VatTier.T1, 10, 4, 3, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tooTallT1(GameTestHelper h) {
        size(h, VatTier.T1, 3, 8, 3, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tooDeepT1(GameTestHelper h) {
        size(h, VatTier.T1, 3, 4, 10, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void universalServicesT1(GameTestHelper h) {
        build(h, VatTier.T1, 5, 4, 5, false);
        var tool = Content.state(VatPartType.ToolPort).setValue(CladdingMaterial.PROPERTY, VatTier.T1.cladding(false));
        h.setBlock(WALL, tool);
        h.setBlock(
                O.offset(4, 1, 1),
                Content.state(VatPartType.Blender).setValue(CladdingMaterial.PROPERTY, VatTier.T1.cladding(false)));
        h.setBlock(
                O.offset(2, 1, 4),
                Content.state(VatPartType.ItemPort).setValue(CladdingMaterial.PROPERTY, VatTier.T1.cladding(true)));
        h.runAtTickTime(40, () -> {
            h.assertTrue(vat(h).isAssembled(), "Universal services rejected: " + vat(h).getLastError());
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void maximumT2(GameTestHelper h) {
        size(h, VatTier.T2, 9, 9, 9, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rectangularT2(GameTestHelper h) {
        size(h, VatTier.T2, 3, 4, 9, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100, rotationSteps = 1)
    public static void rotatedRectangleT2(GameTestHelper h) {
        size(h, VatTier.T2, 9, 4, 3, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tooWideT2(GameTestHelper h) {
        size(h, VatTier.T2, 10, 4, 3, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tooTallT2(GameTestHelper h) {
        size(h, VatTier.T2, 3, 10, 3, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tooDeepT2(GameTestHelper h) {
        size(h, VatTier.T2, 3, 4, 10, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void universalServicesT2(GameTestHelper h) {
        build(h, VatTier.T2, 5, 4, 5, false);
        var tool = Content.state(VatPartType.ToolPort).setValue(CladdingMaterial.PROPERTY, VatTier.T2.cladding(false));
        h.setBlock(WALL, tool);
        h.setBlock(
                O.offset(4, 1, 1),
                Content.state(VatPartType.Blender).setValue(CladdingMaterial.PROPERTY, VatTier.T2.cladding(false)));
        h.setBlock(
                O.offset(2, 1, 4),
                Content.state(VatPartType.ItemPort).setValue(CladdingMaterial.PROPERTY, VatTier.T2.cladding(true)));
        h.runAtTickTime(40, () -> {
            h.assertTrue(vat(h).isAssembled(), "Universal services rejected: " + vat(h).getLastError());
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void maximumT3(GameTestHelper h) {
        size(h, VatTier.T3, 12, 12, 12, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rectangularT3(GameTestHelper h) {
        size(h, VatTier.T3, 3, 4, 12, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100, rotationSteps = 1)
    public static void rotatedRectangleT3(GameTestHelper h) {
        size(h, VatTier.T3, 12, 4, 3, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tooWideT3(GameTestHelper h) {
        size(h, VatTier.T3, 13, 4, 3, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tooTallT3(GameTestHelper h) {
        size(h, VatTier.T3, 3, 13, 3, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tooDeepT3(GameTestHelper h) {
        size(h, VatTier.T3, 3, 4, 13, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void universalServicesT3(GameTestHelper h) {
        build(h, VatTier.T3, 5, 4, 5, false);
        var tool = Content.state(VatPartType.ToolPort).setValue(CladdingMaterial.PROPERTY, VatTier.T3.cladding(false));
        h.setBlock(WALL, tool);
        h.setBlock(
                O.offset(4, 1, 1),
                Content.state(VatPartType.Blender).setValue(CladdingMaterial.PROPERTY, VatTier.T3.cladding(false)));
        h.setBlock(
                O.offset(2, 1, 4),
                Content.state(VatPartType.ItemPort).setValue(CladdingMaterial.PROPERTY, VatTier.T3.cladding(true)));
        h.runAtTickTime(40, () -> {
            h.assertTrue(vat(h).isAssembled(), "Universal services rejected: " + vat(h).getLastError());
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oneCellChickenMinimum(GameTestHelper h) {
        build(h, VatTier.T1, 3, 3, 3, true);
        h.runAtTickTime(40, () -> {
            var head = (VatHeadEntity) h.getBlockEntity(H.below());
            h.assertTrue(
                    head.isMachineAssembled(),
                    "3x3x3 chicken rejected: " + head.getMultiblockController().flatMap(MultiblockVat::getLastError));
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void normalCowMinimum(GameTestHelper h) {
        size(h, VatTier.T1, 3, 4, 3, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void controllerMismatch(GameTestHelper h) {
        mismatch(
                h,
                O.offset(1, 1, 0),
                Content.block(VatTier.T3, VatPartType.Controller).defaultBlockState());
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void casingCornerMismatch(GameTestHelper h) {
        mismatch(h, O, Content.block(VatTier.T1, VatPartType.Casing).defaultBlockState());
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void casingEdgeMismatch(GameTestHelper h) {
        mismatch(
                h,
                O.offset(0, 1, 0),
                Content.block(VatTier.T3, VatPartType.Casing).defaultBlockState());
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void casingFaceMismatch(GameTestHelper h) {
        mismatch(h, WALL, Content.block(VatTier.T1, VatPartType.Casing).defaultBlockState());
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void glassMismatch(GameTestHelper h) {
        mismatch(h, WALL, Content.block(VatTier.T3, VatPartType.Glass).defaultBlockState());
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void toolCladdingMismatch(GameTestHelper h) {
        mismatch(
                h,
                WALL,
                Content.state(VatPartType.ToolPort)
                        .setValue(CladdingMaterial.PROPERTY, CladdingMaterial.VAT_T1_CASING));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void conduitCladdingMismatch(GameTestHelper h) {
        mismatch(
                h,
                WALL,
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING)
                        .setValue(CladdingMaterial.PROPERTY, CladdingMaterial.VAT_T3_CASING));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bufferCladdingMismatch(GameTestHelper h) {
        mismatch(
                h,
                WALL,
                FluidBufferContent.blocks()
                        .iterator()
                        .next()
                        .defaultBlockState()
                        .setValue(CladdingMaterial.PROPERTY, CladdingMaterial.VAT_T1_CASING));
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void replacementRetainsInventoryAndRevalidates(GameTestHelper h) {
        build(h, VatTier.T2, 5, 4, 5, false);
        h.setBlock(
                WALL,
                Content.state(VatPartType.ItemPort)
                        .setValue(CladdingMaterial.PROPERTY, CladdingMaterial.VAT_T2_CASING));
        var port = (VatItemPortEntity) h.getBlockEntity(WALL);
        port.staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 7));
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        h.runAtTickTime(35, () -> {
            h.assertTrue(vat(h).isAssembled(), "Initial formation failed");
            CladdingInteraction.apply(
                    new ItemStack(CladdingMaterial.VAT_T3_GLASS.item()),
                    h.getBlockState(WALL),
                    h.getLevel(),
                    h.absolutePos(WALL),
                    player);
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(!vat(h).isAssembled(), "Wrong cladding did not disassemble");
            h.assertTrue(
                    h.getBlockEntity(WALL) == port
                            && port.staging().getStackInSlot(0).getCount() == 7,
                    "Cladding lost entity/inventory");
            CladdingInteraction.apply(
                    new ItemStack(CladdingMaterial.VAT_T2_CASING.item()),
                    h.getBlockState(WALL),
                    h.getLevel(),
                    h.absolutePos(WALL),
                    player);
        });
        h.runAtTickTime(105, () -> {
            h.assertTrue(vat(h).isAssembled(), "Matching replacement did not reform");
            h.assertTrue(port.staging().getStackInSlot(0).getCount() == 7, "Reformation lost inventory");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void registrationsAndCladdingRecovery(GameTestHelper h) {
        var tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(
                ResourceLocation.fromNamespaceAndPath("mobvats", "mobvats"));
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess()));
        for (var tier : VatTier.values()) {
            for (var type : new VatPartType[] {VatPartType.Controller, VatPartType.Casing, VatPartType.Glass}) {
                var b = Content.block(tier, type);
                h.assertTrue(
                        b.asItem() != Items.AIR && Content.entityType(type).isValid(b.defaultBlockState()),
                        "Missing structural registration " + tier + type);
                h.assertTrue(
                        tab.getDisplayItems().stream().anyMatch(stack -> stack.is(b.asItem())),
                        "Missing Creative item " + tier + type);
            }
            for (boolean glass : new boolean[] {false, true}) {
                var m = tier.cladding(glass);
                h.assertTrue(CladdingMaterial.from(new ItemStack(m.item())) == m, "Material identity lost");
                var p = O.offset(0, 1, 0);
                var state = ConduitContent.state(ConduitResource.ITEM, ConduitCladding.BARE)
                        .setValue(CladdingMaterial.PROPERTY, m);
                h.setBlock(p, state);
                var drops = state.getDrops(new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                        .withParameter(
                                net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                                net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(p)))
                        .withParameter(
                                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, state)
                        .withParameter(
                                net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,
                                ItemStack.EMPTY));
                h.assertTrue(
                        drops.stream().anyMatch(stack -> stack.is(m.item()) && stack.getCount() == 1),
                        "Cladding loot lost " + m);
            }
        }
        h.succeed();
    }

    private static void pauseAndRestore(GameTestHelper h, VatTier tier) {
        build(h, tier, 5, 4, 5, false);
        var saved = new net.minecraft.nbt.CompoundTag();
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            h.assertTrue(c.isAssembled(), "Tier did not assemble before pause");
            c.setMachineActive(false);
            c.processEngine().state(h.absolutePos(H)).growth().elapsed = 123;
            c.processEngine().save(saved, h.getLevel().registryAccess());
            var controller = (VatControllerEntity) h.getBlockEntity(O.offset(1, 1, 0));
            var data = controller.saveWithFullMetadata(h.getLevel().registryAccess());
            controller.onChunkUnloaded();
            h.assertTrue(c.isPaused(), "Tier did not pause");
            controller.loadWithComponents(data, h.getLevel().registryAccess());
            controller.clearRemoved();
        });
        h.runAtTickTime(80, () -> {
            var c = vat(h);
            var after = new net.minecraft.nbt.CompoundTag();
            c.processEngine().save(after, h.getLevel().registryAccess());
            h.assertTrue(
                    c.isAssembled() && c.structuralTier() == tier && !c.isMachineActive(),
                    "Reload lost tier/formation/operator state: " + c.getLastError());
            h.assertTrue(saved.equals(after), "Tier reload changed saved process state");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ironChunkPauseAndRestore(GameTestHelper h) {
        pauseAndRestore(h, VatTier.T2);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bloodIronChunkPauseAndRestore(GameTestHelper h) {
        pauseAndRestore(h, VatTier.T3);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bloodIronDoesNotUnlockHostiles(GameTestHelper h) {
        build(h, VatTier.T3, 5, 4, 5, false);
        var zombie = ResourceLocation.withDefaultNamespace("zombie");
        head(h).encode(zombie);
        h.runAtTickTime(40, () -> {
            var definition =
                    net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveMobDefinitionRegistry.resolve(
                            zombie);
            var profile =
                    net.impactvector.mobvats.gamecontent.multiblock.vat.process.MobProcessRegistry.DEFAULT.resolve(
                            zombie);
            h.assertTrue(vat(h).isAssembled(), "Existing representation-only hostile behavior changed");
            h.assertTrue(
                    !definition.capture().enabled() && profile.definitions().isEmpty(),
                    "Higher tier unlocked hostile capture/processing");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cowStillRequiresTwoInteriorVerticalCells(GameTestHelper h) {
        build(h, VatTier.T1, 3, 3, 3, true);
        var head = (VatHeadEntity) h.getBlockEntity(H.below());
        head.encode(ResourceLocation.withDefaultNamespace("cow"));
        h.runAtTickTime(40, () -> {
            h.assertTrue(!head.isMachineAssembled(), "Cow geometry was loosened for a one-cell Vat");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void harnessCladdingMismatch(GameTestHelper h) {
        mismatch(
                h,
                H.above(),
                Content.state(VatPartType.Harness).setValue(CladdingMaterial.PROPERTY, CladdingMaterial.VAT_T1_CASING));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sumpCladdingMismatch(GameTestHelper h) {
        mismatch(
                h,
                O.offset(1, 0, 1),
                Content.state(VatPartType.Sump).setValue(CladdingMaterial.PROPERTY, CladdingMaterial.VAT_T3_CASING));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void interiorServicesDoNotSelectShellTier(GameTestHelper h) {
        build(h, VatTier.T3, 5, 4, 5, false);
        h.setBlock(O.offset(3, 1, 3), Content.state(VatPartType.ToolPort));
        h.setBlock(O.offset(3, 2, 3), ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE));
        h.runAtTickTime(40, () -> {
            h.assertTrue(
                    vat(h).isAssembled() && vat(h).structuralTier() == VatTier.T3,
                    "Interior service selected a shell tier: " + vat(h).getLastError());
            h.succeed();
        });
    }
}
