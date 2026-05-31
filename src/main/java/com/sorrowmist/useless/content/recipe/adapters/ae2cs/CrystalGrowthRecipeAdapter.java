package com.sorrowmist.useless.content.recipe.adapters.ae2cs;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import io.github.lounode.ae2cs.common.init.AECSItems;
import io.github.lounode.ae2cs.common.item.CrystalSeedItem;
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
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AE2CS 晶体催生仓种子→纯水晶配方适配器
 * <p>
 * 根据种子与纯水晶的对应关系自动生成配方：
 * - 输入：水晶种子（消耗）
 * - 模具：ae2cs:crystal_growth_chamber
 * - 输出：对应纯水晶
 */
public class CrystalGrowthRecipeAdapter implements IRecipeAdapter<CrystalGrowthRecipeAdapter.GrowthDummyRecipe> {

    private final Map<Item, AdvancedAlloyFurnaceRecipe> recipeMap = new HashMap<>();
    private final List<AdvancedAlloyFurnaceRecipe> allRecipes = new ArrayList<>();

    public CrystalGrowthRecipeAdapter() {
        buildRecipes();
    }

    private void buildRecipes() {
        List<DeferredItem<CrystalSeedItem>> seeds = AECSItems.getCrystalSeeds();

        for (DeferredItem<CrystalSeedItem> seedRef : seeds) {
            CrystalSeedItem seedItem = seedRef.get();
            Item seed = seedRef.asItem();
            Item crystal = seedItem.getGrowTo();

            if (crystal == null) continue;

            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    "ae2cs",
                    "growth_" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(seed).getPath()
            );

            int energy = Math.max(seedItem.getOvergrowTick() * 5, 1000);
            int processTime = Math.max(seedItem.getOvergrowTick() / 10, 60);

            AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                    id,
                    List.of(new CountedIngredient(Ingredient.of(seed), 1)),
                    List.of(),
                    List.of(new ItemStack(crystal)),
                    List.of(),
                    energy,
                    processTime,
                    Ingredient.EMPTY,
                    0,
                    CircuitEtcherRecipeAdapter.makeMold("crystal_growth_chamber"),
                    AlloyFurnaceMode.NORMAL
            );

            recipeMap.put(seed, recipe);
            allRecipes.add(recipe);
        }
    }

    public List<AdvancedAlloyFurnaceRecipe> getAllRecipes() {
        return allRecipes;
    }

    @Override
    public Class<GrowthDummyRecipe> getRecipeClass() {
        return GrowthDummyRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<GrowthDummyRecipe> holder, Level level) {
        return List.of(holder.value().convertedRecipe);
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<GrowthDummyRecipe> holder, Level level) {
        return holder.value().convertedRecipe;
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    public RecipeHolder<GrowthDummyRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs, null);
    }

    @Override
    @Nullable
    public RecipeHolder<GrowthDummyRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        if (level == null || inputs.isEmpty()) return null;
        if (!CircuitEtcherRecipeAdapter.checkMold(mold, "crystal_growth_chamber")) return null;

        for (ItemStack stack : inputs) {
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item instanceof CrystalSeedItem) {
                AdvancedAlloyFurnaceRecipe recipe = recipeMap.get(item);
                if (recipe != null) {
                    return new RecipeHolder<>(recipe.id(), new GrowthDummyRecipe(recipe));
                }
            }
        }
        return null;
    }

    @Override
    public int getPriority() {
        return 62;
    }

    public static class GrowthDummyRecipe implements Recipe<RecipeInput> {
        final AdvancedAlloyFurnaceRecipe convertedRecipe;

        GrowthDummyRecipe(AdvancedAlloyFurnaceRecipe r) { this.convertedRecipe = r; }

        @Override public boolean matches(RecipeInput input, Level level) { return false; }
        @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
        @Override public boolean canCraftInDimensions(int w, int h) { return false; }
        @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
        @Override public RecipeSerializer<?> getSerializer() { return null; }
        @Override public RecipeType<?> getType() { return null; }
    }
}
