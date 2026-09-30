package com.sorrowmist.useless.client.gui;

/**
 * 样板搜索范围。
 *
 * <p>样板同时包含输入侧、输出侧与模具槽，三侧语义互不相同，因此搜索需要显式区分范围，
 * 否则「按产物找样板」与「按原料找样板」会互相干扰。模具集散中心的内容全部为模具，
 * 复用 {@link #MOLD} 即可获得一致的范围语义。</p>
 *
 * <p>枚举声明顺序即界面按钮的循环顺序，须与「输入 → 输出 → 模具」的查找次序一致；
 * 调整声明顺序会同时改变按钮的循环路径、初始范围与默认提示文本。</p>
 */
enum PatternSearchMode {
    /** 只匹配样板输入。 */
    IN,
    /** 只匹配样板输出。 */
    OUT,
    /** 只匹配样板设置所需的模具。 */
    MOLD,
    /** 同时匹配输入、输出与模具。 */
    ALL;

    boolean matchesInput() {
        return this == IN || this == ALL;
    }

    boolean matchesOutput() {
        return this == OUT || this == ALL;
    }

    boolean matchesMold() {
        return this == MOLD || this == ALL;
    }

    PatternSearchMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}