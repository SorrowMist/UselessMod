package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.recipe.AlloyFurnaceCompatApi;
import com.sorrowmist.useless.api.recipe.AlloyFurnaceRecipeBuilder;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlloyFurnaceCompatApiTest {

    @Test
    void builderBuildsEveryPublicRecipeChannel() {
        ItemStack namedOutput = new ItemStack(Items.DIAMOND);
        namedOutput.set(DataComponents.CUSTOM_NAME, Component.literal("api output"));
        GenericStack keyInput = Objects.requireNonNull(
                GenericStack.fromItemStack(new ItemStack(Items.QUARTZ, 2)));
        GenericStack keyOutput = Objects.requireNonNull(
                GenericStack.fromItemStack(new ItemStack(Items.EMERALD)));

        AdvancedAlloyFurnaceRecipe recipe = AlloyFurnaceRecipeBuilder.create()
                .input(Items.OAK_LOG, 2)
                .fluidInput(new FluidStack(Fluids.WATER, 1_000))
                .keyInput(keyInput)
                .output(namedOutput)
                .fluidOutput(new FluidStack(Fluids.LAVA, 250))
                .keyOutput(keyOutput)
                .energy(12_345L)
                .processTime(67)
                .catalyst(Ingredient.of(Items.FLINT), 3)
                .molds(Ingredient.of(Items.FURNACE), Ingredient.of(Items.CRAFTING_TABLE))
                .mode(AlloyFurnaceMode.PRESS)
                .build(ResourceLocation.fromNamespaceAndPath("api_test", "complete"));

        assertEquals(1, recipe.inputs().size());
        assertEquals(2, recipe.inputs().getFirst().count());
        assertEquals(1_000, recipe.inputFluids().getFirst().amount());
        assertEquals(List.of(keyInput), recipe.keyInputs());
        assertEquals(1, recipe.outputs().size());
        assertSame(namedOutput.getItem(), recipe.outputs().getFirst().getItem());
        assertEquals(namedOutput.get(DataComponents.CUSTOM_NAME),
                recipe.outputs().getFirst().get(DataComponents.CUSTOM_NAME));
        assertEquals(250, recipe.outputFluids().getFirst().getAmount());
        assertEquals(List.of(keyOutput), recipe.keyOutputs());
        assertEquals(12_345L, recipe.energy());
        assertEquals(67, recipe.processTime());
        assertEquals(3, recipe.catalystUses());
        assertEquals(2, recipe.molds().size());
        assertEquals(AlloyFurnaceMode.PRESS, recipe.mode());
    }

    @Test
    void publicRegistrationAcceptsMultipleAdaptersAndUsesSourceSwitch() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        ApiAdapter enabledA = new ApiAdapter();
        ApiAdapter enabledB = new ApiAdapter();
        ApiAdapter disabled = new ApiAdapter();

        AlloyFurnaceCompatApi.register("Api_Test_Source", () -> true, enabledA, enabledB);
        AlloyFurnaceCompatApi.register("api_test_disabled", () -> false, disabled);

        assertTrue(manager.getRegisteredAdapters().contains(enabledA));
        assertTrue(manager.getRegisteredAdapters().contains(enabledB));
        assertFalse(manager.getRegisteredAdapters().contains(disabled));
        assertEquals("api_test_source", manager.getAdapterSourceId(enabledA));
        assertEquals("api_test_source", manager.getAdapterSourceId(enabledB));
    }

    @Test
    void failedAdapterRegistrationDoesNotRemainInTheManager() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        ApiAdapter failing = new ApiAdapter();
        failing.throwOnMoldLookup = true;

        AlloyFurnaceCompatApi.register("api_test_failure", failing);

        assertFalse(manager.getRegisteredAdapters().contains(failing));
    }

    @Test
    void multiMoldRecipeUsesIndependentRequirements() {
        AdvancedAlloyFurnaceRecipe recipe = AlloyFurnaceRecipeBuilder.create()
                .input(Items.OAK_LOG, 1)
                .output(Items.DIAMOND, 1)
                .molds(Ingredient.of(Items.FURNACE), Ingredient.of(Items.CRAFTING_TABLE))
                .build(ResourceLocation.fromNamespaceAndPath("api_test", "multi_mold"));

        assertTrue(OmniversalMoldHubBlockEntity.matchesMolds(
                recipe.molds(), List.of(new ItemStack(Items.FURNACE), new ItemStack(Items.CRAFTING_TABLE))));
        assertFalse(OmniversalMoldHubBlockEntity.matchesMolds(
                recipe.molds(), List.of(new ItemStack(Items.FURNACE))));
        assertTrue(AlloyFurnaceRecipeManager.selectBestCandidate(
                List.of(recipe), List.of(new ItemStack(Items.OAK_LOG)), List.of(), List.of(),
                new ItemStack(Items.FURNACE), List.of(), 1) == null);
    }

    private static final class ApiAdapter implements IRecipeAdapter<FakeRecipe> {
        private boolean throwOnMoldLookup;

        @Override
        public Class<FakeRecipe> getRecipeClass() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ItemStack getMoldItem() {
            if (throwOnMoldLookup) {
                throw new IllegalStateException("test adapter failure");
            }
            return null;
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
