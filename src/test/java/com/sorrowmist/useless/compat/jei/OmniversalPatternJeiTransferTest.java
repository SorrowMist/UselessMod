package com.sorrowmist.useless.compat.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.network.SelectOmniversalPatternRecipePacket;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OmniversalPatternJeiTransferTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static AlloyFurnaceRecipeCatalog.Entry entry() {
        var recipe = new AdvancedAlloyFurnaceRecipe(new ResourceLocation("test", "smelting"),
                List.of(new CountedIngredient(Ingredient.of(Items.RAW_IRON), 1L)),
                List.of(), List.of(new ItemStack(Items.IRON_INGOT)), List.of(),
                2000L, 200, Ingredient.EMPTY, 0, Ingredient.of(Items.FURNACE), AlloyFurnaceMode.NORMAL);
        return new AlloyFurnaceRecipeCatalog.Entry(
                new AlloyFurnaceRecipeIdentity(recipe.id(), "test-fingerprint"), recipe, "minecraft");
    }

    private static PatternEncodingTermMenu menu() {
        var menu = mock(PatternEncodingTermMenu.class);
        when(menu.getProcessingInputSlots()).thenReturn(new FakeSlot[9]);
        when(menu.getProcessingOutputSlots()).thenReturn(new FakeSlot[3]);
        return menu;
    }

    @Test void installedExtendedAePlusCannotBlockNormalAe2PatternTransfer() {
        var menu = menu();
        var player = mock(Player.class);
        var level = mock(Level.class);
        when(player.level()).thenReturn(level);
        when(level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        var entry = entry();
        var helper = mock(IRecipeTransferHandlerHelper.class);
        try (var mods = mockStatic(ModList.class);
             var packets = mockStatic(PacketDistributor.class);
             var encoding = mockStatic(EncodingHelper.class)) {
            var modList = mock(ModList.class);
            mods.when(ModList::get).thenReturn(modList);
            when(modList.isLoaded("extendedae_plus")).thenReturn(true);
            assertNull(OmniversalPatternJeiTransferHandler.transferOmniversalRecipe(menu, entry,
                    mock(IRecipeSlotsView.class), player, false, true, helper));
            var selected = ArgumentCaptor.forClass(CustomPacketPayload[].class);
            packets.verify(() -> PacketDistributor.sendToServer(selected.capture()));
            var choice = assertInstanceOf(SelectOmniversalPatternRecipePacket.class, selected.getValue()[0]);
            assertEquals(entry.recipe().id(), choice.recipeId());
            assertEquals(entry.sourceId(), choice.sourceId());
            assertFalse(choice.fingerprint().isBlank());
            encoding.verify(() -> EncodingHelper.encodeProcessingRecipe(menu,
                    List.of(List.of(new GenericStack(AEItemKey.of(Items.RAW_IRON), 1L))),
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L))));
            verifyNoInteractions(helper);
        }
    }

    @Test void jeiSimulationDoesNotSendSelectionOrModifyTerminal() {
        try (var packets = mockStatic(PacketDistributor.class);
             var encoding = mockStatic(EncodingHelper.class)) {
            assertNull(OmniversalPatternJeiTransferHandler.transferOmniversalRecipe(menu(), entry(),
                    mock(IRecipeSlotsView.class), mock(Player.class), false, false,
                    mock(IRecipeTransferHandlerHelper.class)));
            packets.verifyNoInteractions();
            encoding.verifyNoInteractions();
        }
    }

    @Test void insufficientTerminalSlotsRejectBeforeSendingSelection() {
        var menu = menu();
        when(menu.getProcessingInputSlots()).thenReturn(new FakeSlot[0]);
        var helper = mock(IRecipeTransferHandlerHelper.class);
        var error = mock(IRecipeTransferError.class);
        when(helper.createUserErrorWithTooltip(any())).thenReturn(error);
        try (var packets = mockStatic(PacketDistributor.class);
             var encoding = mockStatic(EncodingHelper.class)) {
            assertSame(error, OmniversalPatternJeiTransferHandler.transferOmniversalRecipe(menu, entry(),
                    mock(IRecipeSlotsView.class), mock(Player.class), false, true, helper));
            packets.verifyNoInteractions();
            encoding.verifyNoInteractions();
        }
    }
}
