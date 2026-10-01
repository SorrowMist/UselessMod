package com.sorrowmist.useless.compat.create;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.content.stafflink.StaffLinkStressBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 按需加载 {@link CreateStressBridge}。
 *
 * <p>与仓库里其它可选集成同一套手法：常驻类只引用 {@link StaffLinkStressBridge}
 * 这个不含外部模组类型的接口，实现类用 {@code Class.forName} 反射加载，
 * 从而在没装对应模组的整合包里也不会触发类解析错误。</p>
 *
 * <p>模组缺失时这里的所有方法都退化成安全空操作（返回 {@code null} / 什么都不做），
 * 调用方不需要到处写判空。</p>
 */
public final class CreateStressCompatLoader {
    public static final String MOD_ID = "create";

    private static final String IMPLEMENTATION =
            "com.sorrowmist.useless.compat.create.CreateStressBridge";

    private static volatile boolean initialized;
    private static volatile StaffLinkStressBridge bridge;

    private CreateStressCompatLoader() {
    }

    /** 应力搬运桥；未加载对应模组或初始化失败时为 {@code null}。 */
    @Nullable
    public static StaffLinkStressBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, CreateStressCompatLoader.class.getClassLoader());
                    bridge = (StaffLinkStressBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the kinetic stress bridge", exception);
                }
            }
        }
        return bridge;
    }

    public static boolean isAvailable() {
        return bridge() != null;
    }

    /**
     * 该坐标的应力端点；不是动力学方块或未加载对应模组时返回 {@code null}。
     *
     * <p>返回值对调用方不透明，只用于判空。</p>
     */
    @Nullable
    public static Object resolveEndpoint(Level level, BlockPos pos) {
        StaffLinkStressBridge resolved = bridge();
        return resolved == null ? null : resolved.resolveEndpoint(level, pos);
    }

    public static void applyRoute(MinecraftServer server, UUID networkId, int routeIndex,
                                  List<StaffLinkRoute> releases, List<StaffLinkRoute> absorbs) {
        StaffLinkStressBridge resolved = bridge();
        if (resolved != null) {
            resolved.applyRoute(server, networkId, routeIndex, releases, absorbs);
        }
    }

    public static void sweep(MinecraftServer server) {
        StaffLinkStressBridge resolved = bridge();
        if (resolved != null) {
            resolved.sweep(server);
        }
    }

    public static void syncStatus(MinecraftServer server) {
        StaffLinkStressBridge resolved = bridge();
        if (resolved != null) {
            resolved.syncStatus(server);
        }
    }

    public static void clearRuntimeState() {
        StaffLinkStressBridge resolved = bridge();
        if (resolved != null) {
            resolved.clearRuntimeState();
        }
    }
}
