package com.sorrowmist.useless.mixin.jade;

import com.sorrowmist.useless.compat.jade.JadePatternStorageSnapshot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snownee.jade.addon.universal.ItemStorageProvider;
import snownee.jade.api.view.ViewGroup;

import java.util.List;

@Mixin(value=ItemStorageProvider.class,remap=false)
public abstract class JadeItemStorageExtensionMixin {
    @Inject(method = "getGroups", at = @At("HEAD"), cancellable = true)
    private void useless$useCachedPatternGroups(
            net.minecraft.server.level.ServerPlayer player, net.minecraft.server.level.ServerLevel level,
            Object target, boolean showDetails, CallbackInfoReturnable<List<ViewGroup<ItemStack>>> cir) {
        @Nullable List<ViewGroup<ItemStack>> groups = JadePatternStorageSnapshot.getGroups(target);
        if (groups != null) {
            cir.setReturnValue(groups);
        }
    }
}
