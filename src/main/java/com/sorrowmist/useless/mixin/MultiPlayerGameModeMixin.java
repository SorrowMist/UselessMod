package com.sorrowmist.useless.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 保护造化杖飞行不被原版的「客户端本地游戏模式重置」抹掉。
 * <p>
 * <b>背景</b>：切换游戏模式时，客户端会收到 {@code ClientboundGameEventPacket(CHANGE_GAME_MODE)}，
 * 走 {@code ClientPacketListener#handleGameEvent} → {@link MultiPlayerGameMode#setLocalMode(GameType)}
 * → {@code GameType#updatePlayerAbilities}，<b>在本地把 mayfly / flying 按新模式整套重置</b>。
 * 切到生存时这一步会把两者都清成 false，而服务端那边 NeoForge 是<b>保留 flying</b> 的
 * （{@code ServerPlayerGameMode#setGameModeForPlayer} 的补丁），造化杖也会继续授予 mayfly。
 * 更麻烦的是：客户端紧接着会把 {@code flying=false} 通过
 * {@code ServerboundPlayerAbilitiesPacket} 回传给服务端，于是服务端那份「保留的悬停」也被跟着清掉。
 * <p>
 * <b>做法</b>：在重置前后把服务端上次同步过来的值原样还原回去。这不是「凭空造一个状态」——
 * 只是别让原版这次本地重置提前把飞行丢掉；如果服务端确实要收回（比如杖被拿走、配置关掉了），
 * 它的能力包会紧随其后把客户端纠正过来，服务端始终是权威。
 * <p>
 * 只在切到非创造/非旁观时还原：切进创造/旁观时原版给的默认值本来就是对的，不能动。
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Unique
    private boolean useless_mod$mayflyBefore;

    @Unique
    private boolean useless_mod$flyingBefore;

    @Inject(method = "setLocalMode(Lnet/minecraft/world/level/GameType;)V", at = @At("HEAD"))
    private void useless_mod$captureAbilitiesBeforeReset(GameType type, CallbackInfo ci) {
        Abilities abilities = useless_mod$localAbilities();
        if (abilities == null) {
            return;
        }
        this.useless_mod$mayflyBefore = abilities.mayfly;
        this.useless_mod$flyingBefore = abilities.flying;
    }

    @Inject(method = "setLocalMode(Lnet/minecraft/world/level/GameType;)V", at = @At("RETURN"))
    private void useless_mod$restoreAbilitiesAfterReset(GameType type, CallbackInfo ci) {
        if (type == GameType.CREATIVE || type == GameType.SPECTATOR) {
            return;
        }

        Abilities abilities = useless_mod$localAbilities();
        if (abilities == null) {
            return;
        }
        abilities.mayfly = this.useless_mod$mayflyBefore;
        abilities.flying = this.useless_mod$flyingBefore;
    }

    @Unique
    private static Abilities useless_mod$localAbilities() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : player.getAbilities();
    }
}
