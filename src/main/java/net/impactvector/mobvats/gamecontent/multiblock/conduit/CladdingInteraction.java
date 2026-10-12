package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit, single-block installation. Functional state stays on the same block/entity. */
public final class CladdingInteraction {
    public static ItemInteractionResult apply(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player){
        var material=CladdingMaterial.from(stack);
        if(!player.isShiftKeyDown() || material==CladdingMaterial.NONE || !state.hasProperty(CladdingMaterial.PROPERTY))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(state.getBlock() instanceof net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock block && !block.accepts(material))return ItemInteractionResult.FAIL;
        var old=CladdingMaterial.installed(state);
        if(level.isClientSide || old==material)return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if(!level.setBlock(pos,state.setValue(CladdingMaterial.PROPERTY,material),3))return ItemInteractionResult.FAIL;
        if(state.getBlock() instanceof net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock block)block.refreshAdapter(level,pos,level.getBlockState(pos));
        if(!player.getAbilities().instabuild)stack.shrink(1);
        if(old!=CladdingMaterial.NONE){var recovered=new ItemStack(old.item());if(!player.getInventory().add(recovered))player.drop(recovered,false);}
        ConduitStructure.changed(level,pos,true);
        FluidBufferPresentation.changed(level,pos);
        return ItemInteractionResult.SUCCESS;
    }
    private CladdingInteraction(){}
}
