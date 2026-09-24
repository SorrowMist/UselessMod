package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让服务端在切换游戏模式的瞬间就把造化杖的飞行补回去，消掉客户端那 1 帧的空窗。
 * <p>
 * <b>问题</b>：服务端切模式时 {@link ServerPlayerGameMode#setGameModeForPlayer} 会先按新模式
 * 重置 abilities（生存模式把 mayfly 清成 false，NeoForge 再把 flying 保留回来）。紧接着
 * {@code ServerPlayer#setGameMode} 就会把这个状态发给客户端（{@code changeGameModeForPlayer}
 * 里的能力包，以及 {@code setGameMode} 里的能力包 + CHANGE_GAME_MODE 事件）。
 * <p>
 * 而模组的授予逻辑跑在 {@code PlayerTickEvent.Post}，比这几步晚 —— 所以客户端会先收到
 * {@code mayfly=false}，要等下一个 tick 模组补发的包才恢复。实测就是 1 帧（50ms）的空窗，
 * 玩家正好在这期间双击空格就起不了飞。
 * <p>
 * <b>做法</b>：在这个方法的 RETURN 立刻调用模组的授予逻辑。此时
 * {@code gameModeForPlayer} 已经是新模式，所以 {@code player.isCreative()} 判断是对的，
 * 补发出去的能力包直接带上 {@code mayfly=true}，客户端从头到尾看不到 false。
 * <p>
 * 注意：本 mixin 注册在 json 的<b>通用</b> {@code mixins} 段而不是 {@code server} 段 ——
 * {@code server} 段只在独立服务端生效，单人存档的集成服务端属于客户端侧，放那里会失效。
 */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    private ServerPlayer player;

    @Inject(
            method = "setGameModeForPlayer(Lnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;)V",
            at = @At("RETURN"))
    private void useless_mod$regrantStaffFlight(GameType newGameMode, GameType previousGameMode, CallbackInfo ci) {
        EventHandler.applyBeefToolFlightNow(this.player);
    }
}
