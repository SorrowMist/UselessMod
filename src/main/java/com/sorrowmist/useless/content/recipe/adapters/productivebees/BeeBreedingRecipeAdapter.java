package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import cy.jdkdigital.productivebees.ProductiveBees;
import cy.jdkdigital.productivebees.ProductiveBeesConfig;
import cy.jdkdigital.productivebees.common.crafting.ingredient.BeeIngredient;
import cy.jdkdigital.productivebees.common.crafting.ingredient.BeeIngredientFactory;
import cy.jdkdigital.productivebees.common.entity.bee.ProductiveBee;
import cy.jdkdigital.productivebees.common.recipe.BeeBreedingRecipe;
import cy.jdkdigital.productivebees.init.ModBlocks;
import cy.jdkdigital.productivebees.init.ModRecipeTypes;
import cy.jdkdigital.productivebees.setup.BeeReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Productive Bees breeding-chamber recipes into multi-mold alloy-furnace recipes. */
public final class BeeBreedingRecipeAdapter implements IRecipeAdapter<BeeBreedingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<BeeBreedingRecipe> getRecipeClass() {
        return BeeBreedingRecipe.class;
    }

    @Override
    public List<RecipeHolder<BeeBreedingRecipe>> getGeneratedRecipes(Level level) {
        if (level == null || !isBeeBreedingEnabled()) return List.of();

        List<RecipeHolder<BeeBreedingRecipe>> generated = new ArrayList<>();
        for (Map.Entry<String, BeeIngredient> entry : BeeIngredientFactory.getOrCreateList().entrySet()) {
            BeeIngredient bee = entry.getValue();
            if (bee == null || !canSelfBreed(entry.getKey(), bee, level)) continue;

            try {
                ResourceLocation beeId = ResourceLocation.parse(entry.getKey());
                ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                        ProductiveBees.MODID, "bee_breeding_" + beeId.getPath() + "_self");
                generated.add(new RecipeHolder<>(recipeId,
                        new BeeBreedingRecipe(() -> bee, () -> bee, () -> bee, 0f)));
            } catch (RuntimeException exception) {
                LOGGER.debug("Skipping generated Productive Bees self-breeding recipe: {}",
                        entry.getKey(), exception);
            }
        }
        return List.copyOf(generated);
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModBlocks.BREEDING_CHAMBER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BeeBreedingRecipe> holder, Level level) {
        if (!isBeeBreedingEnabled() || holder == null || holder.value() == null) return List.of();

        BeeBreedingRecipe source = holder.value();
        BeeIngredient parent1 = ProductiveBeesAdapterUtils.resolveBee(source.parent1);
        BeeIngredient parent2 = ProductiveBeesAdapterUtils.resolveBee(source.parent2);
        BeeIngredient offspring = ProductiveBeesAdapterUtils.resolveBee(source.offspring);
        if (parent1 == null || parent2 == null || offspring == null
                || parent1.getBeeType() == null || parent2.getBeeType() == null
                || offspring.getBeeType() == null) {
            LOGGER.warn("Skipping Productive Bees breeding recipe with unresolved bee ingredient: {}",
                    holder.id());
            return List.of();
        }

        ItemStack parentEgg1 = ProductiveBeesAdapterUtils.spawnEgg(parent1);
        ItemStack parentEgg2 = ProductiveBeesAdapterUtils.spawnEgg(parent2);
        ItemStack offspringEgg = ProductiveBeesAdapterUtils.spawnEgg(offspring);
        if (parentEgg1.isEmpty() || parentEgg2.isEmpty() || offspringEgg.isEmpty()) {
            LOGGER.warn("Skipping Productive Bees breeding recipe with unresolved spawn egg: {}",
                    holder.id());
            return List.of();
        }

        int parent1ItemCount = ProductiveBeesAdapterUtils.breedingItemCount(parent1.getBeeType());
        int parent2ItemCount = ProductiveBeesAdapterUtils.breedingItemCount(parent2.getBeeType());
        if (parent1ItemCount < 0 || parent2ItemCount < 0) {
            LOGGER.warn("Skipping Productive Bees breeding recipe with negative breeding item count: {}",
                    holder.id());
            return List.of();
        }

        Map<Ingredient, Long> requiredFlowers = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(requiredFlowers,
                ProductiveBeesAdapterUtils.breedingIngredient(parent1.getBeeType()),
                parent1ItemCount);
        AdapterUtils.mergeIngredient(requiredFlowers,
                ProductiveBeesAdapterUtils.breedingIngredient(parent2.getBeeType()),
                parent2ItemCount);

        List<CountedIngredient> inputs = requiredFlowers.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        if (inputs.isEmpty()) return List.of();

        List<Ingredient> molds = List.of(
                AdapterUtils.toMoldIngredient(getMoldItem()),
                cy.jdkdigital.productivebees.common.crafting.ingredient.ComponentIngredient.of(parentEgg1),
                cy.jdkdigital.productivebees.common.crafting.ingredient.ComponentIngredient.of(parentEgg2));
        int processTime = Math.max(1, source.getProcessingTime());

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                List.of(),
                List.of(),
                List.of(offspringEgg),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                processTime,
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL,
                AdvancedAlloyFurnaceRecipe.NO_EXPLICIT_TIER));
    }

    @Override
    public List<RecipeHolder<BeeBreedingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (!isBeeBreedingEnabled() || level == null || !matchesMold(mold)
                || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<BeeBreedingRecipe>> matches = new ArrayList<>();
        List<RecipeHolder<BeeBreedingRecipe>> recipes = new ArrayList<>(recipeManager.getAllRecipesFor(
                ModRecipeTypes.BEE_BREEDING_TYPE.get()));
        recipes.addAll(getGeneratedRecipes(level));
        for (RecipeHolder<BeeBreedingRecipe> holder : recipes) {
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(holder, level);
            if (!converted.isEmpty() && matchesInputs(converted.getFirst(), mergedInputs)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static boolean matchesInputs(
            AdvancedAlloyFurnaceRecipe recipe, Map<Ingredient, Long> mergedInputs) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
        }
        return AdapterUtils.matchesRequired(mergedInputs, required);
    }

    private static boolean canSelfBreed(String identifier, BeeIngredient bee, Level level) {
        ResourceLocation beeId;
        try {
            beeId = ResourceLocation.parse(identifier);
        } catch (RuntimeException exception) {
            return false;
        }

        var data = BeeReloadListener.INSTANCE.getData(beeId);
        boolean canSelfBreed = !ProductiveBees.MODID.equals(beeId.getNamespace())
                || data == null
                || data.getBoolean("selfbreed");
        if (canSelfBreed && data == null) {
            var entity = bee.getCachedEntity(level);
            canSelfBreed = !(entity instanceof ProductiveBee productiveBee)
                    || productiveBee.canSelfBreed();
        }
        return canSelfBreed;
    }

    private static boolean isBeeBreedingEnabled() {
        return ProductiveBeesConfig.BEES.allowBeeBreeding.get();
    }
}
