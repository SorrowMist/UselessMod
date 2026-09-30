package com.sorrowmist.useless.content.stafflink;

/**
 * 资源族：一条线路「搬的是什么」的粗分类。
 *
 * <p>引入它是因为同一种资源出现了不同的<b>承载方式</b>：物品既可能是方块容器里的槽位，
 * 也可能是 AE 网络里的一条 {@code AEItemKey} 记录。两者在搬运语义上完全等价（都是「物品」），
 * 所以配对时按 {@link #family()} 比较而不是按 {@link LinkMedium} 比较——否则
 * 「AE 网络 → 箱子」这种最常见的用法会因为在两个不同的 medium 上而永远配不上。</p>
 */
public enum ResourceFamily {
    ITEM,
    FLUID,
    ENERGY,
    CHEMICAL,
    SOURCE
}