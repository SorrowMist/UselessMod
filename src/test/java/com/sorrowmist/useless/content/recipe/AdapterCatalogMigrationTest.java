package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.event.RecipeAdapterNoticeEvents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdapterCatalogMigrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @BeforeEach @AfterEach void reset() { AdapterGuards.clearFailures(); }

    @Test void explicitRegistrationSourceIsNotOverwrittenOrRequeriedThroughAdapterCode() throws Exception {
        var manager = manager();
        var adapter = new ExplicitSource();
        manager.registerAdapter(adapter, "naturesaura");
        assertEquals(List.of(adapter), manager.getRegisteredAdapters());
        assertEquals("naturesaura", manager.getAdapterSourceId(adapter));
        assertFalse(AdapterGuards.isDisabled(adapter, "sourceId"));
    }

    @Test void aMoldLinkageFailureIsAttributedBeforeTheAdapterIsSkipped() throws Exception {
        var manager = manager();
        manager.registerAdapter(new BrokenMold(), "immersiveengineering");
        assertTrue(manager.getRegisteredAdapters().isEmpty());
        assertEquals(List.of("immersiveengineering"), AdapterGuards.disabledMods());
    }

    @Test void catalogDispatchKeepsOtherAdaptersAndLaterRecipesAfterEnumerationAndRowFailures() throws Exception {
        var broken = new BrokenClass();
        var badRow = new OneBadRow();
        var healthy = new Good();
        for (var adapter : List.of(broken, badRow, healthy)) AdapterGuards.rememberSource(adapter, "testmod");
        Class<?> sourceClass = Class.forName(AlloyFurnaceRecipeCatalog.class.getName() + "$AdapterSource");
        var ctor = sourceClass.getDeclaredConstructor(IRecipeAdapter.class, String.class); ctor.setAccessible(true);
        List<Object> sources = new ArrayList<>();
        for (var adapter : List.of(broken, badRow, healthy)) sources.add(ctor.newInstance(adapter, "testmod"));
        List<RecipeHolder<?>> holders = List.of(new RecipeHolder<>(id("bad"), mock(SmeltingRecipe.class)),
                new RecipeHolder<>(id("good"), mock(SmeltingRecipe.class)));
        var collect = AlloyFurnaceRecipeCatalog.class.getDeclaredMethod("collectConverted", List.class,
                Collection.class, Level.class, List.class); collect.setAccessible(true);
        List<Object> output = new ArrayList<>();
        collect.invoke(null, sources, holders, null, output);
        assertEquals(3, output.size()); // both healthy rows plus the later valid row from OneBadRow
        assertTrue(AdapterGuards.isDisabled(broken, "getRecipeClass"));
        assertFalse(AdapterGuards.isDisabled(badRow, "convertAll"));
        assertEquals(1, AdapterGuards.skippedItems(badRow, "convertAll"));
    }

    @Test void fingerprintLinkageFailureSkipsOneRecipeWithoutAbandoningTheCatalog() throws Exception {
        Class<?> collectedClass = Class.forName(AlloyFurnaceRecipeCatalog.class.getName() + "$CollectedRecipe");
        var ctor = collectedClass.getDeclaredConstructor(AdvancedAlloyFurnaceRecipe.class, String.class); ctor.setAccessible(true);
        var method = AlloyFurnaceRecipeCatalog.class.getDeclaredMethod("fingerprint", collectedClass, Level.class);
        method.setAccessible(true);
        var recipe = converted();
        var level = mock(Level.class); when(level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        try (var fingerprints = mockStatic(AlloyFurnaceRecipeFingerprint.class)) {
            fingerprints.when(() -> AlloyFurnaceRecipeFingerprint.create(recipe, RegistryAccess.EMPTY))
                    .thenThrow(new NoSuchMethodError("foreign ingredient serializer"));
            assertNull(method.invoke(null, ctor.newInstance(recipe, "pneumaticcraft"), level));
            assertEquals(List.of("pneumaticcraft"), AdapterGuards.disabledMods());
            fingerprints.when(() -> AlloyFurnaceRecipeFingerprint.create(recipe, RegistryAccess.EMPTY)).thenReturn("valid");
            assertNotNull(method.invoke(null, ctor.newInstance(recipe, "pneumaticcraft"), level));
        }
    }

    @Test void loginReportsDisabledSourcesOnceAndIncludesAnActualClickableIssueLink() {
        var player = mock(ServerPlayer.class);
        var event = new PlayerEvent.PlayerLoggedInEvent(player);
        RecipeAdapterNoticeEvents.onLogin(event);
        verify(player, never()).sendSystemMessage(any());
        AdapterGuards.recordModFailure("pneumaticcraft");
        RecipeAdapterNoticeEvents.onLogin(event);
        var messages = ArgumentCaptor.forClass(Component.class);
        verify(player, times(2)).sendSystemMessage(messages.capture());
        var click = messages.getAllValues().get(1).getStyle().getClickEvent();
        assertNotNull(click);
        assertEquals(ClickEvent.Action.OPEN_URL, click.getAction());
        assertEquals("https://github.com/SorrowMist/UselessMod/issues/new", click.getValue());
    }

    private static class Good implements IRecipeAdapter<SmeltingRecipe> {
        @Override public Class<SmeltingRecipe> getRecipeClass() { return SmeltingRecipe.class; }
        @Override public ItemStack getMoldItem() { return new ItemStack(Items.STONE); }
        @Override public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<SmeltingRecipe> holder, Level level) { return List.of(converted()); }
    }
    private static final class ExplicitSource extends Good {
        @Override public String sourceId() { throw new NoSuchMethodError("must not query explicit registration"); }
    }
    private static final class BrokenMold extends Good {
        @Override public ItemStack getMoldItem() { throw new NoSuchMethodError("mold API"); }
    }
    private static final class BrokenClass extends Good {
        @Override public Class<SmeltingRecipe> getRecipeClass() { throw new NoSuchMethodError("recipe API"); }
    }
    private static final class OneBadRow extends Good {
        @Override public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<SmeltingRecipe> holder, Level level) {
            if (holder.id().getPath().equals("bad")) throw new IllegalArgumentException("bad external row");
            return super.convertAll(holder, level);
        }
    }
    private static AlloyFurnaceRecipeManager manager() throws Exception {
        var ctor = AlloyFurnaceRecipeManager.class.getDeclaredConstructor(); ctor.setAccessible(true); return ctor.newInstance();
    }
    private static ResourceLocation id(String path) { return new ResourceLocation("test", path); }
    private static AdvancedAlloyFurnaceRecipe converted() {
        return new AdvancedAlloyFurnaceRecipe(id("converted"), List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(), List.of(new ItemStack(Items.GOLD_INGOT)), List.of(), 10, 20, Ingredient.EMPTY, 0,
                Ingredient.of(Items.STONE), AlloyFurnaceMode.NORMAL);
    }
}
