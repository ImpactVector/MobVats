package net.impactvector.mobvats.gamecontent.creative;

import java.util.EnumMap;
import net.impactvector.mobvats.gamecontent.Content;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.*;

public final class CreativeContent {
    private static final EnumMap<CreativeDeviceType,DeferredBlock<Block>> BLOCKS=new EnumMap<>(CreativeDeviceType.class);
    static {for(var type:CreativeDeviceType.values()){
        if(type==CreativeDeviceType.DRIVE)continue; // Registered as a Mixer part, preserving creative_drive IDs.
        var block=Content.BLOCKS.<Block>register(type.id(),()->new CreativeDeviceBlock(type));BLOCKS.put(type,block);
        Content.ITEMS.registerSimpleBlockItem(block);
    }}
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CreativeEndpointEntity>> ENDPOINT=Content.ENTITIES.register("creative_endpoint",()->BlockEntityType.Builder.of(CreativeEndpointEntity::new,BLOCKS.entrySet().stream().filter(e->e.getKey()!=CreativeDeviceType.DRIVE).map(e->e.getValue().get()).toArray(Block[]::new)).build(null));
    public static void initialize(){}
    public static Block block(CreativeDeviceType type){return type==CreativeDeviceType.DRIVE?net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.block(net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.Drive):BLOCKS.get(type).get();}
    private CreativeContent(){}
}
