package com.sorrowmist.useless.content.recipe.adapters.botanypots;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** 承载已转换的植物盆作物配方；作物配方属于 Botany Pots 自有配方类型，不参与原版配方匹配。 */
public final class BotanyPotsCropSyntheticRecipe implements Recipe<RecipeInput> {
    private final AdvancedAlloyFurnaceRecipe convertedRecipe;

    public BotanyPotsCropSyntheticRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
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
