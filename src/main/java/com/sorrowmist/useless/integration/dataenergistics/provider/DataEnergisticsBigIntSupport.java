package com.sorrowmist.useless.integration.dataenergistics.provider;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 「数据能源的 bigint API 到底在不在」的能力探针。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>{@code BigIntegerCraftingProviderAdapter} / {@code BigIntegerCraftingAdmission} 是数据能源
 * <b>3.3.0 才引入</b>的接口，3.2.2 及更早没有。我们的 bigint 适配器子类
 * {@link AlloyFurnaceBigIntegerCraftingAdapter} 实现了它，所以<b>只要那个子类被加载，
 * 旧版数据能源下就会 NoClassDefFoundError</b>。</p>
 *
 * <p>解决办法不是反射，而是<b>把子类的加载推迟到一个运行期判定之后</b>：
 * {@link AlloyFurnaceCountedCraftingAdapter#forAdvancedAlloyFurnace} 与
 * {@link AlloyFurnaceCountedCraftingAdapter#forMePatternAssembly} 先读这里的
 * {@link #AVAILABLE}，为真才去 new 子类。于是：</p>
 *
 * <ul>
 *   <li><b>旧版数据能源</b>：探针为假 ⇒ 子类永不加载 ⇒ 只走长版 counted 路径，能正常启动；</li>
 *   <li><b>升级到 3.3.0 之后</b>：重启时探针为真 ⇒ 自动启用原生 bigint 派发。</li>
 * </ul>
 *
 * <p>结论是「装旧版能玩、换新版自动恢复」，<b>不需要改代码、不需要配置</b>。本类的静态字段
 * 只在第一次触达时求值一次，因此换版本必须重启游戏（这本来就成立）。</p>
 *
 * <h2>为什么先查资源再 Class.forName</h2>
 *
 * <p>资源查找（{@code getResource}）只看 jar 里有没有这个 class 文件，<b>完全不触发类加载与链接</b>，
 * 是最便宜的否定路径；只有它命中时才做一次 {@code Class.forName(initialize=false)} 确认类型
 * 真的可解析。任何一步失败都算「不可用」并降级 —— 这是刻意的：宁可少一个特性，也不要启动时崩。</p>
 */
final class DataEnergisticsBigIntSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 3.3.0 才引入的接口；它的存在与否就是「原生 bigint 派发可用吗」的唯一判据。 */
    private static final String BIGINT_ADAPTER_TYPE =
            "com.fish_dan_.data_energistics.api.crafting.dispatch.BigIntegerCraftingProviderAdapter";

    /** 数据能源是否提供 3.3.0 的 bigint 派发 API（进程内只求值一次）。 */
    static final boolean AVAILABLE = probe();

    private DataEnergisticsBigIntSupport() {
    }

    private static boolean probe() {
        boolean available = classFilePresent() && typeResolvable();
        if (available) {
            LOGGER.info("DataEnergistics bigint API detected: native bigint dispatch enabled");
        } else {
            LOGGER.info("DataEnergistics bigint API absent (pre-3.3.0): long-window counted dispatch only");
        }
        return available;
    }

    /** 只查 jar 里的 class 文件，不触发加载/链接。 */
    private static boolean classFilePresent() {
        String resource = BIGINT_ADAPTER_TYPE.replace('.', '/') + ".class";
        ClassLoader loader = DataEnergisticsBigIntSupport.class.getClassLoader();
        return loader != null && loader.getResource(resource) != null;
    }

    /** 确认类型真的可解析（{@code initialize=false}：不跑静态初始化，不牵连依赖）。 */
    private static boolean typeResolvable() {
        try {
            Class.forName(BIGINT_ADAPTER_TYPE, false, DataEnergisticsBigIntSupport.class.getClassLoader());
            return true;
        } catch (Throwable unavailable) {
            return false;
        }
    }
}
