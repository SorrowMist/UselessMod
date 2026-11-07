package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachment;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingCpuContext;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingDispatchPolicyRegistry;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingDispatchEvent;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingLifecycle;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimRequest;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimResult;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimSink;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NeoEcoForgeDynamicBridgeTest {
    private static final DynamicPatternCpuStateManager MANAGER = DynamicPatternCpuStateManager.INSTANCE;
    private final List<UUID> jobs = new ArrayList<>();

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        NeoEcoCompat.initialize();
        NeoEcoCompat.initialize(); // Registration must be idempotent.
    }

    @AfterEach void clearJobs() { jobs.forEach(MANAGER::clearEcoJob); }

    private ECOCraftingJobContext job(IGrid grid) {
        ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
        when(cpu.getGrid()).thenReturn(grid);
        UUID id = UUID.randomUUID();
        jobs.add(id);
        return new ECOCraftingJobContext(cpu, id, new GenericStack(key("expected"), 12), 12, 12);
    }

    private static AEItemKey key(String data) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.getOrCreateTag().putString("native_data", data);
        return AEItemKey.of(stack);
    }

    private static DynamicComponentPattern pattern() {
        DynamicComponentPattern pattern = mock(DynamicComponentPattern.class);
        when(pattern.dynamicPatternIdentity()).thenReturn("test:dynamic_iron");
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(key("expected"), 2)});
        when(pattern.isItemIdOutput(0)).thenReturn(true);
        when(pattern.usesDynamicOutputs()).thenReturn(true);
        return pattern;
    }

    private static void reserve(ECOCraftingJobContext job, long copies) {
        MANAGER.bindEcoDispatch(job.craftingJobId(), job.cpu(), NeoEcoCompat.grid(job),
                pattern(), job.finalOutput().what(), copies);
    }

    private static void load(ECOCraftingJobAttachment attachment, CompoundTag tag) {
        // Plain JUnit has no Forge AE2 registry event. Supply only key-type lookup;
        // AEKey.fromTagGeneric and AEItemKey.fromTag still decode the real saved native NBT.
        AEKeyType itemType = AEKeyType.items();
        try (var types = mockStatic(AEKeyTypes.class)) {
            types.when(() -> AEKeyTypes.get(itemType.getId())).thenReturn(itemType);
            attachment.load(tag);
        }
    }

    @Test void lifecycleBindsOnlyOwnSuccessfulDispatchesAndClearsOnFinish() {
        IGrid grid = mock(IGrid.class);
        var job = job(grid);
        var provider = mock(AdvancedAlloyFurnaceBlockEntity.class);
        ECOCraftingLifecycle.fireJobStarted(job);
        assertFalse(MANAGER.hasEcoPendingOnGrid(grid));
        ECOCraftingLifecycle.firePatternDispatched(new ECOCraftingDispatchEvent(job, pattern(), 3, provider));
        assertEquals(6, MANAGER.ecoCandidates(grid, key("expected").getId()).get(0).remainingAmount());
        verify((ECOCraftingCPU) job.cpu()).markDirty();
        ECOCraftingLifecycle.fireJobFinished(job, mock(ECOCraftingJobResult.class));
        assertFalse(MANAGER.hasEcoPendingOnGrid(grid));
        ECOCraftingLifecycle.firePatternDispatched(new ECOCraftingDispatchEvent(
                job, pattern(), 3, mock(ICraftingProvider.class)));
        assertFalse(MANAGER.hasEcoPendingOnGrid(grid));
    }

    @Test void nativeNoArgumentAttachmentRoundTripsNbtAndEmptyLoadReplacesOldReservations() {
        IGrid grid = mock(IGrid.class);
        var job = job(grid);
        ECOCraftingJobAttachment attachment = new NeoEcoDynamicJobAttachment(job);
        reserve(job, 3);
        CompoundTag saved = attachment.save();
        assertFalse(saved.isEmpty());
        attachment.clear(mock(ECOCraftingJobResult.class));
        assertFalse(MANAGER.hasEcoPendingOnGrid(grid));
        load(attachment, saved);
        var restored = MANAGER.ecoCandidates(grid, key("expected").getId()).get(0);
        assertEquals(key("expected"), restored.exactExpectedKey());
        assertEquals(6, restored.remainingAmount());
        attachment.load(new CompoundTag());
        assertFalse(MANAGER.hasEcoPendingOnGrid(grid));
        assertTrue(attachment.save().isEmpty());
    }

    @Test void uniqueClaimUsesExactExpectedAndActualKeysCapsToReservationAndPersistsPartialProgress() throws Exception {
        // Also prove the exact signature used by GridOutputTarget's reflective optional boundary.
        assertNotNull(NeoEcoDynamicOutputCompat.class.getMethod("claim", IGrid.class,
                appeng.api.stacks.AEKey.class, long.class));
        IGrid grid = mock(IGrid.class);
        var job = job(grid);
        reserve(job, 3);
        var cpu = (ECOCraftingCPU) job.cpu();
        var sink = mock(ECOCraftingOutputClaimSink.class);
        when(cpu.getOutputClaimSink()).thenReturn(sink);
        AEItemKey actual = key("runtime_nbt");
        List<ECOCraftingOutputClaimRequest> calls = new ArrayList<>();
        when(sink.claimCraftingOutput(any())).thenAnswer(invocation -> {
            ECOCraftingOutputClaimRequest request = invocation.getArgument(0);
            calls.add(request);
            assertEquals(job.craftingJobId(), request.craftingJobId());
            assertEquals(key("expected"), request.expectedKey());
            assertEquals(actual, request.actualKey());
            long claimed = request.mode() == Actionable.SIMULATE ? 4 : 3;
            return new ECOCraftingOutputClaimResult(ECOCraftingOutputClaimResult.Status.ACCEPTED,
                    request.amount(), claimed, claimed, 0, 0, 12 - claimed, false);
        });
        assertEquals(3, NeoEcoDynamicOutputCompat.claim(grid, actual, 100));
        assertEquals(List.of(Actionable.SIMULATE, Actionable.MODULATE), calls.stream().map(ECOCraftingOutputClaimRequest::mode).toList());
        assertEquals(List.of(6L, 4L), calls.stream().map(ECOCraftingOutputClaimRequest::amount).toList());
        assertEquals(3, MANAGER.ecoCandidates(grid, actual.getId()).get(0).remainingAmount());
        verify(cpu).markDirty();
        var attachment = new NeoEcoDynamicJobAttachment(job);
        CompoundTag saved = attachment.save();
        attachment.clear(mock(ECOCraftingJobResult.class));
        load(attachment, saved);
        assertEquals(3, MANAGER.ecoCandidates(grid, actual.getId()).get(0).remainingAmount());
    }

    @Test void ambiguousJobsNeverCommitAndWrongGridOrZeroAmountNeverProbe() {
        IGrid grid = mock(IGrid.class);
        var first = job(grid);
        var second = job(grid);
        for (var job : List.of(first, second)) {
            reserve(job, 1);
            var sink = mock(ECOCraftingOutputClaimSink.class);
            when(((ECOCraftingCPU) job.cpu()).getOutputClaimSink()).thenReturn(sink);
            when(sink.claimCraftingOutput(any())).thenAnswer(invocation -> {
                ECOCraftingOutputClaimRequest request = invocation.getArgument(0);
                assertEquals(Actionable.SIMULATE, request.mode());
                return new ECOCraftingOutputClaimResult(ECOCraftingOutputClaimResult.Status.ACCEPTED,
                        request.amount(), 2, 2, 0, 0, 10, false);
            });
        }
        assertEquals(0, NeoEcoDynamicOutputCompat.claim(mock(IGrid.class), key("actual"), 20));
        assertEquals(0, NeoEcoDynamicOutputCompat.claim(grid, key("actual"), 0));
        assertEquals(0, NeoEcoDynamicOutputCompat.claim(grid, key("actual"), 20));
        assertEquals(2, MANAGER.ecoCandidates(grid, key("actual").getId()).size());
        for (var job : List.of(first, second)) verify((ECOCraftingCPU) job.cpu(), never()).markDirty();
    }

    @Test void restoredAttachmentBindsAfterCpuJoinsItsGridWithoutAnotherStartEvent() {
        var job = job(null);
        var attachment = new NeoEcoDynamicJobAttachment(job);
        reserve(job, 2);
        CompoundTag saved = attachment.save();
        attachment.clear(mock(ECOCraftingJobResult.class));
        load(attachment, saved);
        IGrid grid = mock(IGrid.class);
        var cpu = (ECOCraftingCPU) job.cpu();
        when(cpu.getGrid()).thenReturn(grid);
        assertFalse(MANAGER.hasEcoPendingOnGrid(grid));
        assertTrue(ECOCraftingDispatchPolicyRegistry.mayTick(
                new ECOCraftingCpuContext(cpu, job.craftingJobId(), true, 1, 1L)));
        assertEquals(4, MANAGER.ecoCandidates(grid, key("expected").getId()).get(0).remainingAmount());
    }

    @Test void dispatchPolicySerializesOurDynamicOutputsButLeavesForeignProvidersAlone() {
        IGrid grid = mock(IGrid.class);
        var job = job(grid);
        reserve(job, 1);
        var context = new ECOCraftingCpuContext(job.cpu(), job.craftingJobId(), true, 1, 0L);
        var ours = mock(AdvancedAlloyFurnaceBlockEntity.class);
        var other = mock(ICraftingProvider.class);
        assertFalse(ECOCraftingDispatchPolicyRegistry.isProviderAvailable(context, ours));
        assertTrue(ECOCraftingDispatchPolicyRegistry.isProviderAvailable(context, other));
        when(other.isBusy()).thenReturn(true);
        assertFalse(ECOCraftingDispatchPolicyRegistry.isProviderAvailable(context, other));
        MANAGER.clearEcoJob(job.craftingJobId());
        assertTrue(ECOCraftingDispatchPolicyRegistry.isProviderAvailable(context, ours));
        when(ours.isBusy()).thenReturn(true);
        assertFalse(ECOCraftingDispatchPolicyRegistry.isProviderAvailable(context, ours));
    }
}
