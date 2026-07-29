package com.sorrowmist.useless.content.recipe.adapters.naturesaura;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import de.ellpeck.naturesaura.recipes.AltarRecipe;
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

/** Converts both standard and crimson Nature's Aura altar recipes. */
public final class NatureAltarRecipeAdapter implements IRecipeAdapter<AltarRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<AltarRecipe> getRecipeClass() {
        return AltarRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        // Altar recipes can use either the altar itself or a source-defined catalyst block.
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        // The exact source recipe decides whether this is the altar or one of its catalysts.
        return mold != null && !mold.isEmpty();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<AltarRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid Nature's Aura altar recipe: {}", holder.id());
            return List.of();
        }
        return List.of(createRecipe(holder, converted));
    }

    @Override
    public List<RecipeHolder<AltarRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<AltarRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<AltarRecipe> holder : recipeManager.getAllRecipesFor(
                de.ellpeck.naturesaura.recipes.ModRecipes.ALTAR_TYPE)) {
            Converted converted = convertData(holder.value());
            if (converted != null && converted.mold().test(mold)
                    && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable AltarRecipe source) {
        if (source == null || source.aura < 0 || source.time <= 0 || source.input == null
                || source.input.isEmpty() || source.output == null || source.output.isEmpty()
                || source.output.getCount() <= 0) {
            return null;
        }

        Ingredient mold = source.catalyst == null || source.catalyst.isEmpty()
                ? AdapterUtils.toMoldIngredient(NaturesAuraAdapterUtils.item("nature_altar"))
                : source.catalyst;
        if (mold.isEmpty()) {
            return null;
        }

        Map<Ingredient, Long> requirements = NaturesAuraAdapterUtils.requirements();
        if (!NaturesAuraAdapterUtils.addIngredient(requirements, source.input, 1L)) {
            return null;
        }
        List<CountedIngredient> inputs = NaturesAuraAdapterUtils.counted(requirements);
        if (inputs.isEmpty()) {
            return null;
        }
        return new Converted(inputs, requirements, source.output.copy(), source.aura, source.time, mold);
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(RecipeHolder<AltarRecipe> holder, Converted converted) {
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.of(),
                List.of(converted.output().copy()),
                List.of(),
                converted.energy(),
                converted.time(),
                Ingredient.EMPTY,
                0,
                converted.mold(),
                AlloyFurnaceMode.NORMAL
        );
    }

    private record Converted(
            List<CountedIngredient> inputs,
            Map<Ingredient, Long> requirements,
            ItemStack output,
            long energy,
            int time,
            Ingredient mold) {
    }
}
