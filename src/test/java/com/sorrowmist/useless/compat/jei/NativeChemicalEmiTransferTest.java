package com.sorrowmist.useless.compat.jei;

import appeng.api.integrations.emi.EmiStackConverters;
import appeng.api.integrations.jei.IngredientConverter;
import appeng.api.integrations.jei.IngredientConverters;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.jei.AEKeyEmiStack;
import com.sorrowmist.useless.compat.forge1201.jei.AEKeyEmiStackConverter;
import com.sorrowmist.useless.mixin.emi.JemiAEKeyMixin;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.jemi.JemiPlugin;
import dev.emi.emi.jemi.JemiUtil;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeChemicalEmiTransferTest {
    private record NativeChemical(long amount) { }
    private static final IIngredientType<NativeChemical> TYPE = () -> NativeChemical.class;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void nativeEnrichedCarbonOutputAndChemicalInputKeepEightyNotOne() throws Exception {
        AEKey key = key("carbon");
        var converter = converter(TYPE, key);
        try (var jei = mockStatic(IngredientConverters.class);
             var emi = mockStatic(EmiStackConverters.class)) {
            jei.when(IngredientConverters::getConverters).thenReturn(List.of(converter));
            emi.when(EmiStackConverters::getConverters).thenReturn(List.of(new AEKeyEmiStackConverter()));
            var stack = bridge(TYPE, new NativeChemical(80));
            assertInstanceOf(AEKeyEmiStack.class, stack);
            assertEquals(80, stack.getAmount());
            assertEquals(new GenericStack(key, 80), EmiStackHelper.toGenericStack(stack));
            var recipe = mock(EmiRecipe.class);
            when(recipe.getInputs()).thenReturn(List.of(stack));
            when(recipe.getOutputs()).thenReturn(List.of(stack));
            // AE2 overwrites converted input quantities with EmiIngredient.getAmount().
            // Fixing only its stack converter would still turn an 80 mB input into 1 mB.
            assertEquals(List.of(List.of(new GenericStack(key, 80))), EmiStackHelper.ofInputs(recipe));
            assertEquals(List.of(new GenericStack(key, 80)), EmiStackHelper.ofOutputs(recipe));
        }
    }

    @Test void copiesAndEditedAmountsAreNotMultipliedByOriginalChemicalQuantity() throws Exception {
        AEKey key = key("carbon");
        try (var jei = mockStatic(IngredientConverters.class)) {
            jei.when(IngredientConverters::getConverters).thenReturn(List.of(converter(TYPE, key)));
            var stack = (AEKeyEmiStack) bridge(TYPE, new NativeChemical(80));
            var edited = stack.copy().setAmount(160);
            var converter = new AEKeyEmiStackConverter();
            assertEquals(new GenericStack(key, 160), converter.toGenericStack(edited));
            assertEquals(new GenericStack(key, 80), converter.toGenericStack(stack));
            assertEquals(160, edited.copy().getAmount());
            edited.setAmount(0);
            assertNull(converter.toGenericStack(edited));
        }
    }

    @Test void allFourChemicalFormsAndLongAmountsUseLiveJeiConverters() throws Exception {
        for (String form : List.of("gas", "infusion", "pigment", "slurry")) {
            AEKey key = key(form);
            long amount = 4_500_000_080L;
            IIngredientType<NativeChemical> type = new IIngredientType<>() {
                @Override public Class<NativeChemical> getIngredientClass() { return NativeChemical.class; }
            };
            try (var jei = mockStatic(IngredientConverters.class)) {
                jei.when(IngredientConverters::getConverters).thenReturn(List.of(converter(type, key)));
                assertEquals(new GenericStack(key, amount),
                        GenericStackJeiIngredientProviders.fromJei(type, new NativeChemical(amount)));
                var stack = bridge(type, new NativeChemical(amount));
                assertEquals(amount, stack.getAmount());
                assertEquals(new GenericStack(key, amount), new AEKeyEmiStackConverter().toGenericStack(stack));
            }
        }
    }

    @Test void nativeIdentityViewDoesNotReenterTheJemiConversionHook() throws Exception {
        AEKey key = key("carbon");
        var oldRuntime = JemiPlugin.runtime;
        var runtime = mock(IJeiRuntime.class);
        var manager = mock(IIngredientManager.class);
        @SuppressWarnings("unchecked") var helper = (IIngredientHelper<NativeChemical>) mock(IIngredientHelper.class);
        @SuppressWarnings("unchecked") var renderer = (IIngredientRenderer<NativeChemical>) mock(IIngredientRenderer.class);
        when(runtime.getIngredientManager()).thenReturn(manager);
        when(manager.getIngredientHelper(TYPE)).thenReturn(helper);
        when(manager.getIngredientRenderer(TYPE)).thenReturn(renderer);
        when(helper.getUniqueId(any(NativeChemical.class), any())).thenReturn("infusion:mekanism:carbon");
        JemiPlugin.runtime = runtime;
        try (var jei = mockStatic(IngredientConverters.class);
             var jemi = mockStatic(JemiUtil.class)) {
            jei.when(IngredientConverters::getConverters).thenReturn(List.of(converter(TYPE, key)));
            var stack = bridge(TYPE, new NativeChemical(80));
            assertEquals("infusion:mekanism:carbon", stack.getKey());
            assertEquals(80, stack.copy().getAmount());
            jemi.verifyNoInteractions();
        } finally { JemiPlugin.runtime = oldRuntime; }
    }

    @Test void nativeGhostDragRoundTripKeepsNativeTypeAndEditedAmount() throws Exception {
        AEKey key = key("carbon");
        var oldRuntime = JemiPlugin.runtime;
        var runtime = mock(IJeiRuntime.class);
        var manager = mock(IIngredientManager.class);
        @SuppressWarnings("unchecked") var helper = (IIngredientHelper<NativeChemical>) mock(IIngredientHelper.class);
        @SuppressWarnings("unchecked") var renderer = (IIngredientRenderer<NativeChemical>) mock(IIngredientRenderer.class);
        when(runtime.getIngredientManager()).thenReturn(manager);
        when(manager.getIngredientHelper(TYPE)).thenReturn(helper);
        when(manager.getIngredientRenderer(TYPE)).thenReturn(renderer);
        when(manager.createTypedIngredient(eq(TYPE), any(NativeChemical.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") var typed = (ITypedIngredient<NativeChemical>) mock(ITypedIngredient.class);
            when(typed.getType()).thenReturn(TYPE);
            when(typed.getIngredient()).thenReturn(invocation.getArgument(1));
            return Optional.of(typed);
        });
        JemiPlugin.runtime = runtime;
        try (var jei = mockStatic(IngredientConverters.class)) {
            jei.when(IngredientConverters::getConverters).thenReturn(List.of(converter(TYPE, key)));
            var stack = bridge(TYPE, new NativeChemical(80));
            var original = typedBridge(stack).orElseThrow();
            assertSame(TYPE, original.getType());
            assertEquals(new NativeChemical(80), original.getIngredient());
            var edited = typedBridge(stack.copy().setAmount(160)).orElseThrow();
            assertSame(TYPE, edited.getType());
            assertEquals(new NativeChemical(160), edited.getIngredient());
            assertEquals(new NativeChemical(80), typedBridge(stack).orElseThrow().getIngredient());
            assertNull(typedBridge(stack.copy().setAmount(0)));
        } finally { JemiPlugin.runtime = oldRuntime; }
    }

    @Test void unsupportedOrEmptyNativeIngredientsAreNotTurnedIntoFakeItems() throws Exception {
        AEKey key = key("empty");
        try (var jei = mockStatic(IngredientConverters.class)) {
            jei.when(IngredientConverters::getConverters).thenReturn(List.of());
            assertNull(bridge(TYPE, new NativeChemical(80)));
            assertNull(GenericStackJeiIngredientProviders.fromJei(TYPE, "wrong type"));
            assertNull(GenericStackJeiIngredientProviders.fromJei(null, new NativeChemical(80)));
            jei.when(IngredientConverters::getConverters).thenReturn(List.of(converter(TYPE, key)));
            assertNull(bridge(TYPE, new NativeChemical(0)));
            assertNull(bridge(TYPE, new NativeChemical(-1)));
        }
    }

    @Test void ingredientTypesSharingAClassCannotUseEachOthersConverter() {
        AEKey key = key("carbon");
        IIngredientType<NativeChemical> otherType = new IIngredientType<>() {
            @Override public Class<NativeChemical> getIngredientClass() { return NativeChemical.class; }
        };
        try (var jei = mockStatic(IngredientConverters.class)) {
            jei.when(IngredientConverters::getConverters).thenReturn(List.of(converter(TYPE, key)));
            assertNull(GenericStackJeiIngredientProviders.fromJei(otherType, new NativeChemical(80)));
        }
    }

    @Test void reverseBridgePreservesCustomKeysButLeavesItemsAndFluidsToAe2() {
        var converter = new AEKeyEmiStackConverter();
        AEKey key = key("carbon");
        try (var jei = mockStatic(IngredientConverters.class)) {
            jei.when(IngredientConverters::getConverters).thenReturn(List.of());
            var stack = converter.toEmiStack(new GenericStack(key, 80));
            assertInstanceOf(AEKeyEmiStack.class, stack);
            assertEquals(new GenericStack(key, 80), converter.toGenericStack(stack));
            assertNull(converter.toEmiStack(new GenericStack(mock(AEItemKey.class), 1)));
            assertNull(converter.toEmiStack(new GenericStack(mock(AEFluidKey.class), 1)));
            assertNull(converter.toEmiStack(new GenericStack(key, 0)));
            assertNull(converter.toGenericStack(mock(EmiStack.class)));
        }
    }

    private static AEKey key(String name) {
        AEKey key = mock(AEKey.class);
        var tag = new CompoundTag();
        tag.putString("chemical", "test:" + name);
        when(key.toTagGeneric()).thenReturn(tag);
        return key;
    }

    private static IngredientConverter<NativeChemical> converter(IIngredientType<NativeChemical> type, AEKey key) {
        return new IngredientConverter<>() {
            @Override public IIngredientType<NativeChemical> getIngredientType() { return type; }
            @Override public NativeChemical getIngredientFromStack(GenericStack stack) {
                return stack.what() == key ? new NativeChemical(stack.amount()) : null;
            }
            @Override public GenericStack getStackFromIngredient(NativeChemical ingredient) {
                return new GenericStack(key, ingredient.amount());
            }
        };
    }

    private static Optional<ITypedIngredient<?>> typedBridge(EmiStack stack) throws Exception {
        var callback = new CallbackInfoReturnable<Optional<ITypedIngredient<?>>>("getTyped", true);
        var method = JemiAEKeyMixin.class.getDeclaredMethod("useless$nativeTyped",
                EmiStack.class, CallbackInfoReturnable.class);
        method.setAccessible(true);
        method.invoke(null, stack, callback);
        return callback.isCancelled() ? callback.getReturnValue() : null;
    }

    private static EmiStack bridge(IIngredientType<?> type, Object ingredient) throws Exception {
        var callback = new CallbackInfoReturnable<EmiStack>("getStack", true);
        var method = JemiAEKeyMixin.class.getDeclaredMethod("useless$aeKey",
                IIngredientType.class, Object.class, CallbackInfoReturnable.class);
        method.setAccessible(true);
        method.invoke(null, type, ingredient, callback);
        return callback.isCancelled() ? callback.getReturnValue() : null;
    }
}
