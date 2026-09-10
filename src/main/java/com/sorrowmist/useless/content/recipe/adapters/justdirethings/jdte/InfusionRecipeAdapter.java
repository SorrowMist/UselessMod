package com.sorrowmist.useless.content.recipe.adapters.justdirethings.jdte;

import appeng.api.stacks.AEKey;
import com.jdte.common.blockentities.AdvancedInfusionMachineBE;
import com.jdte.common.blockentities.InfusionMachineBE;
import com.jdte.common.recipes.InfusionRecipe;
import com.jdte.common.utils.InfusionFluidHelper;
import com.jdte.common.utils.MobLootSpawnEggHelper;
import com.jdte.setup.JDTEBlocks;
import com.jdte.setup.JDTEFluids;
import com.jdte.setup.JDTERecipes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Converts JDTE Advanced Infusion Machine recipes to alloy-furnace recipes. */
public final class InfusionRecipeAdapter implements IRecipeAdapter<InfusionRecipe> {
    private static final Map<RecipeManager, List<RecipeHolder<InfusionRecipe>>> GENERATED_BASE_RECIPES =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public String sourceId() {
        return RecipeSourceIds.JUSTDIRETHINGS;
    }

    @Override
    public Class<InfusionRecipe> getRecipeClass() {
        return InfusionRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(JDTEBlocks.ADVANCED_INFUSION_MACHINE.get());
    }

    /**
     * JDTE also creates these operations directly in the machine block entity instead of placing
     * them in RecipeManager: fluid-container filling, vanilla bottle filling, and loot-derived
     * spawn-egg conversion. Keep those operations in the shared recipe directory as well.
     */
    @Override
    public List<RecipeHolder<InfusionRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();

        List<RecipeHolder<InfusionRecipe>> result = new ArrayList<>(baseGeneratedRecipes(level));
        addSpawnEggRecipes(level, result);
        return List.copyOf(result);
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<InfusionRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return null;

