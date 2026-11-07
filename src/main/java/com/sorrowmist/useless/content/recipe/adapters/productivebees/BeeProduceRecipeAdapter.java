package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import cy.jdkdigital.productivebees.common.recipe.AdvancedBeehiveRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** The hive output table alone is not an executable, unconditional bee-production recipe. */
public final class BeeProduceRecipeAdapter implements IRecipeAdapter<AdvancedBeehiveRecipe> {
    @Override
    public Class<AdvancedBeehiveRecipe> getRecipeClass() {
        return AdvancedBeehiveRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return false;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<AdvancedBeehiveRecipe> holder, Level level) {
        return ProductiveBeesAdapterUtils.unsupported(holder,
                "Production needs native hive inhabitants/genetics, nectar/flowers, weather/time, upgrades and inventory");
    }

    @Override
    public List<RecipeHolder<AdvancedBeehiveRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return List.of();
    }
}
