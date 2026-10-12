package net.impactvector.mobvats.gamecontent.multiblock.mixer;
import java.util.EnumMap;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.*;
public final class MixerContent {
    private static final EnumMap<MixerPartType,DeferredBlock<Block>> BLOCKS=new EnumMap<>(MixerPartType.class);
    private static final EnumMap<MixerPartType,DeferredHolder<BlockEntityType<?>,BlockEntityType<AbstractMixerEntity>>> ENTITIES=new EnumMap<>(MixerPartType.class);
    public static void initialize(){for(var type:MixerPartType.values()){
        var shared=net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.shared(type);
        if(shared!=null){ENTITIES.put(type,Content.ENTITIES.register(type.getSerializedName(),()->BlockEntityType.Builder.<AbstractMixerEntity>of((pos,state)->type.createTileEntity(state,pos),Content.block(shared)).build(null)));continue;}
        var block=Content.BLOCKS.<Block>register(type.getSerializedName(),() -> new MixerPartBlock(type));BLOCKS.put(type,block);if(type==MixerPartType.FluidVoid || type==MixerPartType.PullPump || type==MixerPartType.PushPump)Content.ITEMS.register(type.getSerializedName(),()->new net.impactvector.mobvats.gamecontent.item.ItemBoundary(block.get()));else if(type==MixerPartType.Casing || type==MixerPartType.Glass)Content.ITEMS.register(type.getSerializedName(),()->new net.impactvector.mobvats.gamecontent.item.CladdingBlockItem(block.get()));else Content.ITEMS.registerSimpleBlockItem(block);
        ENTITIES.put(type,Content.ENTITIES.register(type.getSerializedName(),() -> BlockEntityType.Builder.<AbstractMixerEntity>of((pos,state)->type.createTileEntity(state,pos),block.get()).build(null)));
    }}
    public static Block block(MixerPartType type){var shared=net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.shared(type);return shared==null?BLOCKS.get(type).get():Content.block(shared);}
    public static net.minecraft.world.level.block.state.BlockState state(MixerPartType type){var block=block(type);var state= block instanceof net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock?block.defaultBlockState().setValue(net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.PROPERTY,net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.MIXER_T1_CASING):block.defaultBlockState();return state;}
    public static BlockEntityType<AbstractMixerEntity> entityType(MixerPartType type){return ENTITIES.get(type).get();}
    private MixerContent(){}
}
