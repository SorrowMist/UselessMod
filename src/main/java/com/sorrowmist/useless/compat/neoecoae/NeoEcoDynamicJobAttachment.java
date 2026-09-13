package com.sorrowmist.useless.compat.neoecoae;

import cn.dancingsnow.neoecoae.api.me.ECOCraftingJobAttachment;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingJobResult;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import net.minecraft.core.HolderLookup;
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
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = DynamicPatternCpuStateManager.INSTANCE.writeEcoJob(craftingJobId, registries);
        return tag == null ? new CompoundTag() : tag;
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.isEmpty()) {
            DynamicPatternCpuStateManager.INSTANCE.ensureEcoJob(craftingJobId, cpu, grid);
        } else {
            DynamicPatternCpuStateManager.INSTANCE.readEcoJob(
                    craftingJobId, cpu, grid, tag, registries);
        }
    }

    @Override
    public void clear(ECOCraftingJobResult result) {
        DynamicPatternCpuStateManager.INSTANCE.clearEcoJob(craftingJobId);
    }
}
