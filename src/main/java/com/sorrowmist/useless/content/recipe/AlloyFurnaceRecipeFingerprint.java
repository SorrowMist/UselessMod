package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEItemKey;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeMap;

public final class AlloyFurnaceRecipeFingerprint {
    private AlloyFurnaceRecipeFingerprint() {
    }

    public static String create(AdvancedAlloyFurnaceRecipe recipe, HolderLookup.Provider registries) {
        return create(recipe, registries, true);
    }

    static String createLegacy(AdvancedAlloyFurnaceRecipe recipe, HolderLookup.Provider registries) {
        return create(recipe, registries, false);
    }

    private static String create(
            AdvancedAlloyFurnaceRecipe recipe,
            HolderLookup.Provider registries,
            boolean normalizeIngredientSemantics) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(registries, "registries");
        var context = registries.createSerializationContext(JsonOps.INSTANCE);
        JsonElement encoded = AdvancedAlloyFurnaceRecipe.CODEC.codec()
                .encodeStart(context, recipe)
                .getOrThrow();
        JsonObject recipeJson = encoded.getAsJsonObject();
        if (normalizeIngredientSemantics) {
            normalizeIngredients(recipe, recipeJson, context);
        }
        JsonArray exactItemOutputs = new JsonArray();
        for (var output : recipe.outputs()) {
            exactItemOutputs.add(encodeExactOutput(output, context));
        }
        recipeJson.add("fingerprint_item_outputs", exactItemOutputs);
        byte[] canonical = canonicalize(encoded).toString().getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Simple ingredients are sent to clients as their expanded item contents,
     * which discards whether the server originally used a tag. Fingerprints
     * therefore use the actual item-matching semantics on both sides.
     * Non-simple custom ingredients retain their codec form because their
     * behavior can depend on components or predicates that cannot be
     * represented by item ids alone.
     */
    private static void normalizeIngredients(
            AdvancedAlloyFurnaceRecipe recipe,
            JsonObject encoded,
            com.mojang.serialization.DynamicOps<JsonElement> context) {
        JsonArray inputs = new JsonArray();
        for (CountedIngredient input : recipe.inputs()) {
            JsonObject counted = new JsonObject();
            counted.add("ingredient", encodeSemanticIngredient(input.ingredient(), context));
            if (input.count() != 1L) {
                counted.addProperty("count", input.count());
            }
            inputs.add(counted);
        }
        encoded.add("ingredients", inputs);

        if (!recipe.catalyst().isEmpty()) {
            encoded.add("catalyst", encodeSemanticIngredient(recipe.catalyst(), context));
        }
        if (!recipe.mold().isEmpty()) {
            encoded.add("mold", encodeSemanticIngredient(recipe.mold(), context));
        }
    }

    private static JsonElement encodeSemanticIngredient(
            Ingredient ingredient,
            com.mojang.serialization.DynamicOps<JsonElement> context) {
        if (!ingredient.isSimple()) {
            return Ingredient.CODEC.encodeStart(context, ingredient).getOrThrow();
        }

        String[] itemIds = Arrays.stream(ingredient.getItems())
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::getItem)
                .map(BuiltInRegistries.ITEM::getKey)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .sorted()
                .toArray(String[]::new);
        JsonArray values = new JsonArray();
        for (String itemId : itemIds) {
            JsonObject value = new JsonObject();
            value.addProperty("item", itemId);
            values.add(value);
        }
        return values.size() == 1 ? values.get(0) : values;
    }

    /**
     * Keep the historical ItemStack representation whenever it can encode the
     * stack. Minecraft's ItemStack codec intentionally caps counts at 99;
     * large machine outputs use a versioned fallback containing an exact
     * single-item AE key and the independent count.
     */
    private static JsonElement encodeExactOutput(
            net.minecraft.world.item.ItemStack output,
            com.mojang.serialization.DynamicOps<JsonElement> context) {
        var encoded = net.minecraft.world.item.ItemStack.CODEC.encodeStart(context, output);
        var result = encoded.result();
        if (result.isPresent()) {
            return result.get();
        }

        AEItemKey key = AEItemKey.of(output);
        if (key == null) {
            throw new IllegalArgumentException("Cannot encode an empty or invalid item output: "
                    + encoded.error().map(Object::toString).orElse("codec rejected output"));
        }
        JsonObject fallback = new JsonObject();
        fallback.addProperty("format", "ae_item_count_v1");
        fallback.add("key", AEItemKey.CODEC.encodeStart(context, key).getOrThrow());
        fallback.addProperty("count", output.getCount());
        return fallback;
    }

    private static JsonElement canonicalize(JsonElement element) {
        if (element.isJsonObject()) {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            for (var entry : element.getAsJsonObject().entrySet()) sorted.put(entry.getKey(), entry.getValue());
            JsonObject result = new JsonObject();
            for (var entry : sorted.entrySet()) result.add(entry.getKey(), canonicalize(entry.getValue()));
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) result.add(canonicalize(child));
            return result;
        }
        return element.deepCopy();
    }
}
