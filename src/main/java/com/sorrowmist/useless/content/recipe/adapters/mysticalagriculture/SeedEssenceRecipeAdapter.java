package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.crop.ICropProvider;
import com.blakebr0.mysticalagriculture.registry.CropRegistry;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 神秘农业种子→精华配方适配器
 * <p>
 * 根据种子与精华的对应关系自动生成配方：
 * - 模具：种子
 * - 输入流体：100mb 水
 * - 输出：2x 对应精华
 */
public class SeedEssenceRecipeAdapter implements IRecipeAdapter<SeedEssenceRecipeAdapter.SeedEssenceDummyRecipe> {

    private static final int WATER_AMOUNT = 1;

    private final Map<Item, AdvancedAlloyFurnaceRecipe> recipeMap = new HashMap<>();
    private final List<AdvancedAlloyFurnaceRecipe> allRecipes = new ArrayList<>();

    public SeedEssenceRecipeAdapter() {
        buildRecipes();
    }

    private void buildRecipes() {
        var crops = CropRegistry.getInstance().getCrops();

        for (var crop : crops) {
            Item seedItem = crop.getSeedsItem();
            Item essenceItem = crop.getEssenceItem();

            if (seedItem == null || essenceItem == null) continue;
            if (seedItem == essenceItem) continue;

            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    "mysticalagriculture",
                    "seed_essence_" + crop.getName()
            );

            FluidStack waterInput = new FluidStack(
                    BuiltInRegistries.FLUID.get(
                            ResourceLocation.withDefaultNamespace("water")
                    ),
                    WATER_AMOUNT
            );

            AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                    id,
                    List.of(),                         // 无物品输入
                    List.of(waterInput),               // 100mb 水
                    List.of(new ItemStack(essenceItem, 2)),  // 2x 精华
                    List.of(),                         // 无流体输出
                    1000,                              // 能量
                    60,                                // 处理时间
                    Ingredient.EMPTY,
                    0,
                    Ingredient.of(seedItem),            // 种子作为模具
                    AlloyFurnaceMode.NORMAL
            );

            recipeMap.put(seedItem, recipe);
            allRecipes.add(recipe);
        }
    }

    /**
     * 获取所有生成的配方（用于 JEI 展示）
     */
    public List<AdvancedAlloyFurnaceRecipe> getAllRecipes() {
        return allRecipes;
    }

    @Override
    public Class<SeedEssenceDummyRecipe> getRecipeClass() {
        return SeedEssenceDummyRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null; // 种子作为模具，无固定模具物品
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && mold.getItem() instanceof ICropProvider;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<SeedEssenceDummyRecipe> holder, Level level) {
        return List.of(holder.value().convertedRecipe);
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<SeedEssenceDummyRecipe> holder, Level level) {
        return holder.value().convertedRecipe;
    }

    @Override
    @Nullable
    public RecipeHolder<SeedEssenceDummyRecipe> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) return null;

        Item moldItem = mold.getItem();
        if (!(moldItem instanceof ICropProvider)) return null;

        AdvancedAlloyFurnaceRecipe recipe = recipeMap.get(moldItem);
        if (recipe == null) return null;

        ResourceLocation holderId = recipe.id();
        return new RecipeHolder<>(holderId, new SeedEssenceDummyRecipe(recipe));
    }

    /**
     * 内部占位配方类，用于满足 RecipeHolder 泛型约束
     */
    public static class SeedEssenceDummyRecipe implements Recipe<RecipeInput> {
        final AdvancedAlloyFurnaceRecipe convertedRecipe;

        SeedEssenceDummyRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
            this.convertedRecipe = convertedRecipe;
        }

        @Override
        public boolean matches(RecipeInput input, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return false;
        }

        @Override
        public ItemStack getResultItem(HolderLookup.Provider registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return null;
        }

        @Override
        public RecipeType<?> getType() {
            return null;
        }
    }
}
