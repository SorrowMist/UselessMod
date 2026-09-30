package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.OmniversalMoldHubMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 模具集散中心的容器界面。
 *
 * <p>搜索按模具物品名与配方登记的可接受物品名匹配，使按任意可接受物品名都能定位到对应模具。
 * 内容全部为模具，不存在输入与输出的区分，因此不提供搜索范围选择；搜索范围由基类限定在
 * 活跃模具槽位，只读恢复页内容不进入结果。</p>
 */
public final class MoldHubScreen extends PagedRecoverableScreen<OmniversalMoldHubMenu> {
    public MoldHubScreen(OmniversalMoldHubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected boolean matchesSearch(int menuSlot) {
        Slot slot = menu.getSlot(menuSlot);
        return PatternSearchMatcher.matchesMold(slot.getItem(), normalizedQuery(),
                minecraft == null ? null : minecraft.level);
    }
}
