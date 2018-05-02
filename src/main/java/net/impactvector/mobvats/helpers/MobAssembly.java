package net.impactvector.mobvats.helpers;

import net.impactvector.mobvats.MultiblockVat;
import net.impactvector.mobvats.tileentity.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class MobAssembly {
    public MobAssembly(final TileEntityVatHarness harness) {
        if (null == harness || !this.build(harness))
            throw new IllegalStateException("Invalid homunculus assembly");

//        currLearnTicks = 0;
//        currSpawnTicks = 0;
        //spawnReq = MobVats.spawnerManager.getSpawnReq(head.getMobName(), head);
    }

    private boolean build(final TileEntityVatHarness harness) {
        MultiblockVat vat = harness.getVatController();

        BlockPos minCoord = vat.getMinimumCoord();
        BlockPos maxCoord = vat.getMaximumCoord();
        BlockPos lookupPosition = harness.getWorldPosition();

        World world = harness.getWorld();
        TileEntity te;
        this._harness = harness;
        this._fleshCount = 0;

        boolean fleshDone = false;
        //search the harness' column for a valid homunculus
        for (int i = 1; i <= maxCoord.getY() - minCoord.getY(); i++)
        {
            lookupPosition = lookupPosition.offset(harness.getOutwardFacingFromWorldPosition());
            te = world.getTileEntity(lookupPosition);

            if (te instanceof TileEntityVatHead) {
                this._head = (TileEntityVatHead)te;
            } else if (te instanceof TileEntityVatFlesh) {
                if (fleshDone || this._head == null) {
                    return false; //found disconnected flesh
                } else {
                    this._fleshCount++;
                }
            } else {
                fleshDone = true;
            }
        }
        lookupPosition = lookupPosition.offset(harness.getOutwardFacingFromWorldPosition());
        te = world.getTileEntity(lookupPosition);
        if (te instanceof TileEntityVatDrain)
            _drain = (TileEntityVatDrain)te;

        return _head != null && _drain != null;
    }

    private TileEntityVatHarness _harness;
    private TileEntityVatHead _head;
    private TileEntityVatDrain _drain;
    private int _fleshCount;
}
