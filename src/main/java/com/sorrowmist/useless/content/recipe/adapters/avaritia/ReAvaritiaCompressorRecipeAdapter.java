package com.sorrowmist.useless.content.recipe.adapters.avaritia;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingAdapterUtils;
import committee.nova.mods.avaritia.api.common.crafting.ICompressorRecipe;
import committee.nova.mods.avaritia.init.registry.ModBlocks;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import committee.nova.mods.avaritia.init.registry.enums.CompressorTier;
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

/** Converts Re-Avaritia neutron compressor recipes and all four machine tiers. */
public final class ReAvaritiaCompressorRecipeAdapter
        implements IRecipeAdapter<ICompressorRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String sourceId() {
        return RecipeSourceIds.AVARITIA;
    }

    @Override
    public Class<ICompressorRecipe> getRecipeClass() {
        return ICompressorRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) {
            return false;
        }
        for (CompressorTier tier : CompressorTier.values()) {
            if (compressorMold(tier).is(mold.getItem())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ICompressorRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        List<AdvancedAlloyFurnaceRecipe> converted = new ArrayList<>();
        for (CompressorTier tier : CompressorTier.values()) {
            Converted data = convertData(holder.value(), tier);
            if (data == null) {
                LOGGER.warn("Skipping invalid Re-Avaritia compressor recipe {} ({})", holder.id(), tier);
                continue;
            }
            converted.add(new AdvancedAlloyFurnaceRecipe(
                    variantId(holder, tier),
                    data.inputs(),
                    List.of(),
                    data.outputs(),
                    List.of(),
                    AdapterUtils.DEFAULT_ENERGY,
                    data.processTime(),
                    Ingredient.EMPTY,
                    0,
                    AdapterUtils.toMoldIngredient(compressorMold(tier)),
                    AlloyFurnaceMode.NORMAL
            ));
        }
        return List.copyOf(converted);
    }

    @Override
    public List<RecipeHolder<ICompressorRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)
                || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        CompressorTier tier = tierFor(mold);
        if (tier == null) {
            return List.of();
        }

        List<RecipeHolder<ICompressorRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<ICompressorRecipe> holder : recipeManager.getAllRecipesFor(
                ModRecipeTypes.COMPRESSOR_RECIPE.get())) {
            Converted converted = convertData(holder.value(), tier);
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(
            @Nullable ICompressorRecipe source, CompressorTier tier) {
        if (source == null || tier == null) {
            return null;
        }

        Ingredient input;
        int inputCount;
        int baseTime;
        ItemStack result;
        try {
            input = source.getInput();
            inputCount = source.getInputCount();
            baseTime = source.getTimeCost();
            result = ExtendedCraftingAdapterUtils.copyResult(source);
        } catch (RuntimeException exception) {
            return null;
        }
        if (input == null || input.isEmpty() || inputCount <= 0 || baseTime < 0
                || result.isEmpty() || result.getCount() <= 0) {
            return null;
        }

        long requiredInput = scaleCeiling(inputCount, tier.inputAmplifier);
        int processTime = scaleCeilingToInt(baseTime, tier.timeAmplifier);
        int outputCount = multiplyToInt(result.getCount(), tier.outputAmplifier);
        if (requiredInput <= 0 || processTime <= 0 || outputCount <= 0) {
            return null;
        }

        result.setCount(outputCount);
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(requirements, input, requiredInput);
        return new Converted(
                List.of(new CountedIngredient(input, requiredInput)),
                List.of(result),
                requirements,
                processTime);
    }

    private static ResourceLocation variantId(
            RecipeHolder<ICompressorRecipe> holder, CompressorTier tier) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                holder.id().getNamespace(),
                holder.id().getPath() + "_" + tier.name + "_converted");
    }

    private static long scaleCeiling(long value, float multiplier) {
        if (value < 0 || !Float.isFinite(multiplier) || multiplier <= 0f) {
            return -1L;
        }
        double scaled = value * (double) multiplier;
        if (!Double.isFinite(scaled) || scaled > Long.MAX_VALUE) {
            return -1L;
        }
        return (long) Math.ceil(scaled);
    }

    private static int scaleCeilingToInt(long value, float multiplier) {
        long scaled = scaleCeiling(value, multiplier);
        if (scaled <= 0) {
            return -1;
        }
        return scaled >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) scaled;
    }

    private static int multiplyToInt(int value, int multiplier) {
        long result = (long) value * multiplier;
        return result <= 0 || result > Integer.MAX_VALUE ? -1 : (int) result;
    }

    @Nullable
    private static CompressorTier tierFor(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) {
            return null;
        }
        for (CompressorTier tier : CompressorTier.values()) {
            if (compressorMold(tier).is(mold.getItem())) {
                return tier;
            }
        }
        return null;
    }

    private static ItemStack compressorMold(CompressorTier tier) {
        return switch (tier) {
            case DEFAULT -> new ItemStack(ModBlocks.neutron_compressor.get());
            case DENSE -> new ItemStack(ModBlocks.dense_neutron_compressor.get());
            case DENSER -> new ItemStack(ModBlocks.denser_neutron_compressor.get());
            case DENSEST -> new ItemStack(ModBlocks.densest_neutron_compressor.get());
        };
    }

    private record Converted(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements,
            int processTime) {
    }
}
