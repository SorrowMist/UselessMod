package com.sorrowmist.useless.content.recipe.adapters.ae.dataenergistics;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.registry.DERecipes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DataEnergistics 数据重组器配方适配器
 * <p>
 * 将数据重组器配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 物品输入 → 普通输入（合并相同物品）
 * - 流体输入 → 流体输入
 * - 物品产物 → 输出
 * - 流体产物 → 流体输出
 * - keyInput → AEKey 输入
 * - keyOutput → AEKey 输出
 * - data_energistics:data_reassembler → 模具（不消耗）
 */
public class DataReassemblerRecipeAdapter implements IRecipeAdapter<DataRipperReassemblerRecipe> {

    private static final int BASE_PROCESS_TIME = 200;
    private static final int BASE_ENERGY = 2000;

    @Override
    public Class<DataRipperReassemblerRecipe> getRecipeClass() {
        return DataRipperReassemblerRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("data_energistics", "data_reassembler")));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<DataRipperReassemblerRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        DataRipperReassemblerRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        // 获取输入
        List<DataRipperReassemblerIngredient> itemInputs = recipe.getItemInputs();
        List<GenericStack> fluidInputs = recipe.getFluidInputs();
        GenericStack keyInput = recipe.getKeyInput();

        // 获取输出
        List<ItemStack> itemOutputs = recipe.getItemOutputs();
        List<GenericStack> fluidOutputs = recipe.getFluidOutputs();
        GenericStack keyOutput = recipe.getKeyOutput();

        boolean hasItemInputs = !itemInputs.isEmpty();
        boolean hasFluidInputs = !fluidInputs.isEmpty();
        boolean hasKeyInput = keyInput != null && keyInput.amount() > 0;
        boolean hasItemOutputs = !itemOutputs.isEmpty();
        boolean hasFluidOutputs = !fluidOutputs.isEmpty();
        boolean hasKeyOutput = keyOutput != null && keyOutput.amount() > 0;

        // 只要有输入并且有输出就可以转换
        if (!hasItemInputs && !hasFluidInputs && !hasKeyInput) {
            return result;
        }
        if (!hasItemOutputs && !hasFluidOutputs && !hasKeyOutput) {
            return result;
        }

        // 合并物品输入
        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        for (DataRipperReassemblerIngredient input : itemInputs) {
            AdapterUtils.mergeIngredient(ingredientCounts, input.ingredient(), input.count());
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        List<GenericStack> keyInputs = hasKeyInput ? List.of(keyInput) : List.of();

        // 转换流体输入
        List<SizedFluidIngredient> inputFluids = new ArrayList<>();
        for (GenericStack fluidInput : fluidInputs) {
            if (fluidInput.what() instanceof AEFluidKey fluidKey && fluidInput.amount() > 0) {
                int amount = fluidInput.amount() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fluidInput.amount();
                SizedFluidIngredient ingredient = AdapterUtils.toSizedFluidIngredient(fluidKey.toStack(amount));
                if (ingredient != null) inputFluids.add(ingredient);
            }
        }

        // 转换物品输出
        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack itemOutput : itemOutputs) {
            if (!itemOutput.isEmpty()) {
                outputs.add(itemOutput.copy());
            }
        }

        List<GenericStack> keyOutputs = hasKeyOutput ? List.of(keyOutput) : List.of();

        // 转换流体输出
        List<FluidStack> outputFluids = new ArrayList<>();
        for (GenericStack fluidOutput : fluidOutputs) {
            if (fluidOutput.what() instanceof AEFluidKey fluidKey && fluidOutput.amount() > 0) {
                int amount = fluidOutput.amount() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fluidOutput.amount();
                outputFluids.add(fluidKey.toStack(amount));
            }
        }

        int processTime = recipe.getProcessTicks();
        int energy = BASE_ENERGY + (itemInputs.size() + fluidInputs.size()) * 200;
        if (hasKeyInput) energy += 500;
        if (hasKeyOutput) energy += 500;

        // 使用数据重组器本身作为模具
        Ingredient moldIngredient = Ingredient.of(new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("data_energistics", "data_reassembler")
                )
        ));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                inputFluids,
                keyInputs,
                outputs,
                outputFluids,
                keyOutputs,
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                moldIngredient,
                AlloyFurnaceMode.NORMAL
        );

        result.add(convertedRecipe);
        return result;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<DataRipperReassemblerRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null) return List.of();

        // 检查模具是否匹配数据重组器
        if (mold != null && !mold.isEmpty()) {
            ResourceLocation moldId = BuiltInRegistries.ITEM.getKey(mold.getItem());
            if (!"data_energistics".equals(moldId.getNamespace()) || !"data_reassembler".equals(moldId.getPath())) return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<DataRipperReassemblerRecipe>> recipes = (List<RecipeHolder<DataRipperReassemblerRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                DERecipes.DATA_RIPPER_REASSEMBLER_TYPE.get()
        );

        List<RecipeHolder<DataRipperReassemblerRecipe>> matches = new ArrayList<>();

        for (RecipeHolder<DataRipperReassemblerRecipe> holder : recipes) {
            DataRipperReassemblerRecipe recipe = holder.value();

            List<DataRipperReassemblerIngredient> recipeItemInputs = recipe.getItemInputs();
            List<GenericStack> recipeFluidInputs = recipe.getFluidInputs();
            GenericStack keyInput = recipe.getKeyInput();

            // 构建 requiredCounts
            Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
            for (DataRipperReassemblerIngredient input : recipeItemInputs) {
                AdapterUtils.mergeIngredient(requiredCounts, input.ingredient(), input.count());
            }

            boolean hasKeyInput = keyInput != null && keyInput.amount() > 0;

            // 检查物品输入匹配（包括 keyInput）
            boolean itemsMatch = true;
            if (!requiredCounts.isEmpty()) {
                itemsMatch = AdapterUtils.matchesRequired(mergedInputs, requiredCounts);
            }

            // 检查流体输入匹配
            boolean fluidsMatch = true;
            if (!recipeFluidInputs.isEmpty()) {
                fluidsMatch = matchesFluidInputs(mergedFluids, recipeFluidInputs);
            }

            boolean keysMatch = !hasKeyInput || matchesKeyInput(mergedInputs, mergedFluids, mergedKeys, keyInput);

            if ((requiredCounts.isEmpty() || itemsMatch) && (recipeFluidInputs.isEmpty() || fluidsMatch) && keysMatch) {
                matches.add(holder);
            }
        }

        return matches;
    }

    private boolean matchesFluidInputs(Map<FluidStack, Long> mergedFluids, List<GenericStack> requiredFluids) {
        if (mergedFluids == null || mergedFluids.isEmpty()) {
            return false;
        }

        List<SizedFluidIngredient> requirements = new ArrayList<>();
        for (GenericStack required : requiredFluids) {
            if (!(required.what() instanceof AEFluidKey requiredKey) || required.amount() <= 0) continue;
            int amount = required.amount() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) required.amount();
            SizedFluidIngredient ingredient = AdapterUtils.toSizedFluidIngredient(requiredKey.toStack(amount));
            if (ingredient != null) requirements.add(ingredient);
        }
        return FluidIngredientAllocator.matches(requirements, mergedFluids, 1L);
    }

    private boolean matchesKeyInput(Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys, GenericStack keyInput) {
        if (keyInput.what() instanceof AEItemKey itemKey) {
            return AdapterUtils.hasMatchingIngredient(mergedInputs, Ingredient.of(itemKey.toStack()), keyInput.amount());
        }
        if (keyInput.what() instanceof AEFluidKey fluidKey) {
            if (keyInput.amount() <= 0 || keyInput.amount() > Integer.MAX_VALUE) return false;
            SizedFluidIngredient requirement = AdapterUtils.toSizedFluidIngredient(
                    fluidKey.toStack((int) keyInput.amount()));
            return requirement != null && FluidIngredientAllocator.matches(
                    List.of(requirement), mergedFluids, 1L);
        }
        return mergedKeys.getOrDefault(keyInput.what(), 0L) >= keyInput.amount();
    }
}
