package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery;

import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.TeapotRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.List;

public final class TeapotRecipeAdapter extends AbstractCookeryRecipeAdapter<TeapotRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "teapot");
    private static final ResourceLocation EMPTY_TEA_FLUID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "empty");

    @Override
    public Class<TeapotRecipe> getRecipeClass() {
        return TeapotRecipe.class;
    }

    @Override
    protected ResourceLocation moldId() {
        return MOLD_ID;
    }

    @Override
    protected AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, TeapotRecipe source, Level level) {
        if (source.teaFluid() == null || EMPTY_TEA_FLUID.equals(source.teaFluid())
                || source.ingredient() == null || source.ingredient().isEmpty()
                || source.ingredientCount() <= 0 || source.result() == null
                || source.result().isEmpty()) {
            return null;
        }

        var fluid = BuiltInRegistries.FLUID.getOptional(source.teaFluid()).orElse(null);
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
            return null;
        }

        ItemStack output = source.result().copyWithCount(TeapotRecipe.OUTPUT_COUNT);
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(recipeId),
                List.of(new CountedIngredient(source.ingredient(), source.ingredientCount())),
                List.of(new LongSizedFluidIngredient(
                        FluidIngredient.single(new FluidStack(fluid, 1_000)), 1_000)),
                List.of(),
                List.of(output),
                List.of(),
                List.of(),
                energyFor(Math.max(1, source.time())),
                Math.max(1, source.time()),
                Ingredient.EMPTY,
                0,
                moldIngredients(),
                AlloyFurnaceMode.NORMAL
        );
    }
}
