package com.sorrowmist.useless.world.inventory;

import com.mojang.datafixers.DataFixer;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.content.blockentities.HighStackItemStackHandler;
import com.sorrowmist.useless.core.component.ExternalInventoryKind;
import com.sorrowmist.useless.core.component.ExternalInventoryReference;
import com.sorrowmist.useless.world.ae.AeConnectLinkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real native SavedData disk writes/reopens in a disposable directory, never a pack world. */
class NativeSavedDataPortTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }

    @Test void nativeSavedDataCallbackWritesAndReloadsLargeInventoryAndOpaquePayload() throws Exception {
        var reference = new ExternalInventoryReference(ExternalInventoryKind.FURNACE, UUID.randomUUID());
        var constructor = ExternalInventorySavedData.class.getDeclaredConstructor(ExternalInventoryReference.class);
        constructor.setAccessible(true);
        var data = constructor.newInstance(reference);
        var handler = inventory();
        var stack = new ItemStack(Items.IRON_INGOT, 2_000_000_000);
        stack.getOrCreateTag().putString("othermod", "preserved");
        handler.setStackInSlot(399, stack);
        data.saveFrom(handler, RegistryAccess.EMPTY);
        var payload = new CompoundTag(); payload.putLong("huge", Long.MAX_VALUE);
        data.saveRawPayload(payload);
        var storage = storage(); storage.set("native_inventory", data); storage.save();
        assertTrue(Files.size(directory.resolve("native_inventory.dat")) > 0);

        var load = ExternalInventorySavedData.class.getDeclaredMethod("load", ExternalInventoryReference.class, CompoundTag.class);
        load.setAccessible(true);
        var reloaded = storage().get(tag -> {
            try { return (ExternalInventorySavedData)load.invoke(null, reference, tag); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }, "native_inventory");
        assertNotNull(reloaded); assertTrue(reloaded.isInitialized());
        var destination = inventory();
        reloaded.loadInto(destination, RegistryAccess.EMPTY);
        assertEquals(2_000_000_000, destination.getStackInSlot(399).getCount());
        assertEquals("preserved", destination.getStackInSlot(399).getTag().getString("othermod"));
        assertEquals(Long.MAX_VALUE, reloaded.rawPayload().getLong("huge"));
        reloaded.rawPayload().putLong("huge", 0);
        assertEquals(Long.MAX_VALUE, reloaded.rawPayload().getLong("huge"));
    }

    @Test void nativeAeLinkSavedDataKeepsBothDimensionsAcrossDiskReload() throws Exception {
        var data = new AeConnectLinkSavedData();
        var link = new AeConnectLinkSavedData.Link(Level.NETHER, Level.OVERWORLD,
                new BlockPos(1, 64, 2), new BlockPos(1, 64, 2));
        data.add(link);
        var storage = storage(); storage.set("native_links", data); storage.save();
        var load = AeConnectLinkSavedData.class.getDeclaredMethod("load", CompoundTag.class);
        load.setAccessible(true);
        var restored = storage().get(tag -> {
            try { return (AeConnectLinkSavedData)load.invoke(null, tag); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }, "native_links");
        assertNotNull(restored); assertEquals(java.util.List.of(link), restored.snapshot());
        assertTrue(restored.machinesOf(Level.OVERWORLD, Level.NETHER, link.accessPoint()).isEmpty());
        assertEquals(java.util.List.of(link.machine()), restored.machinesOf(Level.NETHER, Level.OVERWORLD, link.accessPoint()));
    }

    private DimensionDataStorage storage() {
        DataFixer fixer = mock(DataFixer.class);
        when(fixer.update(any(), any(), anyInt(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        return new DimensionDataStorage(directory.toFile(), fixer);
    }
    private static HighStackItemStackHandler inventory() {
        return new HighStackItemStackHandler(400, 0, 1, 2, 398, 400, 400,
                () -> {}, slot -> {}, slot -> {}, slot -> {});
    }
}
