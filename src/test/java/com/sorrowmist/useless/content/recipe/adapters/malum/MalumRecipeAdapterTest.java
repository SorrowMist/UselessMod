package com.sorrowmist.useless.content.recipe.adapters.malum;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.sammy.malum.common.recipe.SpiritFocusingRecipe;
import com.sammy.malum.common.recipe.SpiritInfusionRecipe;
import com.sammy.malum.core.systems.recipe.SpiritIngredient;
import com.sammy.malum.registry.common.magic.MalumSpiritTypes;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternStackView;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalumRecipeAdapterTest {

    @Test
    void focusingConsumesSpiritsButIgnoresTheImpetus() {
        SpiritIngredient arcane = new SpiritIngredient(MalumSpiritTypes.ARCANE_SPIRIT, 3);
        ItemStack output = named(new ItemStack(Items.DIAMOND), "focused output");
        SpiritFocusingRecipe source = new SpiritFocusingRecipe(
                Ingredient.of(Items.DIAMOND_SWORD), output, List.of(arcane), 140, 25);

        SpiritFocusingRecipeAdapter adapter = new SpiritFocusingRecipeAdapter();
        AdvancedAlloyFurnaceRecipe converted = adapter.convertAll(holder("focus", source), null).getFirst();

        ItemStack shard = arcane.asItemStack().copyWithCount(1);
        assertEquals(3L, required(converted, shard));
        assertEquals(0L, required(converted, new ItemStack(Items.DIAMOND_SWORD)));
        assertEquals(AdapterUtils.DEFAULT_ENERGY, converted.energy());
        assertEquals(140, converted.processTime());
        assertTrue(converted.mold().test(adapter.getMoldItem()));
        assertEquals("focused output", converted.outputs().getFirst()
                .get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void infusionConsumesItsFullInputSetAndPreservesOutputComponents() {
        ItemStack output = named(new ItemStack(Items.NETHERITE_SWORD), "infused output");
        SpiritInfusionRecipe source = new SpiritInfusionRecipe(
                new SizedIngredient(Ingredient.of(Items.DIAMOND_SWORD), 1),
                output,
                List.of(),
                List.of(new SizedIngredient(Ingredient.of(Items.NETHER_STAR), 2)),
                false);

        SpiritInfusionRecipeAdapter adapter = new SpiritInfusionRecipeAdapter();
        AdvancedAlloyFurnaceRecipe converted = adapter.convertAll(holder("infusion", source), null).getFirst();

        assertEquals(1L, required(converted, new ItemStack(Items.DIAMOND_SWORD)));
        assertEquals(2L, required(converted, new ItemStack(Items.NETHER_STAR)));
        assertEquals(AdapterUtils.DEFAULT_ENERGY, converted.energy());
        assertEquals(300, converted.processTime());
        assertTrue(converted.mold().test(adapter.getMoldItem()));
        assertEquals("infused output", converted.outputs().getFirst()
                .get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void carryOverInfusionsBindTheActualPrimaryAndRelaxOnlyThatAeSlot() {
        ItemStack sourceResult = new ItemStack(Items.NETHERITE_SWORD);
        SpiritInfusionRecipe source = new SpiritInfusionRecipe(
                new SizedIngredient(Ingredient.of(Items.DIAMOND_SWORD), 1),
                sourceResult,
                List.of(),
                List.of(new SizedIngredient(Ingredient.of(Items.NETHER_STAR), 1)),
                true);
        ItemStack primary = named(new ItemStack(Items.DIAMOND_SWORD), "actual primary");
        ItemStack otherPrimary = named(new ItemStack(Items.DIAMOND_SWORD), "other primary");

        SpiritInfusionRecipeAdapter adapter = new SpiritInfusionRecipeAdapter();
        AdvancedAlloyFurnaceRecipe runtimeRecipe = adapter.convertAll(
                        holder("carry_over", source), null,
                        List.of(primary, new ItemStack(Items.NETHER_STAR)))
                .getFirst();
        assertTrue(ItemIngredientAllocator.matches(runtimeRecipe.inputs(),
                List.of(primary, new ItemStack(Items.NETHER_STAR)), 1));
        assertFalse(ItemIngredientAllocator.matches(runtimeRecipe.inputs(),
                List.of(otherPrimary, new ItemStack(Items.NETHER_STAR)), 1));

        var profile = SpiritInfusionRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("carry_over", source)),
                List.of(primary, new ItemStack(Items.NETHER_STAR)),
                List.of(sourceResult)).orElseThrow();
        assertEquals(Set.of(0), profile.idOnlyInputSlots());
        assertEquals(Set.of(0), profile.idOnlyOutputSlots());

        PatternStackView pattern = new PatternStackView(
                List.of(new GenericStack(Objects.requireNonNull(AEItemKey.of(primary)), 1L),
                        new GenericStack(Objects.requireNonNull(AEItemKey.of(Items.NETHER_STAR)), 1L)),
                List.of(new GenericStack(Objects.requireNonNull(AEItemKey.of(sourceResult)), 1L)));
        var longProfile = SpiritInfusionRecipeAdapter.findDynamicPatternProfileLong(
                List.of(holder("carry_over", source)), pattern).orElseThrow();
        assertEquals(profile.idOnlyInputSlots(), longProfile.idOnlyInputSlots());
        assertEquals(profile.idOnlyOutputSlots(), longProfile.idOnlyOutputSlots());
    }

    private static long required(AdvancedAlloyFurnaceRecipe recipe, ItemStack stack) {
        return recipe.inputs().stream()
                .filter(input -> input.ingredient().test(stack))
                .mapToLong(input -> input.count())
                .sum();
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeHolder<T> holder(
            String path, T recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("malum", "test/" + path), recipe);
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
}
