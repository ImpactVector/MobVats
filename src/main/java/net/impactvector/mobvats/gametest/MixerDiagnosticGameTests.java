package net.impactvector.mobvats.gametest;

import com.mojang.authlib.GameProfile;
import it.zerono.mods.zerocore.lib.multiblock.validation.ValidationError;
import java.util.List;
import java.util.UUID;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerDiagnosticGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 2, 3),
            GLASS = new BlockPos(3, 3, 1),
            IMPELLER = new BlockPos(3, 4, 3);

    private static MultiblockMixer mixer(GameTestHelper h) {
        return ((AbstractMixerEntity) h.getBlockEntity(CONTROLLER))
                .getMultiblockController()
                .orElseThrow();
    }
    // Capture the actual block-interaction message without a client or network connection.
    private static String click(GameTestHelper h, BlockPos relative, boolean crouching) {
        String[] message = {null};
        var player =
                new ServerPlayer(
                        h.getLevel().getServer(),
                        h.getLevel(),
                        new GameProfile(UUID.randomUUID(), "mixer-diagnostics"),
                        ClientInformation.createDefault()) {
                    @Override
                    public void displayClientMessage(Component text, boolean actionBar) {
                        h.assertTrue(!actionBar, "Mixer report changed message channel");
                        message[0] = text.getString();
                    }
                };
        player.setShiftKeyDown(crouching);
        if (crouching) player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        player.giveExperiencePoints(15);
        var pos = h.absolutePos(relative);
        h.getLevel()
                .getBlockState(pos)
                .useWithoutItem(
                        h.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
        h.assertTrue(message[0] != null, "Mixer click did not report diagnostics");
        if (crouching)
            h.assertTrue(
                    player.totalExperience == (mixer(h).isAssembled() && relative.equals(CONTROLLER) ? 2 : 15),
                    "Diagnostic click changed XP deposit behavior");
        return message[0];
    }

    private static void formationOnly(GameTestHelper h, String report) {
        h.assertTrue(report.startsWith("Mixer unformed"), "Missing unformed status: " + report);
        for (var label : List.of(
                "Water:",
                "WASTE:",
                "XP:",
                "Plants:",
                "Recipe:",
                "Progress:",
                "Resources:",
                "Shaft depth:",
                "Effective diameter:",
                "Geometric mixing volume:",
                "Processing slots:"))
            h.assertTrue(!report.contains(label), "Unformed report leaked " + label + ": " + report);
        var reason =
                mixer(h).getLastError().map(e -> e.getChatMessage().getString()).orElse("");
        h.assertTrue(
                report.equals("Mixer unformed" + (reason.isBlank() ? "" : "\nReason: " + reason)),
                "Unformed report included unrelated diagnostics: " + report);
    }

    private static void runtime(GameTestHelper h, String report) {
        h.assertTrue(mixer(h).isAssembled(), "Runtime fixture failed formation");
        for (var label : List.of(
                "Mixer formed / active",
                "Water:",
                "WASTE:",
                "XP:",
                "Plants:",
                "Recipe:",
                "Progress:",
                "Resources:",
                "Shaft depth: 2",
                "Effective diameter:",
                "Geometric mixing volume:",
                "Processing slots:"))
            h.assertTrue(report.contains(label), "Formed report missing " + label + ": " + report);
        h.assertTrue(
                report.equals("Operator: enabled\n" + mixer(h).state().status(true) + "\nDrive: "
                        + mixer(h).operatingState() + " | Startup: " + mixer(h).startupTicks() + " / "
                        + mixer(h).spinUpTicks() + "\n" + mixer(h).capacityDiagnostics() + "\n"
                        + String.join("\n", mixer(h).conduitTopology().diagnostics())),
                "Formed storage/conduit report changed");
        h.assertTrue(!report.contains("Reason:"), "Formed report retained formation error");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void unformedClicksReportActualShaftValidationReason(GameTestHelper h) {
        MixerGameTests.rig(h);
        h.setBlock(IMPELLER.below(), Blocks.AIR);
        h.runAtTickTime(35, () -> {
            h.assertTrue(!mixer(h).isAssembled(), "Shaft gap formed");
            h.assertTrue(
                    mixer(h).getLastError()
                            .orElseThrow()
                            .getChatMessage()
                            .equals(Component.translatable("multiblock.mobvats.mixer_shaft_required")),
                    "Unexpected formation error");
            formationOnly(h, click(h, CONTROLLER, true));
            formationOnly(h, click(h, GLASS, false));
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void missingAndBlankReasonsFallBackToGenericUnformed(GameTestHelper h) {
        MixerGameTests.rig(h);
        h.setBlock(IMPELLER.below(), Blocks.AIR);
        h.runAtTickTime(35, () -> {
            var c = mixer(h);
            h.assertTrue(!c.isAssembled(), "Fallback fixture formed");
            c.setLastError((ValidationError) null);
            h.assertTrue(click(h, CONTROLLER, false).equals("Mixer unformed"), "Missing reason did not fall back");
            c.setLastError("");
            h.assertTrue(click(h, CONTROLLER, false).equals("Mixer unformed"), "Empty reason did not fall back");
            c.setLastError("   ");
            h.assertTrue(click(h, GLASS, false).equals("Mixer unformed"), "Blank reason did not fall back");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void formedBreakAndRepairSwitchClickDiagnostics(GameTestHelper h) {
        MixerGameTests.rig(h);
        boolean[] switched = {false, false};
        h.runAtTickTime(35, () -> {
            runtime(h, click(h, GLASS, false));
            runtime(h, click(h, CONTROLLER, true));
            h.assertTrue(mixer(h).state().experienceMb() == 260, "Crouch/controller click did not deposit XP");
            h.setBlock(IMPELLER.below(), Blocks.AIR);
        });
        // ZeroCore validates on its own cadence; check the very first unformed tick.
        for (long tick = 36; tick < 65; tick++)
            h.runAtTickTime(tick, () -> {
                if (!switched[0] && !mixer(h).isAssembled()) {
                    formationOnly(h, click(h, CONTROLLER, false));
                    switched[0] = true;
                }
            });
        h.runAtTickTime(65, () -> {
            h.assertTrue(switched[0], "Broken Mixer never switched to formation diagnostics");
            formationOnly(h, click(h, GLASS, false));
            h.setBlock(IMPELLER.below(), MixerContent.state(MixerPartType.Shaft));
        });
        for (long tick = 66; tick < 95; tick++)
            h.runAtTickTime(tick, () -> {
                if (!switched[1] && mixer(h).isAssembled()) {
                    runtime(h, click(h, CONTROLLER, false));
                    switched[1] = true;
                }
            });
        h.runAtTickTime(95, () -> {
            h.assertTrue(switched[1], "Repaired Mixer never restored runtime diagnostics");
            runtime(h, click(h, GLASS, false));
            h.succeed();
        });
    }
}
