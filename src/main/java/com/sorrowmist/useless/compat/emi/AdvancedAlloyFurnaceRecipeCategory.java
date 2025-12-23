package com.sorrowmist.useless.compat.emi;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlock;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class AdvancedAlloyFurnaceRecipeCategory extends EmiRecipeCategory {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(UselessMod.MOD_ID, "advanced_alloy_furnace");
    public static final AdvancedAlloyFurnaceRecipeCategory CATEGORY = new AdvancedAlloyFurnaceRecipeCategory();

    public AdvancedAlloyFurnaceRecipeCategory() {
        super(ID, EmiStack.of(new ItemStack(AdvancedAlloyFurnaceBlock.ADVANCED_ALLOY_FURNACE_BLOCK.get())));
    }
}