package net.impactvector.mobvats.integration.jade;

import net.impactvector.mobvats.gamecontent.multiblock.common.MachineInspection;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;

/** Loaded exclusively by Jade's annotation discovery. Main mod initialization never references it. */
@WailaPlugin
public final class MobVatsJadePlugin implements IWailaPlugin {
    @Override public void register(IWailaCommonRegistration registration) {
        for(var type:new Class<?>[]{AbstractVatEntity.class,AbstractMixerEntity.class,FluidBufferEntity.class,ConduitBlock.class})
            registration.registerBlockDataProvider(Inspection.INSTANCE,type);
    }
    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Inspection.INSTANCE,VatPartBlock.class);
        registration.registerBlockComponent(Inspection.INSTANCE,MixerPartBlock.class);
        registration.registerBlockComponent(Inspection.INSTANCE,FluidBufferBlock.class);
        registration.registerBlockComponent(Inspection.INSTANCE,ConduitBlock.class);
    }
    public enum Inspection implements IBlockComponentProvider,IServerDataProvider<BlockAccessor> {
        INSTANCE;
        private static final String KEY="mobvats:inspection";
        @Override public ResourceLocation getUid(){return ResourceLocation.fromNamespaceAndPath("mobvats","inspection");}
        @Override public void appendServerData(CompoundTag data,BlockAccessor accessor) {
            var lines=new ListTag();
            for(var line:MachineInspection.lines(accessor.getLevel(),accessor.getPosition()))
                lines.add(StringTag.valueOf(Component.Serializer.toJson(line,accessor.getLevel().registryAccess())));
            data.put(KEY,lines);
        }
        @Override public void appendTooltip(ITooltip tooltip,BlockAccessor accessor,IPluginConfig config) {
            var lines=accessor.getServerData().getList(KEY,Tag.TAG_STRING);
            for(int i=0;i<Math.min(96,lines.size());i++) {
                var line=Component.Serializer.fromJson(lines.getString(i),accessor.getLevel().registryAccess());
                if(line!=null)tooltip.add(line);
            }
        }
    }
}
