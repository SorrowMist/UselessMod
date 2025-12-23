package com.sorrowmist.useless.compat.emi;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.registry.ModIngots;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class CatalystInfoCategory extends EmiRecipeCategory {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(UselessMod.MOD_ID, "catalyst_info");
    public static final CatalystInfoCategory CATEGORY = new CatalystInfoCategory();

    public CatalystInfoCategory() {
        super(ID, EmiStack.of(new ItemStack(ModIngots.USEFUL_INGOT.get())));
    }
}