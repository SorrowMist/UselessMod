package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.recipe.DissolutionChamberRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Industrial Foregoing 溶解成型机配方适配器
 * <p>
 * 将溶解成型机配方（物品+流体→物品/流体）转换为高级合金熔炉配方
 * 支持流体输入和流体输出
 */
public class DissolutionChamberRecipeAdapter implements IRecipeAdapter<DissolutionChamberRecipe> {

    @Override
    public Class<DissolutionChamberRecipe> getRecipeClass() {
        return DissolutionChamberRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("industrialforegoing", "dissolution_chamber")));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<DissolutionChamberRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        DissolutionChamberRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

        // 获取输入物品列表
        List<Ingredient> itemInputs = originalRecipe.input;

        // 获取输出
        ItemStack itemOutput = originalRecipe.output.orElse(ItemStack.EMPTY);
        FluidStack fluidOutput = originalRecipe.outputFluid.orElse(FluidStack.EMPTY);

        // 如果没有物品输出且没有流体输出，则不转换
        if (itemOutput.isEmpty() && fluidOutput.isEmpty()) {
            return result;
        }

        // 构建物品输入列表 - 合并同类型的输入
        List<CountedIngredient> countedIngredients = AdapterUtils.mergeIngredients(itemInputs);

        SizedFluidIngredient inputFluidIngredient = originalRecipe.inputFluid;
        if (inputFluidIngredient == null) {
            return result;
        }

        FluidStack[] fluids = inputFluidIngredient.getFluids();
        if (fluids == null || fluids.length == 0) {
            return result;
        }

        // 创建溶解成型机模具要求
        Ingredient moldIngredient = AdapterUtils.toMoldIngredient(getMoldItem());

        // 计算总能量消耗 (Industrial Foregoing: 90 FE/tick * processingTime * 倍率)
        int processingTime = originalRecipe.processingTime;
        int totalEnergy = AdapterUtils.safeInt((long) AdapterUtils.IF_BASE_ENERGY_PER_TICK * processingTime * AdapterUtils.IF_ENERGY_MULTIPLIER);

        // 构建物品输出列表
        List<ItemStack> outputs = new ArrayList<>();
        if (!itemOutput.isEmpty()) {
            outputs.add(itemOutput.copy());
        }

        // 构建流体输出列表
        List<FluidStack> outputFluids = new ArrayList<>();
        if (!fluidOutput.isEmpty()) {
            outputFluids.add(fluidOutput.copy());
        }

        Map<ResourceLocation, FluidStack> uniqueFluids = new LinkedHashMap<>();
        for (FluidStack fluid : fluids) {
            if (fluid == null || fluid.isEmpty()) continue;
            FluidStack inputFluid = fluid.copy();
            inputFluid.setAmount(inputFluidIngredient.amount());
            uniqueFluids.putIfAbsent(BuiltInRegistries.FLUID.getKey(inputFluid.getFluid()), inputFluid);
        }

        for (FluidStack inputFluid : uniqueFluids.values()) {
            result.add(createRecipe(originalId, countedIngredients, inputFluid, outputs, outputFluids, totalEnergy, processingTime, moldIngredient, result.isEmpty()));
        }
        return result;
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(ResourceLocation originalId,
                                                    List<CountedIngredient> countedIngredients,
                                                    FluidStack inputFluid,
                                                    List<ItemStack> outputs,
                                                    List<FluidStack> outputFluids,
                                                    int totalEnergy,
                                                    int processingTime,
                                                    Ingredient moldIngredient,
                                                    boolean primaryId) {
        ResourceLocation id = primaryId ? AdapterUtils.convertedId(originalId) : convertedFluidId(originalId, inputFluid.getFluid());
        return new AdvancedAlloyFurnaceRecipe(
                id,
                countedIngredients,
                List.of(inputFluid),
                outputs,
                outputFluids,
                totalEnergy,
                processingTime,
                Ingredient.EMPTY,
                0,
                moldIngredient,
                AlloyFurnaceMode.NORMAL
        );
    }

    private ResourceLocation convertedFluidId(ResourceLocation originalId, Fluid fluid) {
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        String suffix = fluidId.getNamespace() + "_" + fluidId.getPath().replace('/', '_');
        return ResourceLocation.fromNamespaceAndPath(originalId.getNamespace(), originalId.getPath() + "_" + suffix + "_converted");
    }

    @Override
    @Nullable
    @SuppressWarnings({"unchecked"})
    public RecipeHolder<DissolutionChamberRecipe> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || (mergedInputs.isEmpty() && mergedFluids.isEmpty()) || !matchesMold(mold)) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        RecipeType<DissolutionChamberRecipe> recipeType = (RecipeType<DissolutionChamberRecipe>) ModuleCore.DISSOLUTION_TYPE.get();

        for (RecipeHolder<DissolutionChamberRecipe> holder : recipeManager.getAllRecipesFor(recipeType)) {
            DissolutionChamberRecipe recipe = holder.value();

            if (matchesItemInputs(mergedInputs, recipe.input) && matchesFluidInput(mergedFluids, recipe.inputFluid)) {
                return holder;
            }
        }

        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        ItemStack moldItem = getMoldItem();
        return mold != null && !mold.isEmpty() && moldItem != null && ItemStack.isSameItem(mold, moldItem);
    }

    private boolean matchesItemInputs(Map<Ingredient, Long> mergedInputs, List<Ingredient> itemInputs) {
        List<CountedIngredient> countedIngredients = AdapterUtils.mergeIngredients(itemInputs);
        for (CountedIngredient counted : countedIngredients) {
            if (!AdapterUtils.hasMatchingIngredient(mergedInputs, counted.ingredient(), counted.count())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFluidInput(Map<FluidStack, Long> mergedFluids, SizedFluidIngredient inputFluid) {
        if (inputFluid == null) return true;
        if (mergedFluids.isEmpty()) return false;
        long amount = 0L;
        for (Map.Entry<FluidStack, Long> entry : mergedFluids.entrySet()) {
            if (inputFluid.test(entry.getKey())) {
                amount += entry.getValue();
            }
        }
        return amount >= inputFluid.amount();
    }

}
