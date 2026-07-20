package com.sorrowmist.useless.mixin.eco;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
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
import com.sorrowmist.useless.mixin.ae2.ElapsedTimeTrackerAccessor;
import com.sorrowmist.useless.mixin.ae2.ExecutingCraftingJobAccessor;
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
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic", remap = false)
public abstract class CraftingCpuLogicMixin {
    @Unique
    private static final @Nullable Class<?> LOGIC_CLASS =
            DynamicReflectionSupport.findClassSafe("cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic");
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
    private static final boolean AVAILABLE = LOGIC_CLASS != null && JOB_FIELD != null
            && INVENTORY_FIELD != null && CPU_FIELD != null && FINISH_METHOD != null && POST_CHANGE_METHOD != null;

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
                ((EcoCraftingCpuAccessor) cpu).uselessMod$markDirty();
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
        ExecutingCraftingJob job = uselessMod$getJob();
        if (pushed && job != null) {
            ExecutingCraftingJobAccessor access = (ExecutingCraftingJobAccessor) job;
            CraftingLink link = access.uselessMod$getLink();
            GenericStack finalOutput = access.uselessMod$getFinalOutput();
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
        ExecutingCraftingJob job = uselessMod$getJob();
        if (job != null && data.contains(DynamicPatternCpuStateManager.NBT_KEY, CompoundTag.TAG_COMPOUND)) {
            CraftingLink link = ((ExecutingCraftingJobAccessor) job).uselessMod$getLink();
            if (link != null) {
                DynamicPatternCpuStateManager.INSTANCE.readFromTag(
                        this, link.getCraftingID(), data.getCompound(DynamicPatternCpuStateManager.NBT_KEY), registries);
            }
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
    private ExecutingCraftingJob uselessMod$getJob() {
        Object value = DynamicReflectionSupport.get(JOB_FIELD, this);
        return value instanceof ExecutingCraftingJob job ? job : null;
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
    private void uselessMod$deductWaitingFor(DynamicPatternCpuStateManager.ClaimResult claims) {
        ExecutingCraftingJob job = uselessMod$getJob();
        if (job == null) {
            return;
        }
        ListCraftingInventory waitingFor = ((ExecutingCraftingJobAccessor) job).uselessMod$getWaitingFor();
        for (DynamicPatternCpuStateManager.Claim claim : claims.claims()) {
            waitingFor.extract(claim.exactExpectedKey(), claim.claimedAmount(), Actionable.MODULATE);
        }
    }

    @Unique
    private void uselessMod$decrementItems(ExecutingCraftingJob job, long amount, AEKey key) {
        ((ElapsedTimeTrackerAccessor) (Object)
                ((ExecutingCraftingJobAccessor) job).uselessMod$getTimeTracker())
                .uselessMod$decrementItems(amount, key.getType());
    }

    @Unique
    private long uselessMod$applyInventoryClaims(
            AEKey incoming, DynamicPatternCpuStateManager.ClaimResult claims) {
        long claimed = claims.claimedForInventory();
        ExecutingCraftingJob job = uselessMod$getJob();
        ListCraftingInventory inventory = uselessMod$getInventory();
        if (claimed <= 0 || job == null || inventory == null) {
            return 0;
        }
        uselessMod$decrementItems(job, claimed, incoming);
        inventory.insert(incoming, claimed, Actionable.MODULATE);
        return claimed;
    }

    @Unique
    private long uselessMod$applyRequesterClaims(
            AEKey incoming, DynamicPatternCpuStateManager.ClaimResult claims) {
        long claimed = claims.claimedForRequester();
        ExecutingCraftingJob job = uselessMod$getJob();
        if (claimed <= 0 || job == null) {
            return 0;
        }
        ExecutingCraftingJobAccessor access = (ExecutingCraftingJobAccessor) job;
        uselessMod$decrementItems(job, claimed, incoming);
        CraftingLink link = access.uselessMod$getLink();
        long inserted = link == null ? 0 : link.insert(incoming, claimed, Actionable.MODULATE);
        DynamicReflectionSupport.invoke(POST_CHANGE_METHOD, this, "post ECO dynamic output", incoming);
        long remaining = Math.max(0L, access.uselessMod$getRemainingAmount() - claimed);
        access.uselessMod$setRemainingAmount(remaining);
        if (remaining <= 0) {
            DynamicReflectionSupport.invoke(FINISH_METHOD, this, "finish ECO dynamic job", true);
        }
        return inserted;
    }
}
