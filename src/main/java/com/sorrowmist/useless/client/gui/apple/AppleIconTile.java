package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleUi;
import com.sorrowmist.useless.client.gui.DesignAssets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** 图标块：iOS 设置风格的圆角方块。filled = 彩色底 + 白色图形；否则浅色底 + 彩色图形。也可放物品图标。 */
public class AppleIconTile extends AppleWidget {
    private final Supplier<AppleUi.Icon> icon;
    private final IntSupplier accent;
    private Supplier<ItemStack> item;
    private boolean filled;

    public AppleIconTile(int x, int y, int size, Supplier<AppleUi.Icon> icon, IntSupplier accent) {
        super(x, y, size, size);
        this.icon = icon; this.accent = accent;
    }

    public AppleIconTile filled() { filled = true; return this; }
    public AppleIconTile item(Supplier<ItemStack> s) { item = s; return this; }

    public static void draw(GuiGraphics g, int x, int y, int size, AppleUi.Icon icon, int accent, boolean filled) {
        if (filled) {
            DesignAssets.draw(g, tileKey(accent, size), x, y, size, size);
        } else {
            AppleUi2.round(g, x, y, size, size, Math.max(AppleStyle.R_TILE, size / 4.5f), AppleStyle.tintOf(accent));
        }
        if (icon != null) {
            int s = size >= 16 ? 10 : 8;
            AppleUi.icon(g, icon, x + (size - s) / 2, y + (size - s) / 2, s, filled ? AppleStyle.ON_ACCENT : accent);
        }
    }

    /** icontile 只有 20/28/40/56 四档原画，尺寸向上对齐后整体缩放。 */
    static String tileKey(int accent, int size) {
        int bucket = size <= 20 ? 20 : size <= 28 ? 28 : size <= 40 ? 40 : 56;
        return "icontile_" + DesignAssets.colorName(accent) + "_" + bucket;
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), s = getSizeWidth();
        ItemStack st = item == null ? ItemStack.EMPTY : item.get();
        if (!st.isEmpty()) {
            AppleUi2.round(g, x, y, s, s, Math.max(AppleStyle.R_TILE, s / 4.5f), AppleStyle.tintOf(accent.getAsInt()));
            g.renderItem(st, x + (s - 16) / 2, y + (s - 16) / 2);
            return;
        }
        draw(g, x, y, s, icon.get(), dim(accent.getAsInt(), isEnabled()), filled);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
