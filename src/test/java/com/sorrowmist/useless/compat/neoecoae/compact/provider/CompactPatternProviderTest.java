package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider.Preparation;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class CompactPatternProviderTest {
    @Test
    void fastPathPreparationOnlyVisitsTheBusThatAdvertisesThePattern() {
        var unrelatedPattern = mock(IPatternDetails.class);
        var requestedPattern = mock(IPatternDetails.class);
        var unrelatedBus = mock(ECOCraftingPatternBusBlockEntity.class);
        var ownerBus = mock(ECOCraftingPatternBusBlockEntity.class);
        when(unrelatedBus.getAvailablePatterns()).thenReturn(List.of(unrelatedPattern));
        when(ownerBus.getAvailablePatterns()).thenReturn(List.of(requestedPattern));

        var provider = new CompactPatternProvider();
        provider.bind(List.of(unrelatedBus, ownerBus));
        provider.getAvailablePatterns();

        var context = new ECOBatchDispatchContext(
                requestedPattern, List.of(), List.of(), List.of(), mock(Level.class), null);
        var preparation = new Preparation(1, null, false, batch -> true);
        when(ownerBus.eco$prepareFastPath(context)).thenReturn(preparation);

        assertSame(preparation, provider.eco$prepareFastPath(context));
        verify(ownerBus).eco$prepareFastPath(context);
        verify(unrelatedBus, never()).eco$prepareFastPath(any());
    }
}
