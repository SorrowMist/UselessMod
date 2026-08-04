package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.content.machines.soul_binder.SoulBindingRecipe;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPatternDetails;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoulBindingRecipeAdapterTest {
    @Test
    void entitySelectorCreatesAnExactVialAndBoundResult() {
        SoulBindingRecipe source = recipe(
                new ItemStack(EIOBlocks.POWERED_SPAWNER_ITEM.get()),
                Optional.of(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.COW)),
                Optional.empty(), Optional.empty(), false);

        List<AdvancedAlloyFurnaceRecipe> converted = new SoulBindingRecipeAdapter()
                .convertAll(holder("entity", source), null);

        assertEquals(1, converted.size());
        AdvancedAlloyFurnaceRecipe result = converted.getFirst();
        ItemStack cowVial = vial(EntityType.COW);
        ItemStack zombieVial = vial(EntityType.ZOMBIE);
        assertTrue(result.inputs().stream().anyMatch(input -> input.ingredient().test(cowVial)));
        assertFalse(result.inputs().stream().anyMatch(input -> input.ingredient().test(zombieVial)));

        Soul boundSoul = SoulBoundUtils.getBoundSoul(result.outputs().getFirst());
        assertNotNull(boundSoul);
        assertEquals(EntityType.COW, boundSoul.entityType());
        assertTrue(result.outputs().stream().anyMatch(stack -> stack.is(EIOItems.SOUL_VIAL.asItem())));
    }

    @Test
    void mobCategoryDoesNotExposeVialsFromOtherCategories() {
        SoulBindingRecipe source = recipe(
                new ItemStack(EIOItems.ANIMAL_TOKEN.get()), Optional.empty(),
                Optional.of(MobCategory.CREATURE), Optional.empty(), false);

        List<AdvancedAlloyFurnaceRecipe> converted = new SoulBindingRecipeAdapter()
                .convertAll(holder("category", source), null);
        ItemStack cowVial = vial(EntityType.COW);
        ItemStack zombieVial = vial(EntityType.ZOMBIE);

        AdvancedAlloyFurnaceRecipe cowRecipe = converted.stream()
                .filter(recipe -> recipe.inputs().stream()
                        .anyMatch(input -> input.ingredient().test(cowVial)))
                .findFirst()
                .orElseThrow();
        assertTrue(cowRecipe.inputs().stream().anyMatch(input -> input.ingredient().test(cowVial)));
        assertFalse(converted.stream().anyMatch(recipe -> recipe.inputs().stream()
                .anyMatch(input -> input.ingredient().test(zombieVial))));
    }

    @Test
    void nonBindableOutputUsesOneMergedFilledVialEntry() {
        SoulBindingRecipe source = recipe(
                new ItemStack(Items.DIAMOND), Optional.empty(),
                Optional.empty(), Optional.empty(), false);

        List<AdvancedAlloyFurnaceRecipe> converted = new SoulBindingRecipeAdapter()
                .convertAll(holder("static", source), null);

        assertEquals(1, converted.size());
        AdvancedAlloyFurnaceRecipe result = converted.getFirst();
        assertTrue(result.outputs().stream().anyMatch(stack -> stack.is(Items.DIAMOND)));
        assertTrue(result.inputs().stream().anyMatch(input ->
                input.ingredient().test(vial(EntityType.COW))));
        ItemStack emptyVial = new ItemStack(EIOItems.SOUL_VIAL.asItem());
        assertFalse(result.inputs().stream().anyMatch(input ->
                input.ingredient().test(emptyVial)));
    }

    @Test
    void dynamicVialMatcherKeepsSelectorAndAllowsCapturedVialComponents() {
        SoulBindingRecipe source = recipe(
                new ItemStack(EIOBlocks.POWERED_SPAWNER_ITEM.get()),
                Optional.of(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.COW)),
                Optional.empty(), Optional.empty(), true);
        RecipeHolder<SoulBindingRecipe> holder = holder("dynamic_entity", source);
        ItemStack cowVial = vial(EntityType.COW);
        cowVial.set(com.enderio.enderio.init.EIODataComponents.ENTITY_MAX_HEALTH, 20.0F);
        ItemStack displayOutput = source.output().copy();
        assertTrue(SoulBoundUtils.tryBindSoul(displayOutput, Soul.of(EntityType.COW)));

        var profile = SoulBindingRecipeAdapter.findDynamicPatternProfile(
                List.of(holder), null,
                List.of(cowVial, new ItemStack(Items.IRON_INGOT)),
                List.of(displayOutput, new ItemStack(EIOItems.SOUL_VIAL.asItem())))
                .orElseThrow();

        assertEquals(java.util.Set.of(0, 1), profile.idOnlyInputSlots());
        assertEquals(java.util.Set.of(0), profile.idOnlyOutputSlots());
        var matcher = profile.inputMatchers().get(0);
        assertTrue(matcher.test(Objects.requireNonNull(AEItemKey.of(cowVial))));
        assertFalse(matcher.test(Objects.requireNonNull(AEItemKey.of(vial(EntityType.ZOMBIE)))));
        assertFalse(matcher.test(Objects.requireNonNull(
                AEItemKey.of(new ItemStack(EIOItems.SOUL_VIAL.asItem())))));
    }

    @Test
    void enderIoCraftCopiesTargetComponentsAndReturnsEmptyVial() {
        SoulBindingRecipe source = recipe(
                new ItemStack(EIOBlocks.POWERED_SPAWNER_ITEM.get()), Optional.empty(),
                Optional.empty(), Optional.empty(), true);
        ItemStack target = new ItemStack(EIOBlocks.POWERED_SPAWNER_ITEM.get());
        target.set(DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("kept component"));
        List<com.enderio.core.common.recipes.OutputStack> crafted = source.craft(
                new SoulBindingRecipe.Input(vial(EntityType.COW), target, net.neoforged.neoforge.fluids.FluidStack.EMPTY),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        ItemStack output = crafted.getFirst().getItem();
        assertEquals("kept component", output.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(EntityType.COW, SoulBoundUtils.getBoundSoul(output).entityType());
        assertTrue(crafted.stream().anyMatch(stack ->
                stack.isItem() && stack.getItem().is(EIOItems.SOUL_VIAL.asItem())
                        && SoulBoundUtils.getBoundSoul(stack.getItem()).isEmpty()));
    }

    private static SoulBindingRecipe recipe(
            ItemStack output,
            Optional<ResourceLocation> entityType,
            Optional<MobCategory> mobCategory,
            Optional<String> soulData,
            boolean copyInputComponents) {
        return new SoulBindingRecipe(
                output,
                Ingredient.of(Items.IRON_INGOT),
                1,
                0,
                entityType,
                mobCategory,
                soulData,
                copyInputComponents);
    }

    private static RecipeHolder<SoulBindingRecipe> holder(String path, SoulBindingRecipe recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("enderio_test", path), recipe);
    }

    private static ItemStack vial(EntityType<?> entityType) {
        ItemStack vial = new ItemStack(EIOItems.SOUL_VIAL.asItem());
        vial.set(EIODataComponents.SOUL, Soul.of(entityType));
        return vial;
    }
}
