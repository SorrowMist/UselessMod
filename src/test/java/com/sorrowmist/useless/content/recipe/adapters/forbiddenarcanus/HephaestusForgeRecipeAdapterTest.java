package com.sorrowmist.useless.content.recipe.adapters.forbiddenarcanus;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssencesDefinition;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualInput;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualRequirements;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.CreateItemResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.TransmuteInputResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.UpgradeTierResult;
import com.stal111.forbidden_arcanus.core.init.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HephaestusForgeRecipeAdapterTest {

    @Test
    void convertsCreateItemRitualAndUsesTierOneForgeAsMold() {
        Ritual ritual = ritual(
                Ingredient.of(Items.DIAMOND),
                new CreateItemResult(new ItemStack(Items.NETHER_STAR, 2)),
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 2)),
                137);

        HephaestusForgeRecipeAdapter adapter = new HephaestusForgeRecipeAdapter();
        AdvancedAlloyFurnaceRecipe converted = adapter.convertRitual(id("create_item"), ritual).getFirst();

        assertEquals(2L, required(converted, Items.IRON_INGOT));
        assertEquals(1L, required(converted, Items.DIAMOND));
        assertEquals(Items.NETHER_STAR, converted.outputs().getFirst().getItem());
        assertEquals(2, converted.outputs().getFirst().getCount());
        assertEquals(137, converted.processTime());
        assertEquals(AdapterUtils.DEFAULT_ENERGY, converted.energy());
        assertTrue(adapter.matchesMold(new ItemStack(ModBlocks.HEPHAESTUS_FORGE_TIER_1.get())));
        assertFalse(adapter.matchesMold(new ItemStack(ModBlocks.HEPHAESTUS_FORGE_TIER_2.get())));
        assertTrue(converted.mold().test(new ItemStack(ModBlocks.HEPHAESTUS_FORGE_TIER_1.get())));
    }

    @Test
    void expandsTransmuteRitualForEveryConcreteMainRepresentation() {
        Ingredient main = Ingredient.of(Items.DIAMOND, Items.EMERALD);
        Ritual ritual = ritual(
                main,
                new TransmuteInputResult(BuiltInRegistries.ITEM.wrapAsHolder(Items.NETHERITE_INGOT)),
                List.of(new RitualInput(Ingredient.of(Items.GOLD_INGOT), 3)),
                80);

        List<AdvancedAlloyFurnaceRecipe> converted = HephaestusForgeRecipeAdapter
                .convertRitual(id("transmute"), ritual);

        assertEquals(2, converted.size());
        assertTrue(converted.stream().anyMatch(recipe -> recipe.inputs().stream()
                .anyMatch(input -> input.ingredient().test(new ItemStack(Items.DIAMOND)))));
        assertTrue(converted.stream().anyMatch(recipe -> recipe.inputs().stream()
                .anyMatch(input -> input.ingredient().test(new ItemStack(Items.EMERALD)))));
        assertTrue(converted.stream().allMatch(recipe -> recipe.outputs().getFirst().is(Items.NETHERITE_INGOT)));
        assertEquals(2, converted.stream().map(AdvancedAlloyFurnaceRecipe::id).distinct().count());
    }

    @Test
    void doesNotConvertUpgradeOrInvalidRituals() {
        Ritual upgrade = ritual(
                Ingredient.of(Items.DIAMOND),
                new UpgradeTierResult(2),
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 1)),
                100);
        Ritual emptyOutput = ritual(
                Ingredient.of(Items.DIAMOND),
                new CreateItemResult(ItemStack.EMPTY),
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 1)),
                100);
        Ritual tooManyInputs = ritual(
                Ingredient.of(Items.DIAMOND),
                new CreateItemResult(new ItemStack(Items.STICK)),
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 5),
                        new RitualInput(Ingredient.of(Items.GOLD_INGOT), 4)),
                100);
        Ritual noPedestalInputs = ritual(
                Ingredient.of(Items.DIAMOND),
                new CreateItemResult(new ItemStack(Items.STICK)),
                List.of(),
                100);

        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id("upgrade"), upgrade).isEmpty());
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id("empty"), emptyOutput).isEmpty());
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id("too_many"), tooManyInputs).isEmpty());
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id("no_inputs"), noPedestalInputs).isEmpty());
    }

    @Test
    void keepsForgeRequirementsOutOfInputs() {
        Ritual ritual = ritual(
                Ingredient.of(Items.DIAMOND),
                new CreateItemResult(new ItemStack(Items.STICK)),
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 1)),
                new RitualRequirements(EssencesDefinition.of(3, 5, 7, 11), null, null),
                60);

        AdvancedAlloyFurnaceRecipe converted = HephaestusForgeRecipeAdapter
                .convertRitual(id("requirements"), ritual).getFirst();
        assertEquals(2, converted.inputs().size());
        assertEquals(1L, converted.inputs().getFirst().count());
        assertEquals(1L, converted.inputs().getLast().count());
        assertEquals(52_000L, converted.energy());
        assertEquals(60, converted.processTime());
    }

    @Test
    void zeroOrMissingEssenceRequirementsUseDefaultEnergy() {
        Ritual zero = ritual(
                Ingredient.of(Items.DIAMOND),
                new CreateItemResult(new ItemStack(Items.STICK)),
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 1)),
                RitualRequirements.NONE,
                60);
        Ritual missing = new Ritual(
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 1)),
                Ingredient.of(Items.DIAMOND),
                new CreateItemResult(new ItemStack(Items.STICK)),
                null,
                null,
                60);

        assertEquals(AdapterUtils.DEFAULT_ENERGY,
                HephaestusForgeRecipeAdapter.convertRitual(id("zero"), zero).getFirst().energy());
        assertEquals(AdapterUtils.DEFAULT_ENERGY,
                HephaestusForgeRecipeAdapter.convertRitual(id("missing"), missing).getFirst().energy());
    }

    @Test
    void essenceSumUsesLongBeforeMultiplication() {
        int max = Integer.MAX_VALUE;
        Ritual ritual = ritual(
                Ingredient.of(Items.DIAMOND),
                new CreateItemResult(new ItemStack(Items.STICK)),
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 1)),
                new RitualRequirements(EssencesDefinition.of(max, max, max, max), null, null),
                60);

        long expected = (long) AdapterUtils.DEFAULT_ENERGY * max * 4L;
        long energy = HephaestusForgeRecipeAdapter.convertRitual(id("large"), ritual)
                .getFirst().energy();
        assertEquals(expected, energy);
        assertTrue(energy > 0L);
    }

    private static Ritual ritual(Ingredient main, com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.RitualResult result,
                                 List<RitualInput> inputs, int duration) {
        return ritual(main, result, inputs, RitualRequirements.NONE, duration);
    }

    private static Ritual ritual(Ingredient main,
                                 com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.RitualResult result,
                                 List<RitualInput> inputs, RitualRequirements requirements, int duration) {
        return new Ritual(inputs, main, result, requirements, null, duration);
    }

    private static long required(AdvancedAlloyFurnaceRecipe recipe, net.minecraft.world.item.Item item) {
        return recipe.inputs().stream()
                .filter(input -> input.ingredient().test(new ItemStack(item)))
                .mapToLong(input -> input.count())
                .sum();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", "test/" + path);
    }
}
