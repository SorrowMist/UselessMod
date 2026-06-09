package com.sorrowmist.useless.content.recipe.adapters.ae2lt;

import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

final class AELightningIngredientHelper {

    private AELightningIngredientHelper() {}

    static CountedIngredient createLightningIngredient(LightningKey.Tier tier, long amount) {
        ItemStack stack = GenericStack.wrapInItemStack(LightningKey.of(tier), 1);
        return new CountedIngredient(Ingredient.of(stack), Math.max(1, amount));
    }

    static void addLightningIngredient(List<CountedIngredient> ingredients, LightningKey.Tier tier, long amount) {
        ingredients.add(createLightningIngredient(tier, amount));
    }

    static boolean matchesLightning(List<ItemStack> inputs, LightningKey.Tier tier, long amount) {
        CountedIngredient required = createLightningIngredient(tier, amount);
        long found = 0;
        for (ItemStack input : inputs) {
            if (!input.isEmpty() && required.ingredient().test(input)) {
                found += input.getCount();
            }
        }
        return found >= required.count();
    }
}
