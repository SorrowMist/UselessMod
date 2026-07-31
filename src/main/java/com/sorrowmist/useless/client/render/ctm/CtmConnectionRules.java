package com.sorrowmist.useless.client.render.ctm;

import com.sorrowmist.useless.content.blocks.multiblock.DirectionalMultiblockPartBlock;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockAlloyFurnaceCoreBlock;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockPartBlock;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class CtmConnectionRules {
    public static final int NONE = 0;
    public static final int FURNACE = 1;
    private static final int COIL_OFFSET = 100;

    private CtmConnectionRules() {
    }

    public static int family(BlockState state) {
        return family(state.getBlock());
    }

    public static int family(Block block) {
        if (block instanceof UselessCoilBlock coil) {
            return COIL_OFFSET + coil.tier();
        }
        if (block instanceof MultiblockAlloyFurnaceCoreBlock
                || block instanceof DirectionalMultiblockPartBlock
                || block.getClass() == MultiblockPartBlock.class) {
            return FURNACE;
        }
        return NONE;
    }

    public static boolean connects(BlockState first, BlockState second) {
        int family = family(first);
        return family != NONE && family == family(second);
    }
}
