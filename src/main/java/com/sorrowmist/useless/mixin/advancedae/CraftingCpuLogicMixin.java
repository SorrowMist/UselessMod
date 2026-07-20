package com.sorrowmist.useless.mixin.advancedae;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sorrowmist.useless.compat.ae.DynamicReflectionSupport;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternInsertContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class CraftingCpuLogicMixin {
    @Unique
    private static final @Nullable Class<?> LOGIC_CLASS =
            DynamicReflectionSupport.findClassSafe("net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic");
    @Unique
    private static final @Nullable Class<?> JOB_CLASS =
            DynamicReflectionSupport.findClassSafe("net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob");
    @Unique
    private static final @Nullable Class<?> TRACKER_CLASS =
            DynamicReflectionSupport.findClassSafe("net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker");
    @Unique
    private static final @Nullable Field JOB_FIELD = DynamicReflectionSupport.findFieldSafe(LOGIC_CLASS, "job");
    @Unique
    private static final @Nullable Field INVENTORY_FIELD = DynamicReflectionSupport.findFieldSafe(LOGIC_CLASS, "inventory");
    @Unique
    private static final @Nullable Field CPU_FIELD = DynamicReflectionSupport.findFieldSafe(LOGIC_CLASS, "cpu");
    @Unique
    private static final @Nullable Method FINISH_METHOD =
            DynamicReflectionSupport.findMethodSafe(LOGIC_CLASS, "finishJob", boolean.class);
    @Unique
    private static final @Nullable Method POST_CHANGE_METHOD =
            DynamicReflectionSupport.findMethodSafe(LOGIC_CLASS, "postChange", AEKey.class);
    @Unique
    private static final @Nullable Field JOB_WAITING_FIELD = DynamicReflectionSupport.findFieldSafe(JOB_CLASS, "waitingFor");
    @Unique
    private static final @Nullable Field JOB_TRACKER_FIELD = DynamicReflectionSupport.findFieldSafe(JOB_CLASS, "timeTracker");
    @Unique
    private static final @Nullable Field JOB_FINAL_OUTPUT_FIELD = DynamicReflectionSupport.findFieldSafe(JOB_CLASS, "finalOutput");
    @Unique
    private static final @Nullable Field JOB_REMAINING_FIELD = DynamicReflectionSupport.findFieldSafe(JOB_CLASS, "remainingAmount");
    @Unique
    private static final @Nullable Field JOB_LINK_FIELD = DynamicReflectionSupport.findFieldSafe(JOB_CLASS, "link");
    @Unique
    private static final @Nullable Method DECREMENT_METHOD =
            DynamicReflectionSupport.findMethodSafe(TRACKER_CLASS, "decrementItems", long.class, AEKeyType.class);
    @Unique
    private static final boolean AVAILABLE = LOGIC_CLASS != null && JOB_CLASS != null && TRACKER_CLASS != null
            && JOB_FIELD != null && INVENTORY_FIELD != null && CPU_FIELD != null
            && FINISH_METHOD != null && POST_CHANGE_METHOD != null && JOB_WAITING_FIELD != null
            && JOB_TRACKER_FIELD != null && JOB_FINAL_OUTPUT_FIELD != null && JOB_REMAINING_FIELD != null
            && JOB_LINK_FIELD != null && DECREMENT_METHOD != null;

    @Unique
    @Nullable
    private DynamicPatternInsertContext uselessMod$insertContext;

    @Inject(method = "insert", at = @At("HEAD"))
    private void uselessMod$beginInsert(AEKey key, long amount, Actionable actionable,
                                        CallbackInfoReturnable<Long> callback) {
        if (AVAILABLE) {
            uselessMod$insertContext = new DynamicPatternInsertContext(key, amount, actionable);
        }
    }

    @WrapOperation(
            method = "insert",
            at = @At(value = "INVOKE",
                    target = "Lappeng/crafting/inv/ListCraftingInventory;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J",
                    ordinal = 0),
            remap = false)
    private long uselessMod$captureStrictMatch(
            ListCraftingInventory waitingFor, AEKey key, long amount, Actionable mode,
            Operation<Long> original) {
        long strict = original.call(waitingFor, key, amount, mode);
        if (AVAILABLE && mode == Actionable.SIMULATE && uselessMod$insertContext != null) {
            uselessMod$insertContext.setStrictMatched(strict);
        }
        return strict;
    }

    @Inject(method = "insert", at = @At("RETURN"), cancellable = true)
    private void uselessMod$claimDynamicOutput(AEKey key, long amount, Actionable actionable,
                                                CallbackInfoReturnable<Long> callback) {
        if (!AVAILABLE) {
            return;
        }
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
            Object cpu = uselessMod$getCpu();
            if (cpu != null) {
                ((AdvancedCraftingCpuAccessor) cpu).uselessMod$markDirty();
            }
            callback.setReturnValue(callback.getReturnValue() + supplemental);
        } else {
            callback.setReturnValue(callback.getReturnValue() + claims.claimedAmount());
        }
    }

    @WrapOperation(
            method = "executeCrafting",
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"),
            remap = false)
    private boolean uselessMod$registerDynamicOutputs(
            ICraftingProvider provider, IPatternDetails details, KeyCounter[] inputHolder,
            Operation<Boolean> original) {
        if (!AVAILABLE || !(details instanceof DynamicComponentPattern dynamic)) {
            return original.call(provider, details, inputHolder);
        }
        if (DynamicPatternCpuStateManager.INSTANCE.hasAmbiguousOutputRegistration(this, dynamic)) {
            return false;
        }
        boolean pushed = original.call(provider, details, inputHolder);
        Object job = uselessMod$getJob();
        if (pushed && job != null) {
            CraftingLink link = uselessMod$getJobLink(job);
            GenericStack finalOutput = uselessMod$getJobFinalOutput(job);
            if (link != null) {
                DynamicPatternCpuStateManager.INSTANCE.registerExpectedOutputs(
                        this, link.getCraftingID(), dynamic,
                        finalOutput == null ? null : finalOutput.what(), 1L);
            }
        }
        return pushed;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void uselessMod$writeDynamicState(
            CompoundTag data, HolderLookup.Provider registries, CallbackInfo callback) {
        if (!AVAILABLE) {
            return;
        }
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
        if (!AVAILABLE) {
            return;
        }
        DynamicPatternCpuStateManager.INSTANCE.clear(this);
        Object job = uselessMod$getJob();
        CraftingLink link = job == null ? null : uselessMod$getJobLink(job);
        if (link != null && data.contains(DynamicPatternCpuStateManager.NBT_KEY, CompoundTag.TAG_COMPOUND)) {
            DynamicPatternCpuStateManager.INSTANCE.readFromTag(
                    this, link.getCraftingID(), data.getCompound(DynamicPatternCpuStateManager.NBT_KEY), registries);
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void uselessMod$clearDynamicState(boolean success, CallbackInfo callback) {
        if (AVAILABLE) {
            DynamicPatternCpuStateManager.INSTANCE.clear(this);
        }
    }

    @Unique
    @Nullable
    private Object uselessMod$getJob() {
        return DynamicReflectionSupport.get(JOB_FIELD, this);
    }

    @Unique
    @Nullable
    private ListCraftingInventory uselessMod$getInventory() {
        Object value = DynamicReflectionSupport.get(INVENTORY_FIELD, this);
        return value instanceof ListCraftingInventory inventory ? inventory : null;
    }

    @Unique
    @Nullable
    private Object uselessMod$getCpu() {
        return DynamicReflectionSupport.get(CPU_FIELD, this);
    }

    @Unique
    @Nullable
    private ListCraftingInventory uselessMod$getJobWaitingFor(Object job) {
        Object value = DynamicReflectionSupport.get(JOB_WAITING_FIELD, job);
        return value instanceof ListCraftingInventory inventory ? inventory : null;
    }

    @Unique
    @Nullable
    private GenericStack uselessMod$getJobFinalOutput(Object job) {
        Object value = DynamicReflectionSupport.get(JOB_FINAL_OUTPUT_FIELD, job);
        return value instanceof GenericStack stack ? stack : null;
    }

    @Unique
    private long uselessMod$getJobRemaining(Object job) {
        return DynamicReflectionSupport.getLong(JOB_REMAINING_FIELD, job, 0L);
    }

    @Unique
    private void uselessMod$setJobRemaining(Object job, long amount) {
        DynamicReflectionSupport.setLong(JOB_REMAINING_FIELD, job, amount, "set Advanced AE remaining amount");
    }

    @Unique
    @Nullable
    private CraftingLink uselessMod$getJobLink(Object job) {
        Object value = DynamicReflectionSupport.get(JOB_LINK_FIELD, job);
        return value instanceof CraftingLink link ? link : null;
    }

    @Unique
    private void uselessMod$deductWaitingFor(DynamicPatternCpuStateManager.ClaimResult claims) {
        Object job = uselessMod$getJob();
        ListCraftingInventory waitingFor = job == null ? null : uselessMod$getJobWaitingFor(job);
        if (waitingFor == null) {
            return;
        }
        for (DynamicPatternCpuStateManager.Claim claim : claims.claims()) {
            waitingFor.extract(claim.exactExpectedKey(), claim.claimedAmount(), Actionable.MODULATE);
        }
    }

    @Unique
    private void uselessMod$decrementItems(Object job, long amount, AEKeyType type) {
        Object tracker = DynamicReflectionSupport.get(JOB_TRACKER_FIELD, job);
        DynamicReflectionSupport.invoke(DECREMENT_METHOD, tracker, "decrement Advanced AE items", amount, type);
    }

    @Unique
    private long uselessMod$applyInventoryClaims(
            AEKey incoming, DynamicPatternCpuStateManager.ClaimResult claims) {
        long claimed = claims.claimedForInventory();
        Object job = uselessMod$getJob();
        ListCraftingInventory inventory = uselessMod$getInventory();
        if (claimed <= 0 || job == null || inventory == null) {
            return 0;
        }
        uselessMod$decrementItems(job, claimed, incoming.getType());
        inventory.insert(incoming, claimed, Actionable.MODULATE);
        return claimed;
    }

    @Unique
    private long uselessMod$applyRequesterClaims(
            AEKey incoming, DynamicPatternCpuStateManager.ClaimResult claims) {
        long claimed = claims.claimedForRequester();
        Object job = uselessMod$getJob();
        if (claimed <= 0 || job == null) {
            return 0;
        }
        uselessMod$decrementItems(job, claimed, incoming.getType());
        CraftingLink link = uselessMod$getJobLink(job);
        long inserted = link == null ? 0 : link.insert(incoming, claimed, Actionable.MODULATE);
        DynamicReflectionSupport.invoke(POST_CHANGE_METHOD, this, "post Advanced AE dynamic output", incoming);
        long remaining = Math.max(0L, uselessMod$getJobRemaining(job) - claimed);
        uselessMod$setJobRemaining(job, remaining);
        Object cpu = uselessMod$getCpu();
        if (remaining <= 0) {
            DynamicReflectionSupport.invoke(FINISH_METHOD, this, "finish Advanced AE dynamic job", true);
            if (cpu != null) {
                ((AdvancedCraftingCpuAccessor) cpu).uselessMod$updateOutput(null);
            }
        } else if (cpu != null) {
            GenericStack finalOutput = uselessMod$getJobFinalOutput(job);
            if (finalOutput != null) {
                ((AdvancedCraftingCpuAccessor) cpu).uselessMod$updateOutput(
                        new GenericStack(finalOutput.what(), remaining));
            }
        }
        return inserted;
    }
}
