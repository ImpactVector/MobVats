package net.impactvector.mobvats.gamecontent.multiblock.vat;

import it.zerono.mods.zerocore.lib.block.multiblock.IMultiblockPartType;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.PartPosition;
import java.util.function.BiFunction;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public enum VatPartType implements IMultiblockPartType {
    Controller("vat_controller", VatControllerEntity::new),
    Casing("vat_casing", VatCasingEntity::new),
    Glass("vat_glass", VatGlassEntity::new),
    Harness("vat_harness", VatHarnessEntity::new),
    Sump("vat_drain", VatSumpEntity::new),
    Head("vat_head", VatHeadEntity::new),
    Flesh("vat_flesh", VatFleshEntity::new),
    ItemPort("vat_item_port", VatItemPortEntity::new),
    FluidPort("vat_fluid_port", VatFluidPortEntity::new),
    ToolPort("vat_tool_port", VatToolPortEntity::new),
    FeedPort("vat_feed_port", VatFeedPortEntity::new),
    Blender("vat_blender", VatBlenderEntity::new),
    PullPump("vat_pull_pump"),
    PushPump("vat_push_pump"),
    FillValve("vat_fill_valve"),
    DrainValve("vat_drain_valve"),
    ItemVoid("vat_item_void"),
    FluidVoid("vat_fluid_void");

    private final String name;
    private final BiFunction<BlockPos, BlockState, AbstractVatEntity> factory;

    VatPartType(String name) {
        this.name = name;
        this.factory = (pos, state) -> new VatBoundaryEntity(this, pos, state);
    }

    VatPartType(String name, BiFunction<BlockPos, BlockState, AbstractVatEntity> factory) {
        this.name = name;
        this.factory = factory;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    @Override
    public String getTranslationKey() {
        return "block.mobvats." + net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.id(this);
    }

    @Override
    public AbstractVatEntity createTileEntity(BlockState state, BlockPos pos) {
        return factory.apply(pos, state);
    }

    /** Future functional shell parts inherit the formed 3px panel treatment. */
    public boolean usesWallPanel() {
        return this != Casing
                && this != Glass
                && this != Head
                && this != Flesh
                && this != ItemVoid
                && this != FluidVoid;
    }

    public boolean allows(PartPosition position, boolean corner) {
        return switch (this) {
            case Casing -> position != PartPosition.Interior && position != PartPosition.Unknown;
            case Glass ->
                !corner
                        && (position == PartPosition.FrameUpDown
                                || position == PartPosition.TopFace
                                || isSide(position));
            case Controller -> isSide(position);
            case Harness -> position == PartPosition.TopFace || position == PartPosition.Interior;
            case Sump -> position == PartPosition.BottomFace || position == PartPosition.Interior;
            case Head, Flesh -> position == PartPosition.Interior;
            case ItemPort, FluidPort ->
                !corner
                        && (isSide(position)
                                || position == PartPosition.TopFace
                                || position == PartPosition.BottomFace);
            case ToolPort, FeedPort -> position == PartPosition.Interior || isSide(position);
            case Blender -> isSide(position);
            case PullPump, PushPump ->
                !corner
                        && (isSide(position)
                                || position == PartPosition.TopFace
                                || position == PartPosition.BottomFace);
            case DrainValve -> position == PartPosition.BottomFace;
            case FillValve, ItemVoid, FluidVoid ->
                position == PartPosition.Interior
                        || !corner
                                && (isSide(position)
                                        || position == PartPosition.TopFace
                                        || position == PartPosition.BottomFace);
        };
    }

    private static boolean isSide(PartPosition p) {
        return p == PartPosition.NorthFace
                || p == PartPosition.SouthFace
                || p == PartPosition.EastFace
                || p == PartPosition.WestFace;
    }
}
