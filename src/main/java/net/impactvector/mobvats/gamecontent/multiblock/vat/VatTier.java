package net.impactvector.mobvats.gamecontent.multiblock.vat;

import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial;
import net.minecraft.world.level.block.state.BlockState;

/** Structural capacity only; production and specimen activation are independent. */
public enum VatTier {
    T1("Copper", 9, 7, 9),
    T2("Iron", 9, 9, 9),
    T3("Blood Iron", 12, 12, 12);
    public final String material;
    public final int maxX, maxY, maxZ;

    VatTier(String material, int x, int y, int z) {
        this.material = material;
        maxX = x;
        maxY = y;
        maxZ = z;
    }

    public String id(VatPartType type) {
        return type.getSerializedName() + (this == T1 ? "" : "_" + name().toLowerCase(java.util.Locale.ROOT));
    }

    public CladdingMaterial cladding(boolean glass) {
        return CladdingMaterial.valueOf("VAT_" + name() + (glass ? "_GLASS" : "_CASING"));
    }

    public static VatTier of(BlockState state) {
        var installed = CladdingMaterial.installed(state);
        if (installed.shell() == net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitShell.VAT)
            return valueOf(installed.name().split("_")[1]);
        for (var tier : values())
            for (var type : new VatPartType[] {VatPartType.Casing, VatPartType.Glass, VatPartType.Controller})
                if (state.is(Content.block(tier, type))) return tier;
        return null;
    }
}
