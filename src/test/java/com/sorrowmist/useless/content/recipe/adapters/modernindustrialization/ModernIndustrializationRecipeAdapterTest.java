package com.sorrowmist.useless.content.recipe.adapters.modernindustrialization;

import aztech.modern_industrialization.machines.init.MIMachineRecipeTypes;
import aztech.modern_industrialization.machines.recipe.MachineRecipe;
import aztech.modern_industrialization.machines.recipe.MachineRecipeType;
import aztech.modern_industrialization.thirdparty.fabrictransfer.api.item.ItemVariant;
import com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernIndustrializationRecipeAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void convertsExpectedBatchAndReusableInputsIntoMultiblockMolds() {
        MachineRecipe source = recipe(
                MIMachineRecipeTypes.COMPRESSOR,
                2,
                20,
                List.of(
                        new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 1, 0.1f),
                        new MachineRecipe.ItemInput(Ingredient.of(Items.STICK), 1, 0.0f)),
                List.of(new MachineRecipe.FluidInput(Fluids.WATER, 100, 0.0f)),
                List.of(new MachineRecipe.ItemOutput(ItemVariant.of(Items.GOLD_INGOT), 1, 1.0f)),
                List.of(new MachineRecipe.FluidOutput(Fluids.LAVA, 10, 0.5f)));

        AdvancedAlloyFurnaceRecipe converted = new ModernIndustrializationRecipeAdapter()
                .convertAll(holder("modern_industrialization", "expected_batch", source), null)
                .getFirst();

        assertEquals(1L, converted.inputs().getFirst().count());
        assertEquals(0, converted.inputFluids().size());
        assertEquals(10, converted.outputs().getFirst().getCount());
        assertEquals(50, converted.outputFluids().getFirst().getAmount());
        assertEquals(400L, converted.energy());
        assertEquals(200, converted.processTime());
        assertEquals(3, converted.molds().size());

        Item electricCompressor = item("electric_compressor");
        Item waterBucket = Items.WATER_BUCKET;
        assertTrue(converted.molds().get(0).test(new ItemStack(electricCompressor)));
        assertTrue(converted.molds().get(1).test(new ItemStack(Items.STICK)));
        assertTrue(converted.molds().get(2).test(new ItemStack(waterBucket)));
        assertTrue(OmniversalMoldHubBlockEntity.matchesMolds(
                converted.molds(), List.of(
                        new ItemStack(electricCompressor),
                        new ItemStack(Items.STICK),
                        new ItemStack(waterBucket))));
    }

    @Test
    void allowsHighEuRecipesForAllMappedMachineVariants() {
        MachineRecipe source = recipe(
                MIMachineRecipeTypes.COMPRESSOR,
                1_000_000,
                20,
                List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 1, 1.0f)),
                List.of(),
                List.of(new MachineRecipe.ItemOutput(ItemVariant.of(Items.GOLD_INGOT), 1, 1.0f)),
                List.of());

        AdvancedAlloyFurnaceRecipe converted = new ModernIndustrializationRecipeAdapter()
                .convertAll(holder("modern_industrialization", "tiered", source), null)
                .getFirst();

        assertTrue(converted.mold().test(new ItemStack(item("bronze_compressor"))));
        assertTrue(converted.mold().test(new ItemStack(item("steel_compressor"))));
        assertTrue(converted.mold().test(new ItemStack(item("electric_compressor"))));
    }

    @Test
    void identifiesOnlyModernIndustrializationMachineMolds() {
        ModernIndustrializationRecipeAdapter adapter = new ModernIndustrializationRecipeAdapter();
        assertTrue(adapter.matchesMold(new ItemStack(item("assembler"))));
        assertTrue(adapter.matchesMold(new ItemStack(item("electric_quarry"))));
        assertTrue(adapter.matchesMold(new ItemStack(item("electric_unpacker"))));
        assertTrue(adapter.matchesMold(new ItemStack(item("electric_wiremill"))));
        assertFalse(adapter.matchesMold(new ItemStack(Items.FURNACE)));
        assertFalse(adapter.matchesMold(ItemStack.EMPTY));
    }

    @Test
    void convertsQuantumCircuitBoardStyleAssemblerRecipe() {
        Item board = item("quantum_circuit_board");
        MachineRecipe source = recipe(
                MIMachineRecipeTypes.ASSEMBLER,
                64,
                2_000,
                List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 1, 1.0f)),
                List.of(),
                List.of(new MachineRecipe.ItemOutput(ItemVariant.of(board), 1, 1.0f)),
                List.of());

        AdvancedAlloyFurnaceRecipe converted = new ModernIndustrializationRecipeAdapter()
                .convertAll(holder("modern_industrialization", "quantum_circuit_board", source), null)
                .getFirst();

        assertEquals(128_000L, converted.energy());
        assertEquals(2_000, converted.processTime());
        assertTrue(converted.outputs().getFirst().is(board));
    }

    @Test
    void omitsNonConsumableFluidWithoutAFilledBucket() {
        MachineRecipe source = recipe(
                MIMachineRecipeTypes.MIXER,
                2,
                20,
                List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 1, 1.0f)),
                List.of(new MachineRecipe.FluidInput(FluidIngredient.empty(), 1, 0.0f)),
                List.of(new MachineRecipe.ItemOutput(ItemVariant.of(Items.GOLD_INGOT), 1, 1.0f)),
                List.of());

        AdvancedAlloyFurnaceRecipe converted = new ModernIndustrializationRecipeAdapter()
                .convertAll(holder("modern_industrialization", "no_bucket_catalyst", source), null)
                .getFirst();

        assertTrue(converted.inputFluids().isEmpty());
        assertEquals(1, converted.molds().size());
        assertTrue(converted.outputs().getFirst().is(Items.GOLD_INGOT));
    }

    @Test
    void addsWaterToMachineRecipesWhoseOnlyInputsAreNonConsumableMolds() {
        List<MachineCase> cases = List.of(
                new MachineCase(MIMachineRecipeTypes.ELECTROLYZER, "electrolyzer", "singularity"),
                new MachineCase(MIMachineRecipeTypes.PRESSURIZER, "pressurizer", "air_intake"),
                new MachineCase(MIMachineRecipeTypes.VACUUM_FREEZER, "vacuum_freezer", "air_intake"));

        for (MachineCase machine : cases) {
            Item nonConsumable = item(machine.inputItem());
            MachineRecipe source = recipe(
                    machine.type(),
                    2,
                    20,
                    List.of(new MachineRecipe.ItemInput(
                            Ingredient.of(nonConsumable), 1, 0.0f)),
                    List.of(),
                    List.of(new MachineRecipe.ItemOutput(
                            ItemVariant.of(Items.GOLD_INGOT), 1, 1.0f)),
                    List.of());

            AdvancedAlloyFurnaceRecipe converted = new ModernIndustrializationRecipeAdapter()
                    .convertAll(holder("modern_industrialization", machine.type().getPath(), source), null)
                    .getFirst();

            assertEquals(1, converted.inputFluids().size(), machine.type().getPath());
            assertEquals(1, converted.inputFluids().getFirst().amount(), machine.type().getPath());
            assertTrue(converted.inputFluids().getFirst().ingredient()
                    .test(new FluidStack(Fluids.WATER, 1)), machine.type().getPath());
            assertEquals(2, converted.molds().size(), machine.type().getPath());
            assertTrue(converted.molds().getFirst().test(new ItemStack(item(machine.machineItem()))));
            assertTrue(converted.molds().get(1).test(new ItemStack(nonConsumable)));

            List<List<appeng.api.stacks.GenericStack>> inputOptions =
                    OmniversalPatternJeiTransferHandler.inputOptions(converted);
            assertNotNull(inputOptions, machine.type().getPath());
            assertEquals(1, inputOptions.size(), machine.type().getPath());
            assertTrue(inputOptions.getFirst().getFirst().what()
                    instanceof appeng.api.stacks.AEFluidKey, machine.type().getPath());
            assertTrue(!OmniversalPatternEncoding.createProcessingPattern(converted).isEmpty(),
                    machine.type().getPath());
            assertFalse(FluidIngredientAllocator.matches(
                    converted.inputFluids(), Map.of(), 1L), machine.type().getPath());
            assertTrue(FluidIngredientAllocator.matches(
                    converted.inputFluids(),
                    Map.of(new FluidStack(Fluids.WATER, 1), 1L), 1L), machine.type().getPath());
        }
    }

    @Test
    void scalesFallbackWaterWithAProbabilityBatch() {
        MachineRecipe source = recipe(
                MIMachineRecipeTypes.VACUUM_FREEZER,
                2,
                20,
                List.of(new MachineRecipe.ItemInput(Ingredient.of(item("air_intake")), 1, 0.0f)),
                List.of(),
                List.of(new MachineRecipe.ItemOutput(ItemVariant.of(Items.GOLD_INGOT), 1, 0.5f)),
                List.of());

        AdvancedAlloyFurnaceRecipe converted = new ModernIndustrializationRecipeAdapter()
                .convertAll(holder("modern_industrialization", "water_batch", source), null)
                .getFirst();

        assertEquals(2, converted.inputFluids().getFirst().amount());
        assertEquals(1, converted.outputs().getFirst().getCount());
    }

    @Test
    void convertsEveryListedMachineRecipeTypeButNotTheGenericFurnaceType() {
        ModernIndustrializationRecipeAdapter adapter = new ModernIndustrializationRecipeAdapter();
        List<MachineRecipeType> listedTypes = List.of(
                MIMachineRecipeTypes.ASSEMBLER,
                MIMachineRecipeTypes.CENTRIFUGE,
                MIMachineRecipeTypes.CHEMICAL_REACTOR,
                MIMachineRecipeTypes.COMPRESSOR,
                MIMachineRecipeTypes.CUTTING_MACHINE,
                MIMachineRecipeTypes.DISTILLERY,
                MIMachineRecipeTypes.ELECTROLYZER,
                MIMachineRecipeTypes.MACERATOR,
                MIMachineRecipeTypes.MIXER,
                MIMachineRecipeTypes.PACKER,
                MIMachineRecipeTypes.POLARIZER,
                MIMachineRecipeTypes.UNPACKER,
                MIMachineRecipeTypes.WIREMILL,
                MIMachineRecipeTypes.BLAST_FURNACE,
                MIMachineRecipeTypes.COKE_OVEN,
                MIMachineRecipeTypes.DISTILLATION_TOWER,
                MIMachineRecipeTypes.FUSION_REACTOR,
                MIMachineRecipeTypes.HEAT_EXCHANGER,
                MIMachineRecipeTypes.IMPLOSION_COMPRESSOR,
                MIMachineRecipeTypes.OIL_DRILLING_RIG,
                MIMachineRecipeTypes.PRESSURIZER,
                MIMachineRecipeTypes.QUARRY,
                MIMachineRecipeTypes.VACUUM_FREEZER);

        for (MachineRecipeType type : listedTypes) {
            AdvancedAlloyFurnaceRecipe converted = adapter
                    .convertAll(holder("modern_industrialization", type.getPath(), recipe(
                            type, 2, 20,
                            List.of(new MachineRecipe.ItemInput(
                                    Ingredient.of(Items.IRON_INGOT), 1, 1.0f)),
                            List.of(),
                            List.of(new MachineRecipe.ItemOutput(
                                    ItemVariant.of(Items.GOLD_INGOT), 1, 1.0f)),
                            List.of())), null)
                    .getFirst();
            assertFalse(converted.molds().isEmpty(), type.getPath());
        }

        assertTrue(adapter.convertAll(holder("modern_industrialization", "furnace", recipe(
                MIMachineRecipeTypes.FURNACE, 2, 20,
                List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 1, 1.0f)),
                List.of(),
                List.of(new MachineRecipe.ItemOutput(ItemVariant.of(Items.GOLD_INGOT), 1, 1.0f)),
                List.of())), null).isEmpty());
    }

    private static Item item(String path) {
        Item item = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("modern_industrialization", path));
        assertNotEquals(Items.AIR, item, "MI item is not registered: " + path);
        return item;
    }

    private static MachineRecipe recipe(
            MachineRecipeType type,
            int eu,
            int duration,
            List<MachineRecipe.ItemInput> itemInputs,
            List<MachineRecipe.FluidInput> fluidInputs,
            List<MachineRecipe.ItemOutput> itemOutputs,
            List<MachineRecipe.FluidOutput> fluidOutputs) {
        try {
            Constructor<MachineRecipe> constructor = MachineRecipe.class
                    .getDeclaredConstructor(MachineRecipeType.class);
            constructor.setAccessible(true);
            MachineRecipe recipe = constructor.newInstance(type);
            recipe.eu = eu;
            recipe.duration = duration;
            recipe.itemInputs = itemInputs;
            recipe.fluidInputs = fluidInputs;
            recipe.itemOutputs = itemOutputs;
            recipe.fluidOutputs = fluidOutputs;
            return recipe;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not construct MI test recipe", exception);
        }
    }

    private static RecipeHolder<MachineRecipe> holder(String namespace, String path, MachineRecipe recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(namespace, path), recipe);
    }

    private record MachineCase(MachineRecipeType type, String machineItem, String inputItem) {
    }
}
