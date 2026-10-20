package com.sorrowmist.useless.compat.jei;

import appeng.api.stacks.*;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.*;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.jei.AEKeyBookmarkCatalog;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.*;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.file.serializers.TypedIngredientSerializer;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.library.ingredients.IngredientSet;
import mezz.jei.library.ingredients.TypedIngredient;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import net.minecraftforge.fml.ModList;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AEKeyBookmarkPortTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    private static MockedStatic<AEKeyTypes> types() {
        var types = mockStatic(AEKeyTypes.class);
        for (var type : List.of(AEKeyType.items(), AEKeyType.fluids(), FluxKeyType.TYPE))
            types.when(() -> AEKeyTypes.get(type.getId())).thenReturn(type);
        return types;
    }
    private static AEItemKey variant(String text) {
        var stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putString("foreign:data", text);
        return AEItemKey.of(stack);
    }
    private static AEKeyJeiIngredients.KeyIngredient ingredient(AEKey key, long count) {
        return new AEKeyJeiIngredients.KeyIngredient(key, count);
    }
    // JEI storage and serializer are real. Only its lifecycle facade is supplied here;
    // no GUI or Minecraft instance is booted in this independent test JVM.
    private static IIngredientManager manager(IngredientSet<AEKeyJeiIngredients.KeyIngredient> values) {
        var manager = mock(IIngredientManager.class);
        when(manager.getIngredientHelper(AEKeyJeiIngredients.TYPE)).thenReturn(AEKeyJeiIngredients.HELPER);
        when(manager.getIngredientHelper(any(AEKeyJeiIngredients.KeyIngredient.class))).thenReturn(AEKeyJeiIngredients.HELPER);
        when(manager.normalizeTypedIngredient(any())).thenAnswer(call -> {
            ITypedIngredient<AEKeyJeiIngredients.KeyIngredient> value = call.getArgument(0);
            return TypedIngredient.createUnvalidated(AEKeyJeiIngredients.TYPE,
                    AEKeyJeiIngredients.HELPER.normalizeIngredient(value.getIngredient()));
        });
        when(manager.getIngredientTypeForUid(AEKeyJeiIngredients.TYPE.getUid())).thenReturn(Optional.of(AEKeyJeiIngredients.TYPE));
        when(manager.getTypedIngredientByUid(eq(AEKeyJeiIngredients.TYPE), anyString())).thenAnswer(call ->
                values.getByUid(call.getArgument(1)).map(value -> TypedIngredient.createUnvalidated(AEKeyJeiIngredients.TYPE, value)));
        return manager;
    }
    @Test void nativeItemsAndFluidsRetainTheirActualTypesAndNbt() {
        var key = variant("&\n中文");
        var item = GenericStackJeiIngredientProviders.resolve(new GenericStack(key, 17));
        assertSame(VanillaTypes.ITEM_STACK, item.type());
        assertEquals(key, AEItemKey.of((ItemStack)item.value()));
        assertEquals(17, ((ItemStack)item.value()).getCount());
        var fluidStack = new FluidStack(Fluids.WATER, 1499);
        fluidStack.getOrCreateTag().putString("foreign:quality", "pure");
        var fluidKey = AEFluidKey.of(fluidStack);
        var fluid = GenericStackJeiIngredientProviders.resolve(new GenericStack(fluidKey, 1499));
        assertSame(ForgeTypes.FLUID_STACK, fluid.type());
        assertEquals(fluidKey, AEFluidKey.of((FluidStack)fluid.value()));
        assertEquals(1499, ((FluidStack)fluid.value()).getAmount());
        assertNull(GenericStackJeiIngredientProviders.resolve(new GenericStack(key, 0)));
    }
    @Test void emiBridgeKeepsLongAmountsAndNbtBeforeNativeIntConversion() {
        var mods = mock(ModList.class);
        when(mods.isLoaded("emi")).thenReturn(true);
        try (var lifecycle = mockStatic(ModList.class)) {
            lifecycle.when(ModList::get).thenReturn(mods);
            for (var key : List.of(variant("a&中文"), variant("b&中文"),
                    AEFluidKey.of(Fluids.WATER), FluxKey.of(EnergyType.FE), FluxKey.of(EnergyType.GTEU))) {
                var resolved = GenericStackJeiIngredientProviders.resolve(new GenericStack(key, 4_000_000_007L));
                assertSame(AEKeyJeiIngredients.TYPE, resolved.type());
                var value = (AEKeyJeiIngredients.KeyIngredient) resolved.value();
                assertEquals(key, value.key());
                assertEquals(4_000_000_007L, value.amount());
            }
        }
    }
    @Test void energyUsesResourceIdentityAndKeepsLongAmountsInRecipeSlots() {
        var fe = FluxKey.of(EnergyType.FE); var eu = FluxKey.of(EnergyType.GTEU);
        var value = GenericStackJeiIngredientProviders.resolve(new GenericStack(fe, 4_000_000_007L));
        assertSame(AEKeyJeiIngredients.TYPE, value.type());
        var resource = (AEKeyJeiIngredients.KeyIngredient)value.value();
        assertEquals(fe, resource.key()); assertEquals(4_000_000_007L, resource.amount());
        assertEquals(fe.getDisplayName().getString(), AEKeyJeiIngredients.HELPER.getDisplayName(resource));
        assertEquals(fe.getId(), AEKeyJeiIngredients.HELPER.getResourceLocation(resource));
        assertEquals(fe.getModId(), AEKeyJeiIngredients.HELPER.getDisplayModId(resource));
        assertNotEquals(AEKeyBookmarkCatalog.uid(fe), AEKeyBookmarkCatalog.uid(eu));
    }
    @Test void actualJeiBookmarksDeduplicateCountsButKeepNbtVariantsDistinct() {
        var set = new IngredientSet<>(AEKeyJeiIngredients.HELPER, UidContext.Ingredient);
        var manager = manager(set);
        var first = TypedIngredient.createUnvalidated(AEKeyJeiIngredients.TYPE, ingredient(variant("a"), 4_000_000_000L));
        var same = TypedIngredient.createUnvalidated(AEKeyJeiIngredients.TYPE, ingredient(variant("a"), 3));
        var other = TypedIngredient.createUnvalidated(AEKeyJeiIngredients.TYPE, ingredient(variant("b"), 3));
        var bookmark = IngredientBookmark.create(first, manager);
        assertEquals(bookmark, IngredientBookmark.create(same, manager));
        assertNotEquals(bookmark, IngredientBookmark.create(other, manager));
        assertEquals(1, bookmark.getIngredient().getIngredient().amount());
        assertEquals(4_000_000_000L, first.getIngredient().amount());
    }
    @Test void actualJeiSerializerRestoresFromFreshDiskCatalogIncludingSeparatorsAndEnergy() throws Exception {
        try (var ignored = types()) {
            var disk = temp.resolve("keys.nbt"); var catalog = new AEKeyBookmarkCatalog();
            var keys = List.of(variant("x&y\n中文"), FluxKey.of(EnergyType.FE), FluxKey.of(EnergyType.GTEU));
            var original = new IngredientSet<>(AEKeyJeiIngredients.HELPER, UidContext.Ingredient);
            for (var key : keys) { catalog.remember(key); original.add(ingredient(key, 1)); }
            catalog.save(disk);
            var saved = new TypedIngredientSerializer(manager(original));
            var lines = original.stream().map(v -> saved.serialize(TypedIngredient.createUnvalidated(AEKeyJeiIngredients.TYPE, v))).toList();
            var restored = new AEKeyBookmarkCatalog(); restored.load(disk);
            var reloaded = new IngredientSet<>(AEKeyJeiIngredients.HELPER, UidContext.Ingredient);
            restored.decoded().forEach(s -> reloaded.add(ingredient(s.what(), 1)));
            var reader = new TypedIngredientSerializer(manager(reloaded));
            for (int i = 0; i < lines.size(); i++) {
                assertEquals(2, lines.get(i).split("&").length);
                var value = reader.deserialize(lines.get(i)).getResult().orElseThrow();
                assertEquals(keys.get(i), ((AEKeyJeiIngredients.KeyIngredient)value.getIngredient()).key());
            }
        }
    }
    @Test void unknownAddonTagsSurviveAndOriginalDiskFileIsBackedUp() throws Exception {
        var disk = temp.resolve("unknown.nbt");
        var unknown = new CompoundTag(); unknown.putString("#type", "absent_addon:custom");
        unknown.putString("payload", "opaque&value"); unknown.putLong("long", 9_000_000_001L);
        var root = new CompoundTag(); var list = new ListTag(); list.add(unknown); root.put("keys", list);
        try (var out = Files.newOutputStream(disk)) { NbtIo.writeCompressed(root, out); }
        var original = Files.readAllBytes(disk);
        var catalog = new AEKeyBookmarkCatalog(); catalog.load(disk);
        catalog.remember(variant("known")); catalog.save(disk);
        assertArrayEquals(original, Files.readAllBytes(temp.resolve("unknown.nbt.bak")));
        try (var in = Files.newInputStream(disk)) {
            assertTrue(NbtIo.readCompressed(in).getList("keys", Tag.TAG_COMPOUND).contains(unknown));
        }
        var reloaded = new AEKeyBookmarkCatalog(); reloaded.load(disk); assertEquals(2, reloaded.size());
    }
    @Test void missingCatalogStartsFreshAndInvalidDiskDoesNotDestroyLoadedData() throws Exception {
        var catalog = new AEKeyBookmarkCatalog(); catalog.remember(variant("old"));
        catalog.load(temp.resolve("new-profile.nbt")); assertEquals(0, catalog.size());
        catalog.remember(variant("new")); var broken = temp.resolve("broken.nbt");
        Files.writeString(broken, "invalid compressed NBT");
        assertThrows(java.io.IOException.class, () -> catalog.load(broken));
        assertEquals(1, catalog.size()); assertEquals("invalid compressed NBT", Files.readString(broken));
    }
}
