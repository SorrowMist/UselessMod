package com.sorrowmist.useless.compat.create;

import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.content.stafflink.StaffLinkStressBridge;
import com.sorrowmist.useless.network.StaffLinkStressStatusPacket;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link StaffLinkStressBridge} 的实现。
 *
 * <p>本类引用了动力学模组的类型，因此只能由
 * {@link CreateStressCompatLoader} 在确认模组存在之后反射加载。它自己不含任何逻辑，
 * 只是把调用转给 {@link StaffLinkStressState}，并把运行状态下发给正在看界面的玩家。</p>
 */
public final class CreateStressBridge implements StaffLinkStressBridge {

    @Override
    @Nullable
    public Object resolveEndpoint(Level level, BlockPos pos) {
        return StaffLinkStressState.INSTANCE.resolveEndpoint(level, pos);
    }

    @Override
    public void applyRoute(MinecraftServer server, UUID networkId, int routeIndex,
                           List<StaffLinkRoute> releases, List<StaffLinkRoute> absorbs) {
        StaffLinkStressState.INSTANCE.applyRoute(server, networkId, routeIndex, releases, absorbs);
    }

    @Override
    public void sweep(MinecraftServer server) {
        StaffLinkStressState.INSTANCE.sweep(server);
    }

    /**
     * 把状态发给「正开着无线物流界面」的玩家，且只发他自己有权编辑的网络。
     *
     * <p>不做广播：这些数字只对正在配置的人有意义，别人既看不懂也不该看到别人的网络规模。
     * 即使一条都没有也要发一个空包——客户端靠它清掉已经失效的旧状态。</p>
     */
    @Override
    public void syncStatus(MinecraftServer server) {
        List<StaffLinkStressStatusPacket.Entry> all = StaffLinkStressState.INSTANCE.entries();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof StaffLinkMenu)) {
                continue;
            }
            List<StaffLinkStressStatusPacket.Entry> visible = new ArrayList<>(all.size());
            for (StaffLinkStressStatusPacket.Entry entry : all) {
                if (StaffLinkManager.canAccess(server, player, entry.networkId())) {
                    visible.add(entry);
                }
            }
            PacketDistributor.sendToPlayer(player, new StaffLinkStressStatusPacket(List.copyOf(visible)));
        }
    }

    @Override
    public void clearRuntimeState() {
        StaffLinkStressState.INSTANCE.clearRuntimeState();
    }
}
