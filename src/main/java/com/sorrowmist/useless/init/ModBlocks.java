package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.teleport.TeleportBlock;
import com.sorrowmist.useless.blocks.teleport.TeleportBlock2;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Objects;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UselessMod.MODID);

    public static final DeferredBlock<Block> TELEPORT_BLOCK = BLOCKS.register(
            "teleport_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(2.0f, 65536.0f)
                    .requiresCorrectToolForDrops()) {
                @Override
                protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                        // 异步处理传送，避免阻塞主线程
                        Objects.requireNonNull(level.getServer()).execute(() -> {
                            TeleportBlock.handleTeleport(serverPlayer, pos);
                        });
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
            }
    );

    public static final DeferredBlock<Block> TELEPORT_BLOCK_2 = BLOCKS.register(
            "teleport_block_2",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(2.0f, 65536.0f)
                    .requiresCorrectToolForDrops()) {
                @Override
                protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                        // 异步处理传送，避免阻塞主线程
                        Objects.requireNonNull(level.getServer()).execute(() -> {
                            TeleportBlock2.handleTeleport(serverPlayer, pos);
                        });
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
            }
    );
    private ModBlocks() {}
}
