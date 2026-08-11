package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlloyFurnaceRecipeFingerprintTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fingerprintIsStableAndChangesWithRecipeContents() {
        AdvancedAlloyFurnaceRecipe original = recipe(4_000L, Ingredient.of(Items.BRICK));
        AdvancedAlloyFurnaceRecipe equivalent = recipe(4_000L, Ingredient.of(Items.BRICK));
        AdvancedAlloyFurnaceRecipe changedEnergy = recipe(4_001L, Ingredient.of(Items.BRICK));
        AdvancedAlloyFurnaceRecipe changedMold = recipe(4_000L, Ingredient.of(Items.FLOWER_POT));

        String fingerprint = AlloyFurnaceRecipeFingerprint.create(original, RegistryAccess.EMPTY);
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
        assertEquals(fingerprint,
                AlloyFurnaceRecipeFingerprint.create(equivalent, RegistryAccess.EMPTY));
        assertNotEquals(fingerprint,
                AlloyFurnaceRecipeFingerprint.create(changedEnergy, RegistryAccess.EMPTY));
        assertNotEquals(fingerprint,
                AlloyFurnaceRecipeFingerprint.create(changedMold, RegistryAccess.EMPTY));
    }

    @Test
    void fingerprintIncludesOutputDataComponents() {
        ItemStack plainOutput = new ItemStack(Items.GOLD_INGOT, 3);
        ItemStack namedOutput = plainOutput.copy();
        namedOutput.set(DataComponents.CUSTOM_NAME, Component.literal("component-sensitive"));

        assertNotEquals(
                AlloyFurnaceRecipeFingerprint.create(recipe(plainOutput), RegistryAccess.EMPTY),
                AlloyFurnaceRecipeFingerprint.create(recipe(namedOutput), RegistryAccess.EMPTY));
    }

    @Test
    void fingerprintFallsBackForOutputCountsAboveItemStackCodecLimit() {
        ItemStack hundred = new ItemStack(Items.GOLD_INGOT, 100);
        ItemStack oneHundredOne = new ItemStack(Items.GOLD_INGOT, 101);
        ItemStack componentVariant = hundred.copy();
        componentVariant.set(DataComponents.CUSTOM_NAME, Component.literal("large-component"));

        String hundredFingerprint = assertDoesNotThrow(() ->
                AlloyFurnaceRecipeFingerprint.create(recipe(hundred), RegistryAccess.EMPTY));
        assertNotEquals(hundredFingerprint,
                AlloyFurnaceRecipeFingerprint.create(recipe(oneHundredOne), RegistryAccess.EMPTY));
        assertNotEquals(hundredFingerprint,
                AlloyFurnaceRecipeFingerprint.create(recipe(componentVariant), RegistryAccess.EMPTY));
    }

    @Test
    void simpleIngredientFingerprintSurvivesNetworkExpansionAndCandidateReordering() {
        TagKey<Item> testTag = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "fingerprint_tag"));
        Ingredient serverTag = Ingredient.of(testTag);
        Ingredient clientExpansion = Ingredient.of(Arrays.stream(serverTag.getItems()));
        RegistryFriendlyByteBuf networkBuffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.OTHER);
        Ingredient networkExpansion;
        try {
            CountedIngredient.INGREDIENT_STREAM_CODEC.encode(networkBuffer, serverTag);
            networkExpansion = CountedIngredient.INGREDIENT_STREAM_CODEC.decode(networkBuffer);
        } finally {
            networkBuffer.release();
        }

        assertNotEquals(fingerprint(recipeWithInput(serverTag)), fingerprint(recipeWithInput(clientExpansion)));
        assertEquals(fingerprint(recipeWithInput(serverTag)), fingerprint(recipeWithInput(networkExpansion)));
        assertTrue(networkExpansion.getValues()[0] instanceof Ingredient.TagValue);
        assertNotEquals(fingerprint(recipeWithInput(serverTag)),
                AlloyFurnaceRecipeFingerprint.createLegacySemantic(recipeWithInput(serverTag), RegistryAccess.EMPTY));
        assertEquals(fingerprint(recipeWithInput(clientExpansion)),
                AlloyFurnaceRecipeFingerprint.createLegacySemantic(recipeWithInput(serverTag), RegistryAccess.EMPTY));
        assertEquals(
                fingerprint(recipeWithInput(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT))),
                fingerprint(recipeWithInput(Ingredient.of(Items.GOLD_INGOT, Items.IRON_INGOT))));
        assertNotEquals(
                fingerprint(recipeWithInput(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT))),
                fingerprint(recipeWithInput(Ingredient.of(Items.IRON_INGOT, Items.DIAMOND))));
    }

    @Test
    void componentSensitiveIngredientStillChangesFingerprint() {
        ItemStack first = namedPaper("first");
        ItemStack second = namedPaper("second");

        assertNotEquals(
                fingerprint(recipeWithInput(DataComponentIngredient.of(true, first))),
                fingerprint(recipeWithInput(DataComponentIngredient.of(true, second))));
    }

    @Test
    void omniversalPatternDataDefensivelyCopiesSlotsAndBuildsIdentity() {
        List<Integer> inputs = new ArrayList<>(List.of(1, 3));
        OmniversalPatternData data = new OmniversalPatternData(
                OmniversalPatternData.CURRENT_VERSION,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "bound_recipe"),
                "abc123",
                false,
                Optional.empty(),
                inputs,
                List.of(2));
        inputs.add(5);

        assertEquals(List.of(1, 3), data.itemIdInputSlots());
        assertEquals(data.recipeId(), data.identity().recipeId());
        assertEquals(data.recipeFingerprint(), data.identity().fingerprint());
        assertThrows(UnsupportedOperationException.class, () -> data.itemIdInputSlots().add(7));
    }

    @Test
    void omittedPatternVersionStillDecodesAsLegacyVersionOne() {
        OmniversalPatternData legacy = patternData(1);
        var encodedLegacy = OmniversalPatternData.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow();
        assertFalse(encodedLegacy.getAsJsonObject().has("version"));
        assertEquals(1, OmniversalPatternData.CODEC.parse(JsonOps.INSTANCE, encodedLegacy)
                .getOrThrow().version());

        var encodedCurrent = OmniversalPatternData.CODEC
                .encodeStart(JsonOps.INSTANCE, patternData(OmniversalPatternData.CURRENT_VERSION))
                .getOrThrow();
        assertEquals(OmniversalPatternData.CURRENT_VERSION,
                encodedCurrent.getAsJsonObject().get("version").getAsInt());
    }

    @Test
    void aCatalogSnapshotClaimsAtMostOneCompensationRebuild() {
        AlloyFurnaceRecipeCatalog.ResolutionMisses misses =
                new AlloyFurnaceRecipeCatalog.ResolutionMisses(false);
        AlloyFurnaceRecipeIdentity first = identity("first");
        AlloyFurnaceRecipeIdentity second = identity("second");
        int rebuilds = 0;
        for (int attempt = 0; attempt < 100; attempt++) {
            if (misses.claimCompensationRebuild(first)) {
                rebuilds++;
                misses.remember(first);
            }
        }
        if (misses.claimCompensationRebuild(second)) rebuilds++;

        assertEquals(1, rebuilds);
        assertFalse(new AlloyFurnaceRecipeCatalog.ResolutionMisses(true)
                .claimCompensationRebuild(first));
    }

    @Test
    void catalogInvalidationAdvancesThePublicationGeneration() {
        long before = AlloyFurnaceRecipeCatalog.generation();
        AlloyFurnaceRecipeCatalog.invalidate();
        assertTrue(AlloyFurnaceRecipeCatalog.generation() > before);
    }

    private static AdvancedAlloyFurnaceRecipe recipe(long energy, Ingredient mold) {
        return recipe(energy, mold, new ItemStack(Items.GOLD_INGOT, 3));
    }

    private static AdvancedAlloyFurnaceRecipe recipe(ItemStack output) {
        return recipe(4_000L, Ingredient.of(Items.BRICK), output);
    }

    private static AdvancedAlloyFurnaceRecipe recipe(long energy, Ingredient mold, ItemStack output) {
        return recipe(energy, mold, output, Ingredient.of(Items.IRON_INGOT));
    }

    private static AdvancedAlloyFurnaceRecipe recipeWithInput(Ingredient input) {
        return recipe(4_000L, Ingredient.of(Items.BRICK),
                new ItemStack(Items.GOLD_INGOT, 3), input);
    }

    private static AdvancedAlloyFurnaceRecipe recipe(
            long energy, Ingredient mold, ItemStack output, Ingredient input) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "fingerprinted"),
                List.of(new CountedIngredient(input, 2)),
                List.of(),
                List.of(),
                List.of(output),
                List.of(),
                List.of(),
                energy,
                40,
                Ingredient.EMPTY,
                0,
                mold,
                AlloyFurnaceMode.NORMAL);
    }

    private static String fingerprint(AdvancedAlloyFurnaceRecipe recipe) {
        return AlloyFurnaceRecipeFingerprint.create(recipe, RegistryAccess.EMPTY);
    }

    private static ItemStack namedPaper(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static OmniversalPatternData patternData(int version) {
        return new OmniversalPatternData(
                version,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "versioned"),
                "fingerprint",
                false,
                Optional.empty(),
                List.of(),
                List.of());
    }

    private static AlloyFurnaceRecipeIdentity identity(String path) {
        return new AlloyFurnaceRecipeIdentity(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", path), path);
    }
}
