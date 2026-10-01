package com.sorrowmist.useless.compat.ftbultimine;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * 把 FTB Ultimine 的当前形状映射为本模组连锁形状的门面。
 *
 * <p>形状的「权威」留在 FTB：玩家沿用 FTB 自己的形状键循环切换，本模组不再维护第二套
 * 切换按键与下标，只读取其结果。映射按形状<b>标识</b>而非下标进行，因此即使第三方模组
 * 在 FTB 形状表中插入自定义形状，也不会让其余形状整体错位；无法识别的标识回退为默认形状。
 *
 * <p>与 {@code ExDeorumCompat} 采用同一策略：本类刻意不引用 FTB 的任何类型，实现被隔离在
 * {@code FtbUltimineShapeCompatImpl}，仅在该模组确已加载后经反射加载。FTB 未安装时全部方法
 * 安全返回「未接管」，本模组原有的 Shift + 滚轮切换逻辑完全不变。
 */
public final class FtbUltimineShapeCompat {

    public static final String MOD_ID = "ftbultimine";

    /** 实现类名，与门面同包，独立成类以避免 FTB 类型进入本类的常量池。 */
    private static final String IMPL_CLASS_NAME =
            "com.sorrowmist.useless.compat.ftbultimine.FtbUltimineShapeCompatImpl";

    private static Method getShapeIdMethod;
    private static Method cycleShapeMethod;
    private static boolean resolved;

    private FtbUltimineShapeCompat() {
    }

    /** FTB Ultimine 是否已加载。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * FTB 是否已加载且方法句柄解析成功，即可由它接管形状。
     *
     * <p>供调用方在读写之前选择分支。若不先判断而直接试调一次，FTB 缺席时这次尝试会白跑，
     * 更糟的是调用方可能已经按「切换成功」走出了另一半逻辑。
     */
    public static boolean isAvailable() {
        resolve();
        return cycleShapeMethod != null;
    }

    /**
     * 读取 FTB 当前形状的标识 path（如 {@code small_tunnel}）。
     *
     * @return 标识 path；FTB 缺失、尚未初始化或调用失败时为 null
     */
    public static String getCurrentShapeId(ServerPlayer player) {
        resolve();
        if (getShapeIdMethod == null) {
            return null;
        }
        try {
            return (String) getShapeIdMethod.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            // 与 cycleShape 保持同一捕获面：反射调用可能因参数不匹配抛 IllegalArgumentException，
            // 被调方自身抛出的异常则会被包装成 InvocationTargetException。两者都必须降级为
            // 「未接管」，否则异常会穿透到每 tick 的调用点。
            return null;
        }
    }

    /**
     * 请求 FTB 切换形状，使本模组的滚轮切换与 FTB 状态保持一致。
     *
     * @return true 表示已由 FTB 接管，调用方不应再自行循环下标
     */
    public static boolean cycleShape(ServerPlayer player, boolean next) {
        resolve();
        if (cycleShapeMethod == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(cycleShapeMethod.invoke(null, player, next));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return false;
        }
    }

    /**
     * 一次性解析实现类的方法句柄。
     *
     * <p>解析失败即永久保持「未接管」：FTB 的加载状态在实例生命周期内不会改变，重试没有意义。
     */
    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!isLoaded()) {
            return;
        }
        try {
            Class<?> impl = Class.forName(IMPL_CLASS_NAME, true,
                    FtbUltimineShapeCompat.class.getClassLoader());
            getShapeIdMethod = impl.getMethod("getCurrentShapeId", ServerPlayer.class);
            cycleShapeMethod = impl.getMethod("cycleShape", ServerPlayer.class, boolean.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            getShapeIdMethod = null;
            cycleShapeMethod = null;
        }
    }
}
