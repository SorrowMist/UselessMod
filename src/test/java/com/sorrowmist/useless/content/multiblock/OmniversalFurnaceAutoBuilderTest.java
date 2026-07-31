package com.sorrowmist.useless.content.multiblock;

import com.sorrowmist.useless.content.blocks.multiblock.DirectionalMultiblockPartBlock;
import com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OmniversalFurnaceAutoBuilderTest {
    @Test
    void everyAutoBuiltFunctionalPartUsesTheControllerFacing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (OmniversalAlloyFurnaceStructure.FunctionalPart part
                    : OmniversalAlloyFurnaceStructure.FunctionalPart.values()) {
                var state = OmniversalFurnaceAutoBuilder.expectedState(
                        OmniversalAlloyFurnaceStructure.Part.CASING, 1, part, facing);
                assertEquals(facing, state.getValue(DirectionalMultiblockPartBlock.FACING));
            }
        }
    }
}
