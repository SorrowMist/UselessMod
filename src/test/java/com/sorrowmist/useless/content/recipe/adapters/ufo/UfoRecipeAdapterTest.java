package com.sorrowmist.useless.content.recipe.adapters.ufo;

import appeng.api.stacks.GenericStack;
import com.mojang.datafixers.util.Pair;
import com.raishxn.ufo.recipe.DimensionalMatterAssemblerRecipe;
import com.raishxn.ufo.recipe.QMFRecipe;
import com.raishxn.ufo.recipe.StellarSimulationRecipe;
import com.raishxn.ufo.recipe.UniversalMultiblockMachineKind;
import com.raishxn.ufo.recipe.UniversalMultiblockRecipe;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProviders;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
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
import net.pedroksl.ae2addonlib.recipes.IngredientStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UfoRecipeAdapterTest {
    private static final TagKey<Fluid> COOLANTS = TagKey.create(
            Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "coolants"));
    private static Map<TagKey<Item>, List<Holder<Item>>> originalItemTags;
    private static Level level;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalItemTags = BuiltInRegistries.ITEM.getTags().collect(Collectors.toUnmodifiableMap(
                Pair::getFirst,
                pair -> StreamSupport.stream(pair.getSecond().spliterator(), false).toList()));
        Map<TagKey<Item>, List<Holder<Item>>> itemTags = new HashMap<>(originalItemTags);
        itemTags.put(ItemTags.LOGS, List.of(itemHolder(Items.OAK_LOG), itemHolder(Items.BIRCH_LOG)));
        BuiltInRegistries.ITEM.bindTags(itemTags);
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        level = (Level) ((Unsafe) field.get(null)).allocateInstance(TestServerLevel.class);
    }

    @AfterAll
    static void restoreItemTags() {
        if (originalItemTags != null) {
            BuiltInRegistries.ITEM.bindTags(originalItemTags);
        }
    }

    @Test
    void convertsQmfAndUniversalRecipesWithSeparateMoldsAndEnergy() {
        QMFRecipe qmf = new QMFRecipe(
                "qmf",
                List.of(new QMFRecipe.QMFRecipeIngredient(Ingredient.of(Items.IRON_INGOT), 2L)),
                List.of(new QMFRecipe.QMFFluidIngredient(new FluidStack(Fluids.WATER, 1), 125L)),
                List.of(), new ItemStack(Items.DIAMOND, 3), 400L, 40, 3);

        QMFRecipeAdapter qmfAdapter = new QMFRecipeAdapter();
        AdvancedAlloyFurnaceRecipe qmfConverted = qmfAdapter
                .convertAll(holder("qmf", qmf), level).getFirst();
        assertEquals(2L, qmfConverted.inputs().getFirst().count());
        assertEquals(125, qmfConverted.inputFluids().getFirst().amount());
        assertEquals(3, qmfConverted.outputs().getFirst().getCount());
        assertEquals(800L, qmfConverted.energy());
        assertTrue(qmfConverted.mold().test(qmfAdapter.getMoldItem()));

        UniversalMultiblockRecipe universalQmf = universal(
                UniversalMultiblockMachineKind.QMF, 5L, FluidStack.EMPTY);
        UniversalMultiblockRecipe cryoforge = universal(
                UniversalMultiblockMachineKind.QUANTUM_CRYOFORGE, 7L, FluidStack.EMPTY);
        UniversalMultiblockRecipeAdapter qmfUniversalAdapter =
                new UniversalMultiblockRecipeAdapter(UniversalMultiblockMachineKind.QMF);
        UniversalMultiblockRecipeAdapter cryoforgeAdapter =
                new UniversalMultiblockRecipeAdapter(UniversalMultiblockMachineKind.QUANTUM_CRYOFORGE);

        AdvancedAlloyFurnaceRecipe universalConverted = qmfUniversalAdapter
                .convertAll(holder("universal_qmf", universalQmf), level).getFirst();
        assertEquals(5, universalConverted.outputs().getFirst().getCount());
        assertTrue(universalConverted.mold().test(qmfUniversalAdapter.getMoldItem()));
        assertTrue(cryoforgeAdapter.convertAll(holder("wrong_machine", universalQmf), level).isEmpty());

        AdvancedAlloyFurnaceRecipe cryoforgeConverted = cryoforgeAdapter
                .convertAll(holder("cryoforge", cryoforge), level).getFirst();
        assertEquals(7, cryoforgeConverted.outputs().getFirst().getCount());
        assertTrue(cryoforgeConverted.mold().test(cryoforgeAdapter.getMoldItem()));
        assertFalse(qmfUniversalAdapter.matchesMold(cryoforgeAdapter.getMoldItem()));
    }

    @Test
    void convertsStellarFuelCoolantAndGenericOutputsWithoutNarrowing() {
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("stellar"));
        GenericStack itemOutput = GenericStack.fromItemStack(named);
        GenericStack fluidOutput = GenericStack.fromFluidStack(new FluidStack(Fluids.LAVA, 9));
        StellarSimulationRecipe source = new StellarSimulationRecipe(
                List.of(new IngredientStack.Item(Ingredient.of(Items.IRON_INGOT), 2)),
                List.of(), List.of(itemOutput), List.of(fluidOutput),
                "stellar", 900L, 80, 9, 4, "minecraft:water", 200L, 40L);

        AdvancedAlloyFurnaceRecipe converted = new StellarSimulationRecipeAdapter()
                .convertAll(holder("stellar", source), level).getFirst();

        assertEquals(2, converted.keyOutputs().size());
        assertEquals(2L, converted.inputs().getFirst().count());
        assertEquals(2, converted.inputFluids().size());
        assertEquals(200, converted.inputFluids().getFirst().amount());
        assertEquals(40, converted.inputFluids().get(1).amount());
        assertEquals(FluidIngredient.tag(COOLANTS), converted.inputFluids().get(1).ingredient());
        assertEquals(40L, converted.inputFluids().get(1).amount());
        assertEquals(1_800L, converted.energy());
        assertSame(itemOutput, converted.keyOutputs().getFirst());
        assertSame(fluidOutput, converted.keyOutputs().get(1));
        assertEquals(itemOutput.what(), converted.keyOutputs().getFirst().what());
    }

    @Test
    void keepsDmaMultipleGenericOutputsAndLongAmounts() {
        GenericStack itemOutput = new GenericStack(
                GenericStack.fromItemStack(new ItemStack(Items.NETHER_STAR)).what(), Long.MAX_VALUE);
        GenericStack fluidOutput = new GenericStack(
                GenericStack.fromFluidStack(new FluidStack(Fluids.WATER, 1)).what(), 17L);
        DimensionalMatterAssemblerRecipe source = new DimensionalMatterAssemblerRecipe(
                List.of(new IngredientStack.Item(Ingredient.of(ItemTags.LOGS), 1)),
                List.of(new IngredientStack.Fluid(FluidIngredient.of(Fluids.WATER), 50)),
                List.of(itemOutput), List.of(fluidOutput), 11, 22);

        AdvancedAlloyFurnaceRecipe converted = new DimensionalMatterAssemblerRecipeAdapter()
                .convertAll(holder("dma", source), level).getFirst();

        assertEquals(2, converted.keyOutputs().size());
        assertEquals(Long.MAX_VALUE, converted.keyOutputs().getFirst().amount());
        assertEquals(17L, converted.keyOutputs().get(1).amount());
        assertEquals(22, converted.processTime());
        assertEquals(22L, converted.energy());
        assertTrue(converted.inputs().getFirst().ingredient().test(Items.OAK_LOG.getDefaultInstance()));
    }

    @Test
    void skipsOnlyChemicalRecipesWhenTheProviderIsUnavailable() {
        ChemicalKeyProvider previous = ChemicalKeyProviders.get();
        try {
            ChemicalKeyProviders.register(ChemicalKeyProvider.NONE);
            QMFRecipe chemical = new QMFRecipe(
                    "chemical",
                    List.of(new QMFRecipe.QMFRecipeIngredient(Ingredient.of(Items.IRON_INGOT), 1L)),
                    List.of(),
                    List.of(new QMFRecipe.QMFChemicalIngredient(
                            ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen"), 10L)),
                    new ItemStack(Items.DIAMOND), 1L, 20, 1);
            QMFRecipe ordinary = new QMFRecipe(
                    "ordinary",
                    List.of(new QMFRecipe.QMFRecipeIngredient(Ingredient.of(Items.IRON_INGOT), 1L)),
                    List.of(), List.of(), new ItemStack(Items.DIAMOND), 1L, 20, 1);

            QMFRecipeAdapter adapter = new QMFRecipeAdapter();
            assertTrue(adapter.convertAll(holder("chemical", chemical), level).isEmpty());
            assertFalse(adapter.convertAll(holder("ordinary", ordinary), level).isEmpty());
        } finally {
            ChemicalKeyProviders.register(previous);
        }
    }

    @Test
    void usesSaturatingLongEnergyConversion() {
        assertEquals(2L, UfoRecipeAdapterSupport.energy(1L));
        assertEquals(Long.MAX_VALUE, UfoRecipeAdapterSupport.energy(Long.MAX_VALUE));
        assertEquals(0L, UfoRecipeAdapterSupport.energy(-1L));
    }

    @Test
    void runtimeLookupUsesTheSameTagAndFluidConditionsAsConversion() {
        QMFRecipe source = new QMFRecipe(
                "runtime",
                List.of(new QMFRecipe.QMFRecipeIngredient(Ingredient.of(ItemTags.LOGS), 2L)),
                List.of(new QMFRecipe.QMFFluidIngredient(new FluidStack(Fluids.WATER, 1), 100L)),
                List.of(), new ItemStack(Items.DIAMOND), 3L, 20, 1);
        RecipeHolder<QMFRecipe> holder = holder("runtime", source);
        RecipeManager recipeManager = new RecipeManager(null);
        recipeManager.replaceRecipes(new ArrayList<>(List.of(holder)));
        TestServerLevel.recipeManager = recipeManager;

        QMFRecipeAdapter adapter = new QMFRecipeAdapter();
        List<RecipeHolder<QMFRecipe>> matches = adapter.findMatchingRecipes(
                level,
                AdapterUtils.mergeInputs(List.of(new ItemStack(Items.OAK_LOG, 2))),
                AdapterUtils.mergeFluids(List.of(new FluidStack(Fluids.WATER, 100))),
                Map.of(), adapter.getMoldItem());
        assertEquals(List.of(holder.id()), matches.stream().map(RecipeHolder::id).toList());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), Map.of(), Map.of(),
                new ItemStack(Items.STICK)).isEmpty());
    }

    private static UniversalMultiblockRecipe universal(
            UniversalMultiblockMachineKind machine, long outputAmount, FluidStack fluidOutput) {
        return new UniversalMultiblockRecipe(
                machine, machine.serializedName(),
                List.of(new UniversalMultiblockRecipe.ItemRequirement(
                        Ingredient.of(Items.IRON_INGOT), 1L)), List.of(), List.of(),
                new ItemStack(Items.DIAMOND), outputAmount,
                fluidOutput, 0L, 100L, 30, 1);
    }

    private static <T extends Recipe<?>> RecipeHolder<T> holder(String path, T recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("ufo_test", path), recipe);
    }

    private static Holder<Item> itemHolder(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return BuiltInRegistries.ITEM.getHolderOrThrow(ResourceKey.create(Registries.ITEM, id));
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
