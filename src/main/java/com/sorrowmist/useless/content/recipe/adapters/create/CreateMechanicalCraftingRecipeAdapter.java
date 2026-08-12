package com.sorrowmist.useless.content.recipe.adapters.create;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Create's unordered mechanical crafting material list. */
public final class CreateMechanicalCraftingRecipeAdapter
        implements IRecipeAdapter<MechanicalCraftingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<MechanicalCraftingRecipe> getRecipeClass() {
        return MechanicalCraftingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return CreateRecipeAdapterUtils.createBlockItem("mechanical_crafter");
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return CreateRecipeAdapterUtils.isCreateMold(mold, "mechanical_crafter");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<MechanicalCraftingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        MechanicalCraftingRecipe source = holder.value();
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        for (Ingredient ingredient : source.getIngredients()) {
            if (ingredient == null || ingredient.isEmpty()) continue;
            AdapterUtils.mergeIngredient(requirements, ingredient, 1L);
        }
        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        var crafterMold = CreateRecipeAdapterUtils.blockMold(
                CreateRecipeAdapterUtils.createBlock("mechanical_crafter"));
        if (requirements.isEmpty() || output.isEmpty() || output.getCount() <= 0 || crafterMold.isEmpty()) {
            LOGGER.warn("Skipping invalid Create mechanical crafting recipe: {}", holder.id());
            return List.of();
        }
        List<CountedIngredient> inputs = requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(crafterMold),
                AlloyFurnaceMode.NORMAL));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<MechanicalCraftingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<MechanicalCraftingRecipe>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof MechanicalCraftingRecipe)) continue;
            RecipeHolder<MechanicalCraftingRecipe> typed =
                    (RecipeHolder<MechanicalCraftingRecipe>) (RecipeHolder<?>) holder;
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(typed, level);
            if (!converted.isEmpty() && CreateRecipeAdapterUtils.matchesConverted(
                    converted.getFirst(), mergedInputs, mergedFluids)) result.add(typed);
        }
        return result;
    }
}
