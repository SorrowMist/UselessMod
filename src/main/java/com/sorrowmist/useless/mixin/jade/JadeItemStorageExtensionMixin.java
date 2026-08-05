package com.sorrowmist.useless.mixin.jade;

import com.sorrowmist.useless.compat.jade.JadePatternStorageSnapshot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snownee.jade.addon.universal.ItemStorageProvider;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.ViewGroup;

import java.util.List;

@Mixin(ItemStorageProvider.Extension.class)
public abstract class JadeItemStorageExtensionMixin {
    @Inject(method = "getGroups", at = @At("HEAD"), cancellable = true)
    private void useless$useCachedPatternGroups(
            Accessor<?> accessor, CallbackInfoReturnable<List<ViewGroup<ItemStack>>> cir) {
        @Nullable List<ViewGroup<ItemStack>> groups = JadePatternStorageSnapshot.getGroups(accessor);
        if (groups != null) {
            cir.setReturnValue(groups);
        }
    }
}
