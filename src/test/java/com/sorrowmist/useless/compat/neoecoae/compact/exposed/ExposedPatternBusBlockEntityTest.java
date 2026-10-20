package com.sorrowmist.useless.compat.neoecoae.compact.exposed;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingService;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.neoecoae.compact.provider.CombinedPatternInventory;
import com.sorrowmist.useless.compat.neoecoae.compact.cluster.CompactCraftingCluster;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
    void advertisedPatternChangesRequestAHostProviderRefresh() {
        var node = mock(IManagedGridNode.class);
        var gridNode = mock(IGridNode.class);
        var grid = mock(IGrid.class);
        var service = mock(ICraftingService.class);
        when(node.getGrid()).thenReturn(grid);
        when(node.getNode()).thenReturn(gridNode);
        when(gridNode.getGrid()).thenReturn(grid);
        when(grid.getCraftingService()).thenReturn(service);
        var bus = createBus(node);

        bus.refreshAdvertisedPatterns();

        verify(service).refreshNodeCraftingProvider(gridNode);
    }

    private static ExposedPatternBusBlockEntity createBus(IManagedGridNode node) {
        var bus = mock(ExposedPatternBusBlockEntity.class, CALLS_REAL_METHODS);
        setField(bus, "mergedPatternInventory", new CombinedPatternInventory());
        setField(bus, "buses", new ArrayList<>());
        bus.bind(List.of(), node);
        return bus;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = ExposedPatternBusBlockEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
