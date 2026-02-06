package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.content.blocks.OreGeneratorBlock;
import com.sorrowmist.useless.content.blocks.UselessGlassBlock;
import com.sorrowmist.useless.world.teleport.UselessDimTeleporter;
import com.sorrowmist.useless.world.teleport.UselessDimTeleporter2;
import com.sorrowmist.useless.world.teleport.UselessDimTeleporter3;
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

import java.util.LinkedHashMap;
import java.util.Map;
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

    public static final DeferredBlock<AdvancedAlloyFurnaceBlock> ADVANCED_ALLOY_FURNACE_BLOCK = BLOCKS.register(
            "advanced_alloy_furnace_block",
            AdvancedAlloyFurnaceBlock::new
    );

    // 无用玻璃方块 - 防爆，Shift+右键快速破坏
    public static final Map<String, DeferredBlock<UselessGlassBlock>> USELESS_GLASS_BLOCKS = new LinkedHashMap<>();

    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_1 = registerGlassBlock(
            "useless_glass_tier_1", 1.0f, 1200.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_2 = registerGlassBlock(
            "useless_glass_tier_2", 1.5f, 1200.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_3 = registerGlassBlock(
            "useless_glass_tier_3", 2.0f, 2400.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_4 = registerGlassBlock(
            "useless_glass_tier_4", 2.5f, 2400.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_5 = registerGlassBlock(
            "useless_glass_tier_5", 3.0f, 3600.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_6 = registerGlassBlock(
            "useless_glass_tier_6", 3.5f, 3600.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_7 = registerGlassBlock(
            "useless_glass_tier_7", 4.0f, 4800.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_8 = registerGlassBlock(
            "useless_glass_tier_8", 4.5f, 6000.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_9 = registerGlassBlock(
            "useless_glass_tier_9", 5.0f, 7200.0f);

    private ModBlocks() {}

    private static DeferredBlock<UselessGlassBlock> registerGlassBlock(String name, float hardness, float resistance) {
        DeferredBlock<UselessGlassBlock> block = BLOCKS.register(name, () -> new UselessGlassBlock(
                BlockBehaviour.Properties.of().strength(hardness, resistance)) {}
        );
        USELESS_GLASS_BLOCKS.put(name, block);
        return block;
    }
}
