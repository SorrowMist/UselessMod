package com.sorrowmist.useless.compat.teams;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 「共享归属」的桥接口：某个玩家所属队伍的 UUID。
 *
 * <p>与 {@code AeLogisticsBridge} 同一套手法：本接口<b>不含任何队伍模组的类型</b>，
 * 实现类由 {@link FtbTeamsCompatLoader} 反射加载，因此没装 FTB Teams 时
 * {@link TeamOwners} 照样能加载、只是永远拿不到队伍。</p>
 */
public interface SharedOwnerBridge {

    /**
     * 该玩家所属队伍的 UUID；不在任何队伍里时返回 {@code null}。
     *
     * <p>「单人自带的个人队伍」不算队伍——那本质上就是他自己。</p>
     */
    @Nullable
    UUID teamOwnerId(ServerPlayer player);
}
