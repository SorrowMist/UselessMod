package com.sorrowmist.useless.content.recipe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.core.component.RitualBlueprintPentacles;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlloyFurnaceRecipeCatalogMoldTest {
    private static Level level;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        level = (Level) ((Unsafe) field.get(null)).allocateInstance(ServerLevel.class);
    }

    @Test
    void acceptsMoldsDeclaredByExternalRecipes() {
        AdvancedAlloyFurnaceRecipe recipe = recipe(Ingredient.of(Items.FURNACE));

        assertTrue(AlloyFurnaceRecipeCatalog.isKnownMold(
                new ItemStack(Items.FURNACE), List.of(recipe)));
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(
                new ItemStack(Items.CHEST), List.of(recipe)));
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(
                ItemStack.EMPTY, List.of(recipe)));
    }

    @Test
    void preservesComponentSensitiveMoldMatching() {
        ItemStack required = namedPaper("required");
        Ingredient componentMold = DataComponentIngredient.of(true, required.copy());
        AdvancedAlloyFurnaceRecipe recipe = recipe(componentMold);

        assertTrue(AlloyFurnaceRecipeCatalog.isKnownMold(required.copy(), List.of(recipe)));
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(
                namedPaper("different"), List.of(recipe)));
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(
                new ItemStack(Items.PAPER), List.of(recipe)));
    }

    @Test
    void acceptsBlueprintsContainingTheRequiredPentacle() {
        ResourceLocation requiredId = ResourceLocation.fromNamespaceAndPath("occultism", "craft_afrit");
        ResourceLocation additionalId = ResourceLocation.fromNamespaceAndPath("occultism", "possess_djinni");
        ItemStack required = new ItemStack(Items.PAPER);
        required.set(UComponents.RITUAL_BLUEPRINT_PENTACLE.get(), RitualBlueprintPentacles.of(requiredId));
        AdvancedAlloyFurnaceRecipe recipe = recipe(DataComponentIngredient.of(true, required));

        ItemStack multiple = new ItemStack(Items.PAPER);
        multiple.set(UComponents.RITUAL_BLUEPRINT_PENTACLE.get(),
                RitualBlueprintPentacles.of(List.of(requiredId, additionalId)));
        ItemStack wrong = new ItemStack(Items.PAPER);
        wrong.set(UComponents.RITUAL_BLUEPRINT_PENTACLE.get(), RitualBlueprintPentacles.of(additionalId));

        assertTrue(AlloyFurnaceRecipeCatalog.isKnownMold(multiple, List.of(recipe)));
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(wrong, List.of(recipe)));
    }

    @Test
    void ignoresRecipesWithoutMolds() {
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(
                new ItemStack(Items.FURNACE), List.of(recipe(Ingredient.EMPTY))));
    }

    @Test
    void matchesOutputComponentsExactly() {
        // Two recipes that are identical except for a non-default output component (custom name
        // standing in for Productive Bees' bee_type). A pattern carrying one component must match
        // only the recipe with that same component, never the other and never a bare-item recipe.
        // This guards against the relaxed matching that collapsed all honeycombs to bare items and
        // caused the wrong per-bee mold (black_quartz) to be encoded.
        ItemStack outputA = namedPaper("bee_a");
        ItemStack outputB = namedPaper("bee_b");
        ItemStack outputPlain = new ItemStack(Items.PAPER);

        AdvancedAlloyFurnaceRecipe recipeA = recipeWithOutput(outputA);
        AdvancedAlloyFurnaceRecipe recipeB = recipeWithOutput(outputB);
        AdvancedAlloyFurnaceRecipe recipePlain = recipeWithOutput(outputPlain);

        IPatternDetails patternA = processingPattern(new ItemStack(Items.IRON_INGOT), outputA);

        assertTrue(AlloyFurnaceRecipeCatalog.matchesPattern(recipeA, patternA));
        assertFalse(AlloyFurnaceRecipeCatalog.matchesPattern(recipeB, patternA));
        assertFalse(AlloyFurnaceRecipeCatalog.matchesPattern(recipePlain, patternA));
    }

    @Test
    void matchesComponentlessOutputsUnaffected() {
        // Normal recipes (no output components) must still match. Default-valued/absent components
        // are not part of the AEItemKey patch, so exact matching does not regress plain recipes.
        AdvancedAlloyFurnaceRecipe recipe = recipeWithOutput(new ItemStack(Items.GOLD_INGOT));
        IPatternDetails pattern = processingPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT));

        assertTrue(AlloyFurnaceRecipeCatalog.matchesPattern(recipe, pattern));
    }

    @Test
    void knownRecipeMayOmitSecondaryOutputs() {
        AdvancedAlloyFurnaceRecipe recipe = recipeWithOutputs(
                new ItemStack(Items.GOLD_INGOT, 3), new ItemStack(Items.DIAMOND));
        IPatternDetails pattern = processingPattern(
                new ItemStack(Items.IRON_INGOT), List.of(new ItemStack(Items.GOLD_INGOT, 3)));

        assertTrue(AlloyFurnaceRecipeCatalog.matchesRecipe(level, recipe, pattern));
    }

    @Test
    void omittedOutputsDoNotRelaxRetainedOutputMatching() {
        AdvancedAlloyFurnaceRecipe recipe = recipeWithOutputs(
                new ItemStack(Items.GOLD_INGOT, 3), new ItemStack(Items.DIAMOND));

        assertFalse(AlloyFurnaceRecipeCatalog.matchesRecipe(level, recipe,
                processingPattern(new ItemStack(Items.IRON_INGOT),
                        List.of(new ItemStack(Items.GOLD_INGOT, 2)))));
        assertFalse(AlloyFurnaceRecipeCatalog.matchesRecipe(level, recipe,
                processingPattern(new ItemStack(Items.IRON_INGOT),
                        List.of(new ItemStack(Items.GOLD_INGOT, 3), new ItemStack(Items.EMERALD)))));
    }

    @Test
    void retainedSecondaryOutputMayBecomeThePrimaryOutput() {
        AdvancedAlloyFurnaceRecipe recipe = recipeWithOutputs(
                new ItemStack(Items.GOLD_INGOT, 3), new ItemStack(Items.DIAMOND));

        assertTrue(AlloyFurnaceRecipeCatalog.matchesRecipe(level, recipe,
                processingPattern(new ItemStack(Items.IRON_INGOT),
                        List.of(new ItemStack(Items.DIAMOND)))));
    }

    private static IPatternDetails processingPattern(ItemStack input, ItemStack output) {
        return processingPattern(input, List.of(output));
    }

    private static IPatternDetails processingPattern(ItemStack input, List<ItemStack> outputs) {
        GenericStack encodedInput = Objects.requireNonNull(GenericStack.fromItemStack(input));
        List<GenericStack> encodedOutputs = outputs.stream()
                .map(stack -> Objects.requireNonNull(GenericStack.fromItemStack(stack)))
                .toList();
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(encodedInput), encodedOutputs);
        return new AEProcessingPattern(Objects.requireNonNull(AEItemKey.of(encoded)));
    }

    private static ItemStack namedPaper(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static AdvancedAlloyFurnaceRecipe recipe(Ingredient mold) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "mold"),
                List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.GOLD_INGOT)),
                List.of(),
                List.of(),
                100L,
                20,
                Ingredient.EMPTY,
                0,
                mold,
                AlloyFurnaceMode.NORMAL);
    }

    private static AdvancedAlloyFurnaceRecipe recipeWithOutput(ItemStack output) {
        return recipeWithOutputs(output);
    }

    private static AdvancedAlloyFurnaceRecipe recipeWithOutputs(ItemStack... outputs) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "output"),
                List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(),
                List.of(),
                List.of(outputs),
                List.of(),
                List.of(),
                100L,
                20,
                Ingredient.EMPTY,
                0,
                Ingredient.EMPTY,
                AlloyFurnaceMode.NORMAL);
    }
}
