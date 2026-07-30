package com.sorrowmist.useless.content.recipe.adapters.malum;

import com.mojang.logging.LogUtils;
import com.sammy.malum.common.recipe.SpiritFocusingRecipe;
import com.sammy.malum.registry.common.recipe.MalumRecipeTypes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
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

/** Converts Malum spirit-crucible recipes while intentionally ignoring the reusable Impetus item. */
public final class SpiritFocusingRecipeAdapter implements IRecipeAdapter<SpiritFocusingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<SpiritFocusingRecipe> getRecipeClass() {
        return SpiritFocusingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return MalumAdapterUtils.item("spirit_crucible");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<SpiritFocusingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid Malum spirit focusing recipe: {}", holder.id());
            return List.of();
        }
        return List.of(createRecipe(holder, converted));
    }

    @Override
    public List<RecipeHolder<SpiritFocusingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null) {
            return List.of();
        }

        List<RecipeHolder<SpiritFocusingRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<SpiritFocusingRecipe> holder : recipeManager.getAllRecipesFor(
                MalumRecipeTypes.SPIRIT_FOCUSING.get())) {
            Converted converted = convertData(holder.value());
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable SpiritFocusingRecipe source) {
        if (source == null || source.time <= 0) {
            return null;
        }
        ItemStack output = source.output;
        if (output == null || output.isEmpty() || output.getCount() <= 0) {
            return null;
        }

        // The source input is the reusable, damageable Impetus. The alloy-furnace integration
        // deliberately omits it, per the compatibility contract.
        Map<Ingredient, Long> requirements = MalumAdapterUtils.requirements();
        if (!MalumAdapterUtils.addSpirits(requirements, source.spirits)) {
            return null;
        }
        List<CountedIngredient> inputs = MalumAdapterUtils.counted(requirements);
        if (inputs == null) {
            return null;
        }
        return new Converted(inputs, requirements, output.copy(), source.time);
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(
            RecipeHolder<SpiritFocusingRecipe> holder, Converted converted) {
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.of(),
                List.of(converted.output().copy()),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                converted.processTime(),
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
            int processTime) {
    }
}
