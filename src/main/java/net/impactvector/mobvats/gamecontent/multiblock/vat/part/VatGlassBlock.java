package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import it.zerono.mods.zerocore.base.multiblock.part.GlassBlock;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class VatGlassBlock extends GlassBlock<MultiblockVat, VatPartType> {
    public VatGlassBlock() {
        super(MultiblockPartProperties.create(VatPartType.Glass,
            GlassBlock.addGlassProperties(BlockBehaviour.Properties.of().strength(2.5f))));
    }
}
