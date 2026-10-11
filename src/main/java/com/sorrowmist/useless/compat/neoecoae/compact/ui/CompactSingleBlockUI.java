package com.sorrowmist.useless.compat.neoecoae.compact.ui;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Small single-block overview used by compact hosts.
 *
 * <p>The ECO controller screens contain a multiblock builder and a structure preview. Those
 * controls are meaningless for a compact block, so the overview deliberately exposes only
 * the fixed hardware, pooled capacity, and the player inventory. Detailed management remains
 * on the sneaking interface entry and on the dedicated fluid/pattern item interactions.</p>
 */
public final class CompactSingleBlockUI {
    private CompactSingleBlockUI() {}

    public static ModularUI create(Player player, Component title, Component... lines) {
        UIElement root = new UIElement().addClass("panel_bg");
        root.addChild(new TextElement().setText(title).textStyle(style -> style
                .adaptiveWidth(true).adaptiveHeight(true).textWrap(TextWrap.NONE)
                .textColor(0x3F3D52).textShadow(false)));
        for (Component line : lines) {
            root.addChild(new TextElement().setText(line).textStyle(style -> style
                    .adaptiveWidth(true).adaptiveHeight(true).textWrap(TextWrap.HOVER_ROLL)
                    .textColor(0x555266).textShadow(false)));
        }
        InventorySlots inventory = new InventorySlots();
        root.addChild(inventory);
        return new ModularUI(UI.of(root,
                List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), player);
    }
}
