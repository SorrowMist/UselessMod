package com.sorrowmist.useless.content.recipe.adapters.modernindustrialization;

import aztech.modern_industrialization.nuclear.FluidNuclearComponent;
import aztech.modern_industrialization.nuclear.NuclearAbsorbable;
import aztech.modern_industrialization.thirdparty.fabrictransfer.api.fluid.FluidVariant;
import aztech.modern_industrialization.thirdparty.fabrictransfer.api.item.ItemVariant;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Modern Industrialization's dynamic neutron absorption into deterministic recipes. */
public final class NuclearAbsorptionRecipeAdapter
        implements IRecipeAdapter<NuclearAbsorptionSyntheticRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "modern_industrialization";
    private static final long ENERGY_PER_NEUTRON = 1_000L;
    private static final int DEFAULT_PROCESS_TIME = AdapterUtils.DEFAULT_PROCESS_TIME;
    private static final ResourceLocation NUCLEAR_REACTOR_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "nuclear_reactor");
    private volatile List<RecipeHolder<NuclearAbsorptionSyntheticRecipe>> cachedRecipes;

    @Override
    public String sourceId() {
        return RecipeSourceIds.MODERN_INDUSTRIALIZATION;
    }

    @Override
    public Class<NuclearAbsorptionSyntheticRecipe> getRecipeClass() {
        return NuclearAbsorptionSyntheticRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        Item item = BuiltInRegistries.ITEM.getOptional(NUCLEAR_REACTOR_ID).orElse(null);
        return item == null ? null : item.getDefaultInstance();
    }

    @Override
    public List<RecipeHolder<NuclearAbsorptionSyntheticRecipe>> getGeneratedRecipes(Level level) {
        List<RecipeHolder<NuclearAbsorptionSyntheticRecipe>> cached = cachedRecipes;
        if (cached != null) return cached;

        synchronized (this) {
            if (cachedRecipes == null) {
                cachedRecipes = createGeneratedRecipes();
            }
            return cachedRecipes;
        }
    }

    private List<RecipeHolder<NuclearAbsorptionSyntheticRecipe>> createGeneratedRecipes() {
        List<RecipeHolder<NuclearAbsorptionSyntheticRecipe>> generated = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof NuclearAbsorbable absorbable)) continue;
            AdvancedAlloyFurnaceRecipe converted;
            try {
                converted = convertItem(absorbable, item);
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping Modern Industrialization nuclear item conversion: {}",
                        BuiltInRegistries.ITEM.getKey(item), exception);
                continue;
            }
            if (converted != null) {
                generated.add(new RecipeHolder<>(converted.id(),
                        new NuclearAbsorptionSyntheticRecipe(converted)));
            }
        }
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            AdvancedAlloyFurnaceRecipe converted;
            try {
                converted = convertFluid(fluid);
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping Modern Industrialization nuclear fluid conversion: {}",
                        BuiltInRegistries.FLUID.getKey(fluid), exception);
                continue;
            }
            if (converted != null) {
                generated.add(new RecipeHolder<>(converted.id(),
                        new NuclearAbsorptionSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(generated);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<NuclearAbsorptionSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<NuclearAbsorptionSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<NuclearAbsorptionSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();

        List<RecipeHolder<NuclearAbsorptionSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<NuclearAbsorptionSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (matches(recipe, mergedInputs, mergedFluids, actualInputs)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static boolean matches(AdvancedAlloyFurnaceRecipe recipe,
                                   Map<Ingredient, Long> mergedInputs,
                                   Map<FluidStack, Long> mergedFluids,
                                   List<ItemStack> actualInputs) {
        boolean items = actualInputs != null && !actualInputs.isEmpty()
                ? ItemIngredientAllocator.matches(recipe.inputs(), actualInputs, 1L)
                : matchesItems(recipe.inputs(), mergedInputs);
        return items && FluidIngredientAllocator.matches(recipe.inputFluids(), mergedFluids, 1L);
    }

    private static boolean matchesItems(List<CountedIngredient> requirements,
                                        Map<Ingredient, Long> mergedInputs) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient input : requirements) {
            AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
        }
        return AdapterUtils.matchesRequired(mergedInputs == null ? Map.of() : mergedInputs, required);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertItem(NuclearAbsorbable absorbable, Item item) {
        int absorptions = absorbable.desintegrationMax;
        if (absorptions <= 0) return null;

        if (!(absorbable.getNeutronProduct() instanceof ItemVariant product)) {
            return null;
        }
        long productAmount = absorbable.getNeutronProductAmount();
        if (productAmount <= 0 || productAmount > Integer.MAX_VALUE) return null;

        ItemStack input = item.getDefaultInstance();
        try {
            absorbable.setRemainingDesintegrations(input, absorptions);
        } catch (RuntimeException exception) {
            return null;
        }

        ItemStack output = product.toStack((int) productAmount);
        if (output.isEmpty()) return null;

        Long energy = multiply(ENERGY_PER_NEUTRON, absorptions);
        Integer processTime = multiplyToInt(DEFAULT_PROCESS_TIME, absorptions);
        if (energy == null || processTime == null) return null;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) return null;
        return createRecipe(
                ResourceLocation.fromNamespaceAndPath(MOD_ID,
                        "nuclear_absorption/item/" + path(itemId)),
                List.of(new CountedIngredient(DataComponentIngredient.of(true, input), 1L)),
                List.of(),
                List.of(output),
                List.of(),
                energy,
                processTime);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertFluid(Fluid fluid) {
        FluidNuclearComponent component = FluidNuclearComponent.get(fluid);
        if (component == null || component.getNeutronProduct() == null
                || component.getNeutronProductAmount() <= 0) {
            return null;
        }

        if (!(component.getNeutronProduct() instanceof FluidVariant product)
                || product.getFluid() == null) return null;
        ModernIndustrializationRecipeAdapter.Rational productProbability =
                ModernIndustrializationRecipeAdapter.probability(
                component.getNeutronProductProbability());
        ModernIndustrializationRecipeAdapter.Rational consumptionProbability =
                ModernIndustrializationRecipeAdapter.divide(
                productProbability, 81L);
        if (consumptionProbability == null || consumptionProbability.isZero()) return null;

        long operations = consumptionProbability.denominator();
        long consumedAmount = ModernIndustrializationRecipeAdapter.scaleAmount(
                1L, consumptionProbability, operations);
        long outputAmount = multiply(consumedAmount, component.getNeutronProductAmount());
        Long energy = multiply(ENERGY_PER_NEUTRON, operations);
        Integer processTime = multiplyToInt(DEFAULT_PROCESS_TIME, operations);
        if (consumedAmount <= 0 || consumedAmount > Integer.MAX_VALUE
                || outputAmount <= 0 || outputAmount > Integer.MAX_VALUE
                || energy == null || processTime == null) {
            return null;
        }

        ResourceLocation sourceId = BuiltInRegistries.FLUID.getKey(fluid);
        FluidVariant source = component.getVariant();
        if (sourceId == null || source == null || source.getFluid() == null) return null;
        List<SizedFluidIngredient> inputFluids = List.of(new SizedFluidIngredient(
                FluidIngredient.single(source.toStack(1)), (int) consumedAmount));
        List<FluidStack> outputFluids = List.of(product.toStack((int) outputAmount));
        return createRecipe(
                ResourceLocation.fromNamespaceAndPath(MOD_ID,
                        "nuclear_absorption/fluid/" + path(sourceId)),
                List.of(),
                inputFluids,
                List.of(),
                outputFluids,
                energy,
                processTime);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation id,
            List<CountedIngredient> inputs,
            List<SizedFluidIngredient> inputFluids,
            List<ItemStack> outputs,
            List<FluidStack> outputFluids,
            long energy,
            int processTime) {
        Ingredient mold = AdapterUtils.toMoldIngredient(
                BuiltInRegistries.ITEM.getOptional(NUCLEAR_REACTOR_ID)
                        .map(Item::getDefaultInstance).orElse(ItemStack.EMPTY));
        if (mold.isEmpty()) return null;

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(id),
                inputs,
                inputFluids,
                List.of(),
                outputs,
                outputFluids,
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(mold),
                AlloyFurnaceMode.NORMAL);
    }

    @Nullable
    private static Long multiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            LOGGER.warn("Skipping overflowing Modern Industrialization nuclear conversion");
            return null;
        }
    }

    @Nullable
    private static Integer multiplyToInt(int left, long right) {
        Long result = multiply(left, right);
        return result == null || result > Integer.MAX_VALUE ? null : result.intValue();
    }

    private static String path(ResourceLocation id) {
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

}
