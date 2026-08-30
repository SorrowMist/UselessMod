package com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Youkai's wood-basin recipes into bottled outputs. */
public final class BasinRecipeAdapter implements IRecipeAdapter<DelightSyntheticRecipe> {
    private static final String SOURCE_CLASS =
            "dev.xkmc.youkaishomecoming.content.pot.basin.SimpleBasinRecipe";
    private static final ResourceLocation BASIN_ID =
            ResourceLocation.fromNamespaceAndPath("youkaisfeasts", "wood_basin");

    @Override
    public String sourceId() {
        return RecipeSourceIds.YOUKAI_HOMECOMING;
    }

    @Override
    public Class<DelightSyntheticRecipe> getRecipeClass() {
        return DelightSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        Item item = DelightRecipeAdapterUtils.registeredItem(BASIN_ID);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
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

        List<RecipeHolder<DelightSyntheticRecipe>> generated = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value() == null || !SOURCE_CLASS.equals(holder.value().getClass().getName())) {
                continue;
            }
            AdvancedAlloyFurnaceRecipe converted = convertSource(holder.id(), holder.value());
            if (converted != null) {
                generated.add(new RecipeHolder<>(converted.id(), new DelightSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(generated);
    }

    @Override
    public List<RecipeHolder<DelightSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<DelightSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<DelightSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe != null
                    && DelightRecipeAdapterUtils.matchesItems(recipe.inputs(), mergedInputs, List.of())
                    && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertSource(ResourceLocation sourceId,
                                                              Object source) {
        Ingredient input = DelightRecipeAdapterUtils.fieldValue(source, "input", Ingredient.class);
        FluidStack output = DelightRecipeAdapterUtils.fieldValue(source, "output", FluidStack.class);
        if (input == null || input.isEmpty() || output == null || output.isEmpty()
                || output.getAmount() <= 0) {
            return null;
        }

        int operations = YoukaiRecipeAdapterUtils.operationsPerContainer(output);
        ItemStack bottledOutput = YoukaiRecipeAdapterUtils.bottledOutputForOperations(output, operations);
        if (bottledOutput != null && operations > 0) {
            return new AdvancedAlloyFurnaceRecipe(
                    AdapterUtils.convertedId(sourceId),
                    List.of(new CountedIngredient(input, operations)),
                    List.of(),
                    List.of(),
                    List.of(bottledOutput),
                    List.of(),
                    List.of(),
                    Math.max(1L, (long) AdapterUtils.DEFAULT_ENERGY * operations),
                    Math.max(1, AdapterUtils.safeInt(
                            (long) AdapterUtils.DEFAULT_PROCESS_TIME * operations)),
                    Ingredient.EMPTY,
                    0,
                    List.of(AdapterUtils.toMoldIngredient(moldStack())),
                    AlloyFurnaceMode.NORMAL
            );
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId),
                List.of(new CountedIngredient(input, 1L)),
                List.of(),
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(moldStack())),
                AlloyFurnaceMode.NORMAL
        );
    }

    @Nullable
    private static ItemStack moldStack() {
        Item item = DelightRecipeAdapterUtils.registeredItem(BASIN_ID);
        return item == null ? null : item.getDefaultInstance();
    }
}
