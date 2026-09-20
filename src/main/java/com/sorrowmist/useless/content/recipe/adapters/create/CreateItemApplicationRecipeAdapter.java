package com.sorrowmist.useless.content.recipe.adapters.create;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 将 Create 的「物品应用」配方自动适配为合金炉配方。
 * <p>
 * 适配器注册在基类 {@link ItemApplicationRecipe} 上，因此无需逐类登记即可同时覆盖：
 * 手动应用配方（create:item_application）与机械手应用配方（create:deploying）。
 * <p>
 * 被处理的物品始终作为输入；手持物品按是否消耗分流：不消耗的工具（斧头、砂纸等）
 * 作为模具，消耗的材料（木板等）作为输入；机械手方块始终作为第一个模具。
 */
public final class CreateItemApplicationRecipeAdapter
        implements IRecipeAdapter<ItemApplicationRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<ItemApplicationRecipe> getRecipeClass() {
        return ItemApplicationRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return CreateRecipeAdapterUtils.createBlockItem("deployer");
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return CreateRecipeAdapterUtils.isCreateMold(mold, "deployer");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ItemApplicationRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        AdvancedAlloyFurnaceRecipe converted = convert(holder.id(), holder.value(), level);
        return converted == null ? List.of() : List.of(converted);
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe convert(
            ResourceLocation originalId, ItemApplicationRecipe source, Level level) {
        if (originalId == null || source == null) return null;
        try {
            return build(originalId, source, level);
        } catch (RuntimeException exception) {
            LOGGER.warn("Skipping malformed Create item application recipe {}", originalId, exception);
            return null;
        }
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe build(
            ResourceLocation originalId, ItemApplicationRecipe source, Level level) {
        Ingredient processedItem = source.getProcessedItem();
        Ingredient requiredHeldItem = source.getRequiredHeldItem();
        if (processedItem == null || processedItem.isEmpty()
                || requiredHeldItem == null || requiredHeldItem.isEmpty()) {
            LOGGER.warn("Skipping Create item application recipe {} with empty inputs", originalId);
            return null;
        }

        Ingredient deployerMold = CreateRecipeAdapterUtils.blockMold(
                CreateRecipeAdapterUtils.createBlock("deployer"));
        if (deployerMold.isEmpty()) {
            LOGGER.warn("Skipping Create item application recipe {} because the deployer block is missing", originalId);
            return null;
        }

        // 手持物品按是否消耗分流：不消耗的工具（斧头、砂纸等）作为模具，
        // 消耗的材料（木板等）作为输入。机械手方块始终作为第一个模具。
        // 工具配方因此有两个模具，而普通合金炉只有一个模具槽、会跳过模具数大于 1 的配方
        // （见 AlloyFurnaceRecipeManager.selectBestRecipe），需通过多方块结构的
        // 模具仓（Omniversal Mold Hub）同时提供机械手和工具才能运行。
        Map<Ingredient, Long> itemRequirements = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(itemRequirements, processedItem, 1L);
        List<Ingredient> molds = new ArrayList<>();
        molds.add(deployerMold);
        if (source.shouldKeepHeldItem()) {
            CreateRecipeAdapterUtils.addUniqueMold(molds, requiredHeldItem);
        } else {
            AdapterUtils.mergeIngredient(itemRequirements, requiredHeldItem, 1L);
        }

        List<SizedFluidIngredient> sourceFluidInputs = new ArrayList<>();
        if (source.getFluidIngredients() != null) {
            for (SizedFluidIngredient ingredient : source.getFluidIngredients()) {
                if (ingredient == null || ingredient.ingredient() == null
                        || ingredient.ingredient().isEmpty() || ingredient.amount() <= 0) {
                    LOGGER.warn("Skipping empty Create fluid input declaration in {}", originalId);
                    continue;
                }
                sourceFluidInputs.add(ingredient);
            }
        }

        // getRollableResults() 返回完整产物列表（含手动配方中被排除的主方块产物），
        // 因此手动配方与机械手配方可以统一处理。
        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = new ArrayList<>();
        for (ProcessingOutput output : source.getRollableResults()) {
            collectOutput(weightedOutputs, output, originalId);
        }

        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ExpectedOutputScaler.scale(weightedOutputs);
        if (scaled.isEmpty()) {
            LOGGER.warn("Skipping Create item application recipe {} because its item output batch cannot be represented", originalId);
            return null;
        }
        int operations = scaled.get().operations();

        List<CountedIngredient> inputs = CreateRecipeAdapterUtils.scaleItemInputs(
                itemRequirements, operations, originalId, LOGGER);
        List<SizedFluidIngredient> inputFluids = CreateRecipeAdapterUtils.scaleFluidInputs(
                sourceFluidInputs, operations, originalId, LOGGER);
        List<FluidStack> outputFluids = CreateRecipeAdapterUtils.scaleFluidOutputs(
                source.getFluidResults(), operations, originalId, LOGGER);
        if (inputs.isEmpty() && inputFluids.isEmpty()) {
            LOGGER.warn("Skipping Create item application recipe {} because it has no valid inputs", originalId);
            return null;
        }
        if (scaled.get().outputs().isEmpty() && outputFluids.isEmpty()) {
            LOGGER.warn("Skipping Create item application recipe {} because it has no valid outputs", originalId);
            return null;
        }

        int declaredTime = source.getProcessingDuration();
        if (declaredTime < 0) {
            LOGGER.warn("Skipping Create item application recipe {} with a negative processing time", originalId);
            return null;
        }
        int baseTime = declaredTime > 0 ? declaredTime : AdapterUtils.DEFAULT_PROCESS_TIME;

        OptionalLong energy = CreateRecipeAdapterUtils.multiplyToLong(AdapterUtils.DEFAULT_ENERGY, operations);
        OptionalLong processTime = CreateRecipeAdapterUtils.multiplyToLong(baseTime, operations);
        if (energy.isEmpty() || processTime.isEmpty() || processTime.getAsLong() > Integer.MAX_VALUE) {
            LOGGER.warn("Skipping overflowing Create item application recipe {}", originalId);
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                inputs,
                inputFluids,
                List.of(),
                scaled.get().outputs(),
                outputFluids,
                List.of(),
                energy.getAsLong(),
                (int) processTime.getAsLong(),
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL);
    }

    private static void collectOutput(
            List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs,
            @Nullable ProcessingOutput output, ResourceLocation originalId) {
        if (output == null) return;
        ItemStack stack = output.getStack();
        float chance = output.getChance();
        if (stack.isEmpty() || stack.getCount() <= 0 || !Float.isFinite(chance)) {
            LOGGER.warn("Skipping invalid Create item output declaration in {}", originalId);
            return;
        }
        weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                stack, stack.getCount(), stack.getCount(), chance));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<ItemApplicationRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<ItemApplicationRecipe>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ItemApplicationRecipe source)) continue;
            AdvancedAlloyFurnaceRecipe converted = convert(holder.id(), source, level);
            if (converted != null && CreateRecipeAdapterUtils.matchesConverted(
                    converted, mergedInputs, mergedFluids)) {
                result.add((RecipeHolder<ItemApplicationRecipe>) (RecipeHolder<?>) holder);
            }
        }
        return result;
    }
}
