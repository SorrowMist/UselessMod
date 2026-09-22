package com.sorrowmist.useless.content.recipe.adapters.fluxnetworks;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import sonar.fluxnetworks.register.RegistryItems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 Flux Networks 的通量粉尘转化机制转换为合金炉配方。
 *
 * <p>Flux Networks 并未把通量粉尘做成数据包配方：它由 {@code EventHandler.onPlayerInteract}
 * 硬编码实现——左键点击黑曜石、其下方第 2 格为基岩或通量块、消耗上方掉落的红石粉，
 * 按 1:1 产出通量粉尘（单次上限 512）。因此本适配器走 {@code getGeneratedRecipes}
 * 合成路径，用黑曜石充当合金炉的模具，把「红石粉 → 通量粉尘」这一转化搬进合金炉。</p>
 */
public final class FluxNetworksRecipeAdapter
        implements IRecipeAdapter<FluxNetworksSyntheticRecipe> {

    /** 合金炉里每一次转化消耗的红石粉数量。 */
    private static final long REDSTONE_PER_CRAFT = 1L;

    /** 模具槽中的黑曜石只用于判定，不参与消耗。 */
    private static final ItemStack MOLD = new ItemStack(Blocks.OBSIDIAN);

    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            RecipeSourceIds.FLUX_NETWORKS, "compat/flux_dust");

    @Override
    public String sourceId() {
        return RecipeSourceIds.FLUX_NETWORKS;
    }

    @Override
    public Class<FluxNetworksSyntheticRecipe> getRecipeClass() {
        return FluxNetworksSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return MOLD.copy();
    }

    @Override
    public List<RecipeHolder<FluxNetworksSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();

        AdvancedAlloyFurnaceRecipe recipe = buildRecipe();
        if (recipe == null) return List.of();
        return List.of(holder(recipe));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<FluxNetworksSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<FluxNetworksSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<FluxNetworksSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();

        List<RecipeHolder<FluxNetworksSyntheticRecipe>> result = new ArrayList<>();
        for (RecipeHolder<FluxNetworksSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe == null || recipe.molds().size() != 1 || !AdapterUtils.matchesMold(recipe.mold(), mold)) {
                continue;
            }

            boolean matches = actualInputs != null && !actualInputs.isEmpty()
                    ? ItemIngredientAllocator.matches(
                            recipe.inputs(), actualInputs, mergedKeyInputs(mergedKeys), 1L)
                    : matchesMergedInputs(recipe.inputs(), mergedInputs);
            if (matches) result.add(holder);
        }
        return List.copyOf(result);
    }

    /** 构造「红石粉 → 通量粉尘」这一条合成配方；通量粉尘物品不可用时返回 {@code null}。 */
    @Nullable
    private static AdvancedAlloyFurnaceRecipe buildRecipe() {
        ItemStack dust;
        try {
            dust = new ItemStack(RegistryItems.FLUX_DUST.get());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
        if (dust.isEmpty()) return null;

        return new AdvancedAlloyFurnaceRecipe(
                RECIPE_ID,
                List.of(new CountedIngredient(Ingredient.of(Items.REDSTONE), REDSTONE_PER_CRAFT)),
                List.of(),
                List.of(),
                List.of(dust),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(MOLD)),
                AlloyFurnaceMode.NORMAL);
    }

    private static RecipeHolder<FluxNetworksSyntheticRecipe> holder(AdvancedAlloyFurnaceRecipe recipe) {
        return new RecipeHolder<>(recipe.id(), new FluxNetworksSyntheticRecipe(recipe));
    }

    private static boolean matchesMergedInputs(
            List<CountedIngredient> requirements, Map<Ingredient, Long> available) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient requirement : requirements) {
            AdapterUtils.mergeIngredient(required, requirement.ingredient(), requirement.count());
        }
        return ItemIngredientAllocator.matches(available, required);
    }

    private static List<appeng.api.stacks.GenericStack> mergedKeyInputs(
            Map<appeng.api.stacks.AEKey, Long> mergedKeys) {
        if (mergedKeys == null || mergedKeys.isEmpty()) return List.of();
        List<appeng.api.stacks.GenericStack> result = new ArrayList<>();
        for (Map.Entry<appeng.api.stacks.AEKey, Long> entry : mergedKeys.entrySet()) {
            long amount = Math.max(0L, entry.getValue());
            if (amount > 0L) result.add(new appeng.api.stacks.GenericStack(entry.getKey(), amount));
        }
        return result;
    }
}