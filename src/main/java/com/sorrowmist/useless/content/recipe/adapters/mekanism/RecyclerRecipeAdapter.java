package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.jerry.mekmm.api.recipes.MoreMachineRecipeTypes;
import com.jerry.mekmm.api.recipes.RecyclerRecipe;
import com.jerry.mekmm.common.config.MoreMachineConfig;
import com.jerry.mekmm.common.registries.MoreMachineBlocks;
import com.jerry.mekmm.common.tile.machine.TileEntityRecycler;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Exposes every possible MoreMachine Recycler output as a water-only recipe. */
public final class RecyclerRecipeAdapter extends MekanismSyntheticRecipeAdapter {
    private static final int WATER_AMOUNT = 1_000;
    private static final int PROCESS_TICKS = TileEntityRecycler.BASE_TICKS_REQUIRED;

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MoreMachineBlocks.RECYCLER.get());
    }

    @Override
    protected List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        List<ItemStack> outputs = new ArrayList<>();
        for (RecipeHolder<RecyclerRecipe> holder : level.getRecipeManager().getAllRecipesFor(
                MoreMachineRecipeTypes.TYPE_RECYCLER.value())) {
            RecyclerRecipe source = holder.value();
            if (source == null || source.isIncomplete() || source.getOutputChance() <= 0) {
                continue;
            }

            for (ItemStack output : source.getChanceOutputDefinition()) {
                if (output == null || output.isEmpty() || containsOutput(outputs, output)) {
                    continue;
                }
                outputs.add(output.copy());
            }
        }

        if (outputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<MekanismSyntheticRecipe>> result = new ArrayList<>(outputs.size());
        for (int index = 0; index < outputs.size(); index++) {
            ItemStack output = outputs.get(index);
            ResourceLocation id = recipeId(index, output);
            AdvancedAlloyFurnaceRecipe converted = MekanismChemicalRecipeSupport.recipe(
                    id,
                    List.of(),
                    List.of(new SizedFluidIngredient(
                            FluidIngredient.tag(FluidTags.WATER), WATER_AMOUNT)),
                    List.of(),
                    List.of(output.copy()),
                    List.of(),
                    List.of(),
                    energyCost(),
                    PROCESS_TICKS,
                    getMoldItem());
            result.add(MekanismChemicalRecipeSupport.syntheticHolder(id, converted));
        }
        return List.copyOf(result);
    }

    private static long energyCost() {
        return MekanismChemicalRecipeSupport.saturatingMultiply(
                MoreMachineConfig.usage.recycler.get(), PROCESS_TICKS);
    }

    private static boolean containsOutput(List<ItemStack> outputs, ItemStack candidate) {
        for (ItemStack output : outputs) {
            if (output.getCount() == candidate.getCount()
                    && ItemStack.isSameItemSameComponents(output, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation recipeId(int index, ItemStack output) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(output.getItem());
        String suffix = itemId == null
                ? "output_" + index
                : itemId.getNamespace() + "_" + itemId.getPath().replace('/', '_');
        return ResourceLocation.fromNamespaceAndPath(
                "mekmm", "recycler_water_" + index + "_" + suffix);
    }
}
