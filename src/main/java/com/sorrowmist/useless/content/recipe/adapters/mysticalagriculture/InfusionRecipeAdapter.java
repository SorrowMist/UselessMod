package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.crafting.IInfusionRecipe;
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
 * Converts Mystical Agriculture 7.0.24 infusion-altar recipes.
 *
 * <p>1.20.1's {@link IInfusionRecipe} does not expose a separate altar-ingredient accessor. The
 * native recipe stores nine entries in {@link IInfusionRecipe#getIngredients()}: index 0 is the
 * altar input and indexes 1-8 are the pedestal ingredients. All nine are consumed once per
 * operation, so the converted recipe keeps them as one count each.</p>
 */
public final class InfusionRecipeAdapter implements IRecipeAdapter<IInfusionRecipe> {
    private static final ResourceLocation ALTAR_ID =
            ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "infusion_altar");
    private static final int ENERGY = 2000;

    @Override
    public Class<IInfusionRecipe> getRecipeClass() {
        return IInfusionRecipe.class;
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
            RecipeHolder<IInfusionRecipe> holder, Level level) {
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
    public List<RecipeHolder<IInfusionRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<IInfusionRecipe>> recipes = RecipeManagerAccess.getAllRecipesFor(
                recipeManager, ModRecipeTypes.INFUSION.get());
        List<RecipeHolder<IInfusionRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<IInfusionRecipe> holder : recipes) {
            Converted converted = convertData(holder.value(), level);
            if (converted != null
                    && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    /**
     * 1.20.1 input layout: 0 = altar item, 1-8 = pedestal items. Each entry is consumed once.
     * The process time counts only the pedestal entries so the 1.20.1 recipe keeps the same
     * 20 + 8 * 15 tick profile as the original wrapper's separate altar/pedestal view.
     */
    @Nullable
    private static Converted convertData(@Nullable IInfusionRecipe source, @Nullable Level level) {
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
        for (int index = 0; index < allIngredients.size(); index++) {
            Ingredient ingredient = allIngredients.get(index);
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            MysticalAgricultureAdapterUtils.addIngredient(requirements, ingredient, 1L);
            if (index > 0) {
                pedestals++;
            }
        }

        List<CountedIngredient> inputs = MysticalAgricultureAdapterUtils.counted(requirements);
        if (inputs.isEmpty()) {
            return null;
        }

        int processTime = AdapterUtils.DEFAULT_PROCESS_TIME + pedestals * 15;
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
