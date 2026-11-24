package com.sorrowmist.useless.mixin.compact;

import cn.dancingsnow.neoecoae.api.storage.ICellHost;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowComputationDriveBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowStorageDriveBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.menu.HostDiskMenu$DiskSlot", remap = false)
public abstract class HostDiskSlotCompactMixin {
    @Shadow @Final private NEBlockEntity<?, ?> drive;

    @ModifyReturnValue(method = "getItem", at = @At("RETURN"), remap = false)
    private ItemStack uselessMod$displayBuiltInCell(ItemStack original) {
        if ((drive instanceof ShadowComputationDriveBlockEntity || drive instanceof ShadowStorageDriveBlockEntity)
                && !drive.isRemoved() && drive.getCluster() != null && !drive.getCluster().isDestroyed()) {
            ItemStack cell = ((ICellHost) drive).getCellStack();
            if (cell != null) return cell;
        }
        return original;
    }
}
