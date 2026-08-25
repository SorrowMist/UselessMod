package com.sorrowmist.useless.content.recipe;

import com.mojang.datafixers.util.Pair;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.CompoundFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidIngredientAllocatorTest {
    private static final TagKey<Fluid> BROAD_TAG = TagKey.create(
            Registries.FLUID, ResourceLocation.fromNamespaceAndPath("useless_mod_test", "broad_fluids"));
    private static final TagKey<Fluid> WATER_TAG = TagKey.create(
            Registries.FLUID, ResourceLocation.fromNamespaceAndPath("useless_mod_test", "water_fluids"));
    private static Map<TagKey<Fluid>, List<Holder<Fluid>>> originalTags;

    @BeforeAll
    static void bootstrapAndBindTags() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalTags = BuiltInRegistries.FLUID.getTags().collect(Collectors.toUnmodifiableMap(
                Pair::getFirst,
                pair -> StreamSupport.stream(pair.getSecond().spliterator(), false).toList()));
        BuiltInRegistries.FLUID.bindTags(Map.of(
                BROAD_TAG, List.of(holder(Fluids.WATER), holder(Fluids.LAVA)),
                WATER_TAG, List.of(holder(Fluids.WATER))));
    }

    @AfterAll
    static void restoreFluidTags() {
        if (originalTags != null) {
            BuiltInRegistries.FLUID.bindTags(originalTags);
        }
    }

    @Test
    void maxFlowAllocatesOverlappingTagsWithoutReusingAFluid() {
        List<SizedFluidIngredient> requirements = List.of(
                SizedFluidIngredient.of(BROAD_TAG, 600),
                SizedFluidIngredient.of(WATER_TAG, 600));

        FluidIngredientAllocator.Allocation allocation = FluidIngredientAllocator.allocate(
                requirements, List.of(water(600), lava(600)), 1L);

        assertNotNull(allocation);
        assertEquals(600L, allocation.consumedFromSupply(0));
        assertEquals(600L, allocation.consumedFromSupply(1));
        assertFalse(FluidIngredientAllocator.matches(
                requirements, List.of(water(600)), 1L));
    }

    @Test
    void compoundIngredientMatchesAnyChild() {
        FluidIngredient compound = CompoundFluidIngredient.of(
                FluidIngredient.tag(WATER_TAG), FluidIngredient.of(Fluids.LAVA));
        List<SizedFluidIngredient> requirements = List.of(new SizedFluidIngredient(compound, 500));

        assertTrue(FluidIngredientAllocator.matches(requirements, List.of(lava(500)), 1L));
        assertTrue(FluidIngredientAllocator.matches(requirements, List.of(water(500)), 1L));
    }

    @Test
    void oneSupplyCannotSatisfyTwoDemands() {
        List<SizedFluidIngredient> requirements = List.of(
                SizedFluidIngredient.of(BROAD_TAG, 600),
                SizedFluidIngredient.of(WATER_TAG, 600));

        assertFalse(FluidIngredientAllocator.matches(requirements, List.of(water(600)), 1L));
    }

    @Test
    void splitsOneDemandAcrossMultipleTanks() {
        FluidTank first = new FluidTank(1_000);
        FluidTank second = new FluidTank(1_000);
        first.setFluid(water(400));
        second.setFluid(lava(600));

        FluidIngredientAllocator.Allocation allocation = FluidIngredientAllocator.allocateTanks(
                List.of(SizedFluidIngredient.of(BROAD_TAG, 1_000)),
                new FluidTank[]{first, second}, 2, 1L);

        assertNotNull(allocation);
        assertEquals(400L, allocation.consumedFromSupply(0));
        assertEquals(600L, allocation.consumedFromSupply(1));
        assertEquals(1, FluidIngredientAllocator.maxTankOperations(
                List.of(SizedFluidIngredient.of(BROAD_TAG, 1_000)),
                new FluidTank[]{first, second}, 2));
    }

    @Test
    void componentDifferencePreventsMatching() {
        FluidStack named = water(100);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("named"));
        SizedFluidIngredient requirement = new SizedFluidIngredient(
                DataComponentFluidIngredient.of(true, named), named.getAmount());

        assertFalse(FluidIngredientAllocator.matches(List.of(requirement), List.of(water(100)), 1L));
        assertTrue(FluidIngredientAllocator.matches(List.of(requirement), List.of(named.copy()), 1L));
    }

    @Test
    void parallelOperationsAndEmptyIngredientsAreValidated() {
        List<SizedFluidIngredient> requirement = List.of(SizedFluidIngredient.of(Fluids.WATER, 250));
        assertEquals(4, FluidIngredientAllocator.maxOperations(requirement, List.of(water(1_000))));
        assertTrue(FluidIngredientAllocator.matches(requirement, List.of(water(1_000)), 4L));
        assertFalse(FluidIngredientAllocator.matches(requirement, List.of(water(1_000)), 5L));

        SizedFluidIngredient empty = new SizedFluidIngredient(FluidIngredient.empty(), 1);
        assertFalse(FluidIngredientAllocator.matches(List.of(empty), List.of(water(1)), 1L));
        assertTrue(FluidIngredientAllocator.matches(List.of(empty), List.of(), 0L));
    }

    @Test
    void longRequirementsUseLongAeSuppliesWithoutCreatingLargeFluidStacks() {
        long requiredAmount = (long) Integer.MAX_VALUE + 1L;
        List<LongSizedFluidIngredient> requirements = List.of(
                new LongSizedFluidIngredient(FluidIngredient.of(Fluids.WATER), requiredAmount));

        assertTrue(FluidIngredientAllocator.matchesLong(
                requirements, Map.of(water(1), requiredAmount), 1L));
        assertFalse(FluidIngredientAllocator.matchesLong(
                requirements, Map.of(water(1), requiredAmount - 1L), 1L));
    }

    private static Holder<Fluid> holder(Fluid fluid) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        return BuiltInRegistries.FLUID.getHolderOrThrow(ResourceKey.create(Registries.FLUID, id));
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    private static FluidStack lava(int amount) {
        return new FluidStack(Fluids.LAVA, amount);
    }
}
