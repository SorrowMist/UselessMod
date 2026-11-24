package com.sorrowmist.useless.mixin.compact;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactInterfaceAccess;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactF9BlockEntity;
import cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewCodec;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes ECO PATTERNS chunks to the in-memory compact interface shadow. */
@Mixin(targets = "cn.dancingsnow.neoecoae.network.MenuDataCodecs", remap = false)
public final class MenuDataCodecsCompactMixin {
    @ModifyExpressionValue(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            remap = false), require = 0, remap = false)
    private static BlockEntity uselessMod$resolvePatternInterface(BlockEntity entity) {
        return CompactF9BlockEntity.resolveCraftingInterface(entity);
    }

    @Inject(method = "receive", at = @At("HEAD"), cancellable = true, remap = false)
    private static void uselessMod$receiveCompactPreview(
            Player player, MenuDataTransport.Channel channel, RegistryFriendlyByteBuf buf, CallbackInfo ci) {
        if (channel != MenuDataTransport.Channel.PATTERNS
                || !(player.containerMenu instanceof ModularUIContainerMenu menu)
                || !(menu.uiHolder instanceof BlockUIMenuType.BlockUIHolder holder)
                || !CompactInterfaceAccess.requested(holder.blockState)
                || !(player.level().getBlockEntity(holder.pos) instanceof CompactF9BlockEntity compact)) {
            return;
        }
        BlockPos target = buf.readBlockPos();
        if (!target.equals(holder.pos)) {
            throw new IllegalArgumentException("Compact pattern preview target does not match menu host");
        }
        CompoundTag payload = PatternPreviewCodec.read(buf);
        compact.receivePatternPreview(payload);
        ci.cancel();
    }
}
