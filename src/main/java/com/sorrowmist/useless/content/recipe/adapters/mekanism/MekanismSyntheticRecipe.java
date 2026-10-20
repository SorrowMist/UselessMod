package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Recipe holder payload for Mekanism data that is generated outside RecipeManager. */
public final class MekanismSyntheticRecipe implements Recipe<Container> {
    private final AdvancedAlloyFurnaceRecipe convertedRecipe;

    public MekanismSyntheticRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
        this.convertedRecipe = convertedRecipe;
    }

    public AdvancedAlloyFurnaceRecipe convertedRecipe() {
        return convertedRecipe;
    }

    @Override
    public net.minecraft.resources.ResourceLocation getId() { return convertedRecipe.id(); }

    @Override
    public boolean matches(Container input, Level level) {
        return this.convertedRecipe != null && this.convertedRecipe.matches(input, level);
    }

    @Override
    public ItemStack assemble(Container input, net.minecraft.core.RegistryAccess registries) {
        return this.convertedRecipe == null ? ItemStack.EMPTY : this.convertedRecipe.assemble(input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return this.convertedRecipe != null && this.convertedRecipe.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
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
