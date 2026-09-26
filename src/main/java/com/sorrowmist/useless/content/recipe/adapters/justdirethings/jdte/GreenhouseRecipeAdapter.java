package com.sorrowmist.useless.content.recipe.adapters.justdirethings.jdte;

import com.jdte.common.content.JDTEContentControl;
import com.jdte.common.integrations.MysticalAgricultureGreenhouseIntegration;
import com.jdte.common.recipes.GreenhouseCropDefinition;
import com.jdte.common.recipes.GreenhouseCropResolver;
import com.jdte.common.recipes.GreenhouseRecipe;
import com.jdte.setup.JDTEBlocks;
import com.jdte.setup.JDTEConfig;
import com.jdte.setup.JDTERecipes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** Converts JDTE Greenhouse recipes into alloy-furnace recipes. */
public final class GreenhouseRecipeAdapter implements IRecipeAdapter<GreenhouseRecipe> {
    private static final Map<RecipeManager, GeneratedRecipes> GENERATED_RECIPES =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public String sourceId() {
        return RecipeSourceIds.JUSTDIRETHINGS;
    }

    @Override
    public Class<GreenhouseRecipe> getRecipeClass() {
        return GreenhouseRecipe.class;
    }

    /** The reusable seed is the recipe-specific mold. */
    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    /** Mirrors JDTE's runtime crop discovery so every supported plant gets a recipe entry. */
    @Override
    public List<RecipeHolder<GreenhouseRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();

        JDTEContentControl control = JDTEContentControl.current();
        if (!control.isDynamicRecipeGenerationEnabled(
                JDTEContentControl.DynamicRecipeFamily.GREENHOUSE)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        long generation = GreenhouseCropResolver.cacheGeneration();
        GeneratedRecipes cached;
        synchronized (GENERATED_RECIPES) {
            cached = GENERATED_RECIPES.get(recipeManager);
            if (cached == null || cached.generation() != generation) {
                cached = new GeneratedRecipes(generation, buildGeneratedRecipes(level, control));
                GENERATED_RECIPES.put(recipeManager, cached);
            }
        }
        return cached.recipes().stream()
                .filter(holder -> control.isRecipeEnabled(holder.id()))
                .toList();
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(
            RecipeHolder<GreenhouseRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return null;

        GreenhouseRecipe source = holder.value();
        Ingredient seed = source.seed();
        List<ItemStack> outputs = copyOutputs(source.outputs());
        int fluidAmount = fluidCost(source.timeFluid());
        int processTime = processTime(source.growthWork());
        int energy = Math.max(0, JDTEConfig.COMMON.greenhouseEnergyPerHarvestV2.get());
        FluidStack fluid = resolveFluid(source.fluid(), fluidAmount);
        if (seed == null || seed.isEmpty() || outputs.isEmpty()
                || fluid == null || processTime <= 0) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(),
                List.of(LongSizedFluidIngredient.from(fluid)),
                List.of(),
                outputs,
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(
                        Ingredient.of(
                                JDTEBlocks.GREENHOUSE.get(),
                                JDTEBlocks.LARGE_GREENHOUSE.get()),
                        seed),
                AlloyFurnaceMode.NORMAL);
    }

