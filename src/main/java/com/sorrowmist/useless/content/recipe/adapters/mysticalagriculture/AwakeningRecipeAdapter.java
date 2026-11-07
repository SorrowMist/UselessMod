package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.crafting.IAwakeningRecipe;
import com.blakebr0.mysticalagriculture.init.ModRecipeTypes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts Mystical Agriculture 7.0.24 awakening-altar recipes.
 *
 * <p>1.20.1's {@link IAwakeningRecipe} does not expose a separate altar-ingredient accessor. The
 * native recipe exposes nine interleaved ingredient entries: index 0 is the altar input, indexes
 * 2, 4, 6 and 8 are the four pedestal items, and indexes 1, 3, 5 and 7 are one-item views of the
 * essence stacks. The exact essence quantities live in {@link IAwakeningRecipe#getEssences()}, so
 * those stacks are used instead of the one-item ingredient views.</p>
 */
public final class AwakeningRecipeAdapter implements IRecipeAdapter<IAwakeningRecipe> {
    private static final ResourceLocation ALTAR_ID =
            ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "awakening_altar");
    private static final int ENERGY = 5000;

    @Override
    public Class<IAwakeningRecipe> getRecipeClass() {
        return IAwakeningRecipe.class;
    }

    @Nullable
    @Override
    public ItemStack getMoldItem() {
        return MysticalAgricultureAdapterUtils.item(ALTAR_ID.getPath());
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null
                && !mold.isEmpty()
                && BuiltInRegistries.ITEM.getKey(mold.getItem()).equals(ALTAR_ID);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<IAwakeningRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value(), level);
        if (converted == null) {
            return List.of();
        }
        return List.of(createRecipe(holder.id(), converted));
    }

    @Override
    @Nullable
    public List<RecipeHolder<IAwakeningRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<IAwakeningRecipe>> recipes = RecipeManagerAccess.getAllRecipesFor(
                recipeManager, ModRecipeTypes.AWAKENING.get());
        List<RecipeHolder<IAwakeningRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<IAwakeningRecipe> holder : recipes) {
            Converted converted = convertData(holder.value(), level);
            if (converted != null
                    && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    /**
     * 1.20.1 layout: altar input at 0, pedestal items at 2/4/6/8, and essence stacks exposed by
     * {@link IAwakeningRecipe#getEssences()} with their real counts. Essence NBT is retained with
     * a strict-NBT ingredient so component-sensitive recipes keep their exact predicate.
     */
    @Nullable
    private static Converted convertData(@Nullable IAwakeningRecipe source, @Nullable Level level) {
        if (source == null || !MysticalAgricultureAdapterUtils.isStaticOutputRecipe(source)) {
            return null;
        }

        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        if (output == null || output.isEmpty() || output.getCount() <= 0) {
            return null;
        }

        List<Ingredient> allIngredients = source.getIngredients();
        if (allIngredients == null || allIngredients.isEmpty()) {
            return null;
        }

        Map<Ingredient, Long> requirements = MysticalAgricultureAdapterUtils.requirements();
        int pedestals = 0;

        Ingredient altarIngredient = allIngredients.get(0);
        if (altarIngredient != null && !altarIngredient.isEmpty()) {
            MysticalAgricultureAdapterUtils.addIngredient(requirements, altarIngredient, 1L);
        }
        for (int index = 2; index < allIngredients.size(); index += 2) {
            Ingredient pedestal = allIngredients.get(index);
            if (pedestal == null || pedestal.isEmpty()) {
                continue;
            }
            MysticalAgricultureAdapterUtils.addIngredient(requirements, pedestal, 1L);
            pedestals++;
        }

        int essenceSlots = 0;
        List<ItemStack> essences = source.getEssences();
        if (essences != null) {
            for (ItemStack essence : essences) {
                if (essence == null || essence.isEmpty() || essence.getCount() <= 0) {
                    continue;
                }
                MysticalAgricultureAdapterUtils.addIngredient(
                        requirements,
                        MysticalAgricultureAdapterUtils.exactStack(essence),
                        essence.getCount());
                essenceSlots++;
            }
        }

        List<CountedIngredient> inputs = MysticalAgricultureAdapterUtils.counted(requirements);
        if (inputs.isEmpty()) {
            return null;
        }

        int processTime = AdapterUtils.DEFAULT_PROCESS_TIME
                + (pedestals + essenceSlots) * 15;
        return new Converted(inputs, requirements, output.copy(), processTime);
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(ResourceLocation sourceId, Converted converted) {
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId),
                converted.inputs(),
                List.of(),
                List.of(converted.output().copy()),
                List.of(),
                ENERGY,
                converted.processTime(),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(MysticalAgricultureAdapterUtils.item(ALTAR_ID.getPath())),
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
