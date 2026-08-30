package com.sorrowmist.useless.content.recipe.adapters.delight.nomadsdelight;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightSyntheticRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Converts Nomad's Delight's JEI-only curd straining recipe. */
public final class CurdBagRecipeAdapter implements IRecipeAdapter<DelightSyntheticRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("nomads_delight", "curd_bag");
    private static final ResourceLocation QATYQ_BUCKET_ID =
            ResourceLocation.fromNamespaceAndPath("nomads_delight", "qatyq_bucket");
    private static final ResourceLocation CURD_ID =
            ResourceLocation.fromNamespaceAndPath("nomads_delight", "curd");
    private static final int STRAINING_TIME_TICKS = 2400;

    @Override
    public String sourceId() {
        return RecipeSourceIds.NOMADS_DELIGHT;
    }

    @Override
    public Class<DelightSyntheticRecipe> getRecipeClass() {
        return DelightSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return itemStack(MOLD_ID);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<DelightSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<DelightSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        ItemStack input = itemStack(QATYQ_BUCKET_ID);
        ItemStack output = itemStack(CURD_ID);
        ItemStack mold = itemStack(MOLD_ID);
        if (input.isEmpty() || output.isEmpty() || mold.isEmpty()) {
            return List.of();
        }

        ResourceLocation sourceId = ResourceLocation.fromNamespaceAndPath(
                "nomads_delight", "curd_bag");
        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId),
                AdapterUtils.mergeIngredients(List.of(Ingredient.of(input))),
                List.of(),
                List.of(),
                List.of(output),
                List.of(),
                List.of(),
                Math.max(1L, (long) STRAINING_TIME_TICKS * AdapterUtils.DEFAULT_ENERGY / 200L),
                STRAINING_TIME_TICKS,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(mold)),
                AlloyFurnaceMode.NORMAL
        );
        return List.of(new RecipeHolder<>(
                converted.id(), new DelightSyntheticRecipe(converted)));
    }

    @Override
    public List<RecipeHolder<DelightSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)
                || mergedInputs == null || mergedInputs.isEmpty()
                || (mergedFluids != null && !mergedFluids.isEmpty())) {
            return List.of();
        }

        return getGeneratedRecipes(level).stream()
                .filter(holder -> {
                    AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
                    return recipe != null && DelightRecipeAdapterUtils.matchesItems(
                            recipe.inputs(), mergedInputs, List.of());
                })
                .toList();
    }

    private static ItemStack itemStack(ResourceLocation id) {
        Item item = DelightRecipeAdapterUtils.registeredItem(id);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }
}
