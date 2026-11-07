package com.sorrowmist.useless.compat.jei;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import com.moakiee.ae2lt.integration.useless.UselessModClientTransfer;
import com.moakiee.ae2lt.menu.Ae2ltMenuBuilder;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.ae.TianshuPatternEmiCompat;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.mixin.emi.EmiEncodePatternHandlerMixin;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OmniversalEmiEncodingTest {
    @BeforeAll static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        // Native menu registration needs AE2LT's MenuTypeBuilder accessor mixin, which is
        // absent from this unit JVM. Only bypass registration; retain the real menu types.
        try (var menuBuilder = mockStatic(Ae2ltMenuBuilder.class)) {
            Class.forName(TianshuPatternEncodingTermMenu.class.getName());
        }
    }

    @Test void nativeEncoderReplacesLossyEmiConversionsWithExactCustomKeysAndAmounts() throws Exception {
        var recipe = mock(AdvancedAlloyFurnaceRecipe.class);
        when(recipe.id()).thenReturn(new ResourceLocation("test", "infusing"));
        var input = new GenericStack(mock(AEKey.class), 80_000_000L);
        var output = new GenericStack(mock(AEKey.class), 45_000_000L);
        when(recipe.inputs()).thenReturn(List.of());
        when(recipe.inputFluids()).thenReturn(List.of());
        when(recipe.outputs()).thenReturn(List.of());
        when(recipe.outputFluids()).thenReturn(List.of());
        when(recipe.keyInputs()).thenReturn(List.of(input));
        when(recipe.keyOutputs()).thenReturn(List.of(output));
        var entry = new AlloyFurnaceRecipeCatalog.Entry(new AlloyFurnaceRecipeIdentity(recipe.id(), "fingerprint"), recipe, "appmek");
        var displayed = mock(EmiRecipe.class);
        var menu = mock(PatternEncodingTermMenu.class);
        when(menu.getProcessingInputSlots()).thenReturn(new FakeSlot[9]);
        when(menu.getProcessingOutputSlots()).thenReturn(new FakeSlot[3]);
        var mc = mock(Minecraft.class);
        mc.level = mock(ClientLevel.class);
        when(mc.level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        try (var minecraft = mockStatic(Minecraft.class);
             var selection = mockStatic(OmniversalEmiSelection.class);
             var fingerprints = mockStatic(AlloyFurnaceRecipeFingerprint.class);
             var packets = mockStatic(PacketDistributor.class);
             var encoding = mockStatic(EncodingHelper.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(mc);
            selection.when(() -> OmniversalEmiSelection.recipe(displayed)).thenReturn(entry);
            fingerprints.when(() -> AlloyFurnaceRecipeFingerprint.create(recipe, RegistryAccess.EMPTY)).thenReturn("fingerprint");
            invoke(menu, List.of(), List.of(), displayed);
            encoding.verify(() -> EncodingHelper.encodeProcessingRecipe(menu, List.of(List.of(input)), List.of(output)));
            packets.verify(() -> PacketDistributor.sendToServer(any(com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload[].class)));
        }
    }

    @Test void unrelatedRecipeKeepsNativeEncodingArgumentsUntouched() throws Exception {
        var menu = mock(PatternEncodingTermMenu.class);
        List<List<GenericStack>> inputs = List.of(List.of(new GenericStack(mock(AEKey.class), 1)));
        List<GenericStack> outputs = List.of(new GenericStack(mock(AEKey.class), 2));
        try (var packets = mockStatic(PacketDistributor.class);
             var encoding = mockStatic(EncodingHelper.class)) {
            invoke(menu, inputs, outputs, mock(EmiRecipe.class));
            encoding.verify(() -> EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs));
            packets.verifyNoInteractions();
        }
    }

    @Test void tianshuUsesDedicatedOmniversalPageBeforeOrdinarySlotLimits() throws Exception {
        assertDedicatedPage(false);
    }

    @Test void tianshuAltClickEncodesExactlyOnce() throws Exception {
        assertDedicatedPage(true);
    }

    @Test void wirelessTianshuInheritsTheDedicatedOmniversalTransferPath() {
        assertTrue(TianshuPatternEncodingTermMenu.class.isAssignableFrom(
                TianshuWirelessPatternEncodingTermMenu.class));
    }

    private static void assertDedicatedPage(boolean altDown) throws Exception {
        var menu = mock(TianshuPatternEncodingTermMenu.class);
        var entry = mock(AlloyFurnaceRecipeCatalog.Entry.class);
        var displayed = mock(EmiRecipe.class);
        var prepared = new ItemStack(Items.PAPER);
        var mc = mock(Minecraft.class);
        try (var minecraft = mockStatic(Minecraft.class);
             var screen = mockStatic(Screen.class);
             var selection = mockStatic(OmniversalEmiSelection.class);
             var modList = mockStatic(ModList.class);
             var nativeTransfer = mockStatic(UselessModClientTransfer.class);
             var packets = mockStatic(PacketDistributor.class);
             var encoding = mockStatic(EncodingHelper.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(mc);
            selection.when(() -> OmniversalEmiSelection.recipe(displayed)).thenReturn(entry);
            var mods = mock(ModList.class);
            modList.when(ModList::get).thenReturn(mods);
            when(mods.isLoaded("ae2lt")).thenReturn(true);
            nativeTransfer.when(() -> UselessModClientTransfer.prepare(menu, entry)).thenReturn(prepared);
            screen.when(Screen::hasAltDown).thenReturn(altDown);

            var result = invokeCraft(menu, displayed);

            assertTrue(result.isCancelled());
            assertEquals(Boolean.TRUE, result.getReturnValue());
            verify(menu).selectOmniversalPattern(prepared);
            verify(menu, never()).getProcessingInputSlots();
            verify(menu, never()).getProcessingOutputSlots();
            verify(menu, never()).resetProcessingEncoding();
            verify(menu, times(altDown ? 1 : 0)).encodeAndUploadDirectly();
            verify(mc).setScreen(null);
            packets.verifyNoInteractions();
            encoding.verifyNoInteractions();
        }
    }

    @Test void invalidTianshuRecipeReportsAnErrorWithoutFallingBackToAPlainPattern() throws Exception {
        var menu = mock(TianshuPatternEncodingTermMenu.class);
        var player = mock(Player.class);
        when(menu.getPlayer()).thenReturn(player);
        var entry = mock(AlloyFurnaceRecipeCatalog.Entry.class);
        var displayed = mock(EmiRecipe.class);
        var mc = mock(Minecraft.class);
        try (var minecraft = mockStatic(Minecraft.class);
             var selection = mockStatic(OmniversalEmiSelection.class);
             var modList = mockStatic(ModList.class);
             var nativeTransfer = mockStatic(UselessModClientTransfer.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(mc);
            selection.when(() -> OmniversalEmiSelection.recipe(displayed)).thenReturn(entry);
            var mods = mock(ModList.class);
            modList.when(ModList::get).thenReturn(mods);
            when(mods.isLoaded("ae2lt")).thenReturn(true);
            nativeTransfer.when(() -> UselessModClientTransfer.prepare(menu, entry)).thenReturn(ItemStack.EMPTY);

            var result = invokeCraft(menu, displayed);

            assertTrue(result.isCancelled());
            assertEquals(Boolean.FALSE, result.getReturnValue());
            verify(player).displayClientMessage(argThat(message -> message.getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents text
                    && text.getKey().equals("ae2lt.tianshu.omniversal.invalid")), eq(true));
            verify(menu, never()).selectOmniversalPattern(any());
            verify(menu, never()).encodeAndUploadDirectly();
            verify(menu, never()).getProcessingInputSlots();
            verify(mc, never()).setScreen(any());
        }
    }

    @Test void ordinaryAe2MenuKeepsNativeTransferEvenWithLightningTechInstalled() throws Exception {
        var menu = mock(PatternEncodingTermMenu.class);
        var entry = mock(AlloyFurnaceRecipeCatalog.Entry.class);
        var displayed = mock(EmiRecipe.class);
        try (var selection = mockStatic(OmniversalEmiSelection.class);
             var modList = mockStatic(ModList.class);
             var nativeTransfer = mockStatic(UselessModClientTransfer.class)) {
            selection.when(() -> OmniversalEmiSelection.recipe(displayed)).thenReturn(entry);
            var mods = mock(ModList.class);
            modList.when(ModList::get).thenReturn(mods);
            when(mods.isLoaded("ae2lt")).thenReturn(true);

            assertFalse(invokeCraft(menu, displayed).isCancelled());

            nativeTransfer.verifyNoInteractions();
        }
    }

    @Test void missingLightningTechLeavesTheNativeAe2TransferAlone() {
        try (var modList = mockStatic(ModList.class)) {
            var mods = mock(ModList.class);
            modList.when(ModList::get).thenReturn(mods);
            assertNull(TianshuPatternEmiCompat.transferRecipe(
                    mock(PatternEncodingTermMenu.class), mock(AlloyFurnaceRecipeCatalog.Entry.class)));
        }
    }

    @Test void encoderDoesNotDependOnForgeInaccessibleSyntheticArgsClasses() {
        for (Method method : EmiEncodePatternHandlerMixin.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter.getName().startsWith("org.spongepowered.asm.mixin.injection.invoke.arg.Args"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static CallbackInfoReturnable<Boolean> invokeCraft(PatternEncodingTermMenu menu, EmiRecipe recipe)
            throws Exception {
        EmiCraftContext<PatternEncodingTermMenu> context = mock(EmiCraftContext.class);
        when(context.getScreenHandler()).thenReturn(menu);
        var result = new CallbackInfoReturnable<Boolean>("craft", true);
        Method method = EmiEncodePatternHandlerMixin.class.getDeclaredMethod("useless$transferTianshuOmniversalRecipe",
                EmiRecipe.class, EmiCraftContext.class, CallbackInfoReturnable.class);
        method.setAccessible(true);
        method.invoke(new EmiEncodePatternHandlerMixin() { }, recipe, context, result);
        return result;
    }

    private static void invoke(PatternEncodingTermMenu menu, List<List<GenericStack>> inputs,
                               List<GenericStack> outputs, EmiRecipe recipe) throws Exception {
        Method method = EmiEncodePatternHandlerMixin.class.getDeclaredMethod("useless$encodeOmniversalRecipe",
                PatternEncodingTermMenu.class, List.class, List.class, PatternEncodingTermMenu.class,
                Recipe.class, EmiRecipe.class, boolean.class);
        method.setAccessible(true);
        method.invoke(new EmiEncodePatternHandlerMixin() { }, menu, inputs, outputs, menu, null, recipe, true);
    }
}
