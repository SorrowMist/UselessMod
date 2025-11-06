package com.sorrowmist.useless.blocks.advancedalloyfurnace;

import net.minecraft.core.Direction;

/**
 * 传输控制接口，模仿热力系列的传输控制设计
 * 允许按方向配置物品和流体的输入输出
 */
public interface ITransferControllable {

    /**
     * 是否支持输入控制
     */
    boolean hasTransferIn();

    /**
     * 是否支持输出控制
     */
    boolean hasTransferOut();

    /**
     * 获取全局输入状态
     */
    boolean getTransferIn();

    /**
     * 获取全局输出状态
     */
    boolean getTransferOut();

    /**
     * 设置全局传输控制
     * @param input 是否允许输入
     * @param output 是否允许输出
     */
    void setControl(boolean input, boolean output);

    /**
     * 获取指定方向的输出状态
     * @param direction 方向
     * @return 是否允许该方向输出
     */
    boolean getTransferOut(Direction direction);

    /**
     * 设置指定方向的输出状态
     * @param direction 方向
     * @param enabled 是否启用输出
     */
    void setTransferOut(Direction direction, boolean enabled);
}