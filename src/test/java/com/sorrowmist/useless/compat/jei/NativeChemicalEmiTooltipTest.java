package com.sorrowmist.useless.compat.jei;

import appeng.api.integrations.jei.IngredientConverter;
import appeng.api.integrations.jei.IngredientConverters;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.jei.AEKeyEmiStack;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.jemi.JemiPlugin;
import dev.emi.emi.jemi.JemiStack;
import dev.emi.emi.jemi.JemiUtil;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import dev.emi.emi.jemi.impl.JemiTooltipBuilder;
import dev.emi.emi.jemi.widget.JemiSlotWidget;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeChemicalEmiTooltipTest {
    private record NativeChemical(long amount) { }
    private static final IIngredientType<NativeChemical> TYPE = () -> NativeChemical.class;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void hoveringNativeChemicalSlotUsesItsRendererWithANativeIngredient() throws Exception {
        try (var fixture = new Fixture()) {
            var stack = spy(fixture.stack(80));
            var copy = spy(stack.copy());
            doReturn(copy).when(stack).copy();
            // Execute JEMI's real copy-and-replace-renderer path. Only final presentation is
            // isolated, since JemiStack.getTooltip initializes GLFW keybindings in a unit JVM.
            doAnswer(i -> richTooltip(copy, TooltipFlag.NORMAL)).when(copy).getTooltip();
            var slot = new JemiRecipeSlot(RecipeIngredientRole.OUTPUT, stack);
            var tooltip = new ArrayList<ClientTooltipComponent>();
            JemiSlotWidget.addTooltip(tooltip, slot, stack, fixture.renderer);
            assertFalse(tooltip.isEmpty());
            assertEquals(List.of(80L), fixture.renderer.richAmounts);
            assertEquals(80, stack.getAmount());
            assertSame(AEKeyJeiIngredients.RENDERER, stack.renderer);
        }
    }

    @Test void overriddenRendererSurvivesCopiesAndUsesTheCurrentLongAmount() throws Exception {
        try (var fixture = new Fixture()) {
            var stack = fixture.stack(80);
            replaceRenderer(stack, fixture.renderer);
            var edited = (AEKeyEmiStack) stack.copy().setAmount(4_500_000_080L);
            assertFalse(richTooltip(edited, TooltipFlag.ADVANCED).isEmpty());
            assertEquals(List.of(Component.literal("chemical:4500000080")), edited.getTooltipText());
            assertSame(fixture.renderer, edited.renderer);
            assertFalse(richTooltip(stack, TooltipFlag.ADVANCED).isEmpty());
            assertEquals(List.of(4_500_000_080L, 80L), fixture.renderer.richAmounts);
            assertEquals(List.of(true, true), fixture.renderer.advancedFlags);
            assertEquals(80, stack.getAmount());
        }
    }

    @Test void fallbackKeySlotKeepsItsOwnRendererAndCurrentAmount() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.converters.when(IngredientConverters::getConverters).thenReturn(List.of());
            var stack = fixture.stack(80);
            var renderer = keyRenderer();
            stack.renderer = renderer;
            var edited = (AEKeyEmiStack) stack.copy().setAmount(160);
            assertEquals(List.of(Component.literal("key:160")), edited.getTooltipText());
            assertFalse(richTooltip(edited, TooltipFlag.NORMAL).isEmpty());
            assertSame(renderer, edited.renderer);
        }
    }

    @Test void keyTypedSlotRendererIsNotGivenANativeValueJustBecauseAConverterExists() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.keyTyping = true;
            var stack = fixture.stack(80);
            stack.renderer = keyRenderer();
            assertEquals(List.of(Component.literal("key:80")), stack.getTooltipText());
            assertFalse(richTooltip(stack, TooltipFlag.NORMAL).isEmpty());
        }
    }

    @Test void missingRuntimeDoesNotPassAKeyToTheOldNativeRenderer() throws Exception {
        try (var fixture = new Fixture()) {
            var stack = fixture.stack(80);
            replaceRenderer(stack, fixture.renderer);
            JemiPlugin.runtime = null;
            assertFalse(richTooltip(stack, TooltipFlag.NORMAL).isEmpty());
            assertTrue(fixture.renderer.richAmounts.isEmpty());
        }
    }

    private static IIngredientRenderer<AEKeyJeiIngredients.KeyIngredient> keyRenderer() {
        return new IIngredientRenderer<>() {
            @Override public void render(GuiGraphics gui, AEKeyJeiIngredients.KeyIngredient value) { }
            @Override public List<Component> getTooltip(AEKeyJeiIngredients.KeyIngredient value, TooltipFlag flag) {
                return List.of(Component.literal("key:" + value.amount()));
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> ITypedIngredient<T> typed(IIngredientType<T> type, T value) {
        var typed = (ITypedIngredient<T>) mock(ITypedIngredient.class);
        when(typed.getType()).thenReturn(type);
        when(typed.getIngredient()).thenReturn(value);
        return typed;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void replaceRenderer(JemiStack<?> stack, IIngredientRenderer<?> renderer) {
        ((JemiStack) stack).renderer = renderer;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "removal"})
    private static List<ClientTooltipComponent> richTooltip(AEKeyEmiStack stack, TooltipFlag flag) throws Exception {
        var method = AEKeyEmiStack.class.getDeclaredMethod("tooltipView");
        method.setAccessible(true);
        JemiStack view = (JemiStack) method.invoke(stack);
        var builder = new JemiTooltipBuilder();
        // Preserve JEMI's rich renderer overload, including the compiler-generated type bridge.
        view.renderer.getTooltip(builder, view.ingredient, flag);
        return builder.tooltip;
    }

    /** A real generic renderer: its compiler-generated bridge rejects KeyIngredient like Mekanism's. */
    private static final class ChemicalRenderer implements IIngredientRenderer<NativeChemical> {
        final List<Long> richAmounts = new ArrayList<>();
        final List<Boolean> advancedFlags = new ArrayList<>();
        @Override public void render(GuiGraphics gui, NativeChemical value) { }
        @Override public List<Component> getTooltip(NativeChemical value, TooltipFlag flag) {
            return List.of(Component.literal("chemical:" + value.amount()));
        }
        @Override public void getTooltip(ITooltipBuilder builder, NativeChemical value, TooltipFlag flag) {
            richAmounts.add(value.amount());
            advancedFlags.add(flag.isAdvanced());
            builder.add(Component.literal("chemical:" + value.amount()));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final IJeiRuntime previousRuntime = JemiPlugin.runtime;
        final AEKey key = mock(AEKey.class);
        final ChemicalRenderer renderer = new ChemicalRenderer();
        final MockedStatic<IngredientConverters> converters;
        final MockedStatic<JemiUtil> jemi;
        boolean keyTyping;

        @SuppressWarnings("unchecked")
        Fixture() {
            var tag = new CompoundTag();
            tag.putString("chemical", "test:carbon");
            when(key.toTagGeneric()).thenReturn(tag);
            when(key.getType()).thenReturn(mock(AEKeyType.class));
            when(key.getDisplayName()).thenReturn(Component.literal("Carbon"));
            when(key.formatAmount(anyLong(), any())).thenAnswer(i -> i.getArgument(0) + " mB");
            var runtime = mock(IJeiRuntime.class);
            var manager = mock(IIngredientManager.class);
            var helper = (IIngredientHelper<NativeChemical>) mock(IIngredientHelper.class);
            when(helper.getUniqueId(any(NativeChemical.class), any())).thenReturn("infusion:test:carbon");
            when(helper.isValidIngredient(any(NativeChemical.class))).thenReturn(true);
            when(runtime.getIngredientManager()).thenReturn(manager);
            when(manager.getIngredientHelper(TYPE)).thenReturn(helper);
            when(manager.getIngredientRenderer(TYPE)).thenReturn(renderer);
            when(manager.createTypedIngredient(eq(TYPE), any(NativeChemical.class)))
                    .thenAnswer(i -> Optional.of(typed(TYPE, i.getArgument(1))));
            JemiPlugin.runtime = runtime;
            converters = mockStatic(IngredientConverters.class);
            converters.when(IngredientConverters::getConverters).thenReturn(List.of(new IngredientConverter<NativeChemical>() {
                @Override public IIngredientType<NativeChemical> getIngredientType() { return TYPE; }
                @Override public NativeChemical getIngredientFromStack(GenericStack stack) {
                    return stack.what() == key ? new NativeChemical(stack.amount()) : null;
                }
                @Override public GenericStack getStackFromIngredient(NativeChemical value) {
                    return new GenericStack(key, value.amount());
                }
            }));
            // The mixin is not installed in the unit JVM; emulate its getTyped dispatch only.
            jemi = mockStatic(JemiUtil.class);
            jemi.when(() -> JemiUtil.getTyped(any(EmiStack.class))).thenAnswer(i -> {
                var stack = (AEKeyEmiStack) i.getArgument(0);
                var nativeTyped = keyTyping ? Optional.<ITypedIngredient<?>>empty() : stack.nativeTypedIngredient();
                return nativeTyped.isPresent() ? nativeTyped : Optional.of(typed(AEKeyJeiIngredients.TYPE,
                        new AEKeyJeiIngredients.KeyIngredient(key, Math.max(1, stack.getAmount()))));
            });
        }

        AEKeyEmiStack stack(long amount) {
            return new AEKeyEmiStack(new AEKeyJeiIngredients.KeyIngredient(key, amount));
        }

        @Override public void close() {
            JemiPlugin.runtime = previousRuntime;
            jemi.close();
            converters.close();
        }
    }
}
