package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.client.gui.apple.AppleCard;
import com.sorrowmist.useless.client.gui.apple.AppleLabel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** 玩家物品栏卡片：标题 + 3×9 主背包 + 4px 间隔 + 快捷栏（174×101，与所有已改造界面一致）。 */
public final class PlayerInventoryNode extends UiNode {
    private static final Component TITLE = Component.translatable("container.inventory");
    @Override protected int prefW() { return 174; }
    private int captionY = 6, h = 101;
    /** 紧凑版（97 高、标题上移 1px）：矿物生成器等竖向空间紧张的界面。 */
    public PlayerInventoryNode compact() { captionY = 5; h = 97; return this; }
    @Override protected int prefH() { return h; }
    @Override protected void place(UiContext ctx, int x, int y, int w, int h) {
        ctx.add(new AppleCard(x, y, w, h));
        int sx = x + 6, sy = y + 17;
        ctx.add(new AppleLabel(sx, y + captionY, w - 12, AppleLabel.Kind.CAPTION, () -> TITLE));
        if (ctx.menu == null) return;
        for (Slot s : ctx.menu.slots) {
            if (!(s.container instanceof Inventory)) continue;
            int i = s.getContainerSlot();
            if (i < 9) SlotsNode.moveSlot(s, sx + i * 18, sy + 58);
            else if (i < 36) SlotsNode.moveSlot(s, sx + (i - 9) % 9 * 18, sy + (i - 9) / 9 * 18);
        }
    }
}
