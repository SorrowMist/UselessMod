package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.oregenerator.OreGeneratorBlock;
import com.sorrowmist.useless.blocks.teleport.UselessDimTeleporter;
import com.sorrowmist.useless.blocks.teleport.UselessDimTeleporter2;
import com.sorrowmist.useless.blocks.teleport.UselessDimTeleporter3;
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
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UselessMod.MODID);

    public static final DeferredBlock<Block> TELEPORT_BLOCK = BLOCKS.register(
            "teleport_block",
            () -> new Block(BlockBehaviour.Properties.of()
                                                     .strength(2.0f, 65536.0f)
                                                     .requiresCorrectToolForDrops()) {
                @Override
                protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack,
                                                                   @NotNull BlockState state,
                                                                   @NotNull Level level,
                                                                   @NotNull BlockPos pos,
                                                                   @NotNull Player player,
                                                                   @NotNull InteractionHand hand,
                                                                   @NotNull BlockHitResult hitResult) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                        // 异步处理传送，避免阻塞主线程
                        Objects.requireNonNull(level.getServer()).execute(() -> {
                            UselessDimTeleporter.teleport(serverPlayer, pos);
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
                protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack,
                                                                   @NotNull BlockState state,
                                                                   @NotNull Level level,
                                                                   @NotNull BlockPos pos,
                                                                   @NotNull Player player,
                                                                   @NotNull InteractionHand hand,
                                                                   @NotNull BlockHitResult hitResult) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                        // 异步处理传送，避免阻塞主线程
                        Objects.requireNonNull(level.getServer()).execute(() -> {
                            UselessDimTeleporter2.teleport(serverPlayer, pos);
                        });
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
            }
    );

    public static final DeferredBlock<Block> TELEPORT_BLOCK_3 = BLOCKS.register(
            "teleport_block_3",
            () -> new Block(BlockBehaviour.Properties.of()
                                                     .strength(2.0f, 65536.0f)
                                                     .requiresCorrectToolForDrops()) {
                @Override
                protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack,
                                                                   @NotNull BlockState state,
                                                                   @NotNull Level level,
                                                                   @NotNull BlockPos pos,
                                                                   @NotNull Player player,
                                                                   @NotNull InteractionHand hand,
                                                                   @NotNull BlockHitResult hitResult) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                        // 异步处理传送，避免阻塞主线程
                        Objects.requireNonNull(level.getServer()).execute(() -> {
                            UselessDimTeleporter3.teleport(serverPlayer, pos);
                        });
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
            }
    );

    public static final DeferredBlock<OreGeneratorBlock> ORE_GENERATOR_BLOCK = BLOCKS.register(
            "ore_generator_block", OreGeneratorBlock::new
    );

    public static final DeferredBlock<Block> ADVANCED_ALLOY_FURNACE_BLOCK = BLOCKS.register(
            "advanced_alloy_furnace_block",
            () -> new Block(BlockBehaviour.Properties.of()
                                                     .strength(3.5f, 8.0f)
                                                     .requiresCorrectToolForDrops())
    );

    private ModBlocks() {}
}
