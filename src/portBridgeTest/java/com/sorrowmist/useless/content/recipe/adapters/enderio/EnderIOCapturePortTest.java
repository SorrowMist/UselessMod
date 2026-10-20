package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.api.capability.StoredEntityData;
import com.enderio.machines.common.blockentity.task.SpawnerMachineTask.SpawnType;
import com.enderio.machines.common.souldata.SpawnerSoul;
import com.enderio.base.common.util.EntityCaptureUtils;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.enderio.NativeSoul;
import com.sorrowmist.useless.compat.forge1201.enderio.NativeSoulStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnderIOCapturePortTest {
    private static final ResourceLocation ZOMBIE = new ResourceLocation("minecraft:zombie");
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    @Test void nativeSpawnerSoulDataUsesResourceIdsAndNativeFallbackEnergy() {
        var original = SpawnerSoul.SPAWNER.map;
        try {
            SpawnerSoul.SPAWNER.map = Map.of(ZOMBIE, new SpawnerSoul.SoulData(ZOMBIE, 112233, SpawnType.COPY));
            assertEquals(112233, SoulVialRecipeAdapter.energyCost(EntityType.ZOMBIE));
            assertEquals(50_000, SoulVialRecipeAdapter.energyCost(EntityType.SKELETON));
        } finally { SpawnerSoul.SPAWNER.map = original; }
    }
    @Test void nativeCaptureListAndSpawnEggRequirementAreRetained() {
        try (var capture = mockStatic(EntityCaptureUtils.class)) {
            capture.when(EntityCaptureUtils::getCapturableEntities).thenReturn(List.of(ZOMBIE));
            assertTrue(SoulVialRecipeAdapter.isSupportedEntity(EntityType.ZOMBIE));
            assertFalse(SoulVialRecipeAdapter.isSupportedEntity(EntityType.SKELETON));
            assertFalse(SoulVialRecipeAdapter.isSupportedEntity(EntityType.ITEM));
            assertFalse(SoulVialRecipeAdapter.isSupportedEntity(null));
        }
    }
    @Test void copyModeRetainsCapturedNbtAndDoesNotAliasTheMold() {
        var original = SpawnerSoul.SPAWNER.map;
        try (var storage = mockStatic(NativeSoulStorage.class)) {
            SpawnerSoul.SPAWNER.map = Map.of(ZOMBIE, new SpawnerSoul.SoulData(ZOMBIE, 50000, SpawnType.COPY));
            var mold = new ItemStack(Items.ZOMBIE_SPAWN_EGG);
            var tag = new CompoundTag(); tag.putString("id", ZOMBIE.toString()); tag.putLong("foreign:level", 77);
            var captured = NativeSoul.from(new StoredEntityData(tag, 100.0F));
            storage.when(() -> NativeSoulStorage.getBoundSoul(mold)).thenReturn(captured);
            var copy = SoulVialRecipeAdapter.captureSoul(EntityType.ZOMBIE, mold);
            assertNotSame(captured, copy);
            assertEquals(captured.storedData().serializeNBT(), copy.storedData().serializeNBT());
            copy.storedData().getEntityTag().putLong("foreign:level", 0);
            assertEquals(77, captured.storedData().getEntityTag().getLong("foreign:level"));
            storage.when(() -> NativeSoulStorage.getBoundSoul(mold)).thenReturn(NativeSoul.empty());
            assertEquals(ZOMBIE, SoulVialRecipeAdapter.captureSoul(EntityType.ZOMBIE, mold).entityTypeId());
        } finally { SpawnerSoul.SPAWNER.map = original; }
    }
}
