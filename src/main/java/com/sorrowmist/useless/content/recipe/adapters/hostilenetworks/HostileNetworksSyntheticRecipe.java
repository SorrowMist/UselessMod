package com.sorrowmist.useless.content.recipe.adapters.hostilenetworks;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Recipe-manager payload for Hostile Neural Networks' runtime-generated recipes. */
public final class HostileNetworksSyntheticRecipe implements Recipe<RecipeInput> {
    private final AdvancedAlloyFurnaceRecipe convertedRecipe;

    public HostileNetworksSyntheticRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
        this.convertedRecipe = convertedRecipe;
    }

    public AdvancedAlloyFurnaceRecipe convertedRecipe() {
        return this.convertedRecipe;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return this.convertedRecipe != null && this.convertedRecipe.matches(input, level);
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return this.convertedRecipe == null ? ItemStack.EMPTY : this.convertedRecipe.assemble(input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return this.convertedRecipe != null && this.convertedRecipe.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.convertedRecipe == null ? ItemStack.EMPTY : this.convertedRecipe.getResultItem(registries);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return this.convertedRecipe == null ? null : this.convertedRecipe.getSerializer();
    }

    @Override
    public RecipeType<?> getType() {
        return this.convertedRecipe == null ? null : this.convertedRecipe.getType();
    }
}
