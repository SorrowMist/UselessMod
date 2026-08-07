package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmniversalPatternDetailsTest {
    private static final int MAX_CACHE_ENTRIES = 1024;

    private static Unsafe unsafe;
    private Level level;
    private Map<Level, Object> decodeCaches;
    private Map<AEItemKey, Object> entries;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
    }

    @BeforeEach
    void setUpCache() throws ReflectiveOperationException {
        decodeCaches = decodeCaches();
        decodeCaches.clear();
        level = (Level) unsafe.allocateInstance(ServerLevel.class);

        Object cache = newLevelCache();
        invokePrepare(cache, AlloyFurnaceRecipeCatalog.generation());
        decodeCaches.put(level, cache);
        entries = cacheEntries(cache);
    }

    @AfterEach
    void clearCache() {
        if (decodeCaches != null) {
            decodeCaches.clear();
        }
    }

    @Test
    void repeatedDecodeReturnsTheStronglyCachedDetails() throws Exception {
        AEItemKey definition = key("repeated");
        OmniversalPatternDetails expected = fakeDetails();
        Object cached = success(expected);
        entries.put(definition, cached);

        assertEquals(OmniversalPatternDetails.class, cachedDetailsField().getType());
        assertSame(expected, cachedDetailsField().get(cached));
        assertSame(expected, OmniversalPatternDetails.decode(definition, level));
        assertSame(expected, OmniversalPatternDetails.decode(definition, level));
    }

    @Test
    void catalogGenerationInvalidatesAStaleSuccessfulEntry() throws Exception {
        AEItemKey definition = key("generation");
        entries.put(definition, success(fakeDetails()));

        assertNotNull(OmniversalPatternDetails.decode(definition, level));
        AlloyFurnaceRecipeCatalog.invalidate();

        RuntimeException first = assertThrows(RuntimeException.class,
                () -> OmniversalPatternDetails.decode(definition, level));
        assertTrue(first.getMessage().contains("Missing or unsupported omniversal pattern data"));
        assertTrue(entries.containsKey(definition));
        assertThrows(RuntimeException.class, () -> OmniversalPatternDetails.decode(definition, level));
        assertEquals(1, entries.size());
    }

    @Test
    void failedDecodeIsCachedAndRethrowsTheSameException() {
        AEItemKey invalidDefinition = key("invalid");

        RuntimeException first = assertThrows(RuntimeException.class,
                () -> OmniversalPatternDetails.decode(invalidDefinition, level));
        RuntimeException second = assertThrows(RuntimeException.class,
                () -> OmniversalPatternDetails.decode(invalidDefinition, level));

        assertSame(first, second);
        assertEquals(1, entries.size());
    }

    @Test
    void cacheEvictsLeastRecentlyUsedEntriesAtTheConfiguredLimit() throws Exception {
        AEItemKey firstDefinition = key("lru-0");
        AEItemKey secondDefinition = key("lru-1");
        entries.put(firstDefinition, success(fakeDetails()));
        entries.put(secondDefinition, success(fakeDetails()));
        for (int index = 2; index < MAX_CACHE_ENTRIES; index++) {
            entries.put(key("lru-" + index), success(fakeDetails()));
        }

        assertEquals(MAX_CACHE_ENTRIES, entries.size());
        assertNotNull(entries.get(firstDefinition));
        entries.put(key("lru-last"), success(fakeDetails()));

        assertEquals(MAX_CACHE_ENTRIES, entries.size());
        assertTrue(entries.containsKey(firstDefinition));
        assertFalse(entries.containsKey(secondDefinition));
    }

    @SuppressWarnings("unchecked")
    private static Map<Level, Object> decodeCaches() throws ReflectiveOperationException {
        Field field = OmniversalPatternDetails.class.getDeclaredField("DECODE_CACHES");
        field.setAccessible(true);
        return (Map<Level, Object>) field.get(null);
    }

    private static Object newLevelCache() throws ReflectiveOperationException {
        Class<?> cacheType = Class.forName(
                OmniversalPatternDetails.class.getName() + "$LevelDecodeCache");
        Constructor<?> constructor = cacheType.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void invokePrepare(Object cache, long generation)
            throws ReflectiveOperationException {
        Method method = cache.getClass().getDeclaredMethod("prepare", long.class);
        method.setAccessible(true);
        method.invoke(cache, generation);
    }

    @SuppressWarnings("unchecked")
    private static Map<AEItemKey, Object> cacheEntries(Object cache)
            throws ReflectiveOperationException {
        Field field = cache.getClass().getDeclaredField("entries");
        field.setAccessible(true);
        return (Map<AEItemKey, Object>) field.get(cache);
    }

    private static Object success(OmniversalPatternDetails details)
            throws ReflectiveOperationException {
        Class<?> cachedType = Class.forName(
                OmniversalPatternDetails.class.getName() + "$CachedDecode");
        Method method = cachedType.getDeclaredMethod("success", OmniversalPatternDetails.class);
        method.setAccessible(true);
        return method.invoke(null, details);
    }

    private static Field cachedDetailsField() throws ReflectiveOperationException {
        Class<?> cachedType = Class.forName(
                OmniversalPatternDetails.class.getName() + "$CachedDecode");
        Field field = cachedType.getDeclaredField("details");
        field.setAccessible(true);
        return field;
    }

    private static OmniversalPatternDetails fakeDetails() {
        try {
            return (OmniversalPatternDetails) unsafe.allocateInstance(OmniversalPatternDetails.class);
        } catch (InstantiationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static AEItemKey key(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return Objects.requireNonNull(AEItemKey.of(stack));
    }
}
