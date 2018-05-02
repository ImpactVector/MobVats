package net.impactvector.mobvats.net.message.multiblock;

import io.netty.buffer.ByteBuf;
import net.impactvector.mobvats.MultiblockVat;
import net.impactvector.mobvats.net.message.base.VatMessageClient;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class VatUpdateMessage extends VatMessageClient {
    public VatUpdateMessage() {
    }

    public VatUpdateMessage(MultiblockVat vat) {
        super(vat);
    }

    @Override
    public void fromBytes(ByteBuf buf) {

        super.fromBytes(buf);
        this._data = buf.readBytes(buf.readableBytes());
    }

    @Override
    public void toBytes(ByteBuf buf) {

        super.toBytes(buf);
        this.VAT.serialize(buf);
    }

    private ByteBuf _data;

    public static class Handler extends VatMessageClient.Handler<VatUpdateMessage> {

        @Override
        protected void processVatMessage(VatUpdateMessage message, MessageContext ctx, MultiblockVat reactor) {
            reactor.deserialize(message._data);
        }
    }
}
