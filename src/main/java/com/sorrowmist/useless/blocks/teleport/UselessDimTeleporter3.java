// UselessDimTeleporter3.java （对应 uselessdim3 + TELEPORT_BLOCK_3）
package com.sorrowmist.useless.blocks.teleport;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class UselessDimTeleporter3 extends AbstractDimensionTeleporter {

    private static final ResourceKey<Level> DIMENSION_KEY =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                               ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "uselessdim3")
            );

    public static void teleport(ServerPlayer player, BlockPos pos) {
        new UselessDimTeleporter3().handleTeleport(player, pos);
    }

    @Override
    protected ResourceKey<Level> getDimensionKey() {
        return DIMENSION_KEY;
    }

    @Override
    protected Supplier<Block> getTeleportBlock() {
        return ModBlocks.TELEPORT_BLOCK_3;
    }
}