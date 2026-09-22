package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import cy.jdkdigital.productivebees.common.crafting.ingredient.BeeIngredient;
import cy.jdkdigital.productivebees.common.recipe.BeeFishingRecipe;
import cy.jdkdigital.productivebees.init.ModRecipeTypes;
import cy.jdkdigital.productivebees.util.BeeCreator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把资源蜜蜂的钓鱼配方（{@code productivebees:bee_fishing}）转换成合金炉配方。
 *
 * <p>资源蜜蜂钓鱼不会往原版 {@code minecraft:fishing} 战利品表里塞条目，而是监听
 * {@code ItemFishedEvent} 后按生物群系抽取独立配方并直接生成蜜蜂实体。因此这里单独读取
 * 该配方类型，并把产物从「蜜蜂实体」改成对应的<b>蜜蜂刷怪蛋</b>物品。</p>
 *
 * <p>配方按 {@link ResourceLocation} 稳定 id 转换：id 只取自数据包里的配方 id，
 * 不依赖生物群系集合的迭代顺序，避免万象样板在重建索引后失效。</p>
 *
 * <p>由于原版钓鱼适配器（{@code FishingLootRecipeAdapter}）不涉及该配方类型，两者不会重复转换。</p>
 */
public final class BeeFishingRecipeAdapter implements IRecipeAdapter<BeeFishingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 每次转换消耗的水量，与其它钓鱼类转换保持一致。 */
    private static final int WATER_PER_OPERATION = 1_000;

    @Override
    public String sourceId() {
        return RecipeSourceIds.PRODUCTIVE_BEES;
    }

    @Override
    public Class<BeeFishingRecipe> getRecipeClass() {
        return BeeFishingRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(Items.FISHING_ROD);
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && mold.is(Items.FISHING_ROD);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BeeFishingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        BeeFishingRecipe source = holder.value();
        ItemStack spawnEgg = resolveSpawnEgg(source);
        if (spawnEgg.isEmpty()) {
            LOGGER.warn("Skipping unsupported Productive Bees fishing recipe: {}", holder.id());
            return List.of();
        }

        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(),
                List.of(new FluidStack(Fluids.WATER, WATER_PER_OPERATION)),
                List.of(spawnEgg),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                Ingredient.of(Items.FISHING_ROD),
                AlloyFurnaceMode.NORMAL);
        return List.of(converted);
    }

    @Override
    public List<RecipeHolder<BeeFishingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<BeeFishingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<BeeFishingRecipe> holder : recipeManager.getAllRecipesFor(
                ModRecipeTypes.BEE_FISHING_TYPE.get())) {
            BeeFishingRecipe source = holder.value();
            if (source == null || resolveSpawnEgg(source).isEmpty()) {
                continue;
            }
            if (availableWater(mergedFluids) >= WATER_PER_OPERATION) {
                matches.add(holder);
            }
        }
        return matches;
    }

    /** 把配方产物（蜜蜂）解析成对应的刷怪蛋物品；无法解析时返回空堆。 */
    private static ItemStack resolveSpawnEgg(BeeFishingRecipe source) {
        BeeIngredient bee = source.output == null ? null : source.output.get();
        if (bee == null) {
            return ItemStack.EMPTY;
        }
        ResourceLocation beeType = bee.getBeeType();
        if (beeType == null) {
            return ItemStack.EMPTY;
        }
        // 资源蜜蜂的刷怪蛋可能不是原版 SpawnEggItem，这里只判空，避免误杀可配置蜂种。
        return BeeCreator.getSpawnEgg(beeType);
    }

    private static long availableWater(Map<FluidStack, Long> fluids) {
        if (fluids == null || fluids.isEmpty()) {
            return 0L;
        }
        long amount = 0L;
        for (Map.Entry<FluidStack, Long> entry : fluids.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            if (entry.getKey() != null && entry.getKey().getFluid() == Fluids.WATER) {
                amount += entry.getValue();
            }
        }
        return amount;
    }
}
