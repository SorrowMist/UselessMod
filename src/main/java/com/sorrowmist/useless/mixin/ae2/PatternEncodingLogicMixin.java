package com.sorrowmist.useless.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDiagnostics;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternUploader;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternUploadNotice;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PendingOmniversalPatternHolder;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.ProcessingPatternRecipeHolder;
import com.sorrowmist.useless.content.recipe.AdapterGuards;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Turns the pattern a pattern encoding terminal just produced into an Omniversal Pattern, but only
 * when the player transferred an Omniversal Alloy Furnace recipe from JEI into that terminal.
 *
 * <p>The recipe identity — which carries the mold — is recorded by
 * {@code OmniversalPatternJeiTransferHandler} the moment "+" is clicked and stored on this logic
 * instance. Encoding is not intercepted at click time: the player still goes through AE2's normal
 * flow, so a blank pattern is consumed from the terminal and the result appears in the bottom-right
 * slot, and AE2's network-aware ingredient substitution still applies.
 *
 * <p>Hooking {@code onChangeInventory} rather than {@code PatternEncodingTermMenu#encode} covers
 * every way a pattern can land in the encoded slot: AE2's own {@code encode()}, DataEnergistics'
 * {@code encode()} mixin, and ExtendedAE's {@code UniversalPatternEncodingTermMenu}, which writes
 * the slot directly. Calling {@code setItemDirect} from inside the callback does not recurse —
 * {@code AppEngInternalInventory.notifyingChanges} is already true while the callback runs. A
 * successful conversion consumes the pending JEI selection so it cannot affect a later encoding.
 */
@Mixin(targets = "appeng.parts.encoding.PatternEncodingLogic", remap = false)
public class PatternEncodingLogicMixin implements PendingOmniversalPatternHolder {
    @Shadow(remap = false)
    @Final
    private IPatternTerminalLogicHost host;

    @Shadow(remap = false)
    @Final
    private AppEngInternalInventory encodedPatternInv;

    @Shadow(remap = false)
    @Final
    private AppEngInternalInventory blankPatternInv;

    /**
     * Not persisted: a pending pick only makes sense within the session that made it, and a stale
     * identity surviving a world reload would silently re-tag an unrelated pattern.
     */
    @Unique
    @Nullable
    private AlloyFurnaceRecipeIdentity uselessMod$pendingOmniversalRecipe;

    @Unique
    @Nullable
    private String uselessMod$pendingOmniversalSourceId;

    @Override
    @Nullable
    public AlloyFurnaceRecipeIdentity uselessMod$getPendingOmniversalRecipe() {
        return uselessMod$pendingOmniversalRecipe;
    }

    @Override
    public void uselessMod$setPendingOmniversalRecipe(@Nullable AlloyFurnaceRecipeIdentity identity) {
        this.uselessMod$pendingOmniversalRecipe = identity;
    }

    @Override
    @Nullable
    public String uselessMod$getPendingOmniversalSourceId() {
        return uselessMod$pendingOmniversalSourceId;
    }

    @Override
    public void uselessMod$setPendingOmniversalSourceId(@Nullable String sourceId) {
        this.uselessMod$pendingOmniversalSourceId = sourceId == null
                ? RecipeSourceIds.UNKNOWN : RecipeSourceIds.normalize(sourceId);
    }

    @Override
    public void uselessMod$tryConvertPendingOmniversalPattern() {
        uselessMod$tryConvertToOmniversal();
    }

    @Inject(method = "onChangeInventory(Lappeng/util/inv/AppEngInternalInventory;I)V", at = @At("HEAD"), remap = false)
    private void uselessMod$tryConvertToOmniversal(AppEngInternalInventory inv, int slot, CallbackInfo ci) {
        if (inv != encodedPatternInv || slot != 0) return;

        uselessMod$tryConvertToOmniversal();
    }

    @Unique
    private void uselessMod$tryConvertToOmniversal() {
        AlloyFurnaceRecipeIdentity pending = uselessMod$pendingOmniversalRecipe;
        if (pending == null) {
            OmniversalPatternDiagnostics.skip("no pending JEI recipe pick");
            return;
        }

        ItemStack pattern = encodedPatternInv.getStackInSlot(0);
        if (pattern.isEmpty()) {
            OmniversalPatternDiagnostics.skip("encoded group is empty");
            return;
        }

        Level level = host.getLevel();
        if (level == null || level.isClientSide()) {
            OmniversalPatternDiagnostics.skip("no server level");
            return;
        }

        IPatternDetails details;
        try {
            details = PatternDetailsHelper.decodePattern(pattern, level);
        } catch (RuntimeException exception) {
            OmniversalPatternDiagnostics.skip("pattern failed to decode",
                    AdapterGuards.describe(exception));
            return;
        }
        if (details == null) {
            OmniversalPatternDiagnostics.skip("pattern decoded to null");
            return;
        }
        if (details instanceof OmniversalPatternDetails) {
            OmniversalPatternDiagnostics.skip("already an omniversal pattern");
            return;
        }
        if (!(details instanceof AEProcessingPattern)) {
            OmniversalPatternDiagnostics.skip("not a processing pattern",
                    details.getClass().getSimpleName());
            return;
        }

        String sourceId = uselessMod$pendingOmniversalSourceId;
        OmniversalPatternDiagnostics.attempt(pending, sourceId);
        // The client and server can materialize a tag-backed Ingredient with different
        // representative items. Resolve the selected recipe by id and the encoded pattern
        // contents when the fingerprint differs, otherwise a shared chemical-conversion /
        // oxidizing recipe is indistinguishable from the plain AE2 pattern at this point.
        Optional<AlloyFurnaceRecipeCatalog.Entry> entry;
        try {
            entry = AlloyFurnaceRecipeCatalog.resolvePattern(level, sourceId, pending, details);
        } catch (RuntimeException exception) {
            OmniversalPatternDiagnostics.blocked("resolvePattern threw", AdapterGuards.describe(exception));
            return;
        }
        if (entry.isEmpty()) {
            OmniversalPatternDiagnostics.blocked("recipe identity not resolved", pending.recipeId());
            return;
        }
        try {
            if (!AlloyFurnaceRecipeCatalog.matchesRecipe(
                    level, sourceId, entry.get().recipe(), details)) {
                OmniversalPatternDiagnostics.blocked(
                        "encoded slots do not match the recipe", entry.get().identity().recipeId());
                return;
            }
        } catch (RuntimeException exception) {
            OmniversalPatternDiagnostics.blocked("matchesRecipe threw", AdapterGuards.describe(exception));
            return;
        }

        if (details instanceof ProcessingPatternRecipeHolder holder) {
            holder.uselessMod$setRecipeIdentity(pending);
        }

        ItemStack omniversal;
        try {
            omniversal = OmniversalPatternEncoding.encode(pattern, details, entry.get(), level);
        } catch (RuntimeException exception) {
            OmniversalPatternDiagnostics.blocked("encoder threw", AdapterGuards.describe(exception));
            return;
        }
        if (omniversal.isEmpty()) {
            OmniversalPatternDiagnostics.blocked(
                    "encoder refused the pattern", entry.get().identity().recipeId());
            return;
        }
        OmniversalPatternDiagnostics.converted(entry.get().identity());

        encodedPatternInv.setItemDirect(0, omniversal);
        // A JEI selection applies to one encoding action. Do not let it upgrade a later
        // ordinary AE2 pattern merely because that pattern happens to be uniquely matchable.
        uselessMod$pendingOmniversalRecipe = null;
        uselessMod$pendingOmniversalSourceId = null;

        // 编码完成后按配置尝试写入当前网络中的多方块合金炉样板总成。
        // 成功写入或确认重复样板后清除结果槽；没有可接收目标或目标容量不足时保留结果。
        // 当前仍处于 onChangeInventory 回调，AppEngInternalInventory.notifyingChanges 已启用，
        // 因此更新结果槽或写入样板总成都不会递归触发该回调。
        // 上传提示在结果槽更新后发送，确保提示与界面状态一致。
        // 关闭自动上传时保留结果，由终端自身的后续逻辑处理。
        if (!ConfigManager.isOmniversalPatternAutoUploadEnabled()) {
            return;
        }

        ServerPlayer player = PatternUploadNotice.resolvePlayer(this, host);
        OmniversalPatternUploader.Result result = OmniversalPatternUploader.upload(host, omniversal);
        if (result.outcome() == OmniversalPatternUploader.Outcome.UPLOADED) {
            encodedPatternInv.setItemDirect(0, ItemStack.EMPTY);
            PatternUploadNotice.notifyUploaded(player, result.target());
            return;
        }
        // 当前网络已有等价样板时不重复写入；玩家可用时清除结果并退还本次消耗的空白样板。
        // 无法取得玩家对象时保留结果，避免未退还空白样板而造成资源损失。
        if (result.outcome() == OmniversalPatternUploader.Outcome.DUPLICATE && player != null) {
            encodedPatternInv.setItemDirect(0, ItemStack.EMPTY);
            // 空白样板优先退回终端空白样板槽；该槽被占用或容量不足时再退回玩家背包。
            ItemStack refund = AEItems.BLANK_PATTERN.stack(1);
            ItemStack remain = blankPatternInv.addItems(refund);
            if (!remain.isEmpty()) {
                player.getInventory().placeItemBackInInventory(remain, false);
            }
            PatternUploadNotice.notifyDuplicate(player);
        }
    }

}
