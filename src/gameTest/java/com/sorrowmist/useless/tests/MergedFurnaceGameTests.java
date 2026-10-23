package com.sorrowmist.useless.tests;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.parts.encoding.PatternEncodingLogic;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.compat.thermal.ThermalParallelAccess;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceInputPort;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PendingOmniversalPatternHolder;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@GameTestHolder(UselessMod.MODID)
@PrefixGameTestTemplate(false)
public final class MergedFurnaceGameTests {
    @GameTest(template = "furnace_empty")
    public static void selectedJeiRecipeEncodesItsMoldInAe2TerminalLogic(GameTestHelper helper) {
        checkTerminalPatternSelection(helper, false);
    }

    @GameTest(template = "furnace_empty")
    public static void lateJeiSelectionConvertsAlreadyEncodedAe2Pattern(GameTestHelper helper) {
        checkTerminalPatternSelection(helper, true);
    }

    private static void checkTerminalPatternSelection(GameTestHelper helper, boolean lateSelection) {
        var entry = AlloyFurnaceRecipeCatalog.entries(helper.getLevel()).stream()
                .filter(value -> value.recipe().inputs().size() == 1
                        && value.recipe().inputs().get(0).count() == 1L
                        && value.recipe().inputs().get(0).ingredient().test(new ItemStack(Items.RAW_IRON))
                        && value.recipe().molds().size() == 1
                        && value.recipe().mold().test(new ItemStack(Items.FURNACE))
                        && value.recipe().outputs().size() == 1
                        && value.recipe().outputs().get(0).is(Items.IRON_INGOT))
                .findFirst().orElseThrow();
        var host = new IPatternTerminalLogicHost() {
            private final PatternEncodingLogic logic = new PatternEncodingLogic(this);
            @Override public PatternEncodingLogic getLogic() { return logic; }
            @Override public Level getLevel() { return helper.getLevel(); }
            @Override public void markForSave() { }
        };
        var logic = host.getLogic();
        helper.assertTrue(logic instanceof PendingOmniversalPatternHolder,
                "Native AE2 terminal logic must receive the selected-recipe mixin");
        var pending = (PendingOmniversalPatternHolder) logic;
        var pattern = OmniversalPatternEncoding.createProcessingPattern(entry.recipe());
        helper.assertTrue(!pattern.isEmpty(), "Native AE2 processing pattern must be encodable");
        if (lateSelection) {
            logic.getEncodedPatternInv().setItemDirect(0, pattern);
            helper.assertTrue(!logic.getEncodedPatternInv().getStackInSlot(0).is(ModItems.OMNIVERSAL_PATTERN.get()),
                    "An ordinary pattern must not be converted before a JEI recipe is selected");
        }
        pending.uselessMod$setPendingOmniversalRecipe(entry.identity());
        pending.uselessMod$setPendingOmniversalSourceId(entry.sourceId());
        if (lateSelection) pending.uselessMod$tryConvertPendingOmniversalPattern();
        else logic.getEncodedPatternInv().setItemDirect(0, pattern);
        var encoded = logic.getEncodedPatternInv().getStackInSlot(0);
        helper.assertTrue(encoded.is(ModItems.OMNIVERSAL_PATTERN.get()),
                "A selected alloy-furnace recipe must encode an omniversal pattern");
        var data = ComponentAccess.get(encoded, UComponents.OMNIVERSAL_PATTERN_DATA.get());
        helper.assertTrue(data != null && data.recipeId().equals(entry.recipe().id())
                        && data.sourceId().equals(entry.sourceId()) && data.requiresMold()
                        && data.displayMold().isPresent()
                        && data.displayMold().get().getItem() == Items.FURNACE,
                "Encoded pattern must retain the selected source, recipe and furnace mold");
        helper.assertTrue(pending.uselessMod$getPendingOmniversalRecipe() == null,
                "A consumed JEI selection must not affect the next ordinary AE2 pattern");
        helper.succeed();
    }

    @GameTest(template = "furnace_empty", timeoutTicks = 300)
    public static void diamondHammerRunsInSingleBlockFurnace(GameTestHelper helper) {
        runHammerRecipe(helper, false);
    }

    @GameTest(template = "furnace_empty", timeoutTicks = 300)
    public static void damagedNamedHammerRunsWithoutLosingItsNbt(GameTestHelper helper) {
        runHammerRecipe(helper, true);
    }

