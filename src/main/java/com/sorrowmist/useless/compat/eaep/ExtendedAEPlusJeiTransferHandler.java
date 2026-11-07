package com.sorrowmist.useless.compat.eaep;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Opt-in decorator for the category-specific omniversal transfer handler. Register this INSTEAD OF
 * that handler, not alongside it: JEI does not compose duplicate handlers for one menu/category.
 */
public final class ExtendedAEPlusJeiTransferHandler<T extends PatternEncodingTermMenu>
        implements IRecipeTransferHandler<T, AlloyFurnaceRecipeCatalog.Entry> {
    private final IRecipeTransferHandler<T, AlloyFurnaceRecipeCatalog.Entry> delegate;

    public ExtendedAEPlusJeiTransferHandler(
            Class<T> menuClass, MenuType<T> menuType, IRecipeTransferHandlerHelper helper) {
        this(new OmniversalPatternJeiTransferHandler<>(menuClass, menuType, helper));
    }

    public ExtendedAEPlusJeiTransferHandler(
            IRecipeTransferHandler<T, AlloyFurnaceRecipeCatalog.Entry> delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Class<? extends T> getContainerClass() {
        return delegate.getContainerClass();
    }

    @Override
    public Optional<MenuType<T>> getMenuType() {
        return delegate.getMenuType();
    }

    @Override
    public RecipeType<AlloyFurnaceRecipeCatalog.Entry> getRecipeType() {
        return delegate.getRecipeType();
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            T menu, AlloyFurnaceRecipeCatalog.Entry recipe, IRecipeSlotsView slots,
            Player player, boolean maxTransfer, boolean doTransfer) {
        IRecipeTransferError error = delegate.transferRecipe(menu, recipe, slots, player, maxTransfer, false);
        if (error != null || !doTransfer) {
            return error;
        }
        // Simulation must not change EAEP's global one-shot search state. Preset before
        // encoding, so automatic upload on the same tick sees the correct provider filter.
        ExtendedAEPlusJeiCompat.presetAlloyFurnaceSearchKey();
        return delegate.transferRecipe(menu, recipe, slots, player, maxTransfer, true);
    }
}