        InfusionRecipe source = holder.value();
        ItemStack input = source.getInput();
        FluidStack fluid = source.getFluidInput();
        ItemStack output = source.getOutput();
        if (!isValid(input, fluid, output)) return null;

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(itemIngredient(input), input.getCount())),
                List.of(LongSizedFluidIngredient.from(fluid)),
                List.of(),
                List.of(output),
                List.of(),
                List.of(),
                Math.max(1, source.getEnergyCost()),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                AlloyFurnaceMode.NORMAL);
    }

    @Override
    public List<RecipeHolder<InfusionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mold == null || mold.isEmpty()) return List.of();

        Map<Ingredient, Long> availableInputs = mergedInputs == null ? Map.of() : mergedInputs;
        Map<FluidStack, Long> availableFluids = mergedFluids == null ? Map.of() : mergedFluids;
        Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches = new LinkedHashMap<>();
        for (RecipeHolder<InfusionRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(JDTERecipes.INFUSION_RECIPE_TYPE.get())) {
            addMatchingHolder(matches, holder, availableInputs, availableFluids);
        }
        for (RecipeHolder<InfusionRecipe> holder : getGeneratedRecipes(level)) {
            addMatchingHolder(matches, holder, availableInputs, availableFluids);
        }
        return List.copyOf(matches.values());
    }

    @Override
    public List<RecipeHolder<InfusionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || mold == null || mold.isEmpty()) return List.of();

        Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches = new LinkedHashMap<>();
        for (RecipeHolder<InfusionRecipe> holder : findMatchingRecipes(
                level, mergedInputs, mergedFluids, mold)) {
            matches.put(holder.id(), holder);
        }

        if (actualInputs == null || actualInputs.isEmpty()) {
            return List.copyOf(matches.values());
        }

        Map<FluidStack, Long> availableFluids = mergedFluids == null ? Map.of() : mergedFluids;
        for (ItemStack input : actualInputs) {
            if (input == null || input.isEmpty()) continue;
            addRuntimeContainerMatches(matches, input, availableFluids);
            addRuntimeBottleMatches(matches, input, availableFluids);
            addRuntimeSpawnEggMatch(matches, level, input, availableFluids);
        }
        return List.copyOf(matches.values());
    }

    private static List<RecipeHolder<InfusionRecipe>> baseGeneratedRecipes(Level level) {
        RecipeManager recipeManager = level.getRecipeManager();
        synchronized (GENERATED_BASE_RECIPES) {
            return GENERATED_BASE_RECIPES.computeIfAbsent(
                    recipeManager, ignored -> buildBaseGeneratedRecipes());
        }
    }

    private static List<RecipeHolder<InfusionRecipe>> buildBaseGeneratedRecipes() {
        Map<ResourceLocation, RecipeHolder<InfusionRecipe>> recipes = new LinkedHashMap<>();

        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack input = item.getDefaultInstance();
            if (input.isEmpty()) continue;

            for (Fluid fluid : BuiltInRegistries.FLUID) {
                if (!InfusionFluidHelper.isFillableSourceFluid(fluid)) continue;
                try {
                    RecipeHolder<InfusionRecipe> holder = createFluidContainerRecipe(
                            input, new FluidStack(fluid, InfusionMachineBE.BASE_FLUID_CAPACITY));
                    if (holder != null) recipes.putIfAbsent(holder.id(), holder);
                } catch (RuntimeException ignored) {
                    // A third-party fluid container may reject capability probing.
                }
            }
        }

        addBottleRecipe(recipes, new FluidStack(Fluids.WATER,
                InfusionFluidHelper.BOTTLE_FLUID_AMOUNT));
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY || !InfusionFluidHelper.isFillableSourceFluid(fluid)
                    || !InfusionFluidHelper.isHoneyFluid(fluid)) {
                continue;
            }
            addBottleRecipe(recipes, new FluidStack(fluid,
                    InfusionFluidHelper.BOTTLE_FLUID_AMOUNT));
        }
        return List.copyOf(recipes.values());
    }

    private static void addBottleRecipe(
            Map<ResourceLocation, RecipeHolder<InfusionRecipe>> recipes, FluidStack fluid) {
        RecipeHolder<InfusionRecipe> holder = createBottleRecipe(fluid);
        if (holder != null) recipes.putIfAbsent(holder.id(), holder);
    }

    @Nullable
    private static RecipeHolder<InfusionRecipe> createFluidContainerRecipe(
            ItemStack input, FluidStack available) {
        if (input == null || input.isEmpty() || available == null || available.isEmpty()) return null;

        ItemStack container = input.copyWithCount(1);
        ItemStack originalContainer = container.copy();
        IFluidHandlerItem handler = container.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null || handler.getTanks() <= 0) return null;

        int fillAmount = handler.fill(available.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (fillAmount <= 0 || fillAmount > available.getAmount()) return null;

        FluidStack toFill = available.copyWithAmount(fillAmount);
        int filled = handler.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) return null;

        ItemStack result = handler.getContainer();
        if (result.isEmpty() || ItemStack.isSameItemSameComponents(originalContainer, result)) {
            return null;
        }
        result.setCount(1);
        return holder("container", originalContainer,
                available.copyWithAmount(filled), result,
                AdvancedInfusionMachineBE.BASE_ENERGY_COST);
    }

    @Nullable
    private static RecipeHolder<InfusionRecipe> createBottleRecipe(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()
                || fluid.getAmount() < InfusionFluidHelper.BOTTLE_FLUID_AMOUNT) return null;

        ItemStack output;
        if (fluid.is(Fluids.WATER)) {
            output = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        } else if (InfusionFluidHelper.isHoneyFluid(fluid)) {
            output = new ItemStack(Items.HONEY_BOTTLE);
        } else {
            return null;
        }
        output.setCount(1);
        return holder("bottle", Items.GLASS_BOTTLE.getDefaultInstance(),
                fluid.copyWithAmount(InfusionFluidHelper.BOTTLE_FLUID_AMOUNT), output,
                AdvancedInfusionMachineBE.BASE_ENERGY_COST);
    }

    private static void addSpawnEggRecipes(
            Level level, List<RecipeHolder<InfusionRecipe>> result) {
        for (Map.Entry<Item, ItemStack> entry : spawnEggRecipes(level).entrySet()) {
            Item inputItem = entry.getKey();
            ItemStack output = entry.getValue();
            if (inputItem == null || output == null || output.isEmpty()) continue;

            ItemStack input = inputItem.getDefaultInstance();
            if (input.isEmpty() || input.getMaxStackSize() <= 1) continue;
            input.setCount(input.getMaxStackSize());
            RecipeHolder<InfusionRecipe> holder = holder(
                    "spawn_egg", input,
                    new FluidStack(JDTEFluids.LIFE_FLUID_SOURCE.get(),
                            MobLootSpawnEggHelper.LIFE_FLUID_COST),
                    output.copyWithCount(1), MobLootSpawnEggHelper.ENERGY_COST);
            result.add(holder);
        }
    }

    private static void addRuntimeContainerMatches(
            Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches,
            ItemStack input, Map<FluidStack, Long> availableFluids) {
        for (FluidStack available : simulationFluids(availableFluids)) {
            RecipeHolder<InfusionRecipe> holder;
            try {
                holder = createFluidContainerRecipe(input, available);
            } catch (RuntimeException ignored) {
                continue;
            }
            addIfFluidIsAvailable(matches, holder, availableFluids);
        }
    }

    private static void addRuntimeBottleMatches(
            Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches,
            ItemStack input, Map<FluidStack, Long> availableFluids) {
        if (!input.is(Items.GLASS_BOTTLE)) return;
        for (FluidStack available : simulationFluids(availableFluids)) {
            RecipeHolder<InfusionRecipe> holder = createBottleRecipe(available);
            addIfFluidIsAvailable(matches, holder, availableFluids);
        }
    }

    private static void addRuntimeSpawnEggMatch(
            Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches,
            Level level, ItemStack input, Map<FluidStack, Long> availableFluids) {
        ItemStack output = spawnEggRecipes(level).get(input.getItem());
        if (output == null || output.isEmpty()
                || input.getMaxStackSize() <= 1
                || input.getCount() < input.getMaxStackSize()) {
            return;
        }

        RecipeHolder<InfusionRecipe> holder = holder(
                "spawn_egg", input.copyWithCount(input.getMaxStackSize()),
                new FluidStack(JDTEFluids.LIFE_FLUID_SOURCE.get(),
                        MobLootSpawnEggHelper.LIFE_FLUID_COST),
                output.copyWithCount(1), MobLootSpawnEggHelper.ENERGY_COST);
        addIfFluidIsAvailable(matches, holder, availableFluids);
    }

    private static List<FluidStack> simulationFluids(Map<FluidStack, Long> availableFluids) {
        if (availableFluids == null || availableFluids.isEmpty()) return List.of();
        List<FluidStack> result = new ArrayList<>();
        for (Map.Entry<FluidStack, Long> entry : availableFluids.entrySet()) {
            FluidStack stack = entry.getKey();
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (stack == null || stack.isEmpty() || amount <= 0L) continue;
            long simulationAmount = Math.min(
                    Math.min(amount, InfusionMachineBE.BASE_FLUID_CAPACITY), Integer.MAX_VALUE);
            if (simulationAmount > 0L) {
                result.add(stack.copyWithAmount((int) simulationAmount));
            }
        }
        return List.copyOf(result);
    }

    private static void addIfFluidIsAvailable(
            Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches,
            @Nullable RecipeHolder<InfusionRecipe> holder,
            Map<FluidStack, Long> availableFluids) {
        if (holder == null || !hasFluid(availableFluids, holder.value().getFluidInput())) return;
        matches.putIfAbsent(holder.id(), holder);
    }

    private static boolean hasFluid(Map<FluidStack, Long> availableFluids, FluidStack required) {
        if (required == null || required.isEmpty() || availableFluids == null) return false;
        for (Map.Entry<FluidStack, Long> entry : availableFluids.entrySet()) {
            FluidStack available = entry.getKey();
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (available != null && FluidStack.isSameFluidSameComponents(available, required)
                    && amount >= required.getAmount()) {
                return true;
            }
        }
        return false;
    }

    private static void addMatchingHolder(
            Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches,
            RecipeHolder<InfusionRecipe> holder,
            Map<Ingredient, Long> availableInputs,
            Map<FluidStack, Long> availableFluids) {
        if (holder == null || holder.value() == null) return;
        InfusionRecipe source = holder.value();
        ItemStack input = source.getInput();
        FluidStack fluid = source.getFluidInput();
        ItemStack output = source.getOutput();
        if (!isValid(input, fluid, output)
                || !AdapterUtils.matchesRequired(availableInputs,
                Map.of(itemIngredient(input), (long) input.getCount()))
                || !FluidIngredientAllocator.matchesLong(
                List.of(LongSizedFluidIngredient.from(fluid)), availableFluids, 1L)) {
            return;
        }
        matches.putIfAbsent(holder.id(), holder);
    }

    private static RecipeHolder<InfusionRecipe> holder(
            String kind, ItemStack input, FluidStack fluid, ItemStack output, int energyCost) {
        ResourceLocation id = dynamicId(kind, input, fluid, output);
        return new RecipeHolder<>(id, new InfusionRecipe(
                id, input.copy(), fluid.copy(), output.copy(), Math.max(1, energyCost)));
    }

    private static ResourceLocation dynamicId(
            String kind, ItemStack input, FluidStack fluid, ItemStack output) {
        String signature = kind + "|" + stackSignature(input)
                + "|" + fluidSignature(fluid) + "|" + stackSignature(output);
        return ResourceLocation.fromNamespaceAndPath(
                "jdte", "infusion/" + kind + "/"
                        + Integer.toUnsignedString(signature.hashCode(), 16));
    }

    private static String stackSignature(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "@"
                + stack.getCount() + "@" + stack.getComponents();
    }

    private static String fluidSignature(FluidStack stack) {
        return BuiltInRegistries.FLUID.getKey(stack.getFluid()) + "@"
                + stack.getAmount() + "@" + stack.getComponents();
    }

    private static Map<Item, ItemStack> spawnEggRecipes(Level level) {
        if (level instanceof ServerLevel serverLevel && serverLevel.getServer() != null) {
            return MobLootSpawnEggHelper.getRecipes(serverLevel.getServer().getResourceManager());
        }

        // JDTE synchronizes the server's loot-derived map to remote clients. Use reflection here so
        // this common-side adapter never links a client-only JDTE class on a dedicated server.
        try {
            Class<?> cache = Class.forName("com.jdte.client.SpawnEggRecipeClientCache");
            Object value = cache.getMethod("get").invoke(null);
            if (!(value instanceof Map<?, ?> synced)) return Map.of();

            Map<Item, ItemStack> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : synced.entrySet()) {
                if (!(entry.getKey() instanceof ResourceLocation inputId)
                        || !(entry.getValue() instanceof ResourceLocation outputId)) continue;
                Item input = BuiltInRegistries.ITEM.getOptional(inputId).orElse(null);
                Item output = BuiltInRegistries.ITEM.getOptional(outputId).orElse(null);
                if (input != null && output != null) result.put(input, output.getDefaultInstance());
            }
            return Map.copyOf(result);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return Map.of();
        }
    }

    private static Ingredient itemIngredient(ItemStack stack) {
        return DataComponentIngredient.of(true, stack.copyWithCount(1));
    }

    private static boolean isValid(ItemStack input, FluidStack fluid, ItemStack output) {
        return input != null && !input.isEmpty() && input.getCount() > 0
                && fluid != null && !fluid.isEmpty() && fluid.getAmount() > 0
                && output != null && !output.isEmpty();
    }
}
