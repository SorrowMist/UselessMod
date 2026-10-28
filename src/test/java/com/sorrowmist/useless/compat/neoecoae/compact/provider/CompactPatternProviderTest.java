package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.UUID;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompactPatternProviderTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void domainDuplicateDoesNotFallThroughToAnotherBus() {
        var first = mock(ECOCraftingPatternBusBlockEntity.class);
        var second = mock(ECOCraftingPatternBusBlockEntity.class);
        var stack = new ItemStack(Items.PAPER);
        var prepared = new ECOPreparedPattern(stack, mock(IPatternDetails.class), null);
        when(first.preparePattern(stack)).thenReturn(prepared);
        when(first.insertPreparedPattern(prepared)).thenReturn(ECOPatternInsertionResult.ALREADY_PRESENT);
        var provider = new CompactPatternProvider();
        provider.bind(List.of(first, second));

        assertEquals(ECOPatternInsertionResult.ALREADY_PRESENT, provider.insertPatternWithResult(stack));
        verifyNoInteractions(second);
        assertEquals(1, stack.getCount());
    }

    @Test
    void fullBusReusesTheNativeDecodeAndDomainCheckAtTheNextBus() {
        var first = mock(ECOCraftingPatternBusBlockEntity.class);
        var second = mock(ECOCraftingPatternBusBlockEntity.class);
        var stack = new ItemStack(Items.PAPER);
        var prepared = new ECOPreparedPattern(stack, mock(IPatternDetails.class), null);
        when(first.preparePattern(stack)).thenReturn(prepared);
        when(first.insertPreparedPattern(prepared)).thenReturn(ECOPatternInsertionResult.NO_SPACE);
        when(second.insertPreparedPatternKnownUnique(prepared)).thenReturn(ECOPatternInsertionResult.INSERTED);
        var provider = new CompactPatternProvider();
        provider.bind(List.of(first, second));

        assertTrue(provider.insertPattern(stack));
        verify(second, never()).preparePattern(any());
        verify(second, never()).insertPreparedPattern(any());
        verify(second).insertPreparedPatternKnownUnique(prepared);
    }

    @Test
    void providerPublishesTheNewPatternAfterNativeDecodingWasDeferred() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var pattern = mock(IPatternDetails.class);
        var decoded = new java.util.ArrayList<IPatternDetails>();
        when(bus.getAvailablePatterns()).thenReturn(decoded);
        doAnswer(call -> {
            decoded.clear();
            decoded.add(pattern);
            return null;
        }).when(bus).refreshPatternDetailsForCatalog();
        var provider = new CompactPatternProvider();
        provider.bind(List.of(bus));

        assertEquals(List.of(pattern), provider.getAvailablePatterns());
    }

    @Test
    void ordinaryFallbackRetainsTheCpuJobIdentity() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var pattern = mock(IPatternDetails.class);
        var inputs = new KeyCounter[]{new KeyCounter()};
        var job = UUID.randomUUID();
        when(bus.pushPattern(pattern, inputs, job)).thenReturn(true);
        var provider = new CompactPatternProvider();
        provider.bind(List.of(bus));

        assertTrue(provider.eco$pushPatternBatch(pattern, inputs, 1, job));
        verify(bus).pushPattern(pattern, inputs, job);
        verify(bus, never()).pushPattern(pattern, inputs);
    }

    @Test
    void ordinaryFallbackRejectsBatchesItCannotAcceptAtomically() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var provider = new CompactPatternProvider();
        provider.bind(List.of(bus));

        assertFalse(provider.eco$pushPatternBatch(mock(IPatternDetails.class), new KeyCounter[0], 2, UUID.randomUUID()));
        verifyNoInteractions(bus);
    }

    @Test
    void fastPathPreservesTheNativeAtomicPreparationAndExactAmounts() {
        var first = mock(ECOCraftingPatternBusBlockEntity.class);
        var second = mock(ECOCraftingPatternBusBlockEntity.class);
        var context = mock(ECOBatchDispatchContext.class);
        var preparation = new ECOFastPathDispatchProvider.Preparation(50, null, false, batch -> true);
        var requested = BigInteger.ONE.shiftLeft(90);
        var exact = new ECOFastPathDispatchProvider.ExactPreparation(requested, () -> true);
        when(second.eco$prepareFastPath(context)).thenReturn(preparation);
        when(second.eco$prepareExactFastPath(context, requested)).thenReturn(exact);
        var provider = new CompactPatternProvider();
        provider.bind(List.of(first, second));

        assertSame(preparation, provider.eco$prepareFastPath(context));
        assertSame(exact, provider.eco$prepareExactFastPath(context, requested));
    }

    @Test
    void knownUniquePreparedInsertionRetainsTheCoordinatorsContract() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var prepared = new ECOPreparedPattern(new ItemStack(Items.PAPER), mock(IPatternDetails.class), null);
        when(bus.insertPreparedPatternKnownUnique(prepared)).thenReturn(ECOPatternInsertionResult.INSERTED);
        var provider = new CompactPatternProvider();
        provider.bind(List.of(bus));

        assertEquals(ECOPatternInsertionResult.INSERTED, provider.insertPreparedPatternKnownUnique(prepared));
        verify(bus, never()).insertPreparedPattern(any());
    }
}
