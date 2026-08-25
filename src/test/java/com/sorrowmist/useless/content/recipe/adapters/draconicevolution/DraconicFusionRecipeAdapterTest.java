package com.sorrowmist.useless.content.recipe.adapters.draconicevolution;

import appeng.api.stacks.GenericStack;
import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.crafting.FusionRecipe;
import com.brandon3055.draconicevolution.api.crafting.IFusionInventory;
import com.brandon3055.draconicevolution.api.crafting.IFusionRecipe;
import com.brandon3055.draconicevolution.api.crafting.StackIngredient;
import com.brandon3055.draconicevolution.init.DEContent;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternStackView;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraconicFusionRecipeAdapterTest {

    @Test
    void convertsLongEnergyTieredMoldAndReturnedIngredient() {
        FusionRecipe source = new FusionRecipe(
                new ItemStack(Items.DIAMOND),
                StackIngredient.of(4, Items.DIRT),
                5_000_000_000L,
                TechLevel.DRACONIC,
                List.of(
                        new FusionRecipe.FusionIngredient(Ingredient.of(Items.IRON_INGOT), true),
                        new FusionRecipe.FusionIngredient(Ingredient.of(Items.STICK), false)
                )
        );
        RecipeHolder<IFusionRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("kubejs", "long_fusion"), source);

        AdvancedAlloyFurnaceRecipe converted = new DraconicFusionRecipeAdapter()
                .convertAll(holder, null)
                .getFirst();

        assertEquals(5_000_000_000L, converted.energy());
        assertEquals(List.of(4L, 1L, 1L), converted.inputs().stream().map(input -> input.count()).toList());
        assertEquals(1, countOutput(converted, new ItemStack(Items.DIAMOND)));
        assertEquals(1, countOutput(converted, new ItemStack(Items.STICK)));

        List<ItemStack> completeInputs = List.of(
                new ItemStack(Items.DIRT, 4),
                new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.STICK));
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        converted.inputs().forEach(input ->
                AdapterUtils.mergeIngredient(requirements, input.ingredient(), input.count()));
        assertTrue(converted.inputs().getFirst().ingredient().test(new ItemStack(Items.DIRT)));
        assertTrue(AdapterUtils.matchesRequired(AdapterUtils.mergeInputs(completeInputs), requirements));
        assertTrue(ItemIngredientAllocator.matches(converted.inputs(), completeInputs, 1));
        assertFalse(ItemIngredientAllocator.matches(converted.inputs(), List.of(
                new ItemStack(Items.DIRT, 3),
                new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.STICK)), 1));

        assertFalse(converted.mold().test(new ItemStack(DEContent.BASIC_CRAFTING_INJECTOR.get())));
        assertFalse(converted.mold().test(new ItemStack(DEContent.WYVERN_CRAFTING_INJECTOR.get())));
        assertTrue(converted.mold().test(new ItemStack(DEContent.AWAKENED_CRAFTING_INJECTOR.get())));
        assertTrue(converted.mold().test(new ItemStack(DEContent.CHAOTIC_CRAFTING_INJECTOR.get())));
    }

    @Test
    void matchesAwakenedDraconiumBlockRequirements() {
        FusionRecipe source = new FusionRecipe(
                new ItemStack(DEContent.ITEM_AWAKENED_DRACONIUM_BLOCK.get(), 4),
                StackIngredient.of(4, DEContent.ITEM_DRACONIUM_BLOCK.get()),
                50_000_000L,
                TechLevel.WYVERN,
                List.of(
                        new FusionRecipe.FusionIngredient(Ingredient.of(DEContent.CORE_DRACONIUM.get()), true),
                        new FusionRecipe.FusionIngredient(Ingredient.of(DEContent.CORE_DRACONIUM.get()), true),
                        new FusionRecipe.FusionIngredient(Ingredient.of(DEContent.CORE_DRACONIUM.get()), true),
                        new FusionRecipe.FusionIngredient(Ingredient.of(DEContent.DRAGON_HEART.get()), true),
                        new FusionRecipe.FusionIngredient(Ingredient.of(DEContent.CORE_DRACONIUM.get()), true),
                        new FusionRecipe.FusionIngredient(Ingredient.of(DEContent.CORE_DRACONIUM.get()), true),
                        new FusionRecipe.FusionIngredient(Ingredient.of(DEContent.CORE_DRACONIUM.get()), true)
                )
        );
        RecipeHolder<IFusionRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("draconicevolution", "awakened_draconium_block"), source);
        AdvancedAlloyFurnaceRecipe converted = new DraconicFusionRecipeAdapter()
                .convertAll(holder, null)
                .getFirst();

        List<ItemStack> inputs = List.of(
                new ItemStack(DEContent.ITEM_DRACONIUM_BLOCK.get(), 4),
                new ItemStack(DEContent.CORE_DRACONIUM.get(), 6),
                new ItemStack(DEContent.DRAGON_HEART.get()));
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        converted.inputs().forEach(input ->
                AdapterUtils.mergeIngredient(requirements, input.ingredient(), input.count()));

        assertEquals(List.of(4L, 6L, 1L),
                converted.inputs().stream().map(input -> input.count()).toList());
        assertEquals(4, countOutput(converted,
                new ItemStack(DEContent.ITEM_AWAKENED_DRACONIUM_BLOCK.get())));
        assertTrue(AdapterUtils.matchesRequired(AdapterUtils.mergeInputs(inputs), requirements));
        assertTrue(ItemIngredientAllocator.matches(converted.inputs(), inputs, 1));
        assertFalse(ItemIngredientAllocator.matches(converted.inputs(), List.of(
                new ItemStack(DEContent.ITEM_DRACONIUM_BLOCK.get(), 3),
                new ItemStack(DEContent.CORE_DRACONIUM.get(), 6),
                new ItemStack(DEContent.DRAGON_HEART.get())), 1));
        assertFalse(converted.mold().test(new ItemStack(DEContent.BASIC_CRAFTING_INJECTOR.get())));
        assertTrue(converted.mold().test(new ItemStack(DEContent.WYVERN_CRAFTING_INJECTOR.get())));
    }

    @Test
    void assemblesComponentSpecificEquipmentOutputsFromActualCatalysts() {
        FusionRecipe source = new FusionRecipe(
                new ItemStack(DEContent.SWORD_WYVERN.get()),
                Ingredient.of(Items.DIAMOND_SWORD),
                8_000_000L,
                TechLevel.WYVERN,
                List.of(new FusionRecipe.FusionIngredient(Ingredient.of(Items.DIAMOND), true))
        ) {
            @Override
            public ItemStack assemble(IFusionInventory inventory, HolderLookup.Provider provider) {
                ItemStack result = super.assemble(inventory, provider);
                Component catalystName = inventory.getCatalystStack().get(DataComponents.CUSTOM_NAME);
                if (catalystName != null) {
                    result.set(DataComponents.CUSTOM_NAME, catalystName);
                }
                return result;
            }
        };
        RecipeHolder<IFusionRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("kubejs", "dynamic_sword"), source);
        DraconicFusionRecipeAdapter adapter = new DraconicFusionRecipeAdapter();

        ItemStack alphaSword = namedSword("alpha");
        ItemStack betaSword = namedSword("beta");
        List<AdvancedAlloyFurnaceRecipe> converted = adapter.convertAll(
                holder, null, List.of(alphaSword, betaSword, new ItemStack(Items.DIAMOND, 2)));

        assertEquals(2, converted.size());
        AdvancedAlloyFurnaceRecipe alphaRecipe = recipeNamed(converted, "alpha");
        AdvancedAlloyFurnaceRecipe betaRecipe = recipeNamed(converted, "beta");

        assertTrue(ItemIngredientAllocator.matches(
                alphaRecipe.inputs(), List.of(alphaSword.copy(), new ItemStack(Items.DIAMOND)), 1));
        assertFalse(ItemIngredientAllocator.matches(
                alphaRecipe.inputs(), List.of(betaSword.copy(), new ItemStack(Items.DIAMOND)), 1));

        GenericStack alphaOutput = Objects.requireNonNull(
                GenericStack.fromItemStack(alphaRecipe.outputs().getFirst()));
        GenericStack betaOutput = Objects.requireNonNull(
                GenericStack.fromItemStack(betaRecipe.outputs().getFirst()));
        assertTrue(AlloyFurnaceRecipeManager.matchesExpectedOutputs(alphaRecipe, List.of(alphaOutput)));
        assertFalse(AlloyFurnaceRecipeManager.matchesExpectedOutputs(alphaRecipe, List.of(betaOutput)));

        AdvancedAlloyFurnaceRecipe staticRecipe = adapter.convertAll(holder, null).getFirst();
        assertFalse(staticRecipe.outputs().getFirst().has(DataComponents.CUSTOM_NAME));
        assertTrue(adapter.convertAll(holder, null, List.of(new ItemStack(Items.DIAMOND))).isEmpty());
    }

    @Test
    void exposesCanonicalChildOutputsForDraconicStaffToolInputs() {
        FusionRecipe staffRecipe = new FusionRecipe(
                new ItemStack(DEContent.STAFF_DRACONIC.get()),
                Ingredient.of(Items.NETHER_STAR),
                256_000_000L,
                TechLevel.DRACONIC,
                List.of(
                        consumed(DEContent.PICKAXE_DRACONIC.get()),
                        consumed(DEContent.SWORD_DRACONIC.get()),
                        consumed(DEContent.SHOVEL_DRACONIC.get()),
                        consumed(Items.IRON_INGOT)
                )
        );
        List<RecipeHolder<IFusionRecipe>> recipes = List.of(
                fusionHolder("staff", staffRecipe),
                dynamicToolRecipe("pickaxe", new ItemStack(DEContent.PICKAXE_DRACONIC.get())),
                dynamicToolRecipe("sword", new ItemStack(DEContent.SWORD_DRACONIC.get())),
                dynamicToolRecipe("shovel", new ItemStack(DEContent.SHOVEL_DRACONIC.get()))
        );

        ItemStack namedPickaxe = named(new ItemStack(DEContent.PICKAXE_DRACONIC.get()), "mining");
        ItemStack namedSword = named(new ItemStack(DEContent.SWORD_DRACONIC.get()), "combat");
        ItemStack namedShovel = named(new ItemStack(DEContent.SHOVEL_DRACONIC.get()), "digging");
        ItemStack displayedStaff = named(new ItemStack(DEContent.STAFF_DRACONIC.get()), "jei-display");
        var profile = DraconicFusionRecipeAdapter.findDynamicPatternProfile(
                recipes,
                null,
                List.of(
                        new ItemStack(Items.NETHER_STAR),
                        namedPickaxe,
                        namedSword,
                        namedShovel,
                        new ItemStack(Items.IRON_INGOT)
                ),
                List.of(displayedStaff)
        ).orElseThrow();

        assertEquals(Set.of(0, 1, 2, 3), profile.idOnlyInputSlots());
        assertEquals(Set.of(0), profile.idOnlyOutputSlots());
        assertEquals(Set.of(1, 2, 3), profile.canonicalInputs().keySet());
        assertTrue(ItemStack.isSameItemSameComponents(
                new ItemStack(DEContent.PICKAXE_DRACONIC.get()), profile.canonicalInputs().get(1)));
        assertTrue(ItemStack.isSameItemSameComponents(
                new ItemStack(DEContent.SWORD_DRACONIC.get()), profile.canonicalInputs().get(2)));
        assertTrue(ItemStack.isSameItemSameComponents(
                new ItemStack(DEContent.SHOVEL_DRACONIC.get()), profile.canonicalInputs().get(3)));
        assertFalse(ItemStack.isSameItemSameComponents(namedPickaxe, profile.canonicalInputs().get(1)));
        assertFalse(profile.idOnlyInputSlots().contains(4));
    }

    @Test
    void keepsLongPatternAmountsWhenMatchingFusionRequirements() {
        FusionRecipe staffRecipe = new FusionRecipe(
                new ItemStack(DEContent.STAFF_DRACONIC.get()),
                Ingredient.of(Items.NETHER_STAR),
                256_000_000L,
                TechLevel.DRACONIC,
                List.of(consumed(DEContent.PICKAXE_DRACONIC.get()))
        );
        List<RecipeHolder<IFusionRecipe>> recipes = List.of(
                fusionHolder("staff_long", staffRecipe),
                dynamicToolRecipe("pickaxe_long", new ItemStack(DEContent.PICKAXE_DRACONIC.get()))
        );
        GenericStack star = Objects.requireNonNull(
                GenericStack.fromItemStack(new ItemStack(Items.NETHER_STAR)));
        GenericStack pickaxe = Objects.requireNonNull(
                GenericStack.fromItemStack(new ItemStack(DEContent.PICKAXE_DRACONIC.get())));
        GenericStack staff = Objects.requireNonNull(
                GenericStack.fromItemStack(new ItemStack(DEContent.STAFF_DRACONIC.get())));

        assertTrue(DraconicFusionRecipeAdapter.findDynamicPatternProfileLong(
                recipes,
                null,
                new PatternStackView(
                        List.of(new GenericStack(star.what(), 1L),
                                new GenericStack(pickaxe.what(), Integer.MAX_VALUE + 1L)),
                        List.of(new GenericStack(staff.what(), 1L)))).isEmpty());
    }

    @Test
    void keepsPatternStrictWhenDynamicInputItemAppearsInMultipleSlots() {
        FusionRecipe ambiguousParent = new FusionRecipe(
                new ItemStack(DEContent.STAFF_DRACONIC.get()),
                Ingredient.of(Items.NETHER_STAR),
                1L,
                TechLevel.DRACONIC,
                List.of(
                        consumed(DEContent.SWORD_DRACONIC.get()),
                        consumed(DEContent.SWORD_DRACONIC.get())
                )
        );
        List<RecipeHolder<IFusionRecipe>> recipes = List.of(
                fusionHolder("ambiguous_parent", ambiguousParent),
                dynamicToolRecipe("sword", new ItemStack(DEContent.SWORD_DRACONIC.get()))
        );

        assertTrue(DraconicFusionRecipeAdapter.findDynamicPatternProfile(
                recipes,
                null,
                List.of(
                        new ItemStack(Items.NETHER_STAR),
                        named(new ItemStack(DEContent.SWORD_DRACONIC.get()), "first"),
                        named(new ItemStack(DEContent.SWORD_DRACONIC.get()), "second")
                ),
                List.of(new ItemStack(DEContent.STAFF_DRACONIC.get()))
        ).isEmpty());
    }

    @Test
    void keepsComponentSensitiveFusionInputsStrict() {
        ItemStack requiredSword = named(new ItemStack(DEContent.SWORD_DRACONIC.get()), "required");
        FusionRecipe componentSensitiveParent = new FusionRecipe(
                new ItemStack(DEContent.STAFF_DRACONIC.get()),
                Ingredient.of(Items.NETHER_STAR),
                1L,
                TechLevel.DRACONIC,
                List.of(new FusionRecipe.FusionIngredient(
                        DataComponentIngredient.of(true, requiredSword.copy()), true))
        );
        List<RecipeHolder<IFusionRecipe>> recipes = List.of(
                fusionHolder("component_sensitive_parent", componentSensitiveParent),
                dynamicToolRecipe("sword", new ItemStack(DEContent.SWORD_DRACONIC.get()))
        );

        assertTrue(DraconicFusionRecipeAdapter.findDynamicPatternProfile(
                recipes,
                null,
                List.of(new ItemStack(Items.NETHER_STAR), requiredSword),
                List.of(new ItemStack(DEContent.STAFF_DRACONIC.get()))
        ).isEmpty());
    }

    private static FusionRecipe.FusionIngredient consumed(net.minecraft.world.level.ItemLike item) {
        return new FusionRecipe.FusionIngredient(Ingredient.of(item), true);
    }

    private static RecipeHolder<IFusionRecipe> dynamicToolRecipe(String id, ItemStack result) {
        return fusionHolder(id, new FusionRecipe(
                result,
                Ingredient.of(Items.STICK),
                1L,
                TechLevel.DRACONIC,
                List.of(consumed(Items.DIAMOND))
        ));
    }

    private static RecipeHolder<IFusionRecipe> fusionHolder(String id, IFusionRecipe recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("kubejs", id), recipe);
    }

    private static ItemStack namedSword(String name) {
        return named(new ItemStack(Items.DIAMOND_SWORD), name);
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static AdvancedAlloyFurnaceRecipe recipeNamed(
            List<AdvancedAlloyFurnaceRecipe> recipes, String name) {
        return recipes.stream()
                .filter(recipe -> {
                    Component outputName = recipe.outputs().getFirst().get(DataComponents.CUSTOM_NAME);
                    return outputName != null && name.equals(outputName.getString());
                })
                .findFirst()
                .orElseThrow();
    }

    private static int countOutput(AdvancedAlloyFurnaceRecipe recipe, ItemStack expected) {
        return recipe.outputs().stream()
                .filter(output -> ItemStack.isSameItemSameComponents(output, expected))
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
