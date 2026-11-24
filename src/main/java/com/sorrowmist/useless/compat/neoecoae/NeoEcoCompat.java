package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticsRegistry;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;
import java.util.function.Supplier;

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
        registerPatternDispatch();
        initialized = true;
    }

    private static void registerPatternDispatch() {
        try {
            var register = MethodHandles.publicLookup().findStatic(ECOParallelCraftingProviders.class, "registerFastPath",
                    MethodType.methodType(void.class, Class.class, Function.class));
            Function<ICraftingProvider, ECOFastPathDispatchProvider> adapter = NeoEcoAlloyFurnaceDispatch::new;
            register.invokeExact((Class<?>) AdvancedAlloyFurnaceBlockEntity.class, (Function<?, ?>) adapter);
            register.invokeExact((Class<?>) MePatternAssemblyBlockEntity.class, (Function<?, ?>) adapter);
        } catch (NoSuchMethodException unavailable) {
            return;
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not register Useless Mod FastPath providers", failure);
        }
        ECOPatternSemanticsRegistry.register(ResourceLocation.fromNamespaceAndPath("useless_mod", "dynamic_pattern"),
                100, new NeoEcoPatternSemantics());
    }

    static Supplier<@Nullable IGrid> grid(ECOCraftingJobContext context) {
        return context.cpu() instanceof ECOCraftingCPU cpu ? cpu::getGrid : () -> null;
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
            if (!isUselessProvider(event.provider())) return;
            DynamicPatternExecution.Resolved resolved =
                    DynamicPatternExecution.resolve(event.pattern());
            if (resolved == null || !resolved.pattern().usesDynamicOutputs()) {
                return;
            }
            long copies = Math.multiplyExact(resolved.copies(), event.exactDispatchedCrafts().longValueExact());
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
