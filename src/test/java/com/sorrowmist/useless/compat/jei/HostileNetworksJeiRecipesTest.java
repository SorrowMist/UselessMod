package com.sorrowmist.useless.compat.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.Entry;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.network.SelectOmniversalPatternRecipePacket;
import mezz.jei.api.gui.builder.IIngredientConsumer;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Deliberately does not load HNN classes: the JEI bridge must work without the optional mod. */
class HostileNetworksJeiRecipesTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @AfterEach void clearGroups() { HostileNetworksJeiRecipes.clear(); }

    @Test void defaultContinuousModelHasFourDisplaysButKeepsAll1249ExactEntries() {
        List<Entry> catalog = new ArrayList<>();
        for (int data = 6; data < 1254; data++) {
            String tier = data < 54 ? "basic" : data < 354 ? "advanced" : "superior";
            catalog.add(entry("zombie", tier, data, data, 10, 3));
        }
        Entry finalTier = entry("zombie", "self_aware", 1254, Integer.MAX_VALUE, 1, 1);
        catalog.add(finalTier);
        List<AlloyFurnaceRecipeIdentity> identities = catalog.stream().map(Entry::identity).toList();
        List<Entry> displayed = HostileNetworksJeiRecipes.prepare(catalog, true);
        assertEquals(1249, catalog.size());
        assertEquals(identities, catalog.stream().map(Entry::identity).toList());
        assertEquals(4, displayed.size());
        assertEquals(List.of(48, 300, 900, 0), displayed.stream()
                .map(e -> HostileNetworksJeiRecipes.variants(e).size()).toList());
        assertSame(finalTier, displayed.get(3));
        for (Entry representative : displayed) {
            for (Entry variant : HostileNetworksJeiRecipes.variants(representative)) {
                assertTrue(catalog.stream().anyMatch(original -> original == variant));
                assertEquals(HostileNetworksJeiRecipes.info(variant).minData(),
                        variant.recipe().mold().getItems()[0].getTag().getInt("data"));
            }
        }
    }

    @Test void modelsTiersRangesAndConflictingExactRecipesAreNotInterchangeable() {
        Entry a = entry("zombie", "basic", 6, 6, 10, 3);
        Entry b = entry("zombie", "basic", 7, 7, 100, 31);
        Entry otherModel = entry("creeper", "basic", 6, 6, 10, 3);
        Entry nextTier = entry("zombie", "advanced", 54, 54, 10, 3);
        Entry ranged = entry("zombie", "self_aware", 1254, Integer.MAX_VALUE, 1, 1);
        assertEquals(List.of(a, otherModel, nextTier, ranged),
                HostileNetworksJeiRecipes.prepare(List.of(b, a, otherModel, nextTier, ranged), true));
        Entry conflict = new Entry(new AlloyFurnaceRecipeIdentity(a.recipe().id(), "conflict"),
                a.recipe(), a.sourceId());
        List<Entry> conflicting = List.of(a, b, conflict);
        assertEquals(conflicting, HostileNetworksJeiRecipes.prepare(conflicting, true));
        assertTrue(HostileNetworksJeiRecipes.variants(a).isEmpty());
    }

    @Test void emiFallbackAndEmptyCatalogClearGroupingWithoutChangingEntries() {
        var entries = List.of(entry("zombie", "basic", 6, 6, 10, 3),
                entry("zombie", "basic", 7, 7, 100, 31));
        HostileNetworksJeiRecipes.prepare(entries, true);
        assertSame(entries, HostileNetworksJeiRecipes.prepare(entries, false));
        assertTrue(HostileNetworksJeiRecipes.variants(entries.get(0)).isEmpty());
        HostileNetworksJeiRecipes.prepare(entries, true);
        assertTrue(HostileNetworksJeiRecipes.prepare(List.of(), true).isEmpty());
        assertTrue(HostileNetworksJeiRecipes.variants(entries.get(0)).isEmpty());
    }

    @Test void layoutLinksEveryExactInputOutputAndModelInTheSameOrder() {
        Entry first = entry("zombie", "basic", 6, 6, 10, 3);
        Entry second = entry("zombie", "basic", 7, 7, 100, 31);
        HostileNetworksJeiRecipes.prepare(List.of(first, second), true);
        var builder = mock(IRecipeLayoutBuilder.class);
        List<IRecipeSlotBuilder> slots = new ArrayList<>();
        when(builder.addSlot(any(), anyInt(), anyInt())).thenAnswer(call -> {
            var slot = mock(IRecipeSlotBuilder.class, RETURNS_SELF); slots.add(slot); return slot;
        });
        assertTrue(HostileNetworksJeiRecipes.setRecipe(builder, first));
        assertEquals(3, slots.size());
        verify(builder).createFocusLink(slots.toArray(IRecipeSlotBuilder[]::new));
        for (int i = 0; i < slots.size(); i++) {
            @SuppressWarnings("unchecked") ArgumentCaptor<List<ItemStack>> values = ArgumentCaptor.forClass(List.class);
            verify(slots.get(i)).addItemStacks(values.capture());
            assertEquals(2, values.getValue().size());
            assertEquals(i == 0 ? 100 : i == 1 ? 31 : 1, values.getValue().get(1).getCount());
        }
    }

    @Test void selectorClearsOverridesAndSynchronizesAmountsBeforeSelectingTheRealIdentity() {
        Entry first = entry("zombie", "basic", 6, 6, 10, 3);
        Entry second = entry("zombie", "basic", 7, 7, 100, 31);
        HostileNetworksJeiRecipes.prepare(List.of(first, second), true);
        Display display = new Display(first);
        var extras = extras(display);
        HostileNetworksJeiRecipes.createExtras(extras, first, mock(IFocusGroup.class));
        var selector = ArgumentCaptor.forClass(HostileNetworksDataSelector.class);
        verify(extras).addInputHandler(selector.capture());
        assertSame(first, HostileNetworksJeiRecipes.selected(first, display.view));
        var input = click(true, 0);
        assertTrue(selector.getValue().handleInput(20, 80, input));
        assertSame(first, HostileNetworksJeiRecipes.selected(first, display.view), "simulation must not mutate");
        assertTrue(selector.getValue().handleInput(20, 80, click(false, 0)));
        assertSame(second, HostileNetworksJeiRecipes.selected(first, display.view));
        assertEquals(100, display.stacks.get(HostileNetworksJeiRecipes.INPUT_SLOT).get(0).getCount());
        assertEquals(31, display.stacks.get("hnn_output_0").get(0).getCount());
        assertTrue(display.stacks.values().stream().allMatch(values -> values.size() == 1));
        assertTrue(selector.getValue().handleInput(20, 80, click(false, 1)));
        assertSame(first, HostileNetworksJeiRecipes.selected(first, display.view));
        assertTrue(selector.getValue().handleMouseScrolled(20, 80, -1));
        assertSame(second, HostileNetworksJeiRecipes.selected(first, display.view));
        assertTrue(display.stacks.values().stream().allMatch(values -> values.size() == 1));
    }

    @Test void automaticJeiCyclesRestoreTheIndependentSelectionOfEachLayout() {
        Entry first = entry("zombie", "basic", 6, 6, 10, 3);
        Entry second = entry("zombie", "basic", 7, 7, 100, 31);
        HostileNetworksJeiRecipes.prepare(List.of(first, second), true);
        Display left = new Display(first);
        Display right = new Display(first);
        var leftExtras = extras(left);
        HostileNetworksJeiRecipes.createExtras(leftExtras, first, mock(IFocusGroup.class));
        HostileNetworksJeiRecipes.createExtras(extras(right), first, mock(IFocusGroup.class));
        var selector = ArgumentCaptor.forClass(HostileNetworksDataSelector.class);
        verify(leftExtras).addInputHandler(selector.capture());
        selector.getValue().handleMouseScrolled(20, 80, -1);
        for (int cycle = 0; cycle < 3; cycle++) {
            left.slots.values().forEach(IRecipeSlotDrawable::clearDisplayOverrides);
            right.slots.values().forEach(IRecipeSlotDrawable::clearDisplayOverrides);
            HostileNetworksJeiRecipes.refreshDisplay(first, List.copyOf(left.slots.values()));
            HostileNetworksJeiRecipes.refreshDisplay(first, List.copyOf(right.slots.values()));
            assertSame(second, HostileNetworksJeiRecipes.selected(first, left.view));
            assertSame(first, HostileNetworksJeiRecipes.selected(first, right.view));
            assertTrue(left.stacks.values().stream().allMatch(values -> values.size() == 1));
        }
    }

    @Test void exactModelFocusSelectsOnlyItsOwnVariant() {
        Entry first = entry("zombie", "basic", 6, 6, 10, 3);
        Entry second = entry("zombie", "basic", 7, 7, 100, 31);
        HostileNetworksJeiRecipes.prepare(List.of(first, second), true);
        Display display = new Display(first);
        var focus = mock(IFocus.class); var typed = mock(ITypedIngredient.class);
        ItemStack focusedModel = second.recipe().mold().getItems()[0];
        when(typed.getIngredient()).thenReturn(focusedModel);
        when(focus.getTypedValue()).thenReturn(typed);
        var focuses = mock(IFocusGroup.class);
        when(focuses.getItemStackFocuses()).thenAnswer(call -> java.util.stream.Stream.of(focus));
        var extras = extras(display);
        HostileNetworksJeiRecipes.createExtras(extras, first, focuses);
        assertSame(second, HostileNetworksJeiRecipes.selected(first, display.view));
        var selector = ArgumentCaptor.forClass(HostileNetworksDataSelector.class);
        verify(extras).addInputHandler(selector.capture());
        selector.getValue().handleMouseScrolled(20, 80, -1);
        assertSame(second, HostileNetworksJeiRecipes.selected(first, display.view));
        display.slots.values().forEach(IRecipeSlotDrawable::clearDisplayOverrides);
        HostileNetworksJeiRecipes.refreshDisplay(first, List.copyOf(display.slots.values()));
        assertSame(second, HostileNetworksJeiRecipes.selected(first, display.view),
                "automatic JEI cycles must not change the focused data value");
    }

    @Test void transferUsesSelectedVariantCountsIdAndFingerprintAndRejectsMismatches() {
        Entry first = entry("zombie", "basic", 6, 6, 10, 3);
        Entry second = entry("zombie", "basic", 7, 7, 100, 31);
        HostileNetworksJeiRecipes.prepare(List.of(first, second), true);
        Display display = new Display(second);
        var menu = mock(PatternEncodingTermMenu.class);
        when(menu.getProcessingInputSlots()).thenReturn(new FakeSlot[9]);
        when(menu.getProcessingOutputSlots()).thenReturn(new FakeSlot[3]);
        var player = mock(Player.class); var level = mock(Level.class);
        when(player.level()).thenReturn(level); when(level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        var helper = mock(IRecipeTransferHandlerHelper.class);
        var error = mock(IRecipeTransferError.class);
        when(helper.createUserErrorWithTooltip(any())).thenReturn(error);
        try (var packets = mockStatic(PacketDistributor.class); var encoding = mockStatic(EncodingHelper.class)) {
            assertNull(OmniversalPatternJeiTransferHandler.transferOmniversalRecipe(menu, first, display.view,
                    player, false, true, helper));
            var sent = ArgumentCaptor.forClass(CustomPacketPayload[].class);
            packets.verify(() -> PacketDistributor.sendToServer(sent.capture()));
            var choice = assertInstanceOf(SelectOmniversalPatternRecipePacket.class, sent.getValue()[0]);
            assertEquals(second.recipe().id(), choice.recipeId());
            assertEquals(AlloyFurnaceRecipeFingerprint.create(second.recipe(), RegistryAccess.EMPTY), choice.fingerprint());
            assertNotEquals(AlloyFurnaceRecipeFingerprint.create(first.recipe(), RegistryAccess.EMPTY), choice.fingerprint());
            encoding.verify(() -> EncodingHelper.encodeProcessingRecipe(menu,
                    List.of(List.of(new GenericStack(AEItemKey.of(Items.PAPER), 100L))),
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 31L))));
            packets.clearInvocations(); encoding.clearInvocations();
            display.stacks.put(HostileNetworksJeiRecipes.INPUT_SLOT, new ArrayList<>(List.of(new ItemStack(Items.PAPER, 10))));
            assertSame(error, OmniversalPatternJeiTransferHandler.transferOmniversalRecipe(menu, first, display.view,
                    player, false, true, helper));
            packets.verifyNoInteractions(); encoding.verifyNoInteractions();
            display.stacks.remove(HostileNetworksJeiRecipes.MODEL_SLOT);
            assertNull(HostileNetworksJeiRecipes.selected(first, display.view));
        }
    }

    @Test void tooltipExplainsDataBatchAccuracyAndExactRecipeId() {
        Entry entry = entry("zombie", "basic", 30, 30, 100, 37);
        var tooltip = mock(ITooltipBuilder.class);
        HostileNetworksJeiRecipes.addDetails(tooltip, entry);
        var text = ArgumentCaptor.forClass(Component.class);
        verify(tooltip, atLeastOnce()).add(text.capture());
        Map<String, Object[]> args = new LinkedHashMap<>();
        for (Component component : text.getAllValues()) {
            var translation = assertInstanceOf(TranslatableContents.class, component.getContents());
            args.put(translation.getKey(), translation.getArgs());
        }
        assertArrayEquals(new Object[]{30}, args.get("jei.useless_mod.hnn.data"));
        assertArrayEquals(new Object[]{100L}, args.get("jei.useless_mod.hnn.batch"));
        assertTrue(args.containsKey("jei.useless_mod.hnn.accuracy"));
        assertTrue(args.containsKey("jei.useless_mod.hnn.expected_batch"));
        assertArrayEquals(new Object[]{entry.recipe().id().toString()}, args.get("jei.useless_mod.tooltip.recipe_id"));
    }

    private static Entry entry(String model, String tier, int min, int max, int operations, int outputs) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "useless_mod:data_model_range");
        json.addProperty("model", "hostilenetworks:" + model);
        json.addProperty("min_data", min); json.addProperty("max_data", max);
        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.getOrCreateTag().putString("model", model); stack.getOrCreateTag().putInt("data", min);
        Ingredient mold = mock(Ingredient.class);
        when(mold.toJson()).thenReturn(json); when(mold.isSimple()).thenReturn(false);
        when(mold.getItems()).thenReturn(new ItemStack[]{stack});
        when(mold.test(any())).thenAnswer(call -> {
            ItemStack candidate = call.getArgument(0);
            return candidate != null && candidate.is(Items.DIAMOND) && candidate.hasTag()
                    && model.equals(candidate.getTag().getString("model"))
                    && candidate.getTag().getInt("data") >= min && candidate.getTag().getInt("data") <= max;
        });
        var id = new ResourceLocation("hostilenetworks", "compat/inference/" + tier
                + (min == max ? "/data_" + min : "") + "/hostilenetworks_" + model);
        var recipe = new AdvancedAlloyFurnaceRecipe(id,
                List.of(new CountedIngredient(Ingredient.of(Items.PAPER), operations)), List.of(),
                List.of(new ItemStack(Items.IRON_INGOT, outputs)), List.of(), operations * 1000L, operations * 20,
                Ingredient.EMPTY, 0, mold, AlloyFurnaceMode.NORMAL);
        return new Entry(new AlloyFurnaceRecipeIdentity(id, "data-" + min + "-" + model), recipe, "hostilenetworks");
    }

    private static IJeiUserInput click(boolean simulated, int modifiers) {
        var input = mock(IJeiUserInput.class);
        when(input.getKey()).thenReturn(InputConstants.Type.MOUSE.getOrCreate(0));
        when(input.isSimulate()).thenReturn(simulated); when(input.getModifiers()).thenReturn(modifiers);
        return input;
    }

    private static IRecipeExtrasBuilder extras(Display display) {
        var builder = mock(IRecipeExtrasBuilder.class); var slots = mock(IRecipeSlotDrawablesView.class);
        when(builder.getRecipeSlots()).thenReturn(slots);
        when(slots.getSlots()).thenAnswer(call -> List.copyOf(display.slots.values()));
        when(slots.findSlotByName(anyString())).thenAnswer(call -> Optional.ofNullable(display.slots.get(call.getArgument(0))));
        return builder;
    }

    /** Mimics JEI's reusable override acceptor: failing to clear accumulates and cycles old amounts. */
    private static class Display {
        final Map<String, List<ItemStack>> stacks = new LinkedHashMap<>();
        final Map<String, IRecipeSlotDrawable> slots = new LinkedHashMap<>();
        final IRecipeSlotsView view = mock(IRecipeSlotsView.class);
        Display(Entry entry) {
            add(HostileNetworksJeiRecipes.INPUT_SLOT, HostileNetworksJeiRecipes.matrix(entry));
            add("hnn_output_0", entry.recipe().outputs().get(0));
            add(HostileNetworksJeiRecipes.MODEL_SLOT, entry.recipe().mold().getItems()[0]);
            when(view.findSlotByName(anyString())).thenAnswer(call -> Optional.ofNullable(slots.get(call.getArgument(0))));
        }
        private void add(String name, ItemStack initial) {
            stacks.put(name, new ArrayList<>(List.of(initial.copy())));
            var slot = mock(IRecipeSlotDrawable.class); var acceptor = mock(IIngredientConsumer.class);
            when(slot.getSlotName()).thenReturn(Optional.of(name));
            when(slot.getDisplayedItemStack()).thenAnswer(call -> stacks.getOrDefault(name, List.of()).stream().findFirst());
            doAnswer(call -> { stacks.put(name, new ArrayList<>()); return null; }).when(slot).clearDisplayOverrides();
            when(slot.createDisplayOverrides()).thenReturn(acceptor);
            when(acceptor.addItemStack(any())).thenAnswer(call -> { stacks.get(name).add(call.getArgument(0)); return acceptor; });
            slots.put(name, slot);
        }
    }
}
