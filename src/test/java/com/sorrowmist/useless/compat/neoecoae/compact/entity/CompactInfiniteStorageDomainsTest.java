package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the installed ECO API, including its package move, without a running world. */
class CompactInfiniteStorageDomainsTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void commitsHealthyNativeDomainAndReleasesIt() throws Exception {
        assertCreation(true, true);
    }

    @Test
    void rejectsFailedNativeCommitAndReleasesIt() throws Exception {
        assertCreation(true, false);
    }

    @Test
    void releasesUnhealthyNativeDomainWithoutCommitting() throws Exception {
        assertCreation(false, false);
    }

    private void assertCreation(boolean healthy, boolean committed) throws Exception {
        Class<?> engineType = ECOStorageSystemBlockEntity.class.getDeclaredMethod("getInfiniteEngine").getReturnType();
        Class<?> domains = Class.forName(engineType.getPackageName() + ".ECOInfiniteStorageDomains");
        Method acquire = domains.getMethod("acquire", ServerLevel.class, UUID.class);
        Method release = domains.getMethod("release", MinecraftServer.class, UUID.class);
        Method isHealthy = engineType.getMethod("isHealthy");
        Method commit = engineType.getMethod("commit");
        Method successful = commit.getReturnType().getMethod("successful");
        Object engine = mock(engineType);
        Object result = mock(commit.getReturnType());
        when(isHealthy.invoke(engine)).thenReturn(healthy);
        when(commit.invoke(engine)).thenReturn(result);
        when(successful.invoke(result)).thenReturn(committed);
        var level = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        UUID id = UUID.randomUUID();
        try (var nativeDomains = mockStatic(domains)) {
            nativeDomains.when(() -> acquire.invoke(null, level, id)).thenReturn(engine);
            assertEquals(healthy && committed, CompactInfiniteStorageDomains.create(level, id));
            nativeDomains.verify(() -> acquire.invoke(null, level, id));
            nativeDomains.verify(() -> release.invoke(null, server, id));
            commit.invoke(verify(engine, times(healthy ? 1 : 0)));
        }
    }
}
