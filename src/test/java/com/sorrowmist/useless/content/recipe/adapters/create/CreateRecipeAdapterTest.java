package com.sorrowmist.useless.content.recipe.adapters.create;

import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.kinetics.mixer.CompactingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateRecipeAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void convertsFillingAndBasinRecipesWithFluidsAndHeatMolds() {
        FillingRecipe filling = new StandardProcessingRecipe.Builder<>(
                FillingRecipe::new, id("filling"))
                .require(Ingredient.of(Items.IRON_INGOT))
                .require(SizedFluidIngredient.of(Fluids.WATER, 250))
                .output(new ItemStack(Items.GOLD_INGOT, 2))
                .duration(40)
                .build();

        CompactingRecipe compacting = new StandardProcessingRecipe.Builder<>(
                CompactingRecipe::new, id("compacting"))
                .require(Items.IRON_INGOT)
                .require(Items.IRON_NUGGET)
                .output(Items.IRON_BLOCK)
                .requiresHeat(HeatCondition.SUPERHEATED)
                .build();

        MixingRecipe mixing = new StandardProcessingRecipe.Builder<>(MixingRecipe::new, id("mixing"))
                .require(Items.CLAY)
                .require(SizedFluidIngredient.of(Fluids.WATER, 500))
                .output(new FluidStack(Fluids.LAVA, 250))
                .build();

        AdvancedAlloyFurnaceRecipe fillingResult = converted(filling);
        AdvancedAlloyFurnaceRecipe compactingResult = converted(compacting);
        AdvancedAlloyFurnaceRecipe mixingResult = converted(mixing);

        assertEquals(40, fillingResult.processTime());
        assertEquals(250, fillingResult.inputFluids().getFirst().amount());
        assertMold(fillingResult, createItem("spout"));

        assertEquals(3, compactingResult.molds().size());
        assertMold(compactingResult, createItem("mechanical_press"));
        assertMold(compactingResult, createItem("basin"));
        assertMold(compactingResult, createItem("blaze_burner"));

        assertEquals(500, mixingResult.inputFluids().getFirst().amount());
        assertEquals(250, mixingResult.outputFluids().getFirst().getAmount());
        assertMold(mixingResult, createItem("mechanical_mixer"));
        assertMold(mixingResult, createItem("basin"));
    }

    @Test
    void scalesProbabilityBatchInputsOutputsTimeAndEnergy() {
        PressingRecipe source = new StandardProcessingRecipe.Builder<>(
                PressingRecipe::new, id("probability"))
                .require(Items.IRON_INGOT)
                .output(new ProcessingOutput(new ItemStack(Items.GOLD_INGOT), 0.5F))
                .duration(20)
                .build();

        AdvancedAlloyFurnaceRecipe result = converted(source);

        assertEquals(2L, required(result, Items.IRON_INGOT));
        assertEquals(1, count(result.outputs(), Items.GOLD_INGOT));
        assertEquals(4_000L, result.energy());
        assertEquals(40, result.processTime());
        assertMold(result, createItem("mechanical_press"));
    }

    @Test
    void convertsFanProcessingAndOnlyBlastingCookingRecipes() {
        HauntingRecipe haunting = new StandardProcessingRecipe.Builder<>(
                HauntingRecipe::new, id("haunting"))
                .require(Items.IRON_INGOT)
                .output(Items.GOLD_INGOT)
                .build();
        SplashingRecipe splashing = new StandardProcessingRecipe.Builder<>(
                SplashingRecipe::new, id("splashing"))
                .require(Items.IRON_INGOT)
                .output(Items.GOLD_INGOT)
                .build();
        BlastingRecipe blasting = new BlastingRecipe(
                "", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE),
                new ItemStack(Items.IRON_INGOT), 0, 80);
        SmokingRecipe smoking = new SmokingRecipe(
                "", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE),
                new ItemStack(Items.IRON_INGOT), 0, 80);
        SmeltingRecipe smelting = new SmeltingRecipe(
                "", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE),
                new ItemStack(Items.IRON_INGOT), 0, 200);

        assertMold(converted(haunting), createItem("encased_fan"));
        assertMold(converted(splashing), createItem("encased_fan"));
        AdvancedAlloyFurnaceRecipe blastingResult = new CreateBlastingRecipeAdapter()
                .convertAll(holder("blasting", blasting), null).getFirst();
        assertEquals(80, blastingResult.processTime());
        assertMold(blastingResult, createItem("encased_fan"));
        assertEquals(BlastingRecipe.class, new CreateBlastingRecipeAdapter().getRecipeClass());
        assertFalse(new CreateBlastingRecipeAdapter().getRecipeClass().isInstance(smoking));
        assertFalse(new CreateBlastingRecipeAdapter().getRecipeClass().isInstance(smelting));
    }

    @Test
    void convertsMechanicalCraftingAsUnorderedInputs() {
        MechanicalCraftingRecipe source = new MechanicalCraftingRecipe(
                "", CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of(
                        '#', Ingredient.of(Items.IRON_INGOT),
                        'X', Ingredient.of(Items.GOLD_INGOT)), "#X"),
                new ItemStack(Items.DIAMOND, 2), false);

        AdvancedAlloyFurnaceRecipe result = new CreateMechanicalCraftingRecipeAdapter()
                .convertAll(holder("mechanical_crafting", source), null).getFirst();

        assertEquals(1L, required(result, Items.IRON_INGOT));
        assertEquals(1L, required(result, Items.GOLD_INGOT));
        assertEquals(2, count(result.outputs(), Items.DIAMOND));
        assertMold(result, createItem("mechanical_crafter"));
    }

    @Test
    void convertsSequencedAssemblyLoopsToolsResultPoolAndThirdPartyAssembly() {
        RecipeHolder<SequencedAssemblyRecipe> holder = new SequencedAssemblyRecipeBuilder(
                id("sequenced"))
                .require(Items.IRON_INGOT)
                .transitionTo(Items.COPPER_INGOT)
                .loops(3)
                .addStep(DeployerApplicationRecipe::new,
                        builder -> builder.require(Items.GOLD_NUGGET).output(Items.COPPER_INGOT))
                .addStep((StandardProcessingRecipe.Factory<ThirdPartyAssemblyRecipe>) ThirdPartyAssemblyRecipe::new,
                        builder -> builder.require(Items.AMETHYST_SHARD)
                                .output(Items.COPPER_INGOT)
                                .duration(5))
                .addOutput(new ItemStack(Items.DIAMOND, 2), 1)
                .addOutput(new ItemStack(Items.EMERALD), 10)
                .build();

        AdvancedAlloyFurnaceRecipe result = new CreateSequencedAssemblyRecipeAdapter()
                .convertAll(holder, null).getFirst();

        assertEquals(1L, required(result, Items.IRON_INGOT));
        assertEquals(3L, required(result, Items.GOLD_NUGGET));
        assertEquals(3L, required(result, Items.AMETHYST_SHARD));
        assertEquals(75, result.processTime());
        assertEquals(12_000L, result.energy());
        assertEquals(2, count(result.outputs(), Items.DIAMOND));
        assertEquals(1, count(result.outputs(), Items.EMERALD));
        assertMold(result, createItem("deployer"));
        assertMold(result, createItem("mechanical_saw"));
        assertFalse(result.inputs().stream()
                .anyMatch(input -> input.ingredient().test(Items.COPPER_INGOT.getDefaultInstance())));
    }

    @Test
    void keepsDeployerToolAsMoldWhenCreateDoesNotConsumeIt() {
        RecipeHolder<SequencedAssemblyRecipe> holder = new SequencedAssemblyRecipeBuilder(
                id("kept_tool"))
                .require(Items.IRON_INGOT)
                .transitionTo(Items.COPPER_INGOT)
                .loops(2)
                .addStep(DeployerApplicationRecipe::new,
                        builder -> builder.require(Items.IRON_PICKAXE)
                                .toolNotConsumed()
                                .output(Items.COPPER_INGOT))
                .addOutput(Items.DIAMOND, 1)
                .build();

        AdvancedAlloyFurnaceRecipe result = new CreateSequencedAssemblyRecipeAdapter()
                .convertAll(holder, null).getFirst();

        assertEquals(1L, required(result, Items.IRON_INGOT));
        assertEquals(0L, required(result, Items.IRON_PICKAXE));
        assertMold(result, createItem("deployer"));
        assertTrue(result.molds().stream().anyMatch(mold ->
                mold.test(Items.IRON_PICKAXE.getDefaultInstance())));
    }

    private static AdvancedAlloyFurnaceRecipe converted(
            com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?> source) {
        return CreateProcessingRecipeAdapter.converted(id("converted"), source).getFirst();
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeHolder<T> holder(
            String path, T recipe) {
        return new RecipeHolder<>(id(path), recipe);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("create_adapter_test", path);
    }

    private static long required(AdvancedAlloyFurnaceRecipe recipe, Item item) {
        return recipe.inputs().stream()
                .filter(input -> input.ingredient().test(item.getDefaultInstance()))
                .mapToLong(input -> input.count())
                .sum();
    }

    private static int count(List<ItemStack> outputs, Item item) {
        return outputs.stream().filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static Item createItem(String path) {
        return BuiltInRegistries.BLOCK
                .get(ResourceLocation.fromNamespaceAndPath("create", path))
                .asItem();
    }

    private static void assertMold(AdvancedAlloyFurnaceRecipe recipe, ItemLike item) {
        assertTrue(recipe.molds().stream().anyMatch(mold ->
                mold.test(item.asItem().getDefaultInstance())), item.toString());
    }

    private static final class ThirdPartyAssemblyRecipe extends PressingRecipe {
        private ThirdPartyAssemblyRecipe(
                com.simibubi.create.content.processing.recipe.ProcessingRecipeParams params) {
            super(params);
        }

        @Override
        public void addAssemblyIngredients(List<Ingredient> list) {
            list.add(Ingredient.of(Items.AMETHYST_SHARD));
        }

        @Override
        public void addRequiredMachines(java.util.Set<ItemLike> list) {
            list.add(createItem("mechanical_saw"));
        }
    }
}
