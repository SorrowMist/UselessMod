package com.sorrowmist.useless.content.recipe.adapters.bloodmagic;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.BatchRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import wayoftime.bloodmagic.recipe.BloodMagicRecipe;
import wayoftime.bloodmagic.recipe.RecipeAlchemyTable;
import wayoftime.bloodmagic.recipe.RecipeBloodAltar;
import wayoftime.bloodmagic.recipe.RecipeTartaricForge;

import java.util.List;

public final class BloodMagicRecipeAdapter<T extends BloodMagicRecipe> extends BatchRecipeAdapter<T> {
    public BloodMagicRecipeAdapter(Class<T> type, String mold, String prefix) {
        super(type, RecipeSourceIds.BLOOD_MAGIC, mold, prefix, AlloyFurnaceMode.NORMAL);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<T> holder, Level level) {
        if (holder == null) return List.of();
        List<Ingredient> inputs;
        ItemStack output;
        long energy = 2000L;
        if (holder.value() instanceof RecipeBloodAltar altar) {
            inputs = List.of(altar.getInput());
            output = altar.getOutput();
            energy = Math.multiplyExact(altar.getSyphon(), 2L);
        } else if (holder.value() instanceof RecipeTartaricForge forge) {
            inputs = forge.getInput();
            output = forge.getOutput();
        } else if (holder.value() instanceof RecipeAlchemyTable table) {
            inputs = table.getInput();
            output = table.getOutput();
        } else {
            return List.of();
        }
        if (output.isEmpty()) return List.of();
        return List.of(recipe(holder.id(), items(inputs, 64L), List.of(),
                List.of(scale(output, 64L)), List.of(), energy));
    }
}
