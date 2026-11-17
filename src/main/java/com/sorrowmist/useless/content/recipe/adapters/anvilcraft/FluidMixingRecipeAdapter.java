package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.dubhe.anvilcraft.recipe.FluidMixingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 转换铁砧工艺的大型炼药锅流体反应。 */
public final class FluidMixingRecipeAdapter implements IRecipeAdapter<FluidMixingRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<FluidMixingRecipe> getRecipeClass() {
        return FluidMixingRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<FluidMixingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        FluidMixingRecipe source = holder.value();
        List<Ingredient> molds = List.of(Multiblock4DRecipeAdapter.mold("giant_anvil"),
                Multiblock4DRecipeAdapter.mold("large_cauldron"));
        if (molds.stream().anyMatch(Ingredient::isEmpty) || source.getFluidIngredients().isEmpty()
                || source.getItemResults().isEmpty() && source.getFluidResults().isEmpty()) return List.of();
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), List.of(),
                source.getFluidIngredients().stream().map(LongSizedFluidIngredient::from).toList(), List.of(),
                source.getItemResults().stream().map(ItemStack::copy).toList(),
                source.getFluidResults().stream().map(fluid -> fluid.copy()).toList(), List.of(),
                AdapterUtils.energyForProcessTime(AdapterUtils.DEFAULT_PROCESS_TIME),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL));
    }
}
