package com.sorrowmist.useless.compat.jei;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.mixin.emi.EmiEncodePatternHandlerMixin;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OmniversalEmiEncodingTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

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

    @Test void encoderDoesNotDependOnForgeInaccessibleSyntheticArgsClasses() {
        for (Method method : EmiEncodePatternHandlerMixin.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter.getName().startsWith("org.spongepowered.asm.mixin.injection.invoke.arg.Args"));
            }
        }
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
