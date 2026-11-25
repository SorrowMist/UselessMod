package com.sorrowmist.useless.compat.neoecoae.compact.exposed;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.grid.pattern.PatternCatalog;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.neoecoae.compact.cluster.CompactCraftingCluster;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExposedPatternBusBlockEntityTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void domainMembershipKeepsDispatchOnTheHost() throws ReflectiveOperationException {
        var bus = createBus(mock(IManagedGridNode.class, RETURNS_SELF));
        var owner = new CompactCraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
        bus.joinPatternDomain(owner);

        assertSame(owner, bus.getCluster());
        assertTrue(owner.getPatternBuses().contains(bus));
        try {
            var domainBuses = owner.getClass().getMethod("getPatternDomainBuses");
            assertTrue(((List<?>) domainBuses.invoke(owner)).contains(bus));
        } catch (NoSuchMethodException olderEco) {
            // The declared ECO dependency predates domain filtering; also validate with newer ECO.
        }
        assertTrue(bus.isBusy());
        assertTrue(bus.getAvailablePatterns().isEmpty());
    }

    @Test
    void shiftingChildOffsetsInvalidatesTheWholeInterfaceEvenAtTheSameTotalSize() {
        var node = mock(IManagedGridNode.class, RETURNS_SELF);
        var gridNode = mock(IGridNode.class);
        var grid = mock(IGrid.class);
        when(node.getNode()).thenReturn(gridNode);
        when(node.getGrid()).thenReturn(grid);
        when(gridNode.getGrid()).thenReturn(grid);
        var catalog = mock(IECOPatternStorageService.class);
        when(grid.getService(IECOPatternStorageService.class)).thenReturn(catalog);
        var bus = createBus(node);
        var machineInterface = shadowInterface(grid, bus);
        var first = mock(ECOCraftingPatternBusBlockEntity.class);
        var second = mock(ECOCraftingPatternBusBlockEntity.class);
        when(first.getPatternSlotCount()).thenReturn(2);
        when(second.getPatternSlotCount()).thenReturn(3);
        bus.bind(List.of(first, second), node);
        assertEquals(5, bus.getPatternSlotCount());
        int previousRevision = bus.getPatternContentRevision();

        when(first.getPatternSlotCount()).thenReturn(3);
        when(second.getPatternSlotCount()).thenReturn(2);
        try (var providers = mockStatic(ICraftingProvider.class)) {
            bus.tick();
            assertEquals(5, bus.getPatternSlotCount());
            var order = inOrder(catalog, machineInterface);
            order.verify(catalog).onPatternSlotsChanged(bus, previousRevision, new int[] { -1 });
            order.verify(machineInterface).onPatternBusInventoryChanged(bus);
            verify(machineInterface, never()).onPatternBusInventoryChanged(eq(bus), any(int[].class));
            providers.verify(() -> ICraftingProvider.requestUpdate(node));
            clearInvocations(catalog, machineInterface);
            bus.tick();
            verifyNoInteractions(catalog, machineInterface);
        }
    }

    @Test
    void changingMultipleChildrenUpdatesCatalogBeforePreview() {
        var node = mock(IManagedGridNode.class, RETURNS_SELF);
        var gridNode = mock(IGridNode.class);
        var grid = mock(IGrid.class);
        when(node.getNode()).thenReturn(gridNode);
        when(node.getGrid()).thenReturn(grid);
        when(gridNode.getGrid()).thenReturn(grid);
        var firstInventory = emptyInventory();
        var secondInventory = emptyInventory();
        var first = childBus(firstInventory);
        var second = childBus(secondInventory);
        when(firstInventory.getStackInSlot(0)).thenReturn(new ItemStack(Items.STONE));
        var bus = createBus(node);
        var machineInterface = shadowInterface(grid, bus);
        bus.bind(List.of(first, second), node);
        var catalog = spy(new PatternCatalog());
        when(gridNode.getService(IECOPatternStorage.class)).thenReturn(bus);
        when(grid.getService(IECOPatternStorageService.class)).thenReturn(catalog);
        catalog.addNode(gridNode, null);
        assertArrayEquals(new int[] { 0 }, catalog.occupiedPatternSlots(bus).stream().toArray());
        int previousRevision = bus.getPatternContentRevision();

        when(firstInventory.getStackInSlot(0)).thenReturn(ItemStack.EMPTY);
        when(firstInventory.getStackInSlot(1)).thenReturn(new ItemStack(Items.DIRT));
        when(secondInventory.getStackInSlot(1)).thenReturn(new ItemStack(Items.COBBLESTONE));
        when(first.getPatternContentRevision()).thenReturn(1);
        when(second.getPatternContentRevision()).thenReturn(1);
        try (var providers = mockStatic(ICraftingProvider.class)) {
            bus.tick();
            providers.verify(() -> ICraftingProvider.requestUpdate(node));
        }

        assertArrayEquals(new int[] { 1, 3 }, catalog.occupiedPatternSlots(bus).stream().toArray());
        var order = inOrder(catalog, machineInterface);
        int[] changedSlots = { 0, 1, 2, 3 };
        order.verify(catalog).onPatternSlotsChanged(bus, previousRevision, changedSlots);
        order.verify(machineInterface).onPatternBusInventoryChanged(bus, changedSlots);
    }

    @Test
    void catalogRefreshDetectsChangesWithoutAnAddonCallback() {
        var inventory = emptyInventory();
        var child = childBus(inventory);
        var node = mock(IManagedGridNode.class, RETURNS_SELF);
        var bus = createBus(node);
        bus.bind(List.of(child), node);
        var gridNode = mock(IGridNode.class);
        when(gridNode.getService(IECOPatternStorage.class)).thenReturn(bus);
        var catalog = new PatternCatalog();
        catalog.addNode(gridNode, null);

        when(inventory.getStackInSlot(1)).thenReturn(new ItemStack(Items.STONE));
        when(child.getPatternContentRevision()).thenReturn(1);
        catalog.refresh();

        assertArrayEquals(new int[] { 1 }, catalog.occupiedPatternSlots(bus).stream().toArray());
    }

    private static ShadowInterfaceBlockEntity<?> shadowInterface(IGrid grid, ExposedPatternBusBlockEntity bus) {
        var machineInterface = mock(ShadowInterfaceBlockEntity.class);
        when(grid.getMachineClasses()).thenReturn(List.of(ShadowInterfaceBlockEntity.class));
        when(grid.getMachines(ShadowInterfaceBlockEntity.class)).thenReturn(Set.of(machineInterface));
        when(machineInterface.mayMapPatternBus(bus)).thenReturn(true);
        return machineInterface;
    }

    private static InternalInventory emptyInventory() {
        var inventory = mock(InternalInventory.class);
        when(inventory.getStackInSlot(anyInt())).thenReturn(ItemStack.EMPTY);
        return inventory;
    }

    private static ECOCraftingPatternBusBlockEntity childBus(InternalInventory inventory) {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        when(bus.getPatternSlotCount()).thenReturn(2);
        when(bus.getPatternSlotInventory()).thenReturn(inventory);
        return bus;
    }

    private static ExposedPatternBusBlockEntity createBus(IManagedGridNode node) {
        try (var gridHelper = mockStatic(GridHelper.class); var config = mockStatic(NEConfig.class)) {
            gridHelper.when(() -> GridHelper.createManagedNode(any(), any())).thenReturn(node);
            config.when(NEConfig::getMaxCraftingPatternBusSlotCount).thenReturn(63);
            config.when(NEConfig::getCraftingPatternBusPages).thenReturn(1);
            var type = mock(BlockEntityType.class);
            when(type.isValid(any())).thenReturn(true);
            return new ExposedPatternBusBlockEntity(type, BlockPos.ZERO,
                    Blocks.AIR.defaultBlockState());
        }
    }
}
