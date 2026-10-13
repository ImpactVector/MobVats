package net.impactvector.mobvats.gamecontent.multiblock.conduit.client;

import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.*;

/** Chunk skin only. Formed plumbing is drawn once by the machine's coordinated renderer. */
public final class ConduitShellModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> FORMED = new ModelProperty<>();
    private static final ModelProperty<Boolean> MIXER = new ModelProperty<>();
    private final ConduitCladding cladding;
    private final BakedModel vatSkin, mixerSkin;

    public ConduitShellModel(
            BakedModel original,
            ConduitCladding cladding,
            BakedModel glass,
            BakedModel casing,
            BakedModel mixerGlass,
            BakedModel mixerCasing) {
        super(original);
        this.cladding = cladding;
        vatSkin = skin(original, cladding, glass, casing);
        mixerSkin = skin(original, cladding, mixerGlass, mixerCasing);
    }

    private static BakedModel skin(BakedModel original, ConduitCladding cladding, BakedModel glass, BakedModel casing) {
        return cladding == ConduitCladding.GLASS
                ? new VatGlassModel(original, glass)
                : cladding == ConduitCladding.CASING ? new VatCasingModel(original, casing) : original;
    }

    private BakedModel skin(ModelData data) {
        return Boolean.TRUE.equals(data.get(MIXER)) ? mixerSkin : vatSkin;
    }

    static ModelData assemblyData(ModelData data, boolean formed, boolean mixer) {
        return data.derive().with(FORMED, formed).with(MIXER, mixer).build();
    }

    public ModelData getModelData(BlockAndTintGetter world, BlockPos pos, BlockState state, ModelData data) {
        var controller = ConduitStructure.containing(Minecraft.getInstance().level, pos);
        var builder = assemblyData(
                        data,
                        controller != null,
                        state.getBlock() instanceof ConduitBlock conduit && conduit.shell(state) == ConduitShell.MIXER
                                || state.getBlock() instanceof FluidBufferBlock buffer
                                        && buffer.shell(state) == ConduitShell.MIXER
                                || controller
                                        instanceof
                                        net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer)
                .derive();
        if (controller != null) {
            var bounds = controller.getBoundingBox();
            builder.with(
                            VatGlassState.PROPERTY,
                            controller instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer
                                    ? VatGlassState.mixer(pos, bounds.getMin(), bounds.getMax())
                                    : VatGlassState.from(pos, bounds.getMin(), bounds.getMax()))
                    .with(VatCasingState.PROPERTY, VatCasingState.from(pos, bounds.getMin(), bounds.getMax()));
        }
        return builder.build();
    }

    public List<BakedQuad> getQuads(
            BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        if (!Boolean.TRUE.equals(data.get(FORMED))) return originalModel.getQuads(state, side, random, data, layer);
        return cladding == ConduitCladding.BARE ? List.of() : skin(data).getQuads(state, side, random, data, layer);
    }

    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return Boolean.TRUE.equals(data.get(FORMED))
                ? (cladding == ConduitCladding.BARE
                        ? ChunkRenderTypeSet.none()
                        : skin(data).getRenderTypes(state, random, data))
                : originalModel.getRenderTypes(state, random, data);
    }

    @Override
    public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(
            BlockState state, ModelData data, RenderType layer) {
        return skin(data).useAmbientOcclusion(state, data, layer);
    }
}
