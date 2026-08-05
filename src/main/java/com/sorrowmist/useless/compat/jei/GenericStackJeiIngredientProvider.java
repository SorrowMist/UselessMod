package com.sorrowmist.useless.compat.jei;

import appeng.api.stacks.GenericStack;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import org.jetbrains.annotations.Nullable;

/** Optional fallback bridge for an AE key type that is not registered with AE2 JEI Integration. */
public interface GenericStackJeiIngredientProvider {
    GenericStackJeiIngredientProvider NONE = new GenericStackJeiIngredientProvider() {
        @Override
        public @Nullable Ingredient resolve(GenericStack stack) {
            return null;
        }
    };

    @Nullable
    Ingredient resolve(GenericStack stack);

    record Ingredient(IIngredientType<?> type,
                      Object value,
                      @Nullable IIngredientRenderer<?> renderer) {
        public Ingredient(IIngredientType<?> type, Object value) {
            this(type, value, null);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void addTo(IRecipeSlotBuilder slot) {
            IIngredientType type = this.type;
            slot.addIngredient(type, this.value);
            if (this.renderer != null) {
                slot.setCustomRenderer(type, (IIngredientRenderer) this.renderer);
            }
        }
    }
}
