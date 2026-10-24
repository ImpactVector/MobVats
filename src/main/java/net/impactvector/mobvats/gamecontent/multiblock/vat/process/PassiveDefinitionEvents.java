package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** Startup and explicit server-thread reload. No client config or player-distance state. */
public final class PassiveDefinitionEvents {
    public static PassiveMobDefinitionRegistry.Loaded reload() {
        CultureProfiles.reload();
        var loaded = PassiveMobDefinitionRegistry.load(
                FMLPaths.CONFIGDIR.get().resolve("mobvats/mobs"),
                message -> LogUtils.getLogger().error("Passive mob definitions: {}", message));
        PassiveMobDefinitionRegistry.publish(loaded);
        LogUtils.getLogger()
                .info("Loaded {} passive mob definitions", loaded.definitions().size());
        return loaded;
    }

    public static void startup(ServerAboutToStartEvent event) {
        reload();
    }

    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("mobvats")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload_passive_mobs").executes(context -> {
                            var loaded = reload();
                            context.getSource()
                                    .sendSuccess(
                                            () -> Component.literal("Loaded "
                                                    + loaded.definitions().size() + " passive definitions; "
                                                    + loaded.errors().size() + " diagnostics. See server log."),
                                            true);
                            return loaded.definitions().size();
                        }))
                        .then(Commands.literal("fluid_filter")
                                .then(Commands.argument(
                                                "position",
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                        .blockPos())
                                        .then(Commands.argument(
                                                        "selector",
                                                        com.mojang.brigadier.arguments.StringArgumentType.string())
                                                .executes(context -> {
                                                    var pos = net.minecraft.commands.arguments.coordinates
                                                            .BlockPosArgument.getLoadedBlockPos(context, "position");
                                                    var selector =
                                                            com.mojang.brigadier.arguments.StringArgumentType.getString(
                                                                    context, "selector");
                                                    if (selector.equals("none")) selector = "";
                                                    try {
                                                        var part = context.getSource()
                                                                .getLevel()
                                                                .getBlockEntity(pos);
                                                        if (part
                                                                instanceof
                                                                net.impactvector.mobvats.gamecontent.multiblock.conduit
                                                                                .FluidBufferEntity
                                                                        buffer) buffer.filter(selector);
                                                        else if (part
                                                                instanceof
                                                                net.impactvector.mobvats.gamecontent.multiblock.vat.part
                                                                                .VatFluidPortEntity
                                                                        port) port.filter(selector);
                                                        else if (part
                                                                instanceof
                                                                net.impactvector.mobvats.gamecontent.multiblock.mixer
                                                                                .part.MixerFluidPortEntity
                                                                        port) port.filter(selector);
                                                        else {
                                                            context.getSource()
                                                                    .sendFailure(
                                                                            Component.literal(
                                                                                    "Select a Fluid Buffer or Vat Fluid Port."));
                                                            return 0;
                                                        }
                                                        context.getSource()
                                                                .sendSuccess(
                                                                        () -> Component.literal(
                                                                                "Fluid filter updated at "
                                                                                        + pos.toShortString()),
                                                                        false);
                                                        return 1;
                                                    } catch (IllegalArgumentException error) {
                                                        context.getSource()
                                                                .sendFailure(Component.literal(error.getMessage()));
                                                        return 0;
                                                    }
                                                })))));
    }

    private PassiveDefinitionEvents() {}
}
