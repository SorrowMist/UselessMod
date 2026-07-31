package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionalMultiblockPartBlockTest {
    @Test
    void placementFacesBackTowardThePlayerInAllSixDirections() {
        for (Direction playerDirection : Direction.values()) {
            assertEquals(playerDirection.getOpposite(),
                    DirectionalMultiblockPartBlock.facingForPlacement(playerDirection));
        }
    }

    @Test
    void missingSavedPropertyFallsBackToNorthAndTransformsCorrectly() {
        var block = ModBlocks.ME_PATTERN_ASSEMBLY.get();
        var north = block.defaultBlockState();
        assertEquals(Direction.NORTH,
                north.getValue(DirectionalMultiblockPartBlock.FACING));
        assertEquals(Direction.EAST, block.rotate(north, Rotation.CLOCKWISE_90)
                .getValue(DirectionalMultiblockPartBlock.FACING));
        assertEquals(Direction.SOUTH, block.mirror(north, Mirror.LEFT_RIGHT)
                .getValue(DirectionalMultiblockPartBlock.FACING));
    }

    @Test
    void verticalFacingsSurviveHorizontalTransforms() {
        var block = ModBlocks.ME_PATTERN_ASSEMBLY.get();
        for (Direction facing : new Direction[] { Direction.UP, Direction.DOWN }) {
            var state = block.defaultBlockState()
                    .setValue(DirectionalMultiblockPartBlock.FACING, facing);
            assertEquals(facing, block.rotate(state, Rotation.CLOCKWISE_90)
                    .getValue(DirectionalMultiblockPartBlock.FACING));
            assertEquals(facing, block.mirror(state, Mirror.FRONT_BACK)
                    .getValue(DirectionalMultiblockPartBlock.FACING));
        }
    }
}
