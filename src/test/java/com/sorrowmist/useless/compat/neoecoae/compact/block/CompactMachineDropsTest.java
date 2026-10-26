package com.sorrowmist.useless.compat.neoecoae.compact.block;

import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class CompactMachineDropsTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void creativeBreakUsesExistingLootPathWithTheLiveBlockEntity() {
        Level level = mock(Level.class);
        Player player = mock(Player.class);
        BlockState state = mock(BlockState.class);
        BlockEntity blockEntity = mock(BlockEntity.class);
        BlockPos pos = new BlockPos(12, 64, -8);
        when(player.isCreative()).thenReturn(true);
        when(level.getBlockEntity(pos)).thenReturn(blockEntity);

        try (var drops = mockStatic(Block.class)) {
            CompactMachineDrops.dropForCreativePlayer(level, pos, state, player);

            drops.verify(() -> Block.dropResources(state, level, pos, blockEntity, player, ItemStack.EMPTY));
            drops.verifyNoMoreInteractions();
            verify(level).isClientSide();
            verify(level).getBlockEntity(pos);
            verifyNoMoreInteractions(level);
        }
    }

    @Test
    void survivalBreakLeavesDropsToTheNormalHarvestPath() {
        Level level = mock(Level.class);
        Player player = mock(Player.class);
        BlockState state = mock(BlockState.class);

        try (var drops = mockStatic(Block.class)) {
            CompactMachineDrops.dropForCreativePlayer(level, BlockPos.ZERO, state, player);

            drops.verifyNoInteractions();
            verify(level).isClientSide();
            verifyNoMoreInteractions(level);
        }
    }

    @Test
    void clientDoesNotReadTheBlockEntityOrSpawnDuplicateDrops() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        Player player = mock(Player.class);
        BlockState state = mock(BlockState.class);

        try (var drops = mockStatic(Block.class)) {
            CompactMachineDrops.dropForCreativePlayer(level, BlockPos.ZERO, state, player);

            drops.verifyNoInteractions();
            verify(level).isClientSide();
            verifyNoMoreInteractions(level);
            verifyNoInteractions(player);
        }
    }
}
