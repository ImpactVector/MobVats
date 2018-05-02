package net.impactvector.mobvats.net.message.base;

import it.zerono.mods.zerocore.lib.network.ModTileEntityMessage;
import it.zerono.mods.zerocore.lib.network.ModTileEntityMessageHandlerClient;
import net.impactvector.mobvats.MultiblockVat;
import net.impactvector.mobvats.tileentity.TileEntityVatPartBase;
import net.impactvector.mobvats.util.ModEventLog;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class VatMessageClient extends ModTileEntityMessage {

    protected final MultiblockVat VAT;

    protected VatMessageClient() {
        this.VAT = null;
    }

    protected VatMessageClient(MultiblockVat vat) {

        super(vat.getReferenceCoord());
        this.VAT = vat;
    }

    public static abstract class Handler<MessageT extends VatMessageClient> extends ModTileEntityMessageHandlerClient<MessageT> {

        @Override
        protected void processTileEntityMessage(MessageT message, MessageContext ctx, TileEntity tileEntity) {

            BlockPos position = null != tileEntity ? tileEntity.getPos() : null;

            if (tileEntity instanceof TileEntityVatPartBase) {

                MultiblockVat vat = ((TileEntityVatPartBase)tileEntity).getVatController();

                if (null != vat) {

                    this.processVatMessage(message, ctx, vat);

                } else {

                    ModEventLog.error("Received ReactorMessageClient for a reactor part @ %d, %d, %d which has no attached reactor",
                            position.getX(), position.getY(), position.getZ());
                }
            } else if (null != position) {

                ModEventLog.error("Received ReactorMessageClient for a non-reactor-part block @ %d, %d, %d",
                        position.getX(), position.getY(), position.getZ());
            }
        }

        protected abstract void processVatMessage(final MessageT message, final MessageContext ctx, final MultiblockVat vat);
    }
}
