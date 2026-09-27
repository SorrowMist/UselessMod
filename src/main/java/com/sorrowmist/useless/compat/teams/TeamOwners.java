package com.sorrowmist.useless.compat.teams;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * 解析「一个玩家当前的归属 ID」。
 *
 * <p>优先级：<b>FTB Teams 队伍 → 原版计分板队伍 → 玩家自己</b>。都没有队伍时就是他本人，
 * 于是每人一套独立网络；一旦组了队，队友之间共享同一批网络。</p>
 *
 * <p>原版计分板队伍只有名字、没有 UUID，所以用队伍名派生一个稳定 UUID：同一支队伍的每个
 * 成员算出来都一样，正好当共享键。</p>
 *
 * <p><b>权限判定的唯一依据是 {@link #ownerIdOf} 的相等比较</b>：玩家能操作一张网络，
 * 当且仅当他的归属 ID 与那张网络的归属 ID 相同。这样「退队」自然失去访问权，
 * 「入队」自然获得访问权，不需要额外的成员表。</p>
 */
public final class TeamOwners {
    /** 派生 UUID 的前缀，避免与真实的玩家/队伍 UUID 撞车。 */
    private static final String VANILLA_TEAM_PREFIX = "useless_mod:team:";

    private TeamOwners() {
    }

    /** 该玩家当前的归属 ID；永远有值（最差就是他自己）。 */
    public static UUID ownerIdOf(ServerPlayer player) {
        return sharedOwnerId(player).orElse(player.getUUID());
    }

    /** 该玩家所属队伍的共享 ID；没有队伍时为空。 */
    public static Optional<UUID> sharedOwnerId(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        SharedOwnerBridge ftb = FtbTeamsCompatLoader.bridge();
        if (ftb != null) {
            UUID teamId = ftb.teamOwnerId(player);
            if (teamId != null) {
                return Optional.of(teamId);
            }
        }
        return vanillaTeamOwnerId(player);
    }

    /** 原版计分板队伍：只有名字，所以派生一个稳定 UUID 当共享键。 */
    private static Optional<UUID> vanillaTeamOwnerId(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team == null) {
            return Optional.empty();
        }
        String name = team.getName();
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(deriveTeamId(name));
    }

    /** 队伍名 → 稳定 UUID。 */
    public static UUID deriveTeamId(String teamName) {
        return UUID.nameUUIDFromBytes(
                (VANILLA_TEAM_PREFIX + teamName).getBytes(StandardCharsets.UTF_8));
    }
}
