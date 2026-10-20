package com.sorrowmist.useless.compat.jei;

import appeng.api.stacks.GenericStack;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import java.util.function.Supplier;

/** Only our own screens: unwrap phantom AE resources before JEI's vanilla slot handler
 * mistakes them for appeng:wrapped_generic_stack bookmarks. Ordinary items are untouched.
 */
public final class WrappedKeyGuiHandler implements IGuiContainerHandler<AbstractContainerScreen<?>> {
    private final Supplier<IIngredientManager> ingredients;
    public WrappedKeyGuiHandler(Supplier<IIngredientManager> ingredients) { this.ingredients = ingredients; }
    @Override public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(
            AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (!screen.getClass().getName().startsWith("com.sorrowmist.useless.")) return Optional.empty();
        var slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.isActive()) return Optional.empty();
        var area = new Rect2i(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16);
        if (mouseX < area.getX() || mouseX >= area.getX() + 16
                || mouseY < area.getY() || mouseY >= area.getY() + 16) return Optional.empty();
        return fromWrappedSlot(slot.getItem(), area, ingredients.get());
    }
    static Optional<IClickableIngredient<?>> fromWrappedSlot(ItemStack item, Rect2i area, IIngredientManager manager) {
        if (manager == null || !GenericStack.isWrapped(item)) return Optional.empty();
        var stack = GenericStack.unwrapItemStack(item);
        var ingredient = GenericStackJeiIngredientProviders.resolve(stack);
        return ingredient == null ? Optional.empty() : ingredient.clickable(manager, area);
    }
}
