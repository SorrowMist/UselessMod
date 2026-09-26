package com.sorrowmist.useless.event.client;

import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 造化杖飞行的客户端侧兜底与状态记录。
 * <p>
 * 客户端那份 {@code abilities} 会被原版与其它 mod 改动（详见
 * {@code LocalPlayerMixin} 与 {@code MultiPlayerGameModeMixin} 的说明），这里做两件事：
 * <ol>
 *   <li>记录「服务端确实授予过飞行能力」，作为外部清除拦截与本地兜底的闸门；</li>
 *   <li>每 tick 兜一次 {@code mayfly}：带杖且服务端给过飞行时不允许它是 false。</li>
 * </ol>
 * 放在 {@link ClientTickEvent.Pre}（{@code Minecraft#tick()} 的头部、早于玩家 {@code aiStep}），
 * 这样补回来的值当帧就能用于「双击起飞」的判定。
 */
@EventBusSubscriber(Dist.CLIENT)
public final class BeefToolFlightClient {
    /**
     * 服务端是否真的授予过飞行能力（收到过 {@code canFly=true} 的能力包）。
     * <p>
     * 用来给外部清除拦截加闸：如果服务端把 {@code enable_flight_effect} 关掉了，它永远不会发
     * {@code canFly=true}，客户端 {@code mayfly} 本来就该是 false —— 此时若还去拦截/兜底，
     * 会把客户端强行设成「能飞」而服务端不认，反而可能被反作弊判定为 floating 踢出。
     * 所以只有服务端确认给过，才允许动作。
     */
    private static boolean serverGrantedFlight;

    private BeefToolFlightClient() {}

    /** 收到服务端能力包时更新「服务端给过飞行」标记（由 {@code ClientPacketListenerMixin} 调用）。 */
    public static void onServerAbilities(boolean mayfly) {
        if (mayfly) {
            serverGrantedFlight = true;
        }
    }

    public static boolean serverGrantedFlight() {
        return serverGrantedFlight;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            serverGrantedFlight = false;
            return;
        }

        Abilities abilities = player.getAbilities();
        // 带杖、服务端给过飞行，但本地 mayfly 是 false：被外部 mod（Re-Avaritia）清掉了。
        // 它紧接着的上报会被 LocalPlayerMixin 拦下，但万一存在不上报的清除路径，客户端就会一直
        // 停在「带杖却不能飞」——服务端状态没变、不会主动补发。这里兜住。
        if (!abilities.mayfly
                && serverGrantedFlight
                && UselessItemUtils.hasTargetToolInInventory(player)) {
            abilities.mayfly = true;
        }
    }
}
