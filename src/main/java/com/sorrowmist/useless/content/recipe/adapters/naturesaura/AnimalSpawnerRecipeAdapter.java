package com.sorrowmist.useless.content.recipe.adapters.naturesaura;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import de.ellpeck.naturesaura.Helper;
import de.ellpeck.naturesaura.recipes.AnimalSpawnerRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
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

/** Converts Nature's Aura animal-spawner recipes into their corresponding spawn eggs. */
public final class AnimalSpawnerRecipeAdapter implements IRecipeAdapter<AnimalSpawnerRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<AnimalSpawnerRecipe> getRecipeClass() {
        return AnimalSpawnerRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return NaturesAuraAdapterUtils.item("animal_spawner");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<AnimalSpawnerRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping unsupported Nature's Aura animal spawner recipe: {}", holder.id());
            return List.of();
        }
        return List.of(createRecipe(holder, converted));
    }

    @Override
    public List<RecipeHolder<AnimalSpawnerRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<AnimalSpawnerRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<AnimalSpawnerRecipe> holder : recipeManager.getAllRecipesFor(
                de.ellpeck.naturesaura.recipes.ModRecipes.ANIMAL_SPAWNER_TYPE)) {
            Converted converted = convertData(holder.value());
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable AnimalSpawnerRecipe source) {
        if (source == null || source.aura < 0 || source.time <= 0 || source.entity == null
                || source.ingredients == null || source.ingredients.isEmpty()) {
            return null;
        }

        SpawnEggItem spawnEgg = SpawnEggItem.byId(source.entity);
        if (spawnEgg == null) {
            return null;
        }

        Map<Ingredient, Long> requirements = NaturesAuraAdapterUtils.requirements();
        for (Ingredient ingredient : source.ingredients) {
            int amount = ingredient == null ? 0 : Helper.getIngredientAmount(ingredient);
            if (!NaturesAuraAdapterUtils.addIngredient(requirements, ingredient, amount)) {
                return null;
            }
        }
        List<CountedIngredient> inputs = NaturesAuraAdapterUtils.counted(requirements);
        if (inputs.isEmpty()) {
            return null;
        }
        return new Converted(inputs, requirements, new ItemStack(spawnEgg), source.aura, source.time);
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(RecipeHolder<AnimalSpawnerRecipe> holder, Converted converted) {
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
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
    }

    private record Converted(
            List<CountedIngredient> inputs,
            Map<Ingredient, Long> requirements,
            ItemStack output,
            long energy,
            int time) {
    }
}
