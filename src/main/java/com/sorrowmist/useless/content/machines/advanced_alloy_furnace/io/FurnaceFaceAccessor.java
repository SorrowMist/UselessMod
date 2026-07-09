package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import net.minecraft.core.Direction;

/**
 * 方向感知处理器所需的最小面配置访问接口。
 * <p>
 * 用于解耦 io 包中的方向感知处理器与方块实体，避免包间循环依赖。
 */
public interface FurnaceFaceAccessor {

    /**
     * 获取方块的水平朝向。
     */
    Direction getFacing();

    /**
     * 获取指定逻辑面的输入输出模式。
     */
    FurnaceFaceMode getFaceMode(FurnaceFace face);
}
