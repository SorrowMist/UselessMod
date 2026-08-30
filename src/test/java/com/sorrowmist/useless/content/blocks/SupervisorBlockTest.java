package com.sorrowmist.useless.content.blocks;

import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupervisorBlockTest {
    @Test
    void defaultsNorthAndFacesBackTowardThePlayer() {
        SupervisorBlock block = ModBlocks.SUPERVISOR.get();

        assertEquals(Direction.NORTH, block.defaultBlockState().getValue(SupervisorBlock.FACING));
        for (Direction playerDirection : new Direction[]{
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            assertEquals(playerDirection.getOpposite(),
                    SupervisorBlock.facingForPlacement(playerDirection));
        }
    }

    @Test
    void usesTheConfiguredHardness() {
        SupervisorBlock block = ModBlocks.SUPERVISOR.get();

        assertEquals(2.0F, block.defaultBlockState().getDestroySpeed(null, BlockPos.ZERO));
    }

    @Test
    void hasOneByOneByOneShapeAtTheBlockPosition() {
        SupervisorBlock block = ModBlocks.SUPERVISOR.get();

        var bounds = block.defaultBlockState().getCollisionShape(null, BlockPos.ZERO).bounds();

        assertEquals(0.0, bounds.minX);
        assertEquals(0.0, bounds.minY);
        assertEquals(0.0, bounds.minZ);
        assertEquals(1.0, bounds.maxX);
        assertEquals(1.0, bounds.maxY);
        assertEquals(1.0, bounds.maxZ);
    }
}
