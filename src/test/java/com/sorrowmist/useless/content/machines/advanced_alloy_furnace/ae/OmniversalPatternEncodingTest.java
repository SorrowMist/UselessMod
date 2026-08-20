package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmniversalPatternEncodingTest {
    @Test
    void plainGoatHornIngredientAcceptsOtherComponentVariantsButExactIngredientDoesNot() {
        ItemStack encodedHorn = namedGoatHorn("encoded");
        ItemStack otherHorn = namedGoatHorn("other");
        AEProcessingPattern source = processingPattern(encodedHorn);

        List<Integer> plainSlots = OmniversalPatternEncoding.resolveItemIdInputSlots(
                recipe(Ingredient.of(Items.GOAT_HORN)), source, List.of());
        assertEquals(List.of(0), plainSlots);

        DynamicComponentPatternDetails plain = new DynamicComponentPatternDetails(
                source, plainSlots, List.of(), RegistryAccess.EMPTY);
        assertTrue(plain.getInputs()[0].isValid(key(otherHorn), null));

        Ingredient exactHorn = DataComponentIngredient.of(true, encodedHorn.copyWithCount(1));
        List<Integer> exactSlots = OmniversalPatternEncoding.resolveItemIdInputSlots(
                recipe(exactHorn), source, List.of());
        assertTrue(exactSlots.isEmpty());

        DynamicComponentPatternDetails exact = new DynamicComponentPatternDetails(
                source, exactSlots, List.of(), RegistryAccess.EMPTY);
        assertFalse(exact.getInputs()[0].isValid(key(otherHorn), null));
    }

    @Test
    void processingPatternKeepsCompoundFluidIngredientInOneSlot() {
        AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "compound_fluid_pattern"),
                List.of(),
                List.of(new SizedFluidIngredient(FluidIngredient.of(Fluids.WATER, Fluids.LAVA), 1_000)),
                List.of(),
                List.of(new ItemStack(Items.NETHER_STAR)),
                List.of(), List.of(),
                1L, 20, Ingredient.EMPTY, 0, Ingredient.EMPTY,
                AlloyFurnaceMode.NORMAL);

        ItemStack encoded = OmniversalPatternEncoding.createProcessingPattern(recipe);
        AEProcessingPattern pattern = new AEProcessingPattern(key(encoded));

        assertEquals(1, pattern.getInputs().length);
        assertEquals(1_000, pattern.getInputs()[0].getMultiplier());
    }

    @Test
    void emptyDisplayFluidIngredientStillHasAnEncodingRepresentative() {
        FluidIngredient custom = new EmptyDisplayFluidIngredient(Fluids.WATER);
        AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "empty_display_fluid"),
                List.of(),
                List.of(new SizedFluidIngredient(custom, 1_000)),
                List.of(),
                List.of(new ItemStack(Items.NETHER_STAR)),
                List.of(), List.of(),
                1L, 20, Ingredient.EMPTY, 0, Ingredient.EMPTY,
                AlloyFurnaceMode.NORMAL);

        List<List<GenericStack>> options = OmniversalPatternJeiTransferHandler.inputOptions(recipe);
        ItemStack encoded = OmniversalPatternEncoding.createProcessingPattern(recipe);

        assertEquals(1, options.size());
        assertEquals(1_000L, options.getFirst().getFirst().amount());
        assertFalse(encoded.isEmpty());
        AEProcessingPattern pattern = new AEProcessingPattern(key(encoded));
        assertEquals(1_000L, pattern.getInputs()[0].getMultiplier());
    }

    private static AEProcessingPattern processingPattern(ItemStack input) {
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(key(input), 2L)),
                List.of(new GenericStack(key(new ItemStack(Items.NETHER_STAR)), 1L)));
        return new AEProcessingPattern(key(encoded));
    }

    private static AdvancedAlloyFurnaceRecipe recipe(Ingredient input) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "goat_horn_components"),
                List.of(new CountedIngredient(input, 2L)),
                List.of(), List.of(),
                List.of(new ItemStack(Items.NETHER_STAR)),
                List.of(), List.of(),
                1L, 20,
                Ingredient.EMPTY, 0, Ingredient.EMPTY,
                AlloyFurnaceMode.NORMAL);
    }

    private static ItemStack namedGoatHorn(String name) {
        ItemStack horn = new ItemStack(Items.GOAT_HORN);
        horn.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return horn;
    }

    private static final class EmptyDisplayFluidIngredient extends FluidIngredient {
        private final net.minecraft.world.level.material.Fluid fluid;

        private EmptyDisplayFluidIngredient(net.minecraft.world.level.material.Fluid fluid) {
            this.fluid = fluid;
        }

        @Override
        public boolean test(net.neoforged.neoforge.fluids.FluidStack stack) {
            return stack != null && stack.getFluid() == fluid;
        }

        @Override
        protected java.util.stream.Stream<net.neoforged.neoforge.fluids.FluidStack> generateStacks() {
            return java.util.stream.Stream.empty();
        }

        @Override
        public boolean isSimple() {
            return false;
        }

        @Override
        public FluidIngredientType<?> getType() {
            return null;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }
    }

    private static AEItemKey key(ItemStack stack) {
        return Objects.requireNonNull(AEItemKey.of(stack));
    }
}
