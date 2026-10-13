package net.impactvector.mobvats.gamecontent.multiblock.conduit.client;

import java.lang.reflect.Proxy;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;

/** CPU test of the actual chunk-model ownership switch, including layered calls. */
public final class ConduitShellOwnershipTest {
    public static void verify() {
        var preview = List.of(new BakedQuad(new int[32], -1, Direction.UP, null, false));
        var original = (BakedModel) Proxy.newProxyInstance(
                BakedModel.class.getClassLoader(),
                new Class<?>[] {BakedModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getQuads" -> preview;
                    case "getRenderTypes" -> ChunkRenderTypeSet.of(RenderType.cutout());
                    default -> throw new AssertionError("Unexpected model call " + method.getName());
                });
        var shell = new ConduitShellModel(original, ConduitCladding.BARE, null, null, null, null);
        var random = RandomSource.create();
        for (boolean formed : new boolean[] {false, true, false, true, false})
            for (boolean mixer : new boolean[] {false, true}) {
                var data = ConduitShellModel.assemblyData(ModelData.EMPTY, formed, mixer);
                for (var layer : new RenderType[] {null, RenderType.cutout(), RenderType.translucent()}) {
                    var quads = shell.getQuads(null, null, random, data, layer);
                    if (formed ? !quads.isEmpty() : quads != preview)
                        throw new AssertionError("Conduit preview ownership or duplicate rendering changed");
                }
                if (formed && !shell.getRenderTypes(null, random, data).isEmpty())
                    throw new AssertionError("Formed bare conduit still schedules a chunk layer");
            }
        if (shell.getQuads(null, null, random, ModelData.EMPTY, null) != preview)
            throw new AssertionError("Initial placement lost preview");
        System.out.println(
                "PASS: actual bare chunk model suppresses all preview quads/layers while formed and recovers on disassembly.");
    }
}
