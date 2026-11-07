package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingCpuContext;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingDispatchPolicy;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingDispatchPolicyRegistry;
import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachmentRegistry;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingDispatchEvent;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingLifecycle;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingLifecycleListener;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternExecution;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Forge ECO 20.5.0 public job/dispatch integration for dynamic native-NBT outputs. */
public final class NeoEcoCompat {
    public static final ResourceLocation DYNAMIC_OUTPUT_ATTACHMENT =
            new ResourceLocation("useless_mod", "dynamic_component_outputs");

    private static final ECOCraftingLifecycleListener LIFECYCLE = new Lifecycle();
    private static final ECOCraftingDispatchPolicy POLICY = new DispatchPolicy();
    private static boolean initialized;

    private NeoEcoCompat() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        ECOCraftingJobAttachmentRegistry.register(
                DYNAMIC_OUTPUT_ATTACHMENT, NeoEcoDynamicJobAttachment::new);
        ECOCraftingLifecycle.register(LIFECYCLE);
        ECOCraftingDispatchPolicyRegistry.register(POLICY);
        initialized = true;
    }

    @Nullable
    static IGrid grid(ECOCraftingJobContext context) {
        return context.cpu() instanceof ECOCraftingCPU cpu ? cpu.getGrid() : null;
    }

    private static boolean isUselessProvider(ICraftingProvider provider) {
        return provider instanceof AdvancedAlloyFurnaceBlockEntity
                || provider instanceof MePatternAssemblyBlockEntity;
    }

    private static final class Lifecycle implements ECOCraftingLifecycleListener {
        @Override
        public void onJobStarted(ECOCraftingJobContext context) {
            DynamicPatternCpuStateManager.INSTANCE.ensureEcoJob(
                    context.craftingJobId(), context.cpu(), grid(context));
        }

        @Override
        public void onPatternDispatched(ECOCraftingDispatchEvent event) {
            DynamicPatternExecution.Resolved resolved =
                    DynamicPatternExecution.resolve(event.pattern());
            if (resolved == null || !isUselessProvider(event.provider())) {
                return;
            }
            // The furnace's dynamic reservation ledger is long-based. Never use ECO's
            // saturated dispatchedCrafts view for a BigInteger dispatch outside that range.
            long copies = Math.multiplyExact(resolved.copies(), event.exactDispatchedCrafts().longValueExact());
            DynamicPatternCpuStateManager.INSTANCE.bindEcoDispatch(
                    event.job().craftingJobId(),
                    event.job().cpu(),
                    grid(event.job()),
                    resolved.pattern(),
                    event.job().finalOutput() == null ? null : event.job().finalOutput().what(),
                    copies);
            if (event.job().cpu() instanceof ECOCraftingCPU cpu) {
                cpu.markDirty();
            }
        }

        @Override
        public void onJobFinished(ECOCraftingJobContext context, ECOCraftingJobResult result) {
            DynamicPatternCpuStateManager.INSTANCE.clearEcoJob(context.craftingJobId());
        }
    }

    private static final class DispatchPolicy implements ECOCraftingDispatchPolicy {
        @Override
        public boolean mayTick(ECOCraftingCpuContext context) {
            // Attachments can load before a CPU joins its grid. A restored job does not
            // emit onJobStarted again in ECO 20.5.0; refresh its binding at the public tick hook.
            if (context.active() && context.craftingJobId() != null
                    && context.cpu() instanceof ECOCraftingCPU cpu) {
                DynamicPatternCpuStateManager.INSTANCE.ensureEcoJob(
                        context.craftingJobId(), cpu, cpu.getGrid());
            }
            return true;
        }

        @Override
        public boolean isProviderAvailable(ECOCraftingCpuContext context, ICraftingProvider provider) {
            if (!isUselessProvider(provider)) {
                return !provider.isBusy();
            }
            Object grid = context.cpu() instanceof ECOCraftingCPU cpu ? cpu.getGrid() : null;
            return !provider.isBusy()
                    && !DynamicPatternCpuStateManager.INSTANCE.hasEcoPendingOnGrid(grid);
        }
    }
}
