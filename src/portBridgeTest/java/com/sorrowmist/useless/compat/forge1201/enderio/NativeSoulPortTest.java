package com.sorrowmist.useless.compat.forge1201.enderio;

import com.enderio.api.capability.StoredEntityData;
import com.enderio.base.common.capability.EntityStorageItemStack;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeSoulPortTest {
    @BeforeAll static void bootstrap() {
        RecipeTestBootstrap.initialize();
        CraftingHelper.register(new ResourceLocation("useless_mod:filled_soul"), FilledSoulIngredient.TYPE);
    }

    private static StoredEntityData captured() {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:zombie");
        entity.putFloat("Health", 13.5F);
        CompoundTag other = new CompoundTag();
        other.putLong("foreign:progress", 123456789012L);
        entity.put("ForgeData", other);
        return new StoredEntityData(entity, 40.0F);
    }

    @Test void copiesPreserveAllCapturedDataAndHealthWithoutAliasing() {
        var original = captured();
        var soul = NativeSoul.from(original);
        var snapshot = original.serializeNBT().copy();
        original.getEntityTag().getCompound("ForgeData").putLong("foreign:progress", 0);
        assertEquals(snapshot, soul.storedData().serializeNBT());
        assertEquals(EntityType.ZOMBIE, soul.entityType());
        assertEquals(new ResourceLocation("minecraft:zombie"), soul.entityTypeId());
        soul.storedData().getEntityTag().putString("id", "minecraft:skeleton");
        assertEquals(snapshot, soul.copy().storedData().serializeNBT());
        assertEquals(13.5F, soul.storedData().getHealthState().orElseThrow().getA());
        assertEquals(40.0F, soul.storedData().getHealthState().orElseThrow().getB());
    }

    @Test void realEnderIoStorageReadsWritesAndCopiesItsNativeBlockEntityTag() {
        var stack = new ItemStack(Items.GLASS_BOTTLE);
        var storage = new EntityStorageItemStack(stack);
        var soul = NativeSoul.from(captured());
        assertTrue(NativeSoulStorage.read(storage).isEmpty());
        assertTrue(NativeSoulStorage.bind(storage, soul));
        assertEquals(soul.storedData().serializeNBT(),
                stack.getTag().getCompound("BlockEntityTag").getCompound("EntityStorage"));
        assertEquals(soul.storedData().serializeNBT(), NativeSoulStorage.read(storage).storedData().serializeNBT());
        var copied = ItemStack.of(stack.save(new CompoundTag()));
        assertEquals(soul.storedData().serializeNBT(),
                NativeSoulStorage.read(new EntityStorageItemStack(copied)).storedData().serializeNBT());
        NativeSoulStorage.read(storage).storedData().getEntityTag().remove("ForgeData");
        assertEquals(123456789012L, storage.getStoredEntityData().getEntityTag()
                .getCompound("ForgeData").getLong("foreign:progress"));
    }

    @Test void emptyUnknownAndMissingStorageAreNotInventedAsValidEntities() {
        assertTrue(NativeSoulStorage.read(null).isEmpty());
        assertFalse(NativeSoulStorage.bind(null, NativeSoul.of(EntityType.ZOMBIE)));
        var storage = new EntityStorageItemStack(new ItemStack(Items.GLASS_BOTTLE));
        assertFalse(NativeSoulStorage.bind(storage, NativeSoul.empty()));
        assertFalse(NativeSoulStorage.bind(storage, null));
        assertTrue(NativeSoul.empty().isEmpty());
        assertNull(NativeSoul.empty().entityType());
        var missing = NativeSoul.of(new ResourceLocation("missing_mod:captured_mob"));
        assertFalse(missing.isEmpty()); // Retain opaque captured data if its mod is not currently present.
        assertNull(missing.entityType());
        assertEquals(new ResourceLocation("missing_mod:captured_mob"), missing.copy().entityTypeId());
    }

    @Test void filledPredicateRoundTripsWithoutFlatteningCapturedEntityNbt() {
        // Only the Forge capability lookup boundary is replaced; use real Ender IO data and
        // EntityStorageItemStack for the predicate's NBT read on every invocation.
        try (var lookup = mockStatic(NativeSoulStorage.class)) {
            lookup.when(() -> NativeSoulStorage.getBoundSoul(any(ItemStack.class))).thenAnswer(call ->
                    NativeSoul.from(new EntityStorageItemStack(call.getArgument(0)).getStoredEntityData()));
            var full = new ItemStack(Items.GLASS_BOTTLE);
            new EntityStorageItemStack(full).setStoredEntityData(captured());
            var source = FilledSoulIngredient.of(Items.GLASS_BOTTLE);
            var json = Ingredient.fromJson(source.toJson());
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                RecipeCodecs.INGREDIENT_STREAM.encode(buffer, source);
                var wire = RecipeCodecs.INGREDIENT_STREAM.decode(buffer);
                for (var ingredient : List.of(source, json, wire)) {
                    assertInstanceOf(FilledSoulIngredient.class, ingredient);
                    assertTrue(ingredient.test(full.copyWithCount(64)));
                    var changed = full.copy(); changed.getOrCreateTag().putString("other_mod:label", "anything");
                    assertTrue(ingredient.test(changed));
                    assertFalse(ingredient.test(new ItemStack(Items.GLASS_BOTTLE)));
                    assertFalse(ingredient.test(ItemStack.EMPTY));
                    assertFalse(ingredient.test(null));
                    var wrongItem = new ItemStack(Items.DIAMOND); wrongItem.setTag(full.getTag().copy());
                    assertFalse(ingredient.test(wrongItem));
                }
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
}
