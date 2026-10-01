package com.sorrowmist.useless.compat.ftbultimine;

import dev.ftb.mods.ftbultimine.client.FTBUltimineClient;
import net.minecraft.client.KeyMapping;

/**
 * {@link FtbUltimineChainKeyCompat} 的实现体，也是本模组唯一引用 FTB 客户端类型的类。
 *
 * <p>本类只在客户端且 FTB Ultimine 确已加载后由门面经反射加载，因此这里的客户端类型引用在
 * 专用服务器或未安装 FTB 的实例上不会被解析，也就不会在调用守卫方法的那一行抛出
 * {@link NoClassDefFoundError}，使「先判断是否加载」的守卫本身失效。
 */
public final class FtbUltimineChainKeyCompatImpl {

    private FtbUltimineChainKeyCompatImpl() {
    }

    /**
     * 读取 FTB 连锁键的按下状态。
     *
     * <p>该绑定由 FTB 在客户端构造时注册，正常不会为 null；判空仅用于覆盖注册尚未完成的极早时点。
     */
    public static boolean isChainKeyDown() {
        KeyMapping key = FTBUltimineClient.keyBindUltimine;
        return key != null && key.isDown();
    }
}
