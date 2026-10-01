package com.sorrowmist.useless.client.stafflink;

import com.sorrowmist.useless.network.StaffLinkStressStatusPacket;
import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端手上的应力运行状态。
 *
 * <p>服务端每 20 tick 覆盖一次；界面只读它，不参与任何判定——「能不能搬」永远是服务端说了算，
 * 这里纯粹是给玩家看的数字与原因。</p>
 */
public final class StaffLinkStressClientState {

    /** 界面每帧都要按「当前网络 × 锚点 × 线路」查一次，因此建成表而不是每次线性扫。 */
    private static volatile Map<Key, StaffLinkStressStatusPacket.Entry> entries = Map.of();

    private StaffLinkStressClientState() {
    }

    public static void replace(List<StaffLinkStressStatusPacket.Entry> incoming) {
        Map<Key, StaffLinkStressStatusPacket.Entry> next = new HashMap<>();
        for (StaffLinkStressStatusPacket.Entry entry : incoming) {
            next.put(new Key(entry.networkId(), entry.anchor(), entry.route()), entry);
        }
        entries = Map.copyOf(next);
    }

    public static void clear() {
        entries = Map.of();
    }

    /** 该「网络 × 锚点 × 线路」的状态；没收到过就是 {@code null}。 */
    @Nullable
    public static StaffLinkStressStatusPacket.Entry find(UUID networkId, GlobalPos anchor, int route) {
        if (networkId == null || anchor == null) {
            return null;
        }
        return entries.get(new Key(networkId, anchor, route));
    }

    private record Key(UUID networkId, GlobalPos anchor, int route) {
    }
}
