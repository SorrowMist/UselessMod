package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingTaskLongCountTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void keepsScaledCraftCountsAboveTheIntegerRange() {
        long multiplier = (long) Integer.MAX_VALUE + 125L;

        assertEquals(multiplier * 3L,
                AdvancedAlloyFurnaceAeManager.calculateTotalCrafts(3L, multiplier));
        assertEquals(Long.MAX_VALUE,
                AdvancedAlloyFurnaceAeManager.calculateTotalCrafts(Long.MAX_VALUE, 2L));
        assertEquals(Long.MAX_VALUE,
                CraftingTask.saturatingAdd(Long.MAX_VALUE - 3L, 4L));
    }

    @Test
    void savesCraftCountAsLongAndKeepsLegacyNumericTagsReadable() {
        long craftCount = (long) Integer.MAX_VALUE + 42L;
        CraftingTask task = new CraftingTask(
                7, pattern(), new KeyCounter[]{new KeyCounter()}, craftCount, null);

        CompoundTag saved = task.save(registries);

        assertEquals(craftCount, saved.getLong("CraftCount"));
        assertNotNull(saved.get("CraftCount"));
        assertEquals(Tag.TAG_LONG, saved.get("CraftCount").getId());

        CompoundTag legacy = new CompoundTag();
        legacy.putInt("CraftCount", 123);
        assertEquals(123L, legacy.getLong("CraftCount"));
    }

    @Test
    void longAeContextsKeepLongMaxInputsAsOneGenericStack() {
        AEItemKey paper = AEItemKey.of(new ItemStack(Items.PAPER));
        KeyCounter input = new KeyCounter();
        input.add(paper, Long.MAX_VALUE);
        CraftingTaskContext context = (CraftingTaskContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CraftingTaskContext.class},
                (proxy, method, arguments) -> method.getName().equals("supportsLongAeAmounts"));
        CraftingTask task = new CraftingTask(
                9, pattern(), new KeyCounter[]{input}, Long.MAX_VALUE, context);

        CompoundTag saved = task.save(registries);
        var inputs = saved.getList("Inputs", Tag.TAG_COMPOUND);
        GenericStack stored = GenericStack.readTag(registries, inputs.getCompound(0));

        assertEquals(1, inputs.size());
        assertNotNull(stored);
        assertEquals(paper, stored.what());
        assertEquals(Long.MAX_VALUE, stored.amount());
    }

    @Test
    void resolvesLongMaxInputsWithoutMaterializingIntegerChunks() throws ReflectiveOperationException {
        AEItemKey iron = Objects.requireNonNull(AEItemKey.of(new ItemStack(Items.IRON_INGOT)));
        AEFluidKey water = Objects.requireNonNull(AEFluidKey.of(new FluidStack(Fluids.WATER, 1)));
        KeyCounter input = new KeyCounter();
        input.add(iron, Long.MAX_VALUE);
        input.add(water, Long.MAX_VALUE);
        CraftingTaskContext context = (CraftingTaskContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CraftingTaskContext.class},
                (proxy, method, arguments) -> method.getName().equals("supportsLongAeAmounts"));
        CraftingTask task = new CraftingTask(
                10, pattern(), new KeyCounter[]{input}, Long.MAX_VALUE, context);
        AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "long_ae_inputs"),
                List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1L)),
                List.of(new FluidStack(Fluids.WATER, 1)),
                List.of(),
                List.of(new ItemStack(Items.PAPER)),
                List.of(),
                List.of(),
                2_000L,
                200,
                Ingredient.EMPTY,
                0,
                Ingredient.EMPTY,
                AlloyFurnaceMode.NORMAL);

        var resolver = CraftingTask.class.getDeclaredMethod(
                "resolvePatternOperations", AdvancedAlloyFurnaceRecipe.class);
        resolver.setAccessible(true);

        assertTrue((boolean) resolver.invoke(task, recipe));
    }

    @Test
    void splitsTenToOneLongPushesBeforeTheySaturate() {
        long operationsPerPush = Long.MAX_VALUE / 10L;
        AEItemKey iron = Objects.requireNonNull(AEItemKey.of(new ItemStack(Items.IRON_INGOT)));
        KeyCounter[] push = new KeyCounter[]{counter(iron, operationsPerPush * 10L)};

        List<List<KeyCounter[]>> batches = AdvancedAlloyFurnaceAeManager.splitInputBatches(
                List.of(push, push), operationsPerPush, operationsPerPush);

        assertEquals(2, batches.size());
        assertEquals(1, batches.getFirst().size());
        assertEquals(1, batches.getLast().size());
    }

    @Test
    void keepsExtremeMergedPushesAsSeparateSubTasks() {
        long operationsPerPush = Long.MAX_VALUE / 10L;
        AEItemKey iron = Objects.requireNonNull(AEItemKey.of(new ItemStack(Items.IRON_INGOT)));
        KeyCounter[] push = new KeyCounter[]{counter(iron, operationsPerPush * 10L)};
        CraftingTaskContext context = (CraftingTaskContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CraftingTaskContext.class},
                (proxy, method, arguments) -> method.getName().equals("supportsLongAeAmounts"));
        CraftingTask task = new CraftingTask(
                11, tenToOnePattern(), new KeyCounter[0], 1L, context);
        List<KeyCounter[]> pushes = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            pushes.add(push);
        }

        assertTrue(task.addMergedBatch(pushes, operationsPerPush));

        CompoundTag saved = task.save(registries);
        var subTasks = saved.getList("SubTasks", Tag.TAG_COMPOUND);
        assertEquals(11, subTasks.size());
        for (int index = 0; index < subTasks.size(); index++) {
            assertEquals(operationsPerPush, subTasks.getCompound(index).getLong("CraftCount"));
        }
    }

    @Test
    void progressTotalsRemainExactAboveTheIntegerRange() {
        long craftCount = (long) Integer.MAX_VALUE + 9L;
        long totalOutput = craftCount * 7L;
        var progress = new AdvancedAlloyFurnaceAeManager.AETaskProgress(
                "structure", 200, craftCount, totalOutput);

        assertEquals(craftCount, progress.getCraftCount());
        assertEquals(totalOutput, progress.getTotalOutputCount());

        progress.updateCraftCount(craftCount + 1L);
        assertEquals((craftCount + 1L) * 7L, progress.getTotalOutputCount());
    }

    @Test
    void mergedBatchesAddTheirRealOperationCounts() {
        CraftingTaskContext context = (CraftingTaskContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CraftingTaskContext.class},
                (proxy, method, arguments) -> method.getReturnType() == boolean.class ? false : null);
        CraftingTask task = new CraftingTask(
                8, pattern(), new KeyCounter[0], 2L, context);

        assertTrue(task.addMergedBatch(List.<KeyCounter[]>of(new KeyCounter[0]), 3L));
        assertTrue(task.addMergedBatch(
                List.<KeyCounter[]>of(new KeyCounter[0], new KeyCounter[0]), 4L));

        CompoundTag saved = task.save(registries);
        CompoundTag merged = saved.getList("SubTasks", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(11L, merged.getLong("CraftCount"));
    }

    private static IPatternDetails pattern() {
        AEItemKey paper = AEItemKey.of(new ItemStack(Items.PAPER));
        return new IPatternDetails() {
            @Override
            public AEItemKey getDefinition() {
                return paper;
            }

            @Override
            public IInput[] getInputs() {
                return new IInput[0];
            }

            @Override
            public List<GenericStack> getOutputs() {
                return List.of(new GenericStack(paper, 1L));
            }
        };
    }

    private static KeyCounter counter(AEKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return counter;
    }

    private static IPatternDetails tenToOnePattern() {
        AEItemKey iron = Objects.requireNonNull(AEItemKey.of(new ItemStack(Items.IRON_INGOT)));
        AEItemKey paper = Objects.requireNonNull(AEItemKey.of(new ItemStack(Items.PAPER)));
        return new IPatternDetails() {
            @Override
            public AEItemKey getDefinition() {
                return paper;
            }

            @Override
            public IInput[] getInputs() {
                return new IInput[]{new IInput() {
                    @Override
                    public GenericStack[] getPossibleInputs() {
                        return new GenericStack[]{new GenericStack(iron, 1L)};
                    }

                    @Override
                    public long getMultiplier() {
                        return 10L;
                    }

                    @Override
                    public boolean isValid(AEKey input, Level level) {
                        return iron.equals(input);
                    }

                    @Override
                    public AEKey getRemainingKey(AEKey template) {
                        return null;
                    }
                }};
            }

            @Override
            public List<GenericStack> getOutputs() {
                return List.of(new GenericStack(paper, 1L));
            }
        };
    }
}
