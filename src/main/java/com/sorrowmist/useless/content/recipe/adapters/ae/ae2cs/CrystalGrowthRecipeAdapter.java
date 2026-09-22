package com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import io.github.lounode.ae2cs.common.init.AECSItems;
import io.github.lounode.ae2cs.common.item.CrystalSeedItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
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
 * - 输出：对应纯水晶
 * <p>
 * 每种种子生成两条同输入同输出的配方，仅模具不同：
 * - 生长腔模具 ae2cs:crystal_growth_chamber：沿用历史配方 id，已编码的样板不受影响
 * - 水桶模具 minecraft:water_bucket：对应「种子浸水生长」的语义
 * <p>
 * 本适配器因此没有唯一模具，{@link #getMoldItem()} 返回 null 走 fallback 分支，
 * 改由每条配方自带的 mold 字段在查找时精确区分（高级熔炉的模具槽只放一个模具）。
 */
public class CrystalGrowthRecipeAdapter implements IRecipeAdapter<CrystalGrowthRecipeAdapter.GrowthDummyRecipe> {

    /** 晶体催生仓方块在 ae2cs 命名空间下的注册名 */
    private static final String GROWTH_CHAMBER_MOLD = "crystal_growth_chamber";

    /** 种子物品 → 该种子的全部模具变体配方 */
    private final Map<Item, List<AdvancedAlloyFurnaceRecipe>> recipeMap = new HashMap<>();
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

            String seedPath = BuiltInRegistries.ITEM.getKey(seed).getPath();

            int energy = Math.max(seedItem.getOvergrowTick() * 5, 1000);
            int processTime = Math.max(seedItem.getOvergrowTick() / 10, 60);

            // 生长腔版本保留原始 id：配方身份含 id，改动会让既有万象样板失配
            AdvancedAlloyFurnaceRecipe chamberRecipe = createRecipe(
                    ResourceLocation.fromNamespaceAndPath("ae2cs", "growth_" + seedPath),
                    seed, crystal, energy, processTime,
                    CircuitEtcherRecipeAdapter.makeMold(GROWTH_CHAMBER_MOLD));

            AdvancedAlloyFurnaceRecipe waterBucketRecipe = createRecipe(
                    ResourceLocation.fromNamespaceAndPath("ae2cs", "growth_" + seedPath + "_water_bucket"),
                    seed, crystal, energy, processTime,
                    AdapterUtils.toMoldIngredient(Items.WATER_BUCKET.getDefaultInstance()));

            recipeMap.put(seed, List.of(chamberRecipe, waterBucketRecipe));
            allRecipes.add(chamberRecipe);
            allRecipes.add(waterBucketRecipe);
        }
    }

    /** 按同一输入输出、仅模具不同的方式构造一条生长配方 */
    private static AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation id, Item seed, Item crystal, int energy, int processTime,
            Ingredient mold) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(new CountedIngredient(Ingredient.of(seed), 1)),
                List.of(),
                List.of(new ItemStack(crystal)),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                mold,
                AlloyFurnaceMode.NORMAL
        );
    }

    public List<AdvancedAlloyFurnaceRecipe> getAllRecipes() {
        return allRecipes;
    }

    @Override
    public Class<GrowthDummyRecipe> getRecipeClass() {
        return GrowthDummyRecipe.class;
    }

    /**
     * 没有唯一模具：生长腔与水桶各有一套配方，故返回 null 让管理器走 fallback 分支，
     * 由 {@link #matchesMold} 与每条配方自带的模具字段共同筛选。
     */
    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    /** 只接受生长腔与水桶两种模具，其余模具直接跳过本适配器。 */
    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) return false;
        return CircuitEtcherRecipeAdapter.checkMold(mold, GROWTH_CHAMBER_MOLD)
                || mold.is(Items.WATER_BUCKET);
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
    @Nullable
    public List<RecipeHolder<GrowthDummyRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) return List.of();
        if (!matchesMold(mold)) return List.of();

        List<RecipeHolder<GrowthDummyRecipe>> matches = new java.util.ArrayList<>();
        for (Ingredient input : mergedInputs.keySet()) {
            for (ItemStack stack : input.getItems()) {
                Item item = stack.getItem();
                if (!(item instanceof CrystalSeedItem)) continue;

                // 同一颗种子有生长腔与水桶两种模具变体，只挑出与当前模具相符的那一条
                List<AdvancedAlloyFurnaceRecipe> variants = recipeMap.get(item);
                if (variants == null) continue;

                for (AdvancedAlloyFurnaceRecipe recipe : variants) {
                    if (AdapterUtils.matchesMold(recipe.mold(), mold)) {
                        matches.add(new RecipeHolder<>(recipe.id(), new GrowthDummyRecipe(recipe)));
                    }
                }
            }
        }
        return matches;
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
