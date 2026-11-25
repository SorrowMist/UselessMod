package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider.ExactPreparation;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider.Preparation;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class CompactPatternProviderTest {
    @Test
    void ordinaryFallbackForwardsTheJobIdThroughRejectionAndStopsAtAcceptance() {
        var pattern = mock(IPatternDetails.class);
        var inputs = new KeyCounter[0];
        var jobId = UUID.randomUUID();
        var rejectingBus = mock(ECOCraftingPatternBusBlockEntity.class);
        var acceptingBus = mock(ECOCraftingPatternBusBlockEntity.class);
        var unusedBus = mock(ECOCraftingPatternBusBlockEntity.class);
        when(acceptingBus.pushPattern(pattern, inputs, jobId)).thenReturn(true);

        var provider = new CompactPatternProvider();
        provider.bind(List.of(rejectingBus, acceptingBus, unusedBus));

        assertTrue(provider.pushPattern(pattern, inputs, jobId));
        var order = inOrder(rejectingBus, acceptingBus);
        order.verify(rejectingBus).pushPattern(pattern, inputs, jobId);
        order.verify(acceptingBus).pushPattern(pattern, inputs, jobId);
        verifyNoInteractions(unusedBus);
    }

    @Test
    void fastPathPreparationsOnlyVisitTheBusThatAdvertisesThePattern() {
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
                requestedPattern, List.of(), List.of(), List.of(), mock(Level.class), UUID.randomUUID());
        var preparation = new Preparation(1, null, false, batch -> true);
        var requested = BigInteger.ONE.shiftLeft(Long.SIZE - 1);
        var exactPreparation = new ExactPreparation(requested, () -> true);
        when(ownerBus.eco$prepareFastPath(context)).thenReturn(preparation);
        when(ownerBus.eco$prepareExactFastPath(context, requested)).thenReturn(exactPreparation);

        assertSame(preparation, provider.eco$prepareFastPath(context));
        assertSame(exactPreparation, provider.eco$prepareExactFastPath(context, requested));
        verify(ownerBus).eco$prepareFastPath(context);
        verify(ownerBus).eco$prepareExactFastPath(context, requested);
        verify(unrelatedBus, never()).eco$prepareFastPath(any());
        verify(unrelatedBus, never()).eco$prepareExactFastPath(any(), any());
    }
}
