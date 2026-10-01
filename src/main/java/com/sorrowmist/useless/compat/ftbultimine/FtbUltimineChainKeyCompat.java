package com.sorrowmist.useless.compat.ftbultimine;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;

/**
 * 读取 FTB Ultimine 连锁键状态的门面。
 *
 * <p>造化杖被加入 {@code #ftbultimine:excluded_tools} 后，FTB 不再对它实施连锁，但其按键绑定
 * 依然存在且默认绑定在重音符上。本模组直接读取该绑定的按下状态，而不是自行注册一个同键位的
 * 绑定：后者会在按键设置界面产生冲突提示，且玩家改动 FTB 的键位后必须同步改动两处。
 *
 * <p>与 {@code FtbUltimineShapeCompat} 分离而非合并，原因是职责的物理边界不同：形状读取发生在
 * 服务端，而本类读取的 {@code FTBUltimineClient} 只存在于客户端。二者若合为一类，专用服务器
 * 加载该实现类时会因无法解析客户端类型而抛出 {@link NoClassDefFoundError}。
 *
 * <p>本类刻意不引用 FTB 的任何类型，实现被隔离在 {@code FtbUltimineChainKeyCompatImpl}，
 * 仅在客户端且该模组确已加载后经反射加载；其余情况下恒返回未按下。
 */
public final class FtbUltimineChainKeyCompat {

    public static final String MOD_ID = "ftbultimine";

    /** 实现类名，与门面同包，独立成类以避免 FTB 类型进入本类的常量池。 */
    private static final String IMPL_CLASS_NAME =
            "com.sorrowmist.useless.compat.ftbultimine.FtbUltimineChainKeyCompatImpl";

    private static Method isChainKeyDownMethod;
    private static boolean resolved;

    private FtbUltimineChainKeyCompat() {
    }

    /**
     * FTB 的连锁键当前是否按下。
     *
     * @return 按下返回 true；FTB 缺失、运行于专用服务器或调用失败时返回 false
     */
    public static boolean isChainKeyDown() {
        resolve();
        if (isChainKeyDownMethod == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isChainKeyDownMethod.invoke(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return false;
        }
    }

    /**
     * 一次性解析实现类的方法句柄。
     *
     * <p>除模组加载状态外还必须确认运行于客户端：实现类引用的 {@code FTBUltimineClient} 只存在于
     * 客户端发行版，专用服务器上尝试解析会直接失败。解析失败即永久保持「未按下」，重试没有意义。
     */
    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!FMLEnvironment.dist.isClient() || !ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            Class<?> impl = Class.forName(IMPL_CLASS_NAME, true,
                    FtbUltimineChainKeyCompat.class.getClassLoader());
            isChainKeyDownMethod = impl.getMethod("isChainKeyDown");
        } catch (ReflectiveOperationException | LinkageError exception) {
            isChainKeyDownMethod = null;
        }
    }
}
