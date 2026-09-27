package com.sorrowmist.useless.compat.teams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link SharedOwnerBridge} 的 FTB Teams 实现。
 *
 * <p><b>只有本类（以及它引用的 FTB Teams 类）会在装了该模组时被加载。</b></p>
 *
 * <p>用 {@code getTeamForPlayerID} 而不是 {@code getPlayerTeamForPlayerID}：FTB Teams 会给
 * 每个玩家自动建一支「个人队伍」，后者返回的正是它——那样每个人永远都属于一支队伍，
 * 也就永远共享不起来了。这里还额外要求队伍不是个人队伍。</p>
 */
public final class FtbTeamsCompat implements SharedOwnerBridge {

    @Override
    @Nullable
    public UUID teamOwnerId(ServerPlayer player) {
        try {
            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            if (api == null || !api.isManagerLoaded()) {
                // 队伍管理器还没起来（服务端刚启动 / 客户端侧）：当作没有队伍。
                return null;
            }
            TeamManager manager = api.getManager();
            if (manager == null) {
                return null;
            }
            Optional<Team> team = manager.getTeamForPlayerID(player.getUUID());
            if (team.isEmpty()) {
                return null;
            }
            Team found = team.get();
            if (found.isPlayerTeam()) {
                // 单人自带的个人队伍：等同于「没有队伍」。
                return null;
            }
            return found.getId();
        } catch (RuntimeException | LinkageError notReady) {
            // FTB Teams 的 API 在未初始化时会抛 NPE。队伍信息拿不到不该让物流崩掉。
            return null;
        }
    }
}
