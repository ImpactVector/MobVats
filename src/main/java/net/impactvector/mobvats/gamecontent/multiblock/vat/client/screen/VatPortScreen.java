package net.impactvector.mobvats.gamecontent.multiblock.vat.client.screen;
import net.impactvector.mobvats.gamecontent.multiblock.common.MachinePortMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class VatPortScreen extends AbstractContainerScreen<MachinePortMenu> {
    private Button toggle;
    public VatPortScreen(MachinePortMenu menu, Inventory inventory, Component title) { super(menu,inventory,title); }
    protected void init() {
        super.init(); toggle=addRenderableWidget(Button.builder(Component.empty(),b->{
            if (minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);
        }).bounds(leftPos+38,topPos+59,100,20).build());
    }
    protected void renderBg(GuiGraphics g,float tick,int mouseX,int mouseY) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffc6c6c6);
        for(var slot:menu.slots) g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xff555555);
    }
    public void render(GuiGraphics g,int mouseX,int mouseY,float tick) {
        toggle.setMessage(Component.literal(menu.isInput()?"Input -> Output":"Output -> Input"));
        super.render(g,mouseX,mouseY,tick); renderTooltip(g,mouseX,mouseY);
    }
}
