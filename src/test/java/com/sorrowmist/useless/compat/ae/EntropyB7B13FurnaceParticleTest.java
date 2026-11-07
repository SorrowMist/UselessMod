package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntropyB7B13FurnaceParticleTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static AdvancedAlloyFurnaceBlockEntity furnace(ServerLevel level) throws Exception {
        var furnace = mock(AdvancedAlloyFurnaceBlockEntity.class, CALLS_REAL_METHODS);
        var position = BlockEntity.class.getDeclaredField("worldPosition");
        position.setAccessible(true);
        position.set(furnace, new BlockPos(10, 60, -2));
        var world = BlockEntity.class.getDeclaredField("level");
        world.setAccessible(true);
        world.set(furnace, level);
        return furnace;
    }
    private static void effect(AdvancedAlloyFurnaceBlockEntity furnace, boolean success) throws Exception {
        var method = AdvancedAlloyFurnaceBlockEntity.class.getDeclaredMethod("spawnCraftEffect", boolean.class);
        method.setAccessible(true);
        method.invoke(furnace, success);
    }

    @Test void successfulBatchBroadcastsBrightParticlesExactlyOnce() throws Exception {
        var level = mock(ServerLevel.class);
        effect(furnace(level), true);
        verify(level).sendParticles(eq(ParticleTypes.HAPPY_VILLAGER), eq(10.5), eq(61.1), eq(-1.5), eq(14),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(level).sendParticles(eq(ParticleTypes.END_ROD), eq(10.5), eq(61.1), eq(-1.5), eq(8),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(level, times(2)).sendParticles(any(ParticleOptions.class), anyDouble(), anyDouble(), anyDouble(), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test void failedBatchBroadcastsOnlySmoke() throws Exception {
        var level = mock(ServerLevel.class);
        effect(furnace(level), false);
        verify(level).sendParticles(eq(ParticleTypes.LARGE_SMOKE), anyDouble(), anyDouble(), anyDouble(), eq(12),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(level).sendParticles(eq(ParticleTypes.SMOKE), anyDouble(), anyDouble(), anyDouble(), eq(8),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(level, times(2)).sendParticles(any(ParticleOptions.class), anyDouble(), anyDouble(), anyDouble(), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test void missingServerLevelHasNoClientSideParticleEffect() throws Exception { effect(furnace(null), true); }
}
