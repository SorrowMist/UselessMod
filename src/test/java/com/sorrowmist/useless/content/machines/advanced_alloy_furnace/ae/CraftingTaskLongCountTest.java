package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
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
                (proxy, method, arguments) -> null);
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
}
