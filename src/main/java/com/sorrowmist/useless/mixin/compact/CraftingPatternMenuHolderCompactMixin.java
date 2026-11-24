package com.sorrowmist.useless.mixin.compact;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactF9BlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.exposed.ExposedPatternBusBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowInterfaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.gui.crafting.CraftingPatternMenu$Holder", remap = false)
public abstract class CraftingPatternMenuHolderCompactMixin {
    @Shadow @Final private BlockEntity entity;

    @Unique private CompactF9BlockEntity uselessMod$compactHost;
    @Unique private ExposedPatternBusBlockEntity uselessMod$patternBus;
    @Unique private long uselessMod$layoutRevision;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void uselessMod$capturePatternLayout(CallbackInfo ci) {
        if (entity instanceof ShadowInterfaceBlockEntity<?> && entity.getLevel() != null
                && !entity.getLevel().isClientSide
                && entity.getLevel().getBlockEntity(entity.getBlockPos()) instanceof CompactF9BlockEntity compact) {
            uselessMod$compactHost = compact;
            uselessMod$patternBus = compact.getExposedPatternBus();
            if (uselessMod$patternBus != null) {
                uselessMod$layoutRevision = uselessMod$patternBus.getPatternSlotInventory().layoutRevision();
            }
        }
    }

    @ModifyReturnValue(method = "isStillValid", at = @At("RETURN"), remap = false)
    private boolean uselessMod$checkPatternLayout(boolean valid) {
        return valid && (uselessMod$patternBus == null
                || uselessMod$compactHost.getExposedPatternBus() == uselessMod$patternBus
                    && uselessMod$patternBus.getPatternSlotInventory().layoutRevision() == uselessMod$layoutRevision);
    }

    @ModifyExpressionValue(method = "isStillValid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            remap = false), remap = false)
    private BlockEntity uselessMod$resolvePatternInterface(BlockEntity entity) {
        return CompactF9BlockEntity.resolveCraftingInterface(entity);
    }
}
