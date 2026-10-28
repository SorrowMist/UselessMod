package com.sorrowmist.useless.mixin.compact;

import cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewCodec;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType.BlockUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactF9BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native ECO preview packets address a world interface; an F9 keeps that interface inside its host. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.network.MenuDataCodecs", remap = false)
public abstract class CompactPatternPreviewPacketMixin {
    @Inject(method = "receive", at = @At("HEAD"), cancellable = true, remap = false)
    private static void useless$receiveF9Patterns(Player player, MenuDataTransport.Channel channel,
                                                FriendlyByteBuf buffer, CallbackInfo ci) {
        if (channel != MenuDataTransport.Channel.PATTERNS) return;
        BlockPos pos = BlockPos.of(buffer.getLong(buffer.readerIndex()));
        if (!(player.level().getBlockEntity(pos) instanceof CompactF9BlockEntity host)) return;
        buffer.readBlockPos();
        var payload = PatternPreviewCodec.read(buffer);
        if (player.containerMenu instanceof ModularUIContainer menu
                && menu.getModularUI().holder instanceof BlockUIHolder holder
                && holder.pos.equals(pos) && !host.isRemoved()) {
            host.compactCraftingInterface().getPatternPreviewSync().receive(payload);
        }
        ci.cancel();
    }
}
