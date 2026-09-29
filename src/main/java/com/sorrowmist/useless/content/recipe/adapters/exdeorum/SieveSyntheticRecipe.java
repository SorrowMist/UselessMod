package com.sorrowmist.useless.content.recipe.adapters.exdeorum;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 承载按筛网拆分后生成的单个合金炉配方。
 *
 * <p>Ex Deorum 的一条筛子配方可能同时匹配多个筛网，而不同筛网对应的产出概率互不相同，
 * 因此转换阶段会按「输入方块 + 具体筛网」重新分组，一条源配方可展开为多条合金炉配方。
 * 每条展开结果由本类型持有，使 {@link SieveRecipeAdapter#convertAll} 能原样取出转换产物。</p>
 */
public final class SieveSyntheticRecipe implements Recipe<RecipeInput> {
    private final AdvancedAlloyFurnaceRecipe convertedRecipe;

    public SieveSyntheticRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
        this.convertedRecipe = convertedRecipe;
    }

    public AdvancedAlloyFurnaceRecipe convertedRecipe() {
        return convertedRecipe;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return convertedRecipe != null && convertedRecipe.matches(input, level);
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return convertedRecipe == null ? ItemStack.EMPTY : convertedRecipe.assemble(input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return convertedRecipe != null && convertedRecipe.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return convertedRecipe == null ? ItemStack.EMPTY : convertedRecipe.getResultItem(registries);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return convertedRecipe == null ? null : convertedRecipe.getSerializer();
    }

    @Override
    public RecipeType<?> getType() {
        return convertedRecipe == null ? null : convertedRecipe.getType();
    }
}
