package com.sorrowmist.useless.utils.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Objects;

/**
 * 用于表示扩展样板供应器的键，包含BlockPos和Direction
 */
public class PatternProviderKey {
    private final BlockPos pos;
    private final Direction direction;
    
    public PatternProviderKey(BlockPos pos, Direction direction) {
        this.pos = pos;
        this.direction = direction;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternProviderKey that = (PatternProviderKey) o;
        return pos.equals(that.pos) && direction == that.direction;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(pos, direction);
    }
    
    public BlockPos getPos() {
        return pos;
    }
    
    public Direction getDirection() {
        return direction;
    }
}