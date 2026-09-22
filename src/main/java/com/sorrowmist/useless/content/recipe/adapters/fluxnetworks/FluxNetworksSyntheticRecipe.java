package com.sorrowmist.useless.content.recipe.adapters.fluxnetworks;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Recipe-manager payload for Flux Networks recipes generated from runtime data.
 *
 * <p>Flux Networks does not ship the flux-dust conversion as a datapack recipe; it is a hard-coded
 * interaction handled by its own event handler. The adapter therefore synthesizes the alloy-furnace
 * view of that mechanic and needs this wrapper to satisfy the recipe-manager contract.</p>
 */
public final class FluxNetworksSyntheticRecipe implements Recipe<RecipeInput> {
    private final AdvancedAlloyFurnaceRecipe convertedRecipe;

    public FluxNetworksSyntheticRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
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
