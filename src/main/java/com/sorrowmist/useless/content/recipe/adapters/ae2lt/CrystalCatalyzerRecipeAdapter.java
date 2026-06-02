package com.sorrowmist.useless.content.recipe.adapters.ae2lt;

import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AE2 Lightning Tech 水晶催化器配方适配器
 * <p>
 * 将水晶催化器配方（仅 CRYSTAL 模式）转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 仅转换 CRYSTAL 模式的配方
 * - 催化剂 → 模具（不消耗）
 * - 无物品输入
 * - 输出固定 256 个
 */
public class CrystalCatalyzerRecipeAdapter implements IRecipeAdapter<CrystalCatalyzerRecipe> {

    private static final int OUTPUT_COUNT = 256;
    private static final int BASE_PROCESS_TIME = 200;

    @Override
    public Class<CrystalCatalyzerRecipe> getRecipeClass() {
        return CrystalCatalyzerRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<CrystalCatalyzerRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        CrystalCatalyzerRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        if (recipe.mode() != Mode.CRYSTAL) {
            return result;
        }

        Optional<Ingredient> catalyst = recipe.catalyst();
        ItemStack outputTemplate = recipe.getOutputTemplate();

        if (outputTemplate.isEmpty()) {
            return result;
        }

        ItemStack output = outputTemplate.copyWithCount(OUTPUT_COUNT);

        int energy = Math.max(recipe.energyPerCycle() * OUTPUT_COUNT, 10000);

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        Ingredient moldIngredient = catalyst.orElse(Ingredient.EMPTY);

        FluidStack waterInput = new FluidStack(Fluids.WATER, 1000);

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                List.of(),
                List.of(waterInput),
                List.of(output),
                List.of(),
                energy,
                BASE_PROCESS_TIME,
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
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<CrystalCatalyzerRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<CrystalCatalyzerRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs, null);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<CrystalCatalyzerRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        return findMatchingRecipeWithFluidsAndMold(level, inputs, List.of(), mold);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<CrystalCatalyzerRecipe> findMatchingRecipeWithFluids(Level level, List<ItemStack> inputs, List<FluidStack> fluidInputs) {
        return findMatchingRecipeWithFluidsAndMold(level, inputs, fluidInputs, null);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<CrystalCatalyzerRecipe> findMatchingRecipeWithFluidsAndMold(Level level, List<ItemStack> inputs, List<FluidStack> fluidInputs, @Nullable ItemStack mold) {
        if (level == null) {
            return null;
        }

        if (mold == null || mold.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CrystalCatalyzerRecipe>> recipes = (List<RecipeHolder<CrystalCatalyzerRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get()
        );

        for (RecipeHolder<CrystalCatalyzerRecipe> holder : recipes) {
            CrystalCatalyzerRecipe recipe = holder.value();

            if (recipe.mode() != Mode.CRYSTAL) continue;

            Optional<Ingredient> catalyst = recipe.catalyst();
            if (catalyst.isEmpty()) continue;

            // 检查模具（催化剂）匹配
            if (!catalyst.get().test(mold)) {
                continue;
            }

            // 检查流体输入匹配（水晶催化器配方固定需要水）
            boolean foundWater = false;
            for (FluidStack input : fluidInputs) {
                if (input.getFluid() == net.minecraft.world.level.material.Fluids.WATER && input.getAmount() >= 1000) {
                    foundWater = true;
                    break;
                }
            }
            if (!foundWater && !fluidInputs.isEmpty()) {
                // 如果用户提供了流体，但不是水，跳过
                continue;
            }

            return holder;
        }

        return null;
    }

    @Override
    public int getPriority() {
        return 58;
    }
}