    @Override
    public List<RecipeHolder<GreenhouseRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mold == null || mold.isEmpty()) return List.of();

        Map<FluidStack, Long> availableFluids = mergedFluids == null ? Map.of() : mergedFluids;
        List<RecipeHolder<GreenhouseRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<GreenhouseRecipe> holder : allRecipes(level)) {
            AdvancedAlloyFurnaceRecipe converted = convert(holder, level);
            if (converted == null
                    // This lookup has one legacy mold argument. Use the dynamic seed to select
                    // the source recipe; the machine + seed pair is checked by the mold hub.
                    || !AdapterUtils.matchesMold(holder.value().seed(), mold)
                    || !FluidIngredientAllocator.matchesLong(
                    converted.inputFluids(), availableFluids, 1L)) {
                continue;
            }
            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    private List<RecipeHolder<GreenhouseRecipe>> allRecipes(Level level) {
        JDTEContentControl control = JDTEContentControl.current();
        Map<ResourceLocation, RecipeHolder<GreenhouseRecipe>> recipes = new LinkedHashMap<>();
        for (RecipeHolder<GreenhouseRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(JDTERecipes.GREENHOUSE_RECIPE_TYPE.get())) {
            if (holder != null && holder.value() != null && control.isRecipeEnabled(holder.id())) {
                recipes.putIfAbsent(holder.id(), holder);
            }
        }
        for (RecipeHolder<GreenhouseRecipe> holder : getGeneratedRecipes(level)) {
            if (holder != null && holder.value() != null) {
                recipes.putIfAbsent(holder.id(), holder);
            }
        }
        return List.copyOf(recipes.values());
    }

    private static List<RecipeHolder<GreenhouseRecipe>> buildGeneratedRecipes(
            Level level, JDTEContentControl control) {
        RecipeManager recipeManager = level.getRecipeManager();
        Set<Item> seen = new HashSet<>();
        for (RecipeHolder<GreenhouseRecipe> holder : recipeManager
                .getAllRecipesFor(JDTERecipes.GREENHOUSE_RECIPE_TYPE.get())) {
            if (holder == null || holder.value() == null || !control.isRecipeEnabled(holder.id())) {
                continue;
            }
            addStaticSeeds(seen, holder.value().seed());
        }
        Map<ResourceLocation, RecipeHolder<GreenhouseRecipe>> generated = new LinkedHashMap<>();
        if (ModList.get().isLoaded("mysticalagriculture")) {
            List<Map.Entry<Item, GreenhouseCropDefinition>> crops = new ArrayList<>(
                    MysticalAgricultureGreenhouseIntegration.getCrops().entrySet());
            crops.sort(Comparator.comparing(entry -> itemId(entry.getKey()).toString()));
            for (Map.Entry<Item, GreenhouseCropDefinition> entry : crops) {
                Item item = entry.getKey();
                GreenhouseCropDefinition definition = entry.getValue();
                if (item == null || !seen.add(item) || definition == null
                        || definition.outputs().isEmpty()) {
                    continue;
                }
                addGeneratedRecipe(generated, dynamicRecipeId(item),
                        item.getDefaultInstance(), definition);
            }
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || seen.contains(item) || !(item instanceof BlockItem)) continue;

            ItemStack seed = item.getDefaultInstance();
            if (seed.isEmpty() || !control.isItemEnabled(seed)) continue;
            GreenhouseCropDefinition definition = GreenhouseCropResolver.findGeneric(seed);
            if (definition == null || definition.outputs().isEmpty()) continue;

            seen.add(item);
            addGeneratedRecipe(generated, dynamicRecipeId(item), seed, definition);
        }

        return generated.values().stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    private static void addStaticSeeds(Set<Item> seen, @Nullable Ingredient seed) {
        if (seed == null || seed.isEmpty()) return;
        try {
            for (ItemStack stack : seed.getItems()) {
                if (stack != null && !stack.isEmpty()) seen.add(stack.getItem());
            }
        } catch (RuntimeException ignored) {
            // Opaque custom ingredients cannot expose concrete seeds for de-duplication.
        }
    }

    private static void addGeneratedRecipe(
            Map<ResourceLocation, RecipeHolder<GreenhouseRecipe>> recipes,
            ResourceLocation id, ItemStack seed, GreenhouseCropDefinition definition) {
        if (id == null || seed == null || seed.isEmpty() || definition == null
                || definition.outputs().isEmpty() || definition.displayBlock() == null
                || definition.fluid() == null) {
            return;
        }

        GreenhouseRecipe recipe = new GreenhouseRecipe(
                Ingredient.of(seed.copyWithCount(1)),
                copyOutputs(definition.outputs()),
                definition.displayBlock(),
                Optional.ofNullable(definition.harvestBlock()),
                definition.useLootTable(),
                definition.growthWork(),
                definition.fluid(),
                definition.timeFluid());
        recipes.putIfAbsent(id, new RecipeHolder<>(id, recipe));
    }

    private static ResourceLocation dynamicRecipeId(Item item) {
        ResourceLocation itemId = itemId(item);
        return ResourceLocation.fromNamespaceAndPath("jdte",
                "jei/greenhouse/" + itemId.getNamespace() + "/" + itemId.getPath());
    }

    private static ResourceLocation itemId(Item item) {
        ResourceLocation id = item == null ? null : BuiltInRegistries.ITEM.getKey(item);
        return id == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "air") : id;
    }

    private static List<ItemStack> copyOutputs(@Nullable List<ItemStack> sourceOutputs) {
        if (sourceOutputs == null || sourceOutputs.isEmpty()) return List.of();

        List<ItemStack> outputs = new ArrayList<>(sourceOutputs.size());
        for (ItemStack output : sourceOutputs) {
            if (output != null && !output.isEmpty()) outputs.add(output.copy());
        }
        return List.copyOf(outputs);
    }

    private static int fluidCost(int rawAmount) {
        int divisor = JDTEConfig.COMMON.greenhouseFluidCostDivisor.get();
        if (rawAmount <= 0 || divisor <= 0) return 0;

        long reduced = ((long) rawAmount + divisor - 1L) / divisor;
        return reduced > Integer.MAX_VALUE ? 0 : Math.max(1, (int) reduced);
    }

    private static int processTime(int growthWork) {
        int baseMultiplier = JDTEConfig.COMMON.greenhouseBaseMultiplier.get();
        if (growthWork <= 0 || baseMultiplier <= 0) return 0;

        long ticks = ((long) growthWork + baseMultiplier - 1L) / baseMultiplier;
        return ticks > Integer.MAX_VALUE ? 0 : Math.max(1, (int) ticks);
    }

    @Nullable
    private static FluidStack resolveFluid(
            @Nullable ResourceLocation id, int amount) {
        if (id == null || amount <= 0) return null;
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (fluid == null || fluid == Fluids.EMPTY || !fluid.defaultFluidState().isSource()) {
            return null;
        }
        return new FluidStack(fluid, amount);
    }

    private record GeneratedRecipes(long generation,
                                    List<RecipeHolder<GreenhouseRecipe>> recipes) {
    }
}
