package com.sorrowmist.useless.compat.neoecoae;

import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.google.gson.JsonParser;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NeoEcoForgeWorkstationBridgeTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static NeoEcoWorkstationRecipeAdapter adapter() {
        var adapter = spy(new NeoEcoWorkstationRecipeAdapter());
        doReturn(new ItemStack(Items.ANVIL)).when(adapter).getMoldItem();
        return adapter;
    }

    private static cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient noFluid() {
        return new cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient(
                cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient.empty(), 1);
    }

    @Test void nativeCountedItemsAndBothNbtOutputsAreCopiedWithEnergyTimeAndMold() {
        ItemStack input = new ItemStack(Items.IRON_INGOT);
        input.getOrCreateTag().putString("grade", "pure");
        Ingredient exact = StrictNBTIngredient.of(input);
        ItemStack output = new ItemStack(Items.DIAMOND, 3);
        output.getOrCreateTag().putString("product", "native");
        FluidStack fluid = new FluidStack(Fluids.WATER, 500);
        fluid.getOrCreateTag().putInt("temperature", 330);
        var source = new IntegratedWorkingStationRecipe(List.of(
                new SizedIngredient(exact, 3), new SizedIngredient(exact, 2)), noFluid(),
                output, fluid, 4096, new ResourceLocation("test", "workstation"), 73);
        var adapter = adapter();
        var converted = adapter.convertAll(new RecipeHolder<>(source.id(), source), null).get(0);
        assertEquals("neoecoae", adapter.sourceId());
        assertEquals(4096, converted.energy());
        assertEquals(73, converted.processTime());
        assertEquals(1, converted.inputs().size());
        assertEquals(5, converted.inputs().get(0).count());
        assertTrue(converted.molds().get(0).test(new ItemStack(Items.ANVIL)));
        assertTrue(ItemIngredientAllocator.matches(converted.inputs(), List.of(input.copyWithCount(5)), 1));
        assertFalse(ItemIngredientAllocator.matches(converted.inputs(), List.of(new ItemStack(Items.IRON_INGOT, 5)), 1));
        source.itemOutput().getOrCreateTag().putString("product", "mutated");
        source.fluidOutput().getOrCreateTag().putInt("temperature", 1);
        assertEquals("native", converted.outputs().get(0).getTag().getString("product"));
        assertEquals(330, converted.outputFluids().get(0).getTag().getInt("temperature"));
        assertEquals(3, converted.outputs().get(0).getCount());
        assertEquals(500, converted.outputFluids().get(0).getAmount());
        assertFalse(adapter.matchesMold(new ItemStack(Items.STICK)));
        assertFalse(adapter.matchesMold(null));
    }

    @Test void fluidOnlyRecipeKeepsItsQuantityAndLiveTagRatherThanEnumeratingMembers() {
        var tag = TagKey.create(Registries.FLUID, new ResourceLocation("test", "coolants"));
        var sourceFluid = cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient.of(tag, 1200);
        var source = new IntegratedWorkingStationRecipe(List.of(), sourceFluid, ItemStack.EMPTY,
                new FluidStack(Fluids.WATER, 250), 0);
        var converted = adapter().convertAll(new RecipeHolder<>(source.id(), source), null).get(0);
        assertTrue(converted.inputs().isEmpty());
        assertEquals(1200, converted.inputFluids().get(0).amount());
        var predicate = converted.inputFluids().get(0).ingredient();
        assertFalse(predicate.isEmpty(), "An unbound tag must remain a live predicate");
        assertEquals(List.of(tag), predicate.tagOnlyTags().orElseThrow());
        assertEquals(sourceFluid.ingredient().toJson(), predicate.toJson());
    }

    @Test void fluidPredicatesRetainStrictPartialAlternativeAndEmptyNbtSemantics() {
        for (String json : List.of(
                "{\"fluid\":\"minecraft:water\",\"nbt\":\"{grade:1}\",\"strict\":true}",
                "{\"fluid\":\"minecraft:water\",\"nbt\":\"{grade:1}\",\"strict\":false}",
                "{\"fluid\":\"minecraft:water\",\"nbt\":\"{}\",\"strict\":true}",
                "{\"fluid\":\"minecraft:water\",\"nbt\":\"{}\",\"strict\":false}",
                "[{\"fluid\":\"minecraft:water\"},{\"fluid\":\"minecraft:lava\"}]")) {
            var element = JsonParser.parseString(json);
            var nativePredicate = cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient.fromJson(element);
            var converted = NeoEcoWorkstationRecipeAdapter.convertFluidPredicate(nativePredicate.toJson());
            assertNotNull(converted);
            FluidStack noNbt = new FluidStack(Fluids.WATER, 800);
            FluidStack emptyNbt = noNbt.copy();
            emptyNbt.setTag(new net.minecraft.nbt.CompoundTag());
            FluidStack exact = noNbt.copy();
            exact.getOrCreateTag().putInt("grade", 1);
            FluidStack extra = exact.copy();
            extra.getOrCreateTag().putInt("extra", 2);
            FluidStack wrong = noNbt.copy();
            wrong.getOrCreateTag().putInt("grade", 2);
            for (FluidStack stack : List.of(noNbt, emptyNbt, exact, extra, wrong, new FluidStack(Fluids.LAVA, 800))) {
                assertEquals(nativePredicate.test(stack), converted.test(stack), json + " vs " + stack.getTag());
            }
        }
    }

    @Test void fluidAmountIsAllocatedWithoutDroppingNativeNbt() {
        var nativePredicate = cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient.fromJson(JsonParser.parseString(
                "{\"fluid\":\"minecraft:water\",\"nbt\":\"{grade:1}\",\"strict\":true}"));
        var source = new IntegratedWorkingStationRecipe(List.of(),
                new cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient(nativePredicate, 1500),
                new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 500);
        var converted = adapter().convertAll(new RecipeHolder<>(source.id(), source), null).get(0);
        FluidStack valid = new FluidStack(Fluids.WATER, 1);
        valid.getOrCreateTag().putInt("grade", 1);
        assertTrue(FluidIngredientAllocator.matchesLong(converted.inputFluids(), Map.of(valid, 1500L), 1L));
        assertFalse(FluidIngredientAllocator.matchesLong(converted.inputFluids(), Map.of(valid, 1499L), 1L));
        assertFalse(FluidIngredientAllocator.matchesLong(converted.inputFluids(), Map.of(new FluidStack(Fluids.WATER, 1), 1500L), 1L));
    }

    @Test void unsupportedTagAndNbtConjunctionAndLargeMachineShapeAreRefusedNotWeakened() {
        var predicate = cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient.fromJson(JsonParser.parseString(
                "{\"tag\":\"test:coolants\",\"nbt\":\"{grade:1}\",\"strict\":true}"));
        assertNull(NeoEcoWorkstationRecipeAdapter.convertFluidPredicate(predicate.toJson()));
        var unsupported = new IntegratedWorkingStationRecipe(List.of(),
                new cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient(predicate, 1000),
                new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 1);
        assertTrue(adapter().convertAll(new RecipeHolder<>(unsupported.id(), unsupported), null).isEmpty());
        var tooMany = new IntegratedWorkingStationRecipe(java.util.Collections.nCopies(10,
                new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 1)), noFluid(),
                new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 1);
        assertTrue(adapter().convertAll(new RecipeHolder<>(tooMany.id(), tooMany), null).isEmpty());
        var noInput = new IntegratedWorkingStationRecipe(List.of(), noFluid(), new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 1);
        assertTrue(adapter().convertAll(new RecipeHolder<>(noInput.id(), noInput), null).isEmpty());
    }
}
