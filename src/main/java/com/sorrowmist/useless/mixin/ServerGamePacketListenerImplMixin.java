package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 高级隐身状态下，拦下其它玩家对被隐身者的交互请求。
 * <p>
 * <b>有意注册在 json 的 {@code server} 段</b>：拦截目标是
 * {@link ServerboundInteractPacket}，即「别的玩家对我发起的交互」。单人存档里不存在
 * 其它玩家，这个包根本不会出现，因此没有在集成服务端生效的必要；只有独立服务端
 * （多人游戏）才需要它。放在 {@code server} 段可避免单人存档无谓地加载这个 mixin。
 * <p>
 * 对比 {@link ServerGamePacketListenerImplFlightMixin}：那个处理的是玩家<b>自己</b>的
 * 飞行上报，单人存档也需要，所以必须放通用段。两者注册段不同是刻意为之，别互相挪。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void useless_mod$preventProtectedPlayerInteraction(
            ServerboundInteractPacket packet,
            CallbackInfo ci
    ) {
        if (packet.getTarget(this.player.serverLevel()) instanceof Player target
                && EventHandler.shouldApplyBeefAdvancedStealth(target)) {
            ci.cancel();
        }
    }

}
