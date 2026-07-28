package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.PatternDetailsHelper;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PendingAEBatchPersistenceTest {
    private static HolderLookup.Provider registries;
    private static Level level;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        level = (Level) ((Unsafe) field.get(null)).allocateInstance(ServerLevel.class);
    }

    @Test
    void roundTripsOperationsPerPush() {
        var pattern = PatternDetailsHelper.decodePattern(encodedPattern(), level);
        assertNotNull(pattern);
        var batch = new AdvancedAlloyFurnaceAeManager.PendingAEBatch(pattern, 17L);
        KeyCounter input = new KeyCounter();
        input.add(Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT)), 17L);
        batch.add(new KeyCounter[]{input});

        CompoundTag saved = batch.save(registries);
        assertNotNull(saved);
        assertEquals(Tag.TAG_LONG, saved.get("OperationsPerPush").getId());

        var restored = AdvancedAlloyFurnaceAeManager.PendingAEBatch.load(saved, level, registries);
        assertNotNull(restored);
        assertEquals(17L, restored.operationsPerPush);
        assertEquals(1, restored.allInputs.size());
    }

    @Test
    void legacyBatchDefaultsToOneOperationPerPush() {
        var pattern = PatternDetailsHelper.decodePattern(encodedPattern(), level);
        assertNotNull(pattern);
        var batch = new AdvancedAlloyFurnaceAeManager.PendingAEBatch(pattern, 9L);
        batch.add(new KeyCounter[0]);
        CompoundTag saved = batch.save(registries);
        assertNotNull(saved);
        saved.remove("OperationsPerPush");

        var restored = AdvancedAlloyFurnaceAeManager.PendingAEBatch.load(saved, level, registries);
        assertNotNull(restored);
        assertEquals(1L, restored.operationsPerPush);
    }

    @Test
    void scaledPatternDefinitionRestoresThroughAeDecoder() {
        var original = PatternDetailsHelper.decodePattern(encodedPattern(), level);
        assertNotNull(original);
        var scaled = SmartDoublingPatterns.scale(original, 17L);

        AEItemKey persistedDefinition = AEItemKey.fromTag(
                registries, scaled.getDefinition().toTag(registries));
        assertNotNull(persistedDefinition);

        var restored = assertInstanceOf(
                ScaledProcessingPattern.class,
                PatternDetailsHelper.decodePattern(persistedDefinition, level));
        assertEquals(17L, restored.getOperationsPerPush());
        assertEquals(original.getDefinition(), restored.getOriginal().getDefinition());
        assertEquals(17L, restored.getInputs()[0].getMultiplier());
        assertEquals(17L, restored.getOutputs().getFirst().amount());
    }

    private static ItemStack encodedPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(
                        Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT)), 1L)),
                List.of(new GenericStack(
                        Objects.requireNonNull(AEItemKey.of(Items.GOLD_INGOT)), 1L)));
    }
}