    private static void runHammerRecipe(GameTestHelper helper, boolean tagged) {
        var hammerItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("exdeorum", "diamond_hammer"));
        helper.assertTrue(hammerItem != null && hammerItem != Items.AIR, "Native Ex Deorum diamond hammer must exist");
        var hammer = new ItemStack(hammerItem);
        if (tagged) {
            hammer.setDamageValue(37);
            hammer.setHoverName(Component.literal("Regression hammer"));
            hammer.getOrCreateTag().putString("custom_marker", "preserved");
        }
        var expectedHammer = hammer.copy();
        BlockPos local = new BlockPos(2, 1, 2);
        helper.setBlock(local, ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());
        var furnace = (AdvancedAlloyFurnaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(local));
        furnace.getItemHandler().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
        furnace.getItemHandler().setStackInSlot(AdvancedAlloyFurnaceLayout.MOLD_SLOT, hammer);
        furnace.setEnergy(100000L);
        helper.succeedWhen(() -> {
            int gravel = 0;
            for (int slot = 9; slot < 18; slot++) {
                var output = furnace.getItemHandler().getStackInSlot(slot);
                if (output.is(Items.GRAVEL)) gravel += output.getCount();
            }
            helper.assertTrue(gravel == 1 && furnace.getItemHandler().getStackInSlot(0).isEmpty(),
                    "Single-block furnace must convert one cobblestone into one gravel using the native hammer recipe");
            helper.assertTrue(ItemStack.matches(expectedHammer,
                    furnace.getItemHandler().getStackInSlot(AdvancedAlloyFurnaceLayout.MOLD_SLOT)),
                    "Hammer damage and all custom NBT must remain unchanged");
        });
    }

    @GameTest(template = "furnace_empty")
    public static void thermalMachineLoadsParallelMixinAndRestoredAugments(GameTestHelper helper)
            throws ReflectiveOperationException {
        var machine = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("thermal", "machine_smelter"));
        helper.assertTrue(machine != null && machine != Blocks.AIR, "Native Thermal smelter must be registered");
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, machine);
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(blockEntity instanceof ThermalParallelAccess, "Thermal machine must receive the parallel mixin");
        helper.assertTrue(((ThermalParallelAccess) blockEntity).useless_mod$getParallel() == 0,
                "Unaugmented machines must not receive an extra parallel operation");
        for (int index = 1; index <= 9; index++) {
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(UselessMod.id("useless_integral_component_tier_" + index)),
                    "Restored integral component must be registered");
        }
        for (int index = 1; index <= 3; index++) {
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(UselessMod.id("augment_parallel_" + index)),
                    "Restored parallel augment must be registered");
        }
        var nativeType = cofh.thermal.lib.common.block.entity.AugmentableBlockEntity.class;
        var install = nativeType.getDeclaredMethod("attemptAugmentInstall", ItemStack.class);
        install.setAccessible(true);
        var augment = ForgeRegistries.ITEMS.getValue(UselessMod.id("augment_parallel_1"));
        helper.assertTrue((boolean) install.invoke(blockEntity, new ItemStack(augment)),
                "The native machine must accept the parallel augment");
        helper.assertTrue(((ThermalParallelAccess) blockEntity).useless_mod$getParallel() == 1,
                "Native augment finalization must apply exactly one extra operation");
        var slots = nativeType.getDeclaredField("augments");
        slots.setAccessible(true);
        for (var slot : (java.util.List<?>) slots.get(blockEntity)) {
            ((cofh.lib.common.inventory.ItemStorageCoFH) slot).setItemStack(ItemStack.EMPTY);
        }
        var refresh = nativeType.getDeclaredMethod("updateAugmentState");
        refresh.setAccessible(true);
        refresh.invoke(blockEntity);
        helper.assertTrue(((ThermalParallelAccess) blockEntity).useless_mod$getParallel() == 0,
                "Removing the augment must clear parallel operations");
        helper.succeed();
    }

    @GameTest(template = "furnace_empty")
    public static void portableFurnaceRebindsAfterForgeCreatesTemporaryInventory(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(local);
        var state = ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get().defaultBlockState();
        helper.setBlock(local, state);
        var original = (AdvancedAlloyFurnaceBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(original.tryUpgrade(10), "Useful tier must remain available");
        original.getItemHandler().setStackInSlot(0, new ItemStack(Items.DIAMOND, Integer.MAX_VALUE));
        original.setEnergy(1234567890123L);
        original.getInputFluidTank(0).setFluid(new FluidStack(Fluids.WATER, 12345));
        original.setFaceMode(FurnaceFace.TOP, FurnaceFaceMode.DISABLED);
        var drops = Block.getDrops(state, helper.getLevel(), pos, original, null, ItemStack.EMPTY);
        helper.assertTrue(drops.size() == 1, "Exactly one portable furnace must drop");
        helper.setBlock(local, Blocks.AIR);
        helper.setBlock(local, state);
        var restored = (AdvancedAlloyFurnaceBlockEntity) helper.getLevel().getBlockEntity(pos);
        restored.getExternalInventoryReference();
        state.getBlock().setPlacedBy(helper.getLevel(), pos, state, null, drops.get(0));
        helper.assertTrue(restored.getItemHandler().getStackInSlot(0).getCount() == Integer.MAX_VALUE
                        && restored.getEnergy() == 1234567890123L
                        && restored.getInputFluidTank(0).getFluidAmount() == 12345
                        && restored.getFurnaceTier() == 10
                        && restored.getFaceMode(FurnaceFace.TOP) == FurnaceFaceMode.DISABLED,
                "Portable furnace must recover its original inventory, energy, fluid, tier and side configuration");
        helper.succeed();
    }

    @GameTest(template = "furnace_empty")
    public static void portableAssemblyRecoversOriginalPatternReference(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(local);
        var state = ModBlocks.ME_PATTERN_ASSEMBLY.get().defaultBlockState();
        helper.setBlock(local, state);
        var original = (MePatternAssemblyBlockEntity) helper.getLevel().getBlockEntity(pos);
        var pattern = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.RAW_IRON), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)});
        original.getPatterns().setStackInSlot(0, pattern);
        var drops = Block.getDrops(state, helper.getLevel(), pos, original, null, ItemStack.EMPTY);
        helper.assertTrue(drops.size() == 1, "Exactly one portable assembly must drop");
        helper.setBlock(local, Blocks.AIR);
        helper.setBlock(local, state);
        var restored = (MePatternAssemblyBlockEntity) helper.getLevel().getBlockEntity(pos);
        restored.getExternalInventoryReference();
        state.getBlock().setPlacedBy(helper.getLevel(), pos, state, null, drops.get(0));
        helper.assertTrue(ItemStack.matches(pattern, restored.getPatterns().getStackInSlot(0)),
                "Rebinding must replace the temporary inventory with the portable pattern inventory");
        helper.succeed();
    }

    @GameTest(template = "furnace_empty", timeoutTicks = 300)
    public static void furnaceRunsNativeSmeltingRecipe(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        helper.setBlock(local, ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());
        var furnace = (AdvancedAlloyFurnaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(local));
        furnace.getItemHandler().setStackInSlot(0, new ItemStack(Items.RAW_IRON));
        furnace.getItemHandler().setStackInSlot(AdvancedAlloyFurnaceLayout.MOLD_SLOT, new ItemStack(Items.FURNACE));
        furnace.setEnergy(100000L);
        helper.succeedWhen(() -> {
            boolean output = false;
            for (int slot = 9; slot < 18; slot++) output |= furnace.getItemHandler().getStackInSlot(slot).is(Items.IRON_INGOT);
            helper.assertTrue(output && furnace.getItemHandler().getStackInSlot(0).isEmpty(),
                    "Registered furnace must consume raw iron and produce an iron ingot");
        });
    }

    @GameTest(template = "furnace_empty")
    public static void extraSourcesContainRealRecipesAfterNativeDataLoad(GameTestHelper helper) {
        AlloyFurnaceRecipeCatalog.prewarm(helper.getLevel());
        int adapters = 0;
        for (var adapter : com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager.getInstance().getRegisteredAdapters()) {
            if (adapter instanceof com.sorrowmist.useless.content.recipe.adapters.BatchRecipeAdapter<?>) {
                helper.assertTrue(!adapter.getMoldItem().isEmpty(), "Every restored adapter needs its native mold: " + adapter.getClass());
                adapters++;
            }
        }
        helper.assertTrue(adapters == 11, "All eleven extra machine adapters must be registered");
        for (String source : List.of("thermal", "bloodmagic", "embers")) {
            helper.assertTrue(!AlloyFurnaceRecipeCatalog.entries(helper.getLevel(), source).isEmpty(),
                    "Native data load must populate the " + source + " recipe source");
        }
        helper.succeed();
    }

    @GameTest(template = "furnace_empty")
    public static void retainedSeedAndWaterUseTheSameAtomicInputPort(GameTestHelper helper) {
        var recipe = new AdvancedAlloyFurnaceRecipe(UselessMod.id("test_retained_seed"),
                List.of(new CountedIngredient(Ingredient.of(Items.WHEAT_SEEDS), 1L, false)),
                List.of(new FluidStack(Fluids.WATER, 500)), List.of(new ItemStack(Items.WHEAT)),
                List.of(), 100L, 20, Ingredient.EMPTY, 0, Ingredient.EMPTY, AlloyFurnaceMode.INSOLATOR);
        var inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS));
        var water = new FluidTank(1000);
        water.setFluid(new FluidStack(Fluids.WATER, 1000));
        FluidTank[] tanks = {water};
        helper.assertTrue(FurnaceInputPort.calculateMaterialParallel(recipe, inventory, 0, 1, tanks, 1) == 2,
                "Retained seeds must not cap parallelism, but water must");
        helper.assertTrue(FurnaceInputPort.consumeRecipeInputs(recipe, 2, inventory, 0, 1, tanks, 1)
                        && inventory.getStackInSlot(0).getCount() == 1 && water.getFluidAmount() == 0,
                "Two operations must drain exactly 1000 mB while retaining the seed");
        helper.assertTrue(!FurnaceInputPort.consumeRecipeInputs(recipe, 1, inventory, 0, 1, tanks, 1)
                        && inventory.getStackInSlot(0).getCount() == 1,
                "An unavailable fluid must fail atomically without consuming the retained input");
        helper.succeed();
    }
}
