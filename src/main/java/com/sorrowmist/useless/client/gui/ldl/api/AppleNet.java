package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** 界面发包唯一入口：复用模组现有 Network 通道，不新建 channel。 */
public final class AppleNet {
    private final AbstractContainerMenu menu;
    public AppleNet(AbstractContainerMenu menu) { this.menu = menu; }

    /** 发送任意已注册的 C2S 包。 */
    public static void send(CustomPacketPayload... packets) { PacketDistributor.sendToServer(packets); }

    /** 最轻量的按钮：Menu#clickMenuButton(player, id)，无需新包（翻页 / 模式切换 / 清空）。 */
    public void button(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && menu != null) mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    public int containerId() { return menu == null ? -1 : menu.containerId; }
}
