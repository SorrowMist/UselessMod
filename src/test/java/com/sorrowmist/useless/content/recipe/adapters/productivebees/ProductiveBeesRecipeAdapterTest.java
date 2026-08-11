package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import cy.jdkdigital.productivebees.common.crafting.ingredient.BeeIngredient;
import cy.jdkdigital.productivebees.common.crafting.ingredient.ComponentIngredient;
import cy.jdkdigital.productivebees.common.recipe.AdvancedBeehiveRecipe;
import cy.jdkdigital.productivebees.common.recipe.CentrifugeRecipe;
import cy.jdkdigital.productivebees.init.ModBlocks;
import cy.jdkdigital.productivebees.init.ModEntities;
import cy.jdkdigital.productivebees.init.ModFluids;
import cy.jdkdigital.productivebees.init.ModItems;
import cy.jdkdigital.productivebees.util.BeeCreator;
import cy.jdkdigital.productivelib.common.recipe.TagOutputRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductiveBeesRecipeAdapterTest {

    @BeforeAll
    static void initializeProductiveLibOutputPreference() {
        TagOutputRecipe.modPreference.putIfAbsent("minecraft", 1);
    }

    @Test
    void convertsBeeProduceIncludingLowChanceByproducts() {
        AdvancedBeehiveRecipe source = new AdvancedBeehiveRecipe(
                () -> new BeeIngredient(ModEntities.RANCHER_BEE.get()),
                List.of(
                        output(Items.HONEYCOMB, 1, 1, 1.0f),
                        output(Items.WHEAT_SEEDS, 1, 1, 0.05f)
                )
        );
        RecipeHolder<AdvancedBeehiveRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("productivebees", "test/bee_produce"), source
        );

        AdvancedAlloyFurnaceRecipe converted = new BeeProduceRecipeAdapter()
                .convertAll(holder, null)
                .getFirst();

        assertTrue(converted.inputs().isEmpty());
        assertEquals(20, converted.inputFluids().getFirst().amount());
        assertTrue(converted.inputFluids().getFirst().ingredient()
                .test(new FluidStack(Fluids.WATER, 1)));
        assertEquals(20, findCount(converted.outputs(), new ItemStack(Items.HONEYCOMB)));
        assertEquals(1, findCount(converted.outputs(), new ItemStack(Items.WHEAT_SEEDS)));
        assertTrue(converted.mold().test(BeeCreator.getSpawnEgg(
                source.ingredient.get().getBeeType()
        )));
        assertEquals(AdapterUtils.DEFAULT_ENERGY * 20, converted.energy());
        assertEquals(AdapterUtils.DEFAULT_PROCESS_TIME * 20, converted.processTime());
    }

    @Test
    void convertsComponentCombCentrifugeRecipeAndScalesDefaultHoney() {
        ItemStack ironComb = configurableComb("productivebees:iron");
        ItemStack diamondComb = configurableComb("productivebees:diamond");
        CentrifugeRecipe source = new CentrifugeRecipe(
                ComponentIngredient.of(ironComb),
                List.of(
                        output(Items.IRON_NUGGET, 1, 1, 0.5f),
                        output(Items.HONEYCOMB, 1, 1, 1.0f)
                ),
                SizedFluidIngredient.of(new FluidStack(ModFluids.HONEY.get(), 100)),
                40
        );
        RecipeHolder<CentrifugeRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("productivebees", "test/centrifuge"), source
        );

        CentrifugeRecipeAdapter adapter = new CentrifugeRecipeAdapter();
        AdvancedAlloyFurnaceRecipe converted = adapter.convertAll(holder, null).getFirst();

        assertEquals(2, converted.inputs().getFirst().count());
        assertTrue(converted.inputs().getFirst().ingredient().test(ironComb));
        assertFalse(converted.inputs().getFirst().ingredient().test(diamondComb));
        assertEquals(1, findCount(converted.outputs(), new ItemStack(Items.IRON_NUGGET)));
        assertEquals(2, findCount(converted.outputs(), new ItemStack(Items.HONEYCOMB)));
        assertTrue(FluidStack.isSameFluid(
                converted.outputFluids().getFirst(), new FluidStack(ModFluids.HONEY.get(), 1)));
        assertEquals(200, converted.outputFluids().getFirst().getAmount());
        assertEquals(80, converted.processTime());
        assertTrue(adapter.matchesMold(new ItemStack(ModBlocks.CENTRIFUGE.get())));
        assertFalse(adapter.matchesMold(new ItemStack(ModBlocks.POWERED_CENTRIFUGE.get())));
        assertFalse(adapter.matchesMold(new ItemStack(ModBlocks.HEATED_CENTRIFUGE.get())));
    }

    @Test
    void preservesAndScalesExplicitNonHoneyFluid() {
        CentrifugeRecipe source = new CentrifugeRecipe(
                Ingredient.of(Items.HONEYCOMB),
                List.of(output(Items.CLAY_BALL, 1, 1, 0.25f)),
                SizedFluidIngredient.of(new FluidStack(Fluids.WATER, 250)),
                15
        );
        RecipeHolder<CentrifugeRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("productivebees", "test/explicit_fluid"), source
        );

        AdvancedAlloyFurnaceRecipe converted = new CentrifugeRecipeAdapter()
                .convertAll(holder, null)
                .getFirst();

        assertEquals(4, converted.inputs().getFirst().count());
        assertEquals(1, findCount(converted.outputs(), new ItemStack(Items.CLAY_BALL)));
        assertEquals(1, converted.outputFluids().size());
        assertTrue(FluidStack.isSameFluid(converted.outputFluids().getFirst(), new FluidStack(Fluids.WATER, 1)));
        assertEquals(1_000, converted.outputFluids().getFirst().getAmount());
        assertEquals(60, converted.processTime());
    }

    private static TagOutputRecipe.ChancedOutput output(
            net.minecraft.world.level.ItemLike item, int min, int max, float chance) {
        return new TagOutputRecipe.ChancedOutput(Ingredient.of(item), min, max, chance);
    }

    private static ItemStack configurableComb(String beeType) {
        ItemStack stack = new ItemStack(ModItems.CONFIGURABLE_HONEYCOMB.get());
        BeeCreator.setType(ResourceLocation.parse(beeType), stack);
        return stack;
    }

    private static int findCount(List<ItemStack> outputs, ItemStack expected) {
        return outputs.stream()
                .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
