package net.impactvector.mobvats.gamecontent.multiblock.conduit;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
/** Passive topology descriptor. There is no inventory, capability, BE or tick method. */
public final class ConduitBlock extends Block {
    public static final EnumProperty<ConduitMode> MODE=EnumProperty.create("mode",ConduitMode.class);
    private final ConduitResource resource;
    public ConduitBlock(ConduitResource resource){
        super(Properties.of().strength(2.5f).noOcclusion());this.resource=resource;
        registerDefaultState(stateDefinition.any().setValue(MODE,ConduitMode.TRUNK).setValue(CladdingMaterial.PROPERTY,CladdingMaterial.NONE));
    }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return cladding(state)==ConduitCladding.BARE?net.minecraft.world.phys.shapes.Shapes.empty():net.minecraft.world.phys.shapes.Shapes.block();}
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,BlockHitResult hit){
        return CladdingInteraction.apply(stack,state,level,pos,player);
    }
    public ConduitResource resource(){return resource;}
    public ConduitCladding cladding(BlockState state){return CladdingMaterial.installed(state).cladding();}
    public ConduitShell shell(BlockState state){return CladdingMaterial.installed(state).shell();}
    protected MapCodec<? extends Block> codec(){return simpleCodec(p->new ConduitBlock(resource));}
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(MODE,CladdingMaterial.PROPERTY);}
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level,BlockPos pos,BlockState state){
        return new net.minecraft.world.item.ItemStack(ConduitContent.item(resource,state.getValue(MODE)));
    }
    protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,level,pos,old,moving);
        if(!state.equals(old))ConduitStructure.changed(level,pos,!state.is(old.getBlock()) || CladdingMaterial.installed(state)!=CladdingMaterial.installed(old));
    }
    protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        super.onRemove(state,level,pos,next,moving);
        if(!state.equals(next))ConduitStructure.changed(level,pos,!next.is(state.getBlock()) || CladdingMaterial.installed(state)!=CladdingMaterial.installed(next));
    }
}
