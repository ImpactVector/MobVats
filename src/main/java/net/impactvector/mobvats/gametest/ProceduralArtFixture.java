package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.common.ConfigurableEndpoint;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Explicit operator command; excluded with all other development fixtures from the JAR. */
@EventBusSubscriber(modid="mobvats")
public final class ProceduralArtFixture {
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mobvatsArtFixture").requires(s->s.hasPermission(2)).executes(c->{
            var source=c.getSource();
            build(source.getLevel(),BlockPos.containing(source.getPosition()).offset(2,0,2));
            source.sendSuccess(()->net.minecraft.network.chat.Component.literal("Placed 12 x 5 x 9 production art fixture two blocks east/south. Connect Pipez to the exposed ports for external pipe acceptance."),false);
            return 1;
        }));
    }
    static void build(ServerLevel level,BlockPos o) {
        // A compact formed shell, adjacent glass, and an independent tiled construction wall.
        MachineFixtures.vat(level::setBlockAndUpdate,o,5,5,5);
        for(int x=0;x<5;x++)for(int y=0;y<3;y++)level.setBlockAndUpdate(o.offset(7+x,y,0),Content.state(VatPartType.Casing));
        int index=0;
        for(var type:java.util.List.of(VatPartType.ItemPort,VatPartType.FluidPort))for(boolean output:new boolean[]{false,true}){
            var p=o.offset(1+index++,2,4);
            // Fourth example stays separate from the shell corner.
            if(index==4)p=o.offset(7,1,4);
            level.setBlockAndUpdate(p,Content.state(type));
            ((ConfigurableEndpoint)level.getBlockEntity(p)).setIoDirection(output?it.zerono.mods.zerocore.lib.data.IoDirection.Output:it.zerono.mods.zerocore.lib.data.IoDirection.Input);
        }
        // Optional external pipe: use registered Pipez only when loaded; never add a runtime dependency.
        var pipe=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("pipez","item_pipe"));
        pipe.ifPresent(block->level.setBlockAndUpdate(o.offset(1,2,5),block.defaultBlockState()));
        level.setBlockAndUpdate(o.offset(8,1,4),Content.state(VatPartType.Glass));
    }
}
