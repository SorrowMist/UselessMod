package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.client.BeefToolFlightClient;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦掉外部 mod 对造化杖飞行的「伪装成玩家操作」的清除上报。
 * <p>
 * <b>问题</b>：<b>Re-Avaritia</b> 的 {@code AbilityHandler#updateClientServerFlight}
 * 在<b>客户端也有一份</b>，它会直接把客户端的 {@code mayfly} / {@code flying} 清成 false，
 * 然后调用 {@link LocalPlayer#onUpdateAbilities()} 发 {@code ServerboundPlayerAbilitiesPacket}。
 * 这个包和「玩家自己双击关飞行 / 落地」发出的包**完全一样**，服务端无法区分，
 * 于是被当成玩家意图处理，把服务端的「粘滞飞行」一起解除 ——
 * 表现为「切回生存后即使离地很高也会掉下来」。
 * <p>
 * <b>判据</b>：服务端每 tick 都保证携带造化杖时 {@code mayfly=true}，而
 * <ul>
 *   <li>玩家自己的操作（双击关飞行、落地检查）<b>只改 {@code flying}</b>，{@code mayfly} 仍是 true；</li>
 *   <li>外部 mod 的清除会把 {@code mayfly} 一起清掉。</li>
 * </ul>
 * 所以「带着造化杖但客户端 {@code mayfly} 是 false」就是外部清除的精确特征。
 * <p>
 * <b>做法</b>：把 {@code mayfly} 还原回去，并取消这次上报 —— 玩家自己的飞行意图就不会被误清除。
 * {@code flying} 不在这里动，交给服务端下一 tick 的补发，这样玩家真落地时仍然能正常停飞。
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Inject(method = "onUpdateAbilities", at = @At("HEAD"), cancellable = true)
    private void useless_mod$blockExternalFlightClear(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        Abilities abilities = self.getAbilities();
        if (abilities.mayfly || !UselessItemUtils.hasTargetToolInInventory(self)) {
            return;
        }
        // 加闸：服务端从没给过飞行能力（例如 enable_flight_effect 被关掉）时，mayfly=false 是正常的，
        // 不能拦，否则会把客户端强行设成「能飞」而服务端不认，反而有被反作弊判 floating 的风险。
        if (!BeefToolFlightClient.serverGrantedFlight()) {
            return;
        }

        abilities.mayfly = true;
        ci.cancel();
    }
}
