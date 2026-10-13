package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class VatControllerEntity extends AbstractVatEntity implements net.minecraft.world.MenuProvider {
    public VatControllerEntity(BlockPos pos, BlockState state) {
        super(VatPartType.Controller, pos, state);
        setCommandDispatcher(it.zerono.mods.zerocore.lib.block.TileCommandDispatcher.<VatControllerEntity>builder()
                .addServerHandler("activate", p -> p.setActive(true))
                .addServerHandler("deactivate", p -> p.setActive(false))
                .build(this));
    }

    private void setActive(boolean active) {
        getMultiblockController()
                .filter(net.impactvector.mobvats.gamecontent.multiblock.vat.MultiblockVat::isAssembled)
                .ifPresent(c -> c.setMachineActive(active));
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.mobvats.vat_controller");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int id,
            net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.entity.player.Player player) {
        return new net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatControllerMenu(id, inventory, this);
    }
}
