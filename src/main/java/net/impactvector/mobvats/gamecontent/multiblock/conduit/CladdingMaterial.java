package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;

/** Installed structural material. Add future materials here, never mechanical registrations. */
public enum CladdingMaterial implements StringRepresentable {
    NONE(ConduitCladding.BARE,ConduitShell.SHARED),
    VAT_T1_CASING(ConduitCladding.CASING,ConduitShell.VAT),
    VAT_T1_GLASS(ConduitCladding.GLASS,ConduitShell.VAT),
    MIXER_T1_CASING(ConduitCladding.CASING,ConduitShell.MIXER),
    MIXER_T1_GLASS(ConduitCladding.GLASS,ConduitShell.MIXER);
    public static final EnumProperty<CladdingMaterial> PROPERTY=EnumProperty.create("cladding_material",CladdingMaterial.class);
    private final ConduitCladding cladding;
    private final ConduitShell shell;
    CladdingMaterial(ConduitCladding cladding,ConduitShell shell){this.cladding=cladding;this.shell=shell;}
    public ConduitCladding cladding(){return cladding;}
    public ConduitShell shell(){return shell;}
    public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}
    public Item item(){return switch(this){
        case NONE -> net.minecraft.world.item.Items.AIR;
        case VAT_T1_CASING -> Content.block(VatPartType.Casing).asItem();
        case VAT_T1_GLASS -> Content.block(VatPartType.Glass).asItem();
        case MIXER_T1_CASING -> MixerContent.block(MixerPartType.Casing).asItem();
        case MIXER_T1_GLASS -> MixerContent.block(MixerPartType.Glass).asItem();
    };}
    public static CladdingMaterial from(ItemStack stack){for(var m:values())if(m!=NONE && stack.is(m.item()))return m;return NONE;}
    public static CladdingMaterial of(ConduitCladding cladding,ConduitShell shell){for(var m:values())if(m.cladding==cladding && (cladding==ConduitCladding.BARE || m.shell==shell))return m;throw new IllegalArgumentException("Unsupported material");}
    public static CladdingMaterial installed(BlockState state){return state.hasProperty(PROPERTY)?state.getValue(PROPERTY):NONE;}
}
