package com.sorrowmist.useless.content.blocks.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmniversalAlloyFurnaceStructureTest {
    @Test
    void templateHasTheDeclaredPartsAndNoDuplicatePositions() {
        var entries = OmniversalAlloyFurnaceStructure.entries();
        Map<OmniversalAlloyFurnaceStructure.Part, Integer> counts =
                new EnumMap<>(OmniversalAlloyFurnaceStructure.Part.class);
        Set<BlockPos> positions = new HashSet<>();
        for (var entry : entries) {
            counts.merge(entry.part(), 1, Integer::sum);
            assertTrue(positions.add(entry.localPos()), "duplicate local position " + entry.localPos());
            assertTrue(entry.localPos().getX() >= -1 && entry.localPos().getX() <= 1);
            assertTrue(entry.localPos().getY() >= 0 && entry.localPos().getY() <= 3);
            assertTrue(entry.localPos().getZ() >= 0 && entry.localPos().getZ() <= 2);
        }

        assertEquals(36, entries.size());
        assertEquals(1, counts.get(OmniversalAlloyFurnaceStructure.Part.CORE));
        assertEquals(1, counts.get(OmniversalAlloyFurnaceStructure.Part.PATTERN_ASSEMBLY));
        assertEquals(1, counts.get(OmniversalAlloyFurnaceStructure.Part.MOLD_HUB));
        assertEquals(OmniversalAlloyFurnaceStructure.COIL_COUNT,
                counts.get(OmniversalAlloyFurnaceStructure.Part.COIL));
        assertEquals(OmniversalAlloyFurnaceStructure.CASING_COUNT,
                counts.get(OmniversalAlloyFurnaceStructure.Part.CASING));
        assertEquals(2, counts.get(OmniversalAlloyFurnaceStructure.Part.AIR));
    }

    @Test
    void localTemplateRotatesAroundTheCoreForEveryHorizontalFacing() {
        BlockPos core = new BlockPos(10, 64, 10);
        BlockPos sample = new BlockPos(1, 2, 2);

        assertEquals(new BlockPos(11, 66, 12),
                OmniversalAlloyFurnaceStructure.toWorld(core, Direction.NORTH, sample));
        assertEquals(new BlockPos(8, 66, 11),
                OmniversalAlloyFurnaceStructure.toWorld(core, Direction.EAST, sample));
        assertEquals(new BlockPos(9, 66, 8),
                OmniversalAlloyFurnaceStructure.toWorld(core, Direction.SOUTH, sample));
        assertEquals(new BlockPos(12, 66, 9),
                OmniversalAlloyFurnaceStructure.toWorld(core, Direction.WEST, sample));

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Set<BlockPos> rotated = new HashSet<>();
            for (var entry : OmniversalAlloyFurnaceStructure.entries()) {
                assertTrue(rotated.add(entry.worldPos(core, facing)),
                        "duplicate world position while facing " + facing);
            }
            assertEquals(36, rotated.size());
            assertTrue(rotated.contains(core));
        }
    }

    @Test
    void validationDoesNotReadBlocksFromAnUnloadedChunk() {
        int[] blockStateReads = {0};
        LevelReader level = (LevelReader) Proxy.newProxyInstance(
                LevelReader.class.getClassLoader(),
                new Class<?>[]{LevelReader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasChunk" -> false;
                    case "getBlockState" -> {
                        blockStateReads[0]++;
                        throw new AssertionError("validation attempted to load an unavailable chunk");
                    }
                    case "toString" -> "unloaded-test-level";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                });

        var result = OmniversalAlloyFurnaceStructure.validate(
                level, new BlockPos(15, 64, 15), Direction.NORTH);

        assertFalse(result.valid());
        assertEquals(0, result.coilTier());
        assertEquals(0, blockStateReads[0]);
    }
}
