package com.sorrowmist.useless.mixin.ae2;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternInsertContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "appeng.crafting.execution.CraftingCpuLogic", remap = false)
public abstract class CraftingCpuLogicMixin {
    @Shadow(remap = false)
    CraftingCPUCluster cluster;

    @Unique
    @Nullable
    private DynamicPatternInsertContext uselessMod$insertContext;

    @Inject(method = "insert", at = @At("HEAD"))
    private void uselessMod$beginInsert(AEKey key, long amount, Actionable actionable,
                                        CallbackInfoReturnable<Long> callback) {
        uselessMod$insertContext = new DynamicPatternInsertContext(key, amount, actionable);
    }

    @WrapOperation(
            method = "insert",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/inv/ListCraftingInventory;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J",
                    ordinal = 0),
            remap = false)
    private long uselessMod$captureStrictMatch(
            ListCraftingInventory waitingFor, AEKey key, long amount, Actionable mode,
            Operation<Long> original) {
        long strict = original.call(waitingFor, key, amount, mode);
        if (mode == Actionable.SIMULATE && uselessMod$insertContext != null) {
            uselessMod$insertContext.setStrictMatched(strict);
        }
        return strict;
    }

    @Inject(method = "insert", at = @At("RETURN"), cancellable = true)
    private void uselessMod$claimDynamicOutput(AEKey key, long amount, Actionable actionable,
                                                CallbackInfoReturnable<Long> callback) {
        DynamicPatternInsertContext context = uselessMod$insertContext;
        uselessMod$insertContext = null;
        if (context == null) {
            return;
        }
        long remainder = Math.max(0L, context.requestedAmount() - context.strictMatched());
        if (remainder <= 0 || !DynamicPatternCpuStateManager.INSTANCE.hasAnyPending(this)) {
            return;
        }

        DynamicPatternCpuStateManager.ClaimResult claims =
                DynamicPatternCpuStateManager.INSTANCE.claim(this, key, remainder, actionable);
        if (!claims.claimedAnything()) {
            return;
        }

        if (actionable == Actionable.MODULATE) {
            uselessMod$deductWaitingFor(claims);
            long supplemental = uselessMod$applyInventoryClaims(key, claims)
                    + uselessMod$applyRequesterClaims(key, claims);
            cluster.markDirty();
            callback.setReturnValue(callback.getReturnValue() + supplemental);
        } else {
            callback.setReturnValue(callback.getReturnValue() + claims.claimedAmount());
        }
    }

    @WrapOperation(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"),
            remap = false)
    private boolean uselessMod$registerDynamicOutputs(
            ICraftingProvider provider, IPatternDetails details, KeyCounter[] inputHolder,
            Operation<Boolean> original) {
        if (!(details instanceof DynamicComponentPattern dynamic)) {
            return original.call(provider, details, inputHolder);
        }
        if (DynamicPatternCpuStateManager.INSTANCE.hasAmbiguousOutputRegistration(this, dynamic)) {
            return false;
        }
        boolean pushed = original.call(provider, details, inputHolder);
        if (pushed) {
            ExecutingCraftingJob job = ((CraftingCpuLogicAccessor) (Object) this).uselessMod$getJob();
            if (job != null) {
                CraftingLink link = ((ExecutingCraftingJobAccessor) job).uselessMod$getLink();
                GenericStack finalOutput = ((ExecutingCraftingJobAccessor) job).uselessMod$getFinalOutput();
                if (link != null) {
                    DynamicPatternCpuStateManager.INSTANCE.registerExpectedOutputs(
                            this,
                            link.getCraftingID(),
                            dynamic,
                            finalOutput == null ? null : finalOutput.what(),
                            1L);
                }
            }
        }
        return pushed;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void uselessMod$writeDynamicState(
            CompoundTag data, HolderLookup.Provider registries, CallbackInfo callback) {
        CompoundTag state = DynamicPatternCpuStateManager.INSTANCE.writeToTag(this, registries);
        if (state == null) {
            data.remove(DynamicPatternCpuStateManager.NBT_KEY);
        } else {
            data.put(DynamicPatternCpuStateManager.NBT_KEY, state);
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void uselessMod$readDynamicState(
            CompoundTag data, HolderLookup.Provider registries, CallbackInfo callback) {
        DynamicPatternCpuStateManager.INSTANCE.clear(this);
        ExecutingCraftingJob job = ((CraftingCpuLogicAccessor) (Object) this).uselessMod$getJob();
        if (job == null || !data.contains(DynamicPatternCpuStateManager.NBT_KEY, CompoundTag.TAG_COMPOUND)) {
            return;
        }
        CraftingLink link = ((ExecutingCraftingJobAccessor) job).uselessMod$getLink();
        if (link != null) {
            DynamicPatternCpuStateManager.INSTANCE.readFromTag(
                    this, link.getCraftingID(), data.getCompound(DynamicPatternCpuStateManager.NBT_KEY), registries);
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void uselessMod$clearDynamicState(boolean success, CallbackInfo callback) {
        DynamicPatternCpuStateManager.INSTANCE.clear(this);
    }

    @Unique
    private void uselessMod$deductWaitingFor(DynamicPatternCpuStateManager.ClaimResult claims) {
        ExecutingCraftingJob job = ((CraftingCpuLogicAccessor) (Object) this).uselessMod$getJob();
        if (job == null) {
            return;
        }
        ListCraftingInventory waitingFor = ((ExecutingCraftingJobAccessor) job).uselessMod$getWaitingFor();
        for (DynamicPatternCpuStateManager.Claim claim : claims.claims()) {
            waitingFor.extract(claim.exactExpectedKey(), claim.claimedAmount(), Actionable.MODULATE);
        }
    }

    @Unique
    private long uselessMod$applyInventoryClaims(
            AEKey incoming, DynamicPatternCpuStateManager.ClaimResult claims) {
        long claimed = claims.claimedForInventory();
        if (claimed <= 0) {
            return 0;
        }
        ExecutingCraftingJob job = ((CraftingCpuLogicAccessor) (Object) this).uselessMod$getJob();
        if (job == null) {
            return 0;
        }
        ElapsedTimeTrackerAccessor tracker = (ElapsedTimeTrackerAccessor) (Object)
                ((ExecutingCraftingJobAccessor) job).uselessMod$getTimeTracker();
        tracker.uselessMod$decrementItems(claimed, incoming.getType());
        ((CraftingCpuLogic) (Object) this).getInventory().insert(incoming, claimed, Actionable.MODULATE);
        return claimed;
    }

    @Unique
    private long uselessMod$applyRequesterClaims(
            AEKey incoming, DynamicPatternCpuStateManager.ClaimResult claims) {
        long claimed = claims.claimedForRequester();
        if (claimed <= 0) {
            return 0;
        }
        ExecutingCraftingJob job = ((CraftingCpuLogicAccessor) (Object) this).uselessMod$getJob();
        if (job == null) {
            return 0;
        }
        ExecutingCraftingJobAccessor jobAccess = (ExecutingCraftingJobAccessor) job;
        ((ElapsedTimeTrackerAccessor) (Object) jobAccess.uselessMod$getTimeTracker())
                .uselessMod$decrementItems(claimed, incoming.getType());
        CraftingLink link = jobAccess.uselessMod$getLink();
        long inserted = link == null ? 0 : link.insert(incoming, claimed, Actionable.MODULATE);
        ((CraftingCpuLogicAccessor) (Object) this).uselessMod$postChange(incoming);

        long remaining = Math.max(0L, jobAccess.uselessMod$getRemainingAmount() - claimed);
        jobAccess.uselessMod$setRemainingAmount(remaining);
        if (remaining <= 0) {
            ((CraftingCpuLogicAccessor) (Object) this).uselessMod$finishJob(true);
            cluster.updateOutput(null);
        } else if (jobAccess.uselessMod$getFinalOutput() != null) {
            cluster.updateOutput(new GenericStack(
                    jobAccess.uselessMod$getFinalOutput().what(), remaining));
        }
        return inserted;
    }
}
