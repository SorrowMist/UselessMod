package com.sorrowmist.useless.compat.ae;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.moakiee.ae2lt.client.tianshu.TianshuDirectUploadClient;
import com.moakiee.ae2lt.integration.useless.UselessModClientTransfer;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Uses Tianshu's dedicated omniversal draft instead of its ordinary processing slots. */
public final class TianshuPatternEmiCompat {
    private TianshuPatternEmiCompat() { }

    /** @return the transfer result, or {@code null} when this is not a Tianshu menu */
    @Nullable
    public static Boolean transferRecipe(PatternEncodingTermMenu menu, AlloyFurnaceRecipeCatalog.Entry entry) {
        if (!ModList.get().isLoaded("ae2lt")) return null;
        return NativeTransfer.transferRecipe(menu, entry);
    }

    // Keep optional AE2 Lightning Tech classes out of the entry point's linkage.
    private static final class NativeTransfer {
        @Nullable
        static Boolean transferRecipe(PatternEncodingTermMenu menu, AlloyFurnaceRecipeCatalog.Entry entry) {
            if (!(menu instanceof TianshuPatternEncodingTermMenu tianshu)) return null;
            var pattern = UselessModClientTransfer.prepare(tianshu, entry);
            if (pattern.isEmpty()) {
                menu.getPlayer().displayClientMessage(
                        Component.translatable("ae2lt.tianshu.omniversal.invalid"), true);
                return false;
            }
            tianshu.selectOmniversalPattern(pattern);
            // The dedicated transfer bypasses the native processing-transfer hooks. Preserve
            // their Alt-click upload behavior, without resetting/restoring closed-loop mode.
            if (Screen.hasAltDown()) {
                tianshu.encodeAndUploadDirectly();
                var screen = Minecraft.getInstance().screen;
                if (screen != null && screen != EmiApi.getHandledScreen()) {
                    TianshuDirectUploadClient.holdRecipeScreen(tianshu, screen);
                }
            }
            return true;
        }
    }
}
