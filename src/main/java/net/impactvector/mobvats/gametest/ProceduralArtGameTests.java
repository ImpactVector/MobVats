package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class ProceduralArtGameTests {
    @GameTest(template="empty",timeoutTicks=20)
    public static void directionProjectionSurvivesConfigurationAndReload(GameTestHelper h) {
        var blocks=new java.util.ArrayList<net.minecraft.world.level.block.state.BlockState>();
        blocks.add(Content.state(VatPartType.ItemPort)); blocks.add(Content.state(VatPartType.FluidPort));
        for(var type:java.util.List.of(MixerPartType.ItemPort,MixerPartType.FluidPort,MixerPartType.ItemPort,MixerPartType.FluidPort,MixerPartType.FluidPort))blocks.add(MixerContent.state(type));
        var p=new BlockPos(1,1,1);
        for(var block:blocks) {
            h.setBlock(p,net.minecraft.world.level.block.Blocks.AIR);h.setBlock(p,block);
            var entity=h.getBlockEntity(p);var endpoint=(ConfigurableEndpoint)entity;
            for(var direction:java.util.List.of(IoDirection.Output,IoDirection.Input)) {
                endpoint.setIoDirection(direction);
                h.assertTrue(h.getBlockState(p).getValue(PortVisualState.OUTPUT)==direction.isOutput(),"Direction model projection differs: "+block);
                h.assertTrue(h.getBlockEntity(p)==entity,"Visual state replaced port entity");
            }
            endpoint.setIoDirection(IoDirection.Output);
            var tag=((ISyncableEntity)entity).syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),ISyncableEntity.SyncReason.FullSync);
            endpoint.setIoDirection(IoDirection.Input);
            ((ISyncableEntity)entity).syncDataFrom(tag,h.getLevel().registryAccess(),ISyncableEntity.SyncReason.FullSync);
            h.assertTrue(h.getBlockState(p).getValue(PortVisualState.OUTPUT),"Loaded direction did not update model");
            endpoint.setIoMode(it.zerono.mods.zerocore.lib.data.IoMode.Active);
            h.assertTrue(h.getBlockState(p).getValue(PortVisualState.OUTPUT),"I/O mode changed directional artwork");
        }
        h.succeed();
    }
}
