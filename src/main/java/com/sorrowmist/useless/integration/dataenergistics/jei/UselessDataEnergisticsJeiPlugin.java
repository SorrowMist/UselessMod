package com.sorrowmist.useless.integration.dataenergistics.jei;

import com.fish_dan_.data_energistics.api.entrypoint.jei.DataEnergisticsJeiEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.jei.DataEnergisticsJeiPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.jei.DataEnergisticsJeiRegistry;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;
import com.fish_dan_.data_energistics.registry.DEMenus;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.jei.AdvancedAlloyFurnaceRecipeCategory;
import com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler;

import appeng.menu.me.items.PatternEncodingTermMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

import org.jetbrains.annotations.NotNull;

/**
 * Makes Data Energistics's universal pattern encoding terminal preserve the exact Omniversal Pattern recipe,
 * including its internal mold, when a player transfers a recipe from JEI.
 */
@DataEnergisticsJeiEntrypoint
public final class UselessDataEnergisticsJeiPlugin implements DataEnergisticsJeiPlugin {
    private static final ResourceLocation OMNIVERSAL_PATTERN_TRANSFER_ID =
            UselessMod.id("omniversal_pattern_transfer");

    /** Public constructor required by Data Energistics's JEI entrypoint scanner. */
    public UselessDataEnergisticsJeiPlugin() {}

    @Override
    public void register(@NotNull DataEnergisticsJeiRegistry registry) {
        registerTransfer(registry, UniversalPatternEncodingTermMenu.class,
                DEMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get());
    }

    /**
     * Registers the alloy-furnace category transfer for one concrete AE2-compatible pattern-encoding menu.
     *
     * @param registry Data Energistics's generic JEI registration surface
     * @param menuClass exact terminal menu class
     * @param menuType exact terminal menu registration
     * @param <T> concrete pattern-encoding menu type
     */
    static <T extends PatternEncodingTermMenu> void registerTransfer(
            @NotNull DataEnergisticsJeiRegistry registry,
            @NotNull Class<T> menuClass,
            @NotNull MenuType<T> menuType) {
        registry.registerRecipeTransferHandler(
                OMNIVERSAL_PATTERN_TRANSFER_ID,
                menuClass,
                menuType,
                AdvancedAlloyFurnaceRecipeCategory.TYPE,
                helper -> new OmniversalPatternJeiTransferHandler<>(
                        menuClass,
                        menuType,
                        helper));
    }
}
