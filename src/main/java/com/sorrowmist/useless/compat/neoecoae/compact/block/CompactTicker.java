package com.sorrowmist.useless.compat.neoecoae.compact.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 紧凑方块实体的服务器 tick 入口。
 *
 * <p>ECO 的各个主机基类都自己声明了 {@code tick(Level, BlockPos, BlockState)}，
 * 但共同的祖先 {@code NEBlockEntity} 上没有这个方法，泛型擦除后没法统一调用，
 * 因此用一个极小的接口把三家收敛起来。</p>
 */
public interface CompactTicker {

    void tick(Level level, BlockPos pos, BlockState state);
}
