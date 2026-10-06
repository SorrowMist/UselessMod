package com.sorrowmist.useless.compat.ftbultimine;

import dev.ftb.mods.ftbultimine.client.FTBUltimineClient;
import net.minecraft.client.KeyMapping;

/**
 * 读取 FTB Ultimine 连锁键状态的兼容层。
 *
 * <p>造化杖被加入 {@code #ftbultimine:excluded_tools} 后，FTB 不再对它实施连锁，但其按键绑定
 * 依然存在且默认绑定在重音符上。本模组直接读取该绑定的按下状态，而不是自行注册一个同键位的
 * 绑定：后者会在按键设置界面产生冲突提示，且玩家改动 FTB 的键位后必须同步改动两处。
 *
 * <p>本类直接引用 FTB 客户端类型，JVM 在链接本类时即须解析这些类型，因此调用方必须先以
 * {@code ModList.get().isLoaded("ftbultimine")} 判定，再调用本类方法。该判定为假时后面的
 * {@code invokestatic} 不执行，本类不会被解析，也就不会抛出 {@link NoClassDefFoundError}；
 * 缺少该判定的调用会在守卫位置抛出该异常。
 *
 * <p>守卫处的模组 id 须写字面量，不得改为引用本类的常量：常量引用是否被内联为字符串字面量
 * 取决于字段是否满足 {@code static final} 与常量表达式两个条件，一旦某次改动使其退化为运行期
 * 求值，就会生成指向本类的 {@code getstatic}，守卫随之失效，而编译期不会有任何提示。
 *
 * <p>约束：判定必须写在调用方。不得由本类提供 isLoaded 之类的方法供调用方判定——调用本类的
 * 任何方法都会触发类链接，使守卫失效。
 *
 * <p>本类隐含一项前置条件：只允许由客户端代码调用。其依赖的 {@code FTBUltimineClient} 仅存在于
 * 客户端发行版，专用服务器上不存在调用方，因而本类不会被解析，无需在守卫处重复判定物理端。
 */
public final class FtbUltimineChainKeyCompat {

    private FtbUltimineChainKeyCompat() {
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
