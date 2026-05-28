package com.sorrowmist.useless.inventory.slot;

/**
 * 内容变化监听器接口，参考 Mekanism 的 IContentsListener
 * 当槽位内容发生变化时会被调用
 */
@FunctionalInterface
public interface IContentsListener {

    /**
     * 当槽位内容发生变化时调用
     */
    void onContentsChanged();
}
