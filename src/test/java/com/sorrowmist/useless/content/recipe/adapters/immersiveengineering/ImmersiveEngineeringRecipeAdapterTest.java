package com.sorrowmist.useless.content.recipe.adapters.immersiveengineering;

import blusunrize.immersiveengineering.api.crafting.AlloyRecipe;
import blusunrize.immersiveengineering.api.crafting.ArcFurnaceRecipe;
import blusunrize.immersiveengineering.api.crafting.BlastFurnaceRecipe;
import blusunrize.immersiveengineering.api.crafting.BlueprintCraftingRecipe;
import blusunrize.immersiveengineering.api.crafting.BottlingMachineRecipe;
import blusunrize.immersiveengineering.api.crafting.ClocheRecipe;
import blusunrize.immersiveengineering.api.crafting.CokeOvenRecipe;
import blusunrize.immersiveengineering.api.crafting.CrusherRecipe;
import blusunrize.immersiveengineering.api.crafting.FermenterRecipe;
import blusunrize.immersiveengineering.api.crafting.IngredientWithSize;
import blusunrize.immersiveengineering.api.crafting.IESerializableRecipe;
import blusunrize.immersiveengineering.api.crafting.MetalPressRecipe;
import blusunrize.immersiveengineering.api.crafting.MixerRecipe;
import blusunrize.immersiveengineering.api.crafting.RefineryRecipe;
import blusunrize.immersiveengineering.api.crafting.SawmillRecipe;
import blusunrize.immersiveengineering.api.crafting.SqueezerRecipe;
import blusunrize.immersiveengineering.api.crafting.StackWithChance;
import blusunrize.immersiveengineering.api.crafting.TagOutput;
import blusunrize.immersiveengineering.api.crafting.TagOutputList;
import blusunrize.immersiveengineering.common.config.IEServerConfig;
import com.mojang.datafixers.util.Pair;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.Holder;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmersiveEngineeringRecipeAdapterTest {
    private static Level level;
    private static Map<net.minecraft.tags.TagKey<Item>, List<Holder<Item>>> originalItemTags;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalItemTags = BuiltInRegistries.ITEM.getTags().collect(Collectors.toUnmodifiableMap(
                Pair::getFirst,
                pair -> StreamSupport.stream(pair.getSecond().spliterator(), false).toList()));
        Map<net.minecraft.tags.TagKey<Item>, List<Holder<Item>>> itemTags = new HashMap<>(originalItemTags);
        itemTags.put(ItemTags.LOGS, List.of(itemHolder(Items.OAK_LOG), itemHolder(Items.BIRCH_LOG)));
        BuiltInRegistries.ITEM.bindTags(itemTags);
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        level = (Level) ((Unsafe) field.get(null)).allocateInstance(TestServerLevel.class);
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreItemTags() {
        if (originalItemTags != null) {
            BuiltInRegistries.ITEM.bindTags(originalItemTags);
        }
    }

    @Test
    void convertsEveryRequestedImmersiveEngineeringRecipeType() {
        ImmersiveEngineeringRecipeAdapter adapter = new ImmersiveEngineeringRecipeAdapter();
        assertEquals("immersiveengineering", adapter.sourceId());
        assertTrue(adapter.matchesMold(new ItemStack(ieItem("alloy_smelter"))));
        assertFalse(adapter.matchesMold(new ItemStack(Items.FURNACE)));

        for (IESerializableRecipe source : requestedRecipes()) {
            List<AdvancedAlloyFurnaceRecipe> converted = adapter.convertAll(
                    holder(source.getClass().getSimpleName().toLowerCase(), source), level);
            assertEquals(1, converted.size(), source.getClass().getSimpleName());
            assertNotNull(converted.getFirst());
        }
    }

    @Test
    void preservesTagInputsAndConvertsArcSecondaryOutputToAStableBatch() {
        IngredientWithSize taggedInput = new IngredientWithSize(ItemTags.LOGS, 2);
        ArcFurnaceRecipe source = new ArcFurnaceRecipe(
                new TagOutputList(new TagOutput(new ItemStack(Items.DIAMOND))),
                new TagOutput(new ItemStack(Items.COAL)),
                List.of(new StackWithChance(new ItemStack(Items.REDSTONE), 0.25f)),
                100,
                20,
                taggedInput,
                List.of(new IngredientWithSize(Ingredient.of(Items.GOLD_INGOT), 1)));

        AdvancedAlloyFurnaceRecipe converted = new ImmersiveEngineeringRecipeAdapter()
                .convertAll(holder("arc", source), level).getFirst();

        assertEquals(8L, converted.inputs().stream()
                .filter(input -> input.ingredient().equals(taggedInput.getBaseIngredient()))
                .findFirst().orElseThrow().count());
        assertEquals(4L, converted.inputs().stream()
                .filter(input -> input.ingredient().test(new ItemStack(Items.GOLD_INGOT)))
                .findFirst().orElseThrow().count());
        assertEquals(1, converted.outputs().stream()
                .filter(output -> output.is(Items.REDSTONE))
                .findFirst().orElseThrow().getCount());
        assertTrue(converted.outputs().stream().anyMatch(output -> output.is(Items.DIAMOND)));
        assertTrue(converted.outputs().stream().anyMatch(output -> output.is(Items.COAL)));
        assertEquals(400, converted.processTime());
        assertEquals(1, converted.molds().size());
    }

    @Test
    void preservesClocheProbabilityFluidAndReusableSeedAndSoil() {
        ClocheRecipe source = new ClocheRecipe(
                List.of(
                        new StackWithChance(new ItemStack(Items.WHEAT, 2), 1.0f),
                        new StackWithChance(new ItemStack(Items.WHEAT_SEEDS), 0.25f)),
                Ingredient.of(Items.WHEAT_SEEDS),
                Ingredient.of(Items.DIRT),
                40,
                FluidIngredient.single(Fluids.LAVA),
                null);

        AdvancedAlloyFurnaceRecipe converted = new ImmersiveEngineeringRecipeAdapter()
                .convertAll(holder("cloche", source), level).getFirst();

        assertTrue(converted.inputs().isEmpty());
        assertEquals(IEServerConfig.getOrDefault(IEServerConfig.MACHINES.cloche_fluid) * 4,
                converted.inputFluids().getFirst().amount());
        assertEquals(8, converted.outputs().stream()
                .filter(output -> output.is(Items.WHEAT))
                .findFirst().orElseThrow().getCount());
        assertEquals(1, converted.outputs().stream()
                .filter(output -> output.is(Items.WHEAT_SEEDS))
                .findFirst().orElseThrow().getCount());
        assertEquals(3, converted.molds().size());
        assertTrue(OmniversalMoldHubBlockEntity.matchesMolds(
                converted.molds(),
                List.of(new ItemStack(ieItem("cloche")),
                        new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.DIRT))));
    }

    @Test
    void preservesFluidOutputsAndAuxiliaryMolds() {
        Ingredient catalyst = Ingredient.of(Items.IRON_INGOT);
        RefineryRecipe source = new RefineryRecipe(
                new FluidStack(Fluids.LAVA, 16),
                new SizedFluidIngredient(FluidIngredient.single(Fluids.WATER), 8),
                new SizedFluidIngredient(FluidIngredient.single(Fluids.LAVA), 4),
                catalyst,
                100);

        AdvancedAlloyFurnaceRecipe converted = new ImmersiveEngineeringRecipeAdapter()
                .convertAll(holder("refinery", source), level).getFirst();

        assertEquals(2, converted.inputFluids().size());
        assertEquals(8, converted.inputFluids().getFirst().amount());
        assertEquals(4, converted.inputFluids().get(1).amount());
        assertEquals(16, converted.outputFluids().getFirst().getAmount());
        assertEquals(2, converted.molds().size());
        assertTrue(converted.molds().get(1).test(new ItemStack(Items.IRON_INGOT)));
    }

    @Test
    void runtimeLookupUsesTagsFluidsAndSkipsMultiMoldRecipes() {
        AlloyRecipe tagged = new AlloyRecipe(
                new TagOutput(new ItemStack(Items.DIAMOND)),
                new IngredientWithSize(ItemTags.LOGS, 1),
                new IngredientWithSize(Ingredient.of(Items.GOLD_INGOT), 1),
                20);
        RecipeHolder<AlloyRecipe> taggedHolder = holder("tagged", tagged);

        MetalPressRecipe multiMold = new MetalPressRecipe(
                new TagOutput(new ItemStack(Items.IRON_NUGGET)),
                new IngredientWithSize(Ingredient.of(Items.IRON_INGOT), 1),
                ieItem("mold_plate"),
                100);
        RecipeHolder<MetalPressRecipe> pressHolder = holder("press", multiMold);

        RecipeManager recipeManager = new RecipeManager(null);
        recipeManager.replaceRecipes(new ArrayList<>(List.of(taggedHolder, pressHolder)));
        TestServerLevel.recipeManager = recipeManager;

        ImmersiveEngineeringRecipeAdapter adapter = new ImmersiveEngineeringRecipeAdapter();
        List<RecipeHolder<IESerializableRecipe>> matches = adapter.findMatchingRecipes(
                level,
                AdapterUtils.mergeInputs(List.of(
                        new ItemStack(Items.OAK_LOG), new ItemStack(Items.GOLD_INGOT))),
                Map.of(),
                new ItemStack(ieItem("alloy_smelter")));

        assertEquals(List.of(taggedHolder.id()), matches.stream().map(RecipeHolder::id).toList());
        assertTrue(adapter.findMatchingRecipes(
                level,
                AdapterUtils.mergeInputs(List.of(new ItemStack(Items.IRON_INGOT))),
                Map.of(),
                new ItemStack(ieItem("metal_press"))).isEmpty());
    }

    @Test
    void preservesMetalPressMoldAndSawmillSecondaryOutputs() {
        MetalPressRecipe press = new MetalPressRecipe(
                new TagOutput(new ItemStack(Items.IRON_NUGGET, 4)),
                new IngredientWithSize(Ingredient.of(Items.IRON_INGOT), 1),
                ieItem("mold_plate"),
                100);
        SawmillRecipe sawmill = new SawmillRecipe(
                new TagOutput(new ItemStack(Items.OAK_PLANKS, 6)),
                TagOutput.EMPTY,
                Ingredient.of(Items.OAK_LOG),
                100,
                TagOutputList.EMPTY,
                new TagOutputList(new TagOutput(new ItemStack(Items.STICK))));

        ImmersiveEngineeringRecipeAdapter adapter = new ImmersiveEngineeringRecipeAdapter();
        AdvancedAlloyFurnaceRecipe pressResult =
                adapter.convertAll(holder("press_mold", press), level).getFirst();
        AdvancedAlloyFurnaceRecipe sawmillResult =
                adapter.convertAll(holder("sawmill_outputs", sawmill), level).getFirst();

        assertEquals(2, pressResult.molds().size());
        assertTrue(pressResult.molds().get(1).test(new ItemStack(ieItem("mold_plate"))));
        assertTrue(sawmillResult.outputs().stream().anyMatch(output -> output.is(Items.STICK)));
    }

    private static List<IESerializableRecipe> requestedRecipes() {
        return List.of(
                new AlloyRecipe(
                        new TagOutput(new ItemStack(Items.DIAMOND)),
                        new IngredientWithSize(Ingredient.of(Items.IRON_INGOT), 1),
                        new IngredientWithSize(Ingredient.of(Items.GOLD_INGOT), 1),
                        20),
                new ArcFurnaceRecipe(
                        new TagOutputList(new TagOutput(new ItemStack(Items.DIAMOND))),
                        TagOutput.EMPTY,
                        List.of(),
                        100,
                        20,
                        new IngredientWithSize(Ingredient.of(Items.IRON_INGOT), 1),
                        List.of()),
                new BlastFurnaceRecipe(
                        new TagOutput(new ItemStack(Items.IRON_INGOT)),
                        new IngredientWithSize(Ingredient.of(Items.RAW_IRON), 1),
                        20,
                        TagOutput.EMPTY),
                new BlueprintCraftingRecipe(
                        "components",
                        new TagOutput(new ItemStack(Items.IRON_INGOT)),
                        List.of(new IngredientWithSize(Ingredient.of(Items.IRON_INGOT), 1))),
                new BottlingMachineRecipe(
                        new TagOutputList(new TagOutput(new ItemStack(Items.GLASS_BOTTLE))),
                        new IngredientWithSize(Ingredient.of(Items.GLASS), 1),
                        new SizedFluidIngredient(FluidIngredient.single(Fluids.WATER), 100)),
                new ClocheRecipe(
                        List.of(new StackWithChance(new ItemStack(Items.WHEAT), 1.0f)),
                        Ingredient.of(Items.WHEAT_SEEDS),
                        Ingredient.of(Items.DIRT),
                        40,
                        FluidIngredient.empty(),
                        null),
                new CokeOvenRecipe(
                        new TagOutput(new ItemStack(Items.CHARCOAL)),
                        new IngredientWithSize(Ingredient.of(Items.OAK_LOG), 1),
                        20,
                        250),
                new CrusherRecipe(
                        new TagOutput(new ItemStack(Items.GRAVEL)),
                        Ingredient.of(Items.COBBLESTONE),
                        100,
                        List.of()),
                new FermenterRecipe(
                        new FluidStack(Fluids.WATER, 20),
                        TagOutput.EMPTY,
                        new IngredientWithSize(Ingredient.of(Items.APPLE), 1),
                        100),
                new MetalPressRecipe(
                        new TagOutput(new ItemStack(Items.IRON_NUGGET)),
                        new IngredientWithSize(Ingredient.of(Items.IRON_INGOT), 1),
                        ieItem("mold_plate"),
                        100),
                new MixerRecipe(
                        new FluidStack(Fluids.WATER, 20),
                        new SizedFluidIngredient(FluidIngredient.single(Fluids.WATER), 100),
                        List.of(new IngredientWithSize(Ingredient.of(Items.SAND), 1)),
                        100),
                new RefineryRecipe(
                        new FluidStack(Fluids.LAVA, 20),
                        new SizedFluidIngredient(FluidIngredient.single(Fluids.WATER), 8),
                        Optional.empty(),
                        Ingredient.of(Items.IRON_INGOT),
                        100),
                new SawmillRecipe(
                        new TagOutput(new ItemStack(Items.OAK_PLANKS)),
                        TagOutput.EMPTY,
                        Ingredient.of(Items.OAK_LOG),
                        100,
                        TagOutputList.EMPTY,
                        TagOutputList.EMPTY),
                new SqueezerRecipe(
                        new FluidStack(Fluids.WATER, 20),
                        TagOutput.EMPTY,
                        new IngredientWithSize(Ingredient.of(Items.APPLE), 1),
                        100));
    }

    private static Item ieItem(String path) {
        return BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath("immersiveengineering", path)).orElseThrow();
    }

    private static Holder<Item> itemHolder(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return BuiltInRegistries.ITEM.getHolderOrThrow(ResourceKey.create(Registries.ITEM, id));
    }

    private static <T extends Recipe<?>> RecipeHolder<T> holder(String path, T recipe) {
        return new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("immersiveengineering_test", path), recipe);
    }

    private static final class TestServerLevel extends ServerLevel {
        private static RecipeManager recipeManager;

        private TestServerLevel() {
            super(null, null, null, null, null, null, null, false, 0L, List.of(), false, null);
        }

        @Override
        public RecipeManager getRecipeManager() {
            return recipeManager;
        }
    }
}
