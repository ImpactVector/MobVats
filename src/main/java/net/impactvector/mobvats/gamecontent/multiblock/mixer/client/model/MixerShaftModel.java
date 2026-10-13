package net.impactvector.mobvats.gamecontent.multiblock.mixer.client.model;

import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerShaftEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Controller owns the formed shaft mesh; retain construction and inventory appearances. */
public final class MixerShaftModel extends BakedModelWrapper<BakedModel> {
    public MixerShaftModel(BakedModel original) {
        super(original);
    }

    @Override
    public List<BakedQuad> getQuads(
            BlockState block, Direction side, RandomSource random, ModelData data, RenderType layer) {
        return Boolean.TRUE.equals(data.get(MixerShaftEntity.FORMED))
                ? List.of()
                : originalModel.getQuads(block, side, random, data, layer);
    }
}
