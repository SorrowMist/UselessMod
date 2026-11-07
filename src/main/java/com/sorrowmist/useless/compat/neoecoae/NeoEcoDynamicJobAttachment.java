package com.sorrowmist.useless.compat.neoecoae;

import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachment;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Persists dynamic-output reservations through NeoECOAE's public job attachment API. */
public final class NeoEcoDynamicJobAttachment implements ECOCraftingJobAttachment {
    private final UUID craftingJobId;
    private final Object cpu;
    private final Object grid;

    public NeoEcoDynamicJobAttachment(ECOCraftingJobContext context) {
        this.craftingJobId = context.craftingJobId();
        this.cpu = context.cpu();
        this.grid = NeoEcoCompat.grid(context);
        DynamicPatternCpuStateManager.INSTANCE.ensureEcoJob(craftingJobId, cpu, grid);
    }

    @Override
    public ResourceLocation id() {
        return NeoEcoCompat.DYNAMIC_OUTPUT_ATTACHMENT;
    }

    @Override
    public CompoundTag save() {
        // ECO 20.5.0 and AE2 15 persist native NBT without a registry lookup. The shared
        // state manager retains its cross-version parameter but does not use it on Forge.
        CompoundTag tag = DynamicPatternCpuStateManager.INSTANCE.writeEcoJob(craftingJobId, RegistryAccess.EMPTY);
        return tag == null ? new CompoundTag() : tag;
    }

    @Override
    public void load(CompoundTag tag) {
        // Replace, including an empty attachment: ensureEcoJob alone would retain stale claims.
        DynamicPatternCpuStateManager.INSTANCE.readEcoJob(
                craftingJobId, cpu, grid, tag, RegistryAccess.EMPTY);
    }

    @Override
    public void clear(ECOCraftingJobResult result) {
        DynamicPatternCpuStateManager.INSTANCE.clearEcoJob(craftingJobId);
    }
}
