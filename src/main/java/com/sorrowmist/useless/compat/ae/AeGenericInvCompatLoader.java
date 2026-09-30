package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongFluidHandler;
import com.sorrowmist.useless.api.logistics.LongItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link AeGenericInvCompat}。
 *
 * <p>与 {@link AeLogisticsCompatLoader} 同一套手法：常驻类只引用 {@link AeGenericInvBridge}
 * 这个不含 AE2 类型的接口，实现类用 {@code Class.forName} 反射加载，
 * 从而在没装 AE2 的整合包里也不会触发类解析错误。</p>
 *
 * <p>下面的静态方法就是常驻代码要用的全部入口：它们只出现 long 契约与 Minecraft 类型，
 * 因此 {@code StaffLinkTargets} 不必自己判空、判模组，也永远不会碰到 AE2 的类。</p>
 */
public final class AeGenericInvCompatLoader {
    private static final String IMPLEMENTATION =
            "com.sorrowmist.useless.compat.ae.AeGenericInvCompat";

    private static volatile boolean initialized;
    private static volatile AeGenericInvBridge bridge;

    private AeGenericInvCompatLoader() {
    }

    /**
     * 通用库存桥；未加载 AE2 或初始化失败时为 {@code null}。
     *
     * <p>{@code Class.forName(..., true, ...)} 里的 {@code initialize = true} 是刻意的：
     * 实现类的<b>静态块形状自检</b>若发现 AE2 的 API 签名漂移（{@code GenericInternalInventory}
     * 标着 {@code @ApiStatus.Experimental}，确实可能变），会在<i>这里</i>抛
     * {@code ExceptionInInitializerError}（属 {@link LinkageError}），被下面捕获后桥就是
     * {@code null}，无线物流干净地退回旧路径。若写成 {@code false}，那个失败会推迟到第一次
     * 调用，那时已经没人接着了。</p>
     */
    @Nullable
    public static AeGenericInvBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(AeLogisticsCompatLoader.MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, AeGenericInvCompatLoader.class.getClassLoader());
                    bridge = (AeGenericInvBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the AE2 generic-inventory bridge", exception);
                }
            }
        }
        return bridge;
    }

    public static boolean isAvailable() {
        return bridge() != null;
    }

    /** 该坐标是不是有局部通用库存的 AE2 方块；未加载 AE2 时恒为 {@code false}。 */
    public static boolean isGenericInv(Level level, BlockPos pos) {
        AeGenericInvBridge resolved = bridge();
        return resolved != null && resolved.isGenericInv(level, pos);
    }

    /** 该坐标的局部通用库存（物品视角）；不是这种方块时返回 {@code null}。 */
    @Nullable
    public static LongItemHandler itemEndpoint(Level level, BlockPos pos, @Nullable Direction side) {
        AeGenericInvBridge resolved = bridge();
        return resolved == null ? null : resolved.itemEndpoint(level, pos, side);
    }

    /** 该坐标的局部通用库存（流体视角）；不是这种方块时返回 {@code null}。 */
    @Nullable
    public static LongFluidHandler fluidEndpoint(Level level, BlockPos pos, @Nullable Direction side) {
        AeGenericInvBridge resolved = bridge();
        return resolved == null ? null : resolved.fluidEndpoint(level, pos, side);
    }
}
