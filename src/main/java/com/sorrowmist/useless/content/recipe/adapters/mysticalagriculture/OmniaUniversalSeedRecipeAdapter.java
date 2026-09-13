package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.omnia.omnia.registry.ModItems;
import com.omnia.omnia.util.TargetHelper;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Converts Omnia's component-backed universal crop seed into universal essence. */
public final class OmniaUniversalSeedRecipeAdapter
        implements IRecipeAdapter<OmniaUniversalSeedRecipeAdapter.DynamicRecipe> {
    private static final int WATER_AMOUNT = 1;
    private static final int ENERGY = 1000;
    private static final int PROCESS_TIME = 60;
    private static final net.minecraft.resources.ResourceLocation RECIPE_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "omnia", "universal_crop_seed_essence");

    @Override
    public Class<DynamicRecipe> getRecipeClass() {
        return DynamicRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return isUniversalCropSeed(mold) && TargetHelper.hasTarget(mold);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<DynamicRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<DynamicRecipe> holder, Level level, List<ItemStack> actualInputs) {
        return convertAll(holder, level);
    }

    @Override
    public List<RecipeHolder<DynamicRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold,
            List<ItemStack> actualInputs) {
        if (level == null) return List.of();

        AdvancedAlloyFurnaceRecipe recipe = createRecipe(mold, level.registryAccess());
        return recipe == null
                ? List.of()
                : List.of(new RecipeHolder<>(RECIPE_ID, new DynamicRecipe(recipe)));
    }

    private static boolean isUniversalCropSeed(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.is(ModItems.UNIVERSAL_CROP_SEED.get());
    }

    /** Builds the target-specific recipe used by direct processing and AE execution. */
    @Nullable
    private static AdvancedAlloyFurnaceRecipe createRecipe(
            @Nullable ItemStack mold, @Nullable HolderLookup.Provider registries) {
        if (!isUniversalCropSeed(mold) || registries == null || !TargetHelper.hasTarget(mold)) {
            return null;
        }

        String targetId = TargetHelper.getTargetId(mold);
        Item targetItem = TargetHelper.getTargetItem(targetId);
        ItemStack targetStack = TargetHelper.getTargetStack(mold, registries);
        if (targetId == null || targetId.isEmpty() || targetItem == Items.AIR
                || targetStack.isEmpty() || TargetHelper.isStorageLike(targetStack)) {
            return null;
        }

        ItemStack output = new ItemStack(ModItems.UNIVERSAL_ESSENCE.get());
        TargetHelper.applyTarget(output, targetId, targetStack, registries);
        return new AdvancedAlloyFurnaceRecipe(
                RECIPE_ID,
                List.of(),
                List.of(new FluidStack(Fluids.WATER, WATER_AMOUNT)),
                List.of(output),
                List.of(),
                ENERGY,
                PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                DataComponentIngredient.of(true, mold.copyWithCount(1)),
                AlloyFurnaceMode.NORMAL);
    }

    /** Recipe-manager payload for the generated JEI template and runtime variant. */
    public static final class DynamicRecipe implements Recipe<RecipeInput> {
        private final AdvancedAlloyFurnaceRecipe convertedRecipe;

        private DynamicRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
            this.convertedRecipe = convertedRecipe;
        }

        public AdvancedAlloyFurnaceRecipe convertedRecipe() {
            return convertedRecipe;
        }

        @Override
        public boolean matches(RecipeInput input, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return false;
        }

        @Override
        public ItemStack getResultItem(HolderLookup.Provider registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return null;
        }

        @Override
        public RecipeType<?> getType() {
            return null;
        }
    }
}
