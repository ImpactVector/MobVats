package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Startup and explicit server-thread reload. No client config or player-distance state. */
public final class PassiveDefinitionEvents {
    private static volatile MinecraftServer activeServer;

    public static PassiveMobDefinitionRegistry.Loaded reload(MinecraftServer server) {
        MobProfileDiscovery.discover(
                server,
                FMLPaths.CONFIGDIR.get().resolve("mobvats/mobs"),
                message -> LogUtils.getLogger().info("Mob profile discovery: {}", message));
        CultureProfiles.reload();
        var loaded = PassiveMobDefinitionRegistry.load(
                FMLPaths.CONFIGDIR.get().resolve("mobvats/mobs"),
                message -> LogUtils.getLogger().error("Passive mob definitions: {}", message));
        PassiveMobDefinitionRegistry.publish(loaded);
        net.impactvector.mobvats.gamecontent.multiblock.vat.MultiblockVat.profilesReloaded(server);
        LogUtils.getLogger()
                .info("Loaded {} passive mob definitions", loaded.definitions().size());
        return loaded;
    }

    public static void startup(ServerStartingEvent event) {
        activeServer = event.getServer();
        reload(activeServer);
    }

    public static void stopped(ServerStoppedEvent event) {
        if (activeServer == event.getServer()) activeServer = null;
    }

    public static void tagsUpdated(TagsUpdatedEvent event) {
        var server = activeServer;
        if (server == null || event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        // Enqueue after datapack replacement completes, even when the tag event fires on the server thread.
        server.tell(new net.minecraft.server.TickTask(server.getTickCount(), () -> {
            if (activeServer == server) reload(server);
        }));
    }

    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("mobvats")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload_passive_mobs").executes(context -> {
                            var loaded = reload(context.getSource().getServer());
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
