package net.impactvector.mobvats.block;

import com.ibm.icu.util.Output;
import it.zerono.mods.zerocore.lib.block.IMultiblockPartType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum PartType implements IMultiblockPartType {
    VatGlass("vatGlass", 1),
    VatCasing("vatCasing", 2),
    VatController("vatController", 3),
    VatHarness("vatHarness", 4),
    VatHead("vatHead", 5),
    VatFlesh("vatFlesh", 6),
    VatPowerPort("vatPowerPort", 7),
    VatItemInputPort("vatItemInputPort", 8),
    VatItemOutputPort("vatItemOutputPort", 9),
    VatCreativeInputPort("", 0);

    public final String oreDictionaryName;
    public final int meta;
    private final String _name = this.name().toLowerCase();

    private PartType(String oreDictionaryName, int meta) {
        this.oreDictionaryName = oreDictionaryName;
        this.meta = meta;
    }

    @Nullable
    public TileEntity createTileEntity(@Nonnull World var1, @Nonnull IBlockState var2)
    {

        return null;
    }

    public int toMeta(){
        return this.meta;
    }

    public String toString() {
        return this._name;
    }

    @Override
    public String getName() {

        return this.toString();
    }
}
