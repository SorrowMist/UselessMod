package com.sorrowmist.useless.compat.forge1201.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Native 1.20.1 button: no newer AE2 widgets, sprite atlas or resource-pack assumptions. */
public class PortButton extends Button {
    public PortButton(int x,int y,int width,int height,Component message,OnPress onPress) {
        super(x,y,width,height,message,onPress,DEFAULT_NARRATION);
    }
    protected final int withAlpha(int rgb) { return Mth.clamp(Mth.ceil(alpha*255),0,255)<<24 | rgb; }
    protected void renderSurface(GuiGraphics graphics,boolean highlighted) {
        int left=getX(),top=getY(),right=left+getWidth(),bottom=top+getHeight();
        int border=withAlpha(!active?0x343440:highlighted?0x9DC4DE:0x747488);
        int fill=withAlpha(!active?0x282832:highlighted?0x354F65:0x3D3D4D);
        graphics.fill(left,top,right,bottom,border);
        if(getWidth()>2 && getHeight()>2) graphics.fill(left+1,top+1,right-1,bottom-1,fill);
        if(isFocused() && active && getWidth()>4 && getHeight()>4)
            graphics.fill(left+2,bottom-3,right-2,bottom-2,withAlpha(0xAED4ED));
    }
    protected void renderLabel(GuiGraphics graphics,int color) {
        renderScrollingString(graphics,Minecraft.getInstance().font,2,withAlpha(color));
    }
    @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderSurface(graphics,isHoveredOrFocused());
        renderLabel(graphics,active?0xF2F2F2:0x888894);
    }
}
