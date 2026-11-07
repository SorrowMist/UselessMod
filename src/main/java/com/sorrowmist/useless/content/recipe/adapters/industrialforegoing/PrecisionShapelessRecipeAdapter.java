package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.level.Level;
import net.yxiao233.ifeu.common.recipe.PrecisionShapelessRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Extra Upgrades precision shapeless recipes. */
public final class PrecisionShapelessRecipeAdapter
        implements IRecipeAdapter<PrecisionShapelessRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<PrecisionShapelessRecipe> getRecipeClass() {
        return PrecisionShapelessRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return ExtraUpgradesCraftingRecipeAdapterSupport.mold("precision_crafting_table");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<PrecisionShapelessRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().getClass() != PrecisionShapelessRecipe.class) return List.of();
        PrecisionShapelessRecipe source = holder.value();
        AdvancedAlloyFurnaceRecipe converted = ExtraUpgradesCraftingRecipeAdapterSupport.precisionRecipe(
                com.sorrowmist.useless.content.recipe.AdapterUtils.convertedId(holder.id()),
                source.inputs, false, source.output, source.chance, getMoldItem());
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public List<RecipeHolder<PrecisionShapelessRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.minecraftforge.fluids.FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<PrecisionShapelessRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<PrecisionShapelessRecipe> holder :
                ExtraUpgradesCraftingRecipeAdapterSupport.recipes(level, PrecisionShapelessRecipe.class)) {
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(holder, level);
            if (!converted.isEmpty()
                    && ExtraUpgradesCraftingRecipeAdapterSupport.matches(
                    converted.get(0), mergedInputs, mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

}
