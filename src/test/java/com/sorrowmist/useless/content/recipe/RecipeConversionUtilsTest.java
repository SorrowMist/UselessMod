package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeConversionUtilsTest {

    @Test
    void skipsFailedRecipeAndContinuesBatchConversion() {
        AdvancedAlloyFurnaceRecipe converted = convertedRecipe("good");
        ThrowingAdapter adapter = new ThrowingAdapter(converted);
        List<RecipeHolder<FakeRecipe>> holders = List.of(
                holder("bad", new FakeRecipe()),
                holder("good", new FakeRecipe()));

        List<AdvancedAlloyFurnaceRecipe> results = new ArrayList<>();
        for (RecipeHolder<FakeRecipe> holder : holders) {
            results.addAll(RecipeConversionUtils.convertAll(adapter, holder, null));
        }

        assertEquals(List.of(converted), results);
    }

    @Test
    void skipsFailedRecipeWithActualInputsAndContinuesBatchConversion() {
        AdvancedAlloyFurnaceRecipe converted = convertedRecipe("good_with_inputs");
        ThrowingAdapter adapter = new ThrowingAdapter(converted);
        List<RecipeHolder<FakeRecipe>> holders = List.of(
                holder("bad_with_inputs", new FakeRecipe()),
                holder("good_with_inputs", new FakeRecipe()));

        List<AdvancedAlloyFurnaceRecipe> results = new ArrayList<>();
        for (RecipeHolder<FakeRecipe> holder : holders) {
            results.addAll(RecipeConversionUtils.convertAll(
                    adapter, holder, null, List.of(ItemStack.EMPTY)));
        }

        assertTrue(adapter.actualInputsOverloadUsed);
        assertEquals(List.of(converted), results);
    }

    @Test
    void convertsNullResultToAnEmptyList() {
        IRecipeAdapter<FakeRecipe> adapter = new ThrowingAdapter(null);

        assertTrue(RecipeConversionUtils.convertAll(
                adapter, holder("null_result", new FakeRecipe()), null).isEmpty());
    }

    @Test
    void runtimeLookupFallsBackToTheLegacyStaticLookup() {
        RecipeHolder<FakeRecipe> expected = holder("static_lookup", new FakeRecipe());
        IRecipeAdapter<FakeRecipe> adapter = new StaticLookupAdapter(expected);

        assertEquals(List.of(expected), adapter.findMatchingRecipes(
                null, Map.of(), Map.of(), Map.of(), ItemStack.EMPTY,
                List.of(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT))));
    }

    @Test
    void doesNotCatchErrors() {
        IRecipeAdapter<FakeRecipe> adapter = new ThrowingAdapter(
                convertedRecipe("error"), true);

        assertThrows(AssertionError.class, () -> RecipeConversionUtils.convertAll(
                adapter, holder("error", new FakeRecipe()), null));
    }

    private static RecipeHolder<FakeRecipe> holder(String path, FakeRecipe recipe) {
        return new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("recipe_conversion_test", path), recipe);
    }

    private static AdvancedAlloyFurnaceRecipe convertedRecipe(String path) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("recipe_conversion_test", path),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                1L, 1, net.minecraft.world.item.crafting.Ingredient.EMPTY, 0,
                net.minecraft.world.item.crafting.Ingredient.EMPTY, AlloyFurnaceMode.NORMAL);
    }

    private static final class ThrowingAdapter implements IRecipeAdapter<FakeRecipe> {
        private final AdvancedAlloyFurnaceRecipe converted;
        private final boolean throwError;
        private boolean actualInputsOverloadUsed;

        private ThrowingAdapter(AdvancedAlloyFurnaceRecipe converted) {
            this(converted, false);
        }

        private ThrowingAdapter(AdvancedAlloyFurnaceRecipe converted, boolean throwError) {
            this.converted = converted;
            this.throwError = throwError;
        }

        @Override
        public Class<FakeRecipe> getRecipeClass() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ItemStack getMoldItem() {
            return null;
        }

        @Override
        public List<AdvancedAlloyFurnaceRecipe> convertAll(
                RecipeHolder<FakeRecipe> holder, Level level) {
            if (throwError) {
                throw new AssertionError("test error");
            }
            if (holder.id().getPath().startsWith("bad")) {
                throw new IllegalStateException("test conversion failure");
            }
            return converted == null ? null : List.of(converted);
        }

        @Override
        public List<AdvancedAlloyFurnaceRecipe> convertAll(
                RecipeHolder<FakeRecipe> holder, Level level, List<ItemStack> actualInputs) {
            actualInputsOverloadUsed = true;
            return convertAll(holder, level);
        }
    }

    private static final class StaticLookupAdapter implements IRecipeAdapter<FakeRecipe> {
        private final RecipeHolder<FakeRecipe> expected;

        private StaticLookupAdapter(RecipeHolder<FakeRecipe> expected) {
            this.expected = expected;
        }

        @Override
        public Class<FakeRecipe> getRecipeClass() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ItemStack getMoldItem() {
            return null;
        }

        @Override
        public List<RecipeHolder<FakeRecipe>> findMatchingRecipes(
                Level level, Map<net.minecraft.world.item.crafting.Ingredient, Long> mergedInputs,
                Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
                @Nullable ItemStack mold) {
            return List.of(expected);
        }
    }

    private static final class FakeRecipe implements Recipe<RecipeInput> {
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
