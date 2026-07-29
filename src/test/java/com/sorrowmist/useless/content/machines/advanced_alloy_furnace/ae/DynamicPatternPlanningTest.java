package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.AEKeyFilter;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicPatternPlanningTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void usesSameItemCraftableWhenDeclaredComponentsAreNotCraftable() {
        AEItemKey declared = namedKey(Items.WRITTEN_BOOK, "declared");
        AEItemKey generated = namedKey(Items.WRITTEN_BOOK, "generated");
        IPatternDetails pattern = dynamicInputPattern(declared);

        AEKey result = DynamicPatternPlanning.preferDeclaredCraftableInput(
                pattern, 0, declared, service(null, generated));

        assertEquals(generated, result);
    }

    @Test
    void prefersAnExactDeclaredCraftableBeforeFuzzyOutput() {
        AEItemKey declared = namedKey(Items.WRITTEN_BOOK, "declared");
        AEItemKey generated = namedKey(Items.WRITTEN_BOOK, "generated");
        IPatternDetails pattern = dynamicInputPattern(declared);

        AEKey result = DynamicPatternPlanning.preferDeclaredCraftableInput(
                pattern, 0, declared, service(declared, generated));

        assertSame(declared, result);
    }

    @Test
    void rejectsFuzzyCraftablesFromAnotherItemId() {
        AEItemKey declared = namedKey(Items.WRITTEN_BOOK, "declared");
        AEItemKey wrongTier = namedKey(Items.ENCHANTED_BOOK, "generated");
        IPatternDetails pattern = dynamicInputPattern(declared);

        AEKey result = DynamicPatternPlanning.preferDeclaredCraftableInput(
                pattern, 0, declared, service(null, wrongTier));

        assertSame(declared, result);
    }

    @Test
    void onlyOccultismSpecialBindingRecipeGetsDynamicOutputMatching() {
        var foliot = id("occultism", "book_of_binding_bound_foliot");
        assertTrue(OccultismBoundBookPatternDetails.isSupportedRecipeAndOutput(
                OccultismBoundBookPatternDetails.RECIPE_ID, foliot));
        assertFalse(OccultismBoundBookPatternDetails.isSupportedRecipeAndOutput(
                id("kubejs", "custom_bound_book"), foliot));
        assertFalse(OccultismBoundBookPatternDetails.isSupportedRecipeAndOutput(
                OccultismBoundBookPatternDetails.RECIPE_ID,
                id("occultism", "book_of_binding_foliot")));
    }

    private static AEItemKey namedKey(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return Objects.requireNonNull(AEItemKey.of(stack));
    }

    private static IPatternDetails dynamicInputPattern(AEItemKey input) {
        return new DynamicComponentPattern() {
            private final IInput[] inputs = {new TestInput(input)};

            @Override
            public String dynamicPatternIdentity() {
                return "test:bound_book_input";
            }

            @Override
            public boolean isItemIdInput(int slot) {
                return slot == 0;
            }

            @Override
            public boolean isItemIdOutput(int slot) {
                return false;
            }

            @Override
            public boolean usesDynamicOutputs() {
                return false;
            }

            @Override
            public AEItemKey getDefinition() {
                return Objects.requireNonNull(AEItemKey.of(Items.PAPER));
            }

            @Override
            public IInput[] getInputs() {
                return inputs;
            }

            @Override
            public List<GenericStack> getOutputs() {
                return List.of(new GenericStack(
                        Objects.requireNonNull(AEItemKey.of(Items.PAPER)), 1));
            }
        };
    }

    private static ICraftingService service(@Nullable AEKey exact, @Nullable AEKey fuzzy) {
        IPatternDetails marker = dynamicInputPattern(namedKey(Items.BOOK, "marker"));
        return (ICraftingService) Proxy.newProxyInstance(
                ICraftingService.class.getClassLoader(),
                new Class<?>[]{ICraftingService.class},
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "getCraftingFor" -> exact != null && exact.equals(arguments[0])
                            ? List.of(marker) : List.of();
                    case "getFuzzyCraftable" -> {
                        AEKeyFilter filter = (AEKeyFilter) arguments[1];
                        yield fuzzy != null && filter.matches(fuzzy) ? fuzzy : null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return 0;
    }

    private record TestInput(AEItemKey key) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[]{new GenericStack(key, 1)};
        }

        @Override
        public long getMultiplier() {
            return 1;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input instanceof AEItemKey item && item.getItem() == key.getItem();
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
