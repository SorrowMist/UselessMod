package com.sorrowmist.useless.client.render.ctm;

import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CtmConnectionMaskTest {
    private static final BlockPos ORIGIN = new BlockPos(4, 8, 12);

    @Test
    void everyFaceUsesItsLocalRightDirection() {
        BlockState casing = ModBlocks.OMNIVERSAL_FURNACE_CASING.get().defaultBlockState();
        for (Direction face : Direction.values()) {
            Map<BlockPos, BlockState> states = new HashMap<>();
            states.put(ORIGIN, casing);
            states.put(ORIGIN.relative(CtmFaceAxes.forFace(face).right()), casing);

            int mask = CtmConnectionMask.forFace(
                    CtmConnectionMask.collect(level(states), ORIGIN, casing), face);
            assertNotEquals(0, mask & CtmQuadrantSelector.RIGHT, face.toString());
        }
    }

    @Test
    void outwardSameFamilyBlockOccludesAnOtherwiseConnectedEdge() {
        BlockState casing = ModBlocks.OMNIVERSAL_FURNACE_CASING.get().defaultBlockState();
        Direction face = Direction.NORTH;
        BlockPos left = ORIGIN.relative(CtmFaceAxes.forFace(face).left());
        Map<BlockPos, BlockState> states = new HashMap<>();
        states.put(ORIGIN, casing);
        states.put(left, casing);

        int visibleMask = CtmConnectionMask.forFace(
                CtmConnectionMask.collect(level(states), ORIGIN, casing), face);
        assertNotEquals(0, visibleMask & CtmQuadrantSelector.LEFT);

        states.put(left.relative(face), casing);
        int occludedMask = CtmConnectionMask.forFace(
                CtmConnectionMask.collect(level(states), ORIGIN, casing), face);
        assertEquals(0, occludedMask & CtmQuadrantSelector.LEFT);
    }

    private static BlockAndTintGetter level(Map<BlockPos, BlockState> states) {
        return (BlockAndTintGetter) Proxy.newProxyInstance(
                BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> states.getOrDefault(
                            ((BlockPos) args[0]).immutable(), Blocks.AIR.defaultBlockState());
                    case "toString" -> "ctm-test-level";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }
}
