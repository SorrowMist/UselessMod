package com.sorrowmist.useless.compat.neoecoae.compact.exposed;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.neoecoae.compact.cluster.CompactCraftingCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    void shiftingChildOffsetsInvalidatesTheWholeInterfaceEvenAtTheSameTotalSize() {
        var node = mock(IManagedGridNode.class, RETURNS_SELF);
        var gridNode = mock(IGridNode.class);
        var grid = mock(IGrid.class);
        when(node.getNode()).thenReturn(gridNode);
        when(node.getGrid()).thenReturn(grid);
        when(gridNode.getGrid()).thenReturn(grid);
        var machineInterface = mock(ECOMachineInterfaceBlockEntity.class);
        when(grid.getActiveMachines(ECOMachineInterfaceBlockEntity.class)).thenReturn(java.util.Set.of(machineInterface));
        var bus = createBus(node);
        var first = mock(ECOCraftingPatternBusBlockEntity.class);
        var second = mock(ECOCraftingPatternBusBlockEntity.class);
        when(first.getPatternSlotCount()).thenReturn(2);
        when(second.getPatternSlotCount()).thenReturn(3);
        bus.bind(List.of(first, second), node);
        assertEquals(5, bus.getPatternSlotCount());

        when(first.getPatternSlotCount()).thenReturn(3);
        when(second.getPatternSlotCount()).thenReturn(2);
        try (var providers = mockStatic(ICraftingProvider.class)) {
            bus.tick();
            assertEquals(5, bus.getPatternSlotCount());
            verify(machineInterface).onPatternBusInventoryChanged(bus);
            verify(machineInterface, never()).onPatternBusInventoryChanged(eq(bus), any(int[].class));
            providers.verify(() -> ICraftingProvider.requestUpdate(node));
            clearInvocations(machineInterface);
            bus.tick();
            verifyNoInteractions(machineInterface);
        }
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
