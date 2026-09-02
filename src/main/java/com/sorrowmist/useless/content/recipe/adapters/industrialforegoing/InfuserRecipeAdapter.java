package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.yxiao233.ifeu.common.config.machine.InfuserConfig;
import net.yxiao233.ifeu.common.recipe.InfuserRecipe;
import net.yxiao233.ifeu.common.registry.IFEUBlocks;
import net.yxiao233.ifeu.common.registry.IFEURecipes;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Industrial Foregoing Extra Upgrades infuser recipes. */
public final class InfuserRecipeAdapter implements IRecipeAdapter<InfuserRecipe> {
    private static final int COMPACT_RECIPE_TIME = 200;
    private static final int COMPACT_RECIPE_FLUID_AMOUNT = 1000;

    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<InfuserRecipe> getRecipeClass() {
        return InfuserRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(IFEUBlocks.INFUSER.getBlock());
    }

    /**
     * The infuser also handles every non-mob fluid bucket directly in its block entity instead
     * of loading those operations from RecipeManager. Expose the same operations to the alloy
     * furnace recipe catalog.
     */
    @Override
    public List<RecipeHolder<InfuserRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();

        List<RecipeHolder<InfuserRecipe>> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BucketItem bucket)
                    || item instanceof MobBucketItem
                    || item == Items.BUCKET) {
                continue;
            }

            FluidStack fluid = new FluidStack(bucket.content, COMPACT_RECIPE_FLUID_AMOUNT);
            if (fluid.isEmpty()) continue;

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                    "ifeu", "infuser/compact/" + itemId.getNamespace() + "/" + itemId.getPath());
            result.add(new RecipeHolder<>(recipeId, new InfuserRecipe(
                    Items.BUCKET.getDefaultInstance(), fluid, COMPACT_RECIPE_TIME,
                    item.getDefaultInstance())));
        }
        return List.copyOf(result);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<InfuserRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();

        InfuserRecipe source = holder.value();
        if (source.input == null || source.input.isEmpty() || source.input.getCount() <= 0
                || source.inputFluid == null || source.inputFluid.isEmpty()
                || source.inputFluid.getAmount() <= 0
                || source.output == null || source.output.isEmpty()) {
            return List.of();
        }

        int processTime = IndustrialForegoingRecipeAdapterUtils.positive(source.processingTime);
        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(
                        Ingredient.of(source.input), source.input.getCount())),
                List.of(LongSizedFluidIngredient.from(source.inputFluid)),
                List.of(),
                List.of(source.output.copy()),
                List.of(),
                List.of(),
                IndustrialForegoingRecipeAdapterUtils.energyPerTick(
                        InfuserConfig.powerPerTick, processTime),
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                AlloyFurnaceMode.NORMAL);
        return List.of(converted);
    }

    @Override
    public List<RecipeHolder<InfuserRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)
                || (mergedInputs == null || mergedInputs.isEmpty())
                || (mergedFluids == null || mergedFluids.isEmpty())) {
            return List.of();
        }

        RecipeType<InfuserRecipe> type = infuserType();
        if (type == null) return List.of();

        List<RecipeHolder<InfuserRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<InfuserRecipe> holder : recipeManager.getAllRecipesFor(type)) {
            if (matchesConverted(holder, level, mergedInputs, mergedFluids)) {
                matches.add(holder);
            }
        }
        for (RecipeHolder<InfuserRecipe> holder : getGeneratedRecipes(level)) {
            if (matchesConverted(holder, level, mergedInputs, mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    private boolean matchesConverted(RecipeHolder<InfuserRecipe> holder, Level level,
                                     Map<Ingredient, Long> mergedInputs,
                                     Map<FluidStack, Long> mergedFluids) {
        List<AdvancedAlloyFurnaceRecipe> converted = convertAll(holder, level);
        return !converted.isEmpty()
                && IndustrialForegoingRecipeAdapterUtils.matches(
                converted.getFirst(), mergedInputs, mergedFluids);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static RecipeType<InfuserRecipe> infuserType() {
        if (IFEURecipes.INFUSER_TYPE == null || IFEURecipes.INFUSER_TYPE.get() == null) {
            return null;
        }
        return (RecipeType<InfuserRecipe>) (RecipeType<?>) IFEURecipes.INFUSER_TYPE.get();
    }
}
