package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.render.PatternSlotRenderer;
import com.sorrowmist.useless.content.menus.MePatternAssemblyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * ME 样板总成的容器界面。
 *
 * <p>在分页界面基础上接入样板搜索：查询按所选范围匹配样板自身的输入、输出或模具显示名，
 * 命中项在原槽位上高亮标记。只读恢复页内容不进入搜索结果，避免玩家在恢复页里看到无法回填的条目。</p>
 */
public final class PatternAssemblyScreen extends PagedRecoverableScreen<MePatternAssemblyMenu> {
    public PatternAssemblyScreen(MePatternAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected boolean supportsSearchMode() {
        return true;
    }

    @Override
    protected boolean matchesSearch(int menuSlot) {
        Slot slot = menu.getSlot(menuSlot);
        return PatternSearchMatcher.matchesPattern(slot.getItem(), normalizedQuery(), searchMode(),
                minecraft == null ? null : minecraft.level);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index < MePatternAssemblyMenu.SLOTS_PER_PAGE
                && PatternSlotRenderer.renderPattern(graphics, font, slot.getItem(), slot.x, slot.y,
                slot.x + slot.y * imageWidth, minecraft == null ? null : minecraft.level)) {
            return;
        }
        super.renderSlot(graphics, slot);
    }
}
