package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongFluidHandler;
import com.sorrowmist.useless.api.logistics.LongItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link AeLogisticsCompat}。
 *
 * <p>与 {@code ArsSourceCompatLoader} / {@code MekanismCompatLoader} 同一套手法：常驻类只引用
 * {@link AeLogisticsBridge} 这个不含 AE2 类型的接口，实现类用 {@code Class.forName} 反射加载，
 * 从而在没装 AE2 的整合包里也不会触发类解析错误。</p>
 *
 * <p>下面的静态方法就是常驻代码要用的全部入口：它们只出现 long 契约与 Minecraft 类型，
 * 因此 {@code StaffLinkTargets} 不必自己判空、判模组，也永远不会碰到 AE2 的类。</p>
 */
public final class AeLogisticsCompatLoader {
    public static final String MOD_ID = "ae2";

    private static final String IMPLEMENTATION = "com.sorrowmist.useless.compat.ae.AeLogisticsCompat";

    private static volatile boolean initialized;
    private static volatile AeLogisticsBridge bridge;

    private AeLogisticsCompatLoader() {
    }

    /** AE 物流桥；未加载 AE2 或初始化失败时为 {@code null}。 */
    @Nullable
    public static AeLogisticsBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, AeLogisticsCompatLoader.class.getClassLoader());
                    bridge = (AeLogisticsBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the AE2 logistics bridge", exception);
                }
            }
        }
        return bridge;
    }

    public static boolean isAvailable() {
        return bridge() != null;
    }

    /** 该坐标是不是 AE 网络端点；未加载 AE2 时恒为 {@code false}。 */
    public static boolean isEndpoint(Level level, BlockPos pos) {
        AeLogisticsBridge resolved = bridge();
        return resolved != null && resolved.isEndpoint(level, pos);
    }

    /** 该坐标的 AE 网络端点（物品）；不是可用端点时返回 {@code null}。 */
    @Nullable
    public static LongItemHandler itemEndpoint(Level level, BlockPos pos) {
        AeLogisticsBridge resolved = bridge();
        return resolved == null ? null : resolved.itemEndpoint(level, pos);
    }

    /** 该坐标的 AE 网络端点（流体）；不是可用端点时返回 {@code null}。 */
    @Nullable
    public static LongFluidHandler fluidEndpoint(Level level, BlockPos pos) {
        AeLogisticsBridge resolved = bridge();
        return resolved == null ? null : resolved.fluidEndpoint(level, pos);
    }
}