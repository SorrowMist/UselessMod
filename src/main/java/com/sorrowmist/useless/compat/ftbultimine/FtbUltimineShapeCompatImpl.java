package com.sorrowmist.useless.compat.ftbultimine;

import dev.ftb.mods.ftbultimine.FTBUltimine;
import dev.ftb.mods.ftbultimine.FTBUltiminePlayerData;
import dev.ftb.mods.ftbultimine.api.shape.Shape;
import dev.ftb.mods.ftbultimine.shape.ShapeRegistry;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@link FtbUltimineShapeCompat} 的实现体，也是本模组唯一允许引用 FTB 类型的类。
 *
 * <p>本类只在 FTB Ultimine 确已加载后由门面经反射加载，因此这里的 FTB 类型引用在未安装
 * FTB 的实例上不会被解析，也就不会在调用守卫方法的那一行抛出 {@link NoClassDefFoundError}，
 * 使「先判断是否加载」的守卫本身失效。
 */
public final class FtbUltimineShapeCompatImpl {

    private FtbUltimineShapeCompatImpl() {
    }

    /**
     * 读取 FTB 侧当前形状的标识 path。
     *
     * <p>FTB 在服务器停止时不会清空其玩家数据表，但 {@code instance} 本身在模组构造前为 null，
     * 因此两处都做空值防护；返回 null 表示「本次读不到」，由调用方维持既有形状不变。
     */
    public static String getCurrentShapeId(ServerPlayer player) {
        FTBUltimine ultimine = FTBUltimine.instance;
        if (ultimine == null) {
            return null;
        }
        FTBUltiminePlayerData data = ultimine.getOrCreatePlayerData(player);
        Shape shape = ShapeRegistry.INSTANCE.getShape(data.getCurrentShapeIndex());
        return shape == null ? null : shape.getName().getPath();
    }

    /**
     * 请求 FTB 切换形状。
     *
     * <p>调用 FTB 自己的 {@code modeChanged}：下标循环、缓存清理与下发提示都由 FTB 完成，
     * 本模组只在其后读结果，避免两端各存一份下标而错位。
     */
    public static boolean cycleShape(ServerPlayer player, boolean next) {
        FTBUltimine ultimine = FTBUltimine.instance;
        if (ultimine == null) {
            return false;
        }
        ultimine.modeChanged(player, next);
        return true;
    }
}
