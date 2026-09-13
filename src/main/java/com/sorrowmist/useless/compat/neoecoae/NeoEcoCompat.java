package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCpuContext;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingDispatchEvent;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingDispatchPolicy;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingDispatchPolicyRegistry;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingJobAttachmentRegistry;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingJobResult;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingLifecycle;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingLifecycleListener;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternExecution;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** P16 public-API integration for dynamic Useless Mod outputs. */
public final class NeoEcoCompat {
    public static final ResourceLocation DYNAMIC_OUTPUT_ATTACHMENT =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "dynamic_component_outputs");

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
    static Object grid(ECOCraftingJobContext context) {
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
            long copies = Math.multiplyExact(resolved.copies(), event.dispatchedCrafts());
            DynamicPatternCpuStateManager.INSTANCE.bindEcoDispatch(
                    event.job().craftingJobId(),
                    event.job().cpu(),
                    grid(event.job()),
                    resolved.pattern(),
                    event.job().finalOutput() == null ? null : event.job().finalOutput().what(),
                    copies);
        }

        @Override
        public void onJobFinished(ECOCraftingJobContext context, ECOCraftingJobResult result) {
            DynamicPatternCpuStateManager.INSTANCE.clearEcoJob(context.craftingJobId());
        }
    }

    private static final class DispatchPolicy implements ECOCraftingDispatchPolicy {
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
