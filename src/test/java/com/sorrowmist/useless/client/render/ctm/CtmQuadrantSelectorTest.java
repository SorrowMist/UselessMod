package com.sorrowmist.useless.client.render.ctm;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CtmQuadrantSelectorTest {
    @Test
    void noConnectionUsesMatchingBaseQuadrant() {
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                var tile = CtmQuadrantSelector.select(0, x, y);
                assertTrue(tile.baseTexture());
                assertEquals(x, tile.x());
                assertEquals(y, tile.y());
            }
        }
    }

    @Test
    void singleEdgesSelectTheOuterCtmTiles() {
        assertTile(CtmQuadrantSelector.LEFT, 0, 0, 0, 2);
        assertTile(CtmQuadrantSelector.RIGHT, 1, 1, 1, 3);
        assertTile(CtmQuadrantSelector.UP, 1, 0, 3, 0);
        assertTile(CtmQuadrantSelector.DOWN, 0, 1, 2, 1);
    }

    @Test
    void joinedCornerWithoutDiagonalUsesInnerCtmTile() {
        assertTile(CtmQuadrantSelector.LEFT | CtmQuadrantSelector.UP,
                0, 0, 2, 2);
    }

    @Test
    void completeCornerUsesCornerTile() {
        assertTile(CtmQuadrantSelector.LEFT | CtmQuadrantSelector.UP
                        | CtmQuadrantSelector.UP_LEFT,
                0, 0, 0, 0);
    }

    @Test
    void allEightConnectionsUseTheFourCornerTiles() {
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                assertTile(0xFF, x, y, x, y);
            }
        }
    }

    @Test
    void everyWorldFaceHasTheExpectedTextureAxes() {
        Map<Direction, CtmFaceAxes> expected = Map.of(
                Direction.EAST, new CtmFaceAxes(Direction.NORTH, Direction.DOWN),
                Direction.WEST, new CtmFaceAxes(Direction.SOUTH, Direction.DOWN),
                Direction.NORTH, new CtmFaceAxes(Direction.WEST, Direction.DOWN),
                Direction.SOUTH, new CtmFaceAxes(Direction.EAST, Direction.DOWN),
                Direction.DOWN, new CtmFaceAxes(Direction.EAST, Direction.SOUTH),
                Direction.UP, new CtmFaceAxes(Direction.EAST, Direction.NORTH));
        expected.forEach((face, axes) -> {
            assertEquals(axes, CtmFaceAxes.forFace(face));
            assertEquals(axes.right().getOpposite(), CtmFaceAxes.forFace(face).left());
            assertEquals(axes.down().getOpposite(), CtmFaceAxes.forFace(face).up());
        });
    }

    private static void assertTile(int mask, int cornerX, int cornerY, int tileX, int tileY) {
        var tile = CtmQuadrantSelector.select(mask, cornerX, cornerY);
        assertFalse(tile.baseTexture());
        assertEquals(tileX, tile.x());
        assertEquals(tileY, tile.y());
    }
}
