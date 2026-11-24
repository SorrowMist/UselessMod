package com.sorrowmist.useless.mixin.compact;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactL9BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageInfiniteRestore", remap = false)
public abstract class InfiniteComponentHandlerCompactMixin {
    @Shadow @Final private ECOStorageSystemBlockEntity host;

    @ModifyReturnValue(method = "componentItemHandler", at = @At("RETURN"), remap = false)
    private IItemHandlerModifiable uselessMod$lockBuiltInComponents(IItemHandlerModifiable handler) {
        return host instanceof CompactL9BlockEntity ? CompactL9BlockEntity.lockBuiltInComponents(handler) : handler;
    }
}
