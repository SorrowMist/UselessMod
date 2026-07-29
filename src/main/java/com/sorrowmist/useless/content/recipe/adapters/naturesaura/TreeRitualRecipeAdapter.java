package com.sorrowmist.useless.content.recipe.adapters.naturesaura;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import de.ellpeck.naturesaura.recipes.TreeRitualRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Nature's Aura tree rituals, including their consumed gold-powder ring. */
public final class TreeRitualRecipeAdapter implements IRecipeAdapter<TreeRitualRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GOLD_POWDER_PER_RITUAL = 16;

    @Override
    public Class<TreeRitualRecipe> getRecipeClass() {
        return TreeRitualRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return NaturesAuraAdapterUtils.item("wood_stand");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<TreeRitualRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid Nature's Aura tree ritual recipe: {}", holder.id());
            return List.of();
        }
        return List.of(createRecipe(holder, converted));
    }

    @Override
    public List<RecipeHolder<TreeRitualRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<TreeRitualRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<TreeRitualRecipe> holder : recipeManager.getAllRecipesFor(
                de.ellpeck.naturesaura.recipes.ModRecipes.TREE_RITUAL_TYPE)) {
            Converted converted = convertData(holder.value());
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable TreeRitualRecipe source) {
        if (source == null || source.time <= 0 || source.saplingType == null || source.saplingType.isEmpty()
                || source.output == null || source.output.isEmpty() || source.output.getCount() <= 0
                || source.ingredients == null) {
            return null;
        }

        Map<Ingredient, Long> requirements = NaturesAuraAdapterUtils.requirements();
        if (!NaturesAuraAdapterUtils.addIngredient(requirements, source.saplingType, 1L)) {
            return null;
        }
        for (Ingredient ingredient : source.ingredients) {
            if (!NaturesAuraAdapterUtils.addIngredient(requirements, ingredient, 1L)) {
                return null;
            }
        }

        ItemStack goldPowder = NaturesAuraAdapterUtils.item("gold_powder");
        if (goldPowder.isEmpty() || !NaturesAuraAdapterUtils.addIngredient(
                requirements, Ingredient.of(goldPowder), GOLD_POWDER_PER_RITUAL)) {
            return null;
        }

        List<CountedIngredient> inputs = NaturesAuraAdapterUtils.counted(requirements);
        if (inputs.isEmpty()) {
            return null;
        }
        return new Converted(inputs, requirements, source.output.copy(), source.time);
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(RecipeHolder<TreeRitualRecipe> holder, Converted converted) {
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.of(),
                List.of(converted.output().copy()),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                converted.time(),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
    }

    private record Converted(
            List<CountedIngredient> inputs,
            Map<Ingredient, Long> requirements,
            ItemStack output,
            int time) {
    }
}
