package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEItemKey;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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

    static String createLegacySemantic(AdvancedAlloyFurnaceRecipe recipe, HolderLookup.Provider registries) {
        return create(recipe, registries, true, false);
    }

    private static String create(
            AdvancedAlloyFurnaceRecipe recipe,
            HolderLookup.Provider registries,
            boolean normalizeIngredientSemantics) {
        return create(recipe, registries, normalizeIngredientSemantics, true);
    }

    private static String create(
            AdvancedAlloyFurnaceRecipe recipe,
            HolderLookup.Provider registries,
            boolean normalizeIngredientSemantics,
            boolean preserveTags) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(registries, "registries");
        var context = registries.createSerializationContext(JsonOps.INSTANCE);
        JsonElement encoded = AdvancedAlloyFurnaceRecipe.CODEC.codec()
                .encodeStart(context, recipe)
                .getOrThrow();
        JsonObject recipeJson = encoded.getAsJsonObject();
        if (normalizeIngredientSemantics) {
            normalizeIngredients(recipe, recipeJson, context, preserveTags);
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
     * Ordinary simple ingredients are normalized to their item ids. Direct tag values retain
     * their tag location so a tag's membership can change without changing the recipe identity.
     * Non-simple custom ingredients retain their codec form because their behavior can depend on
     * components or predicates that cannot be represented by item ids alone.
     */
    private static void normalizeIngredients(
            AdvancedAlloyFurnaceRecipe recipe,
            JsonObject encoded,
            com.mojang.serialization.DynamicOps<JsonElement> context,
            boolean preserveTags) {
        JsonArray inputs = new JsonArray();
        for (CountedIngredient input : recipe.inputs()) {
            JsonObject counted = new JsonObject();
            counted.add("ingredient", encodeSemanticIngredient(input.ingredient(), context, preserveTags));
            if (input.count() != 1L) {
                counted.addProperty("count", input.count());
            }
            inputs.add(counted);
        }
        encoded.add("ingredients", inputs);

        if (!recipe.catalyst().isEmpty()) {
            encoded.add("catalyst", encodeSemanticIngredient(recipe.catalyst(), context, preserveTags));
        }
        if (recipe.molds().size() == 1) {
            // Preserve the historical single-mold fingerprint shape.
            encoded.add("mold", encodeSemanticIngredient(recipe.molds().getFirst(), context, preserveTags));
        } else if (recipe.molds().size() > 1) {
            List<JsonElement> moldElements = new ArrayList<>();
            for (Ingredient mold : recipe.molds()) {
                moldElements.add(encodeSemanticIngredient(mold, context, preserveTags));
            }
            // Mold requirements are a set-like collection for identity purposes, but repeated
            // entries remain significant. Sorting the encoded elements makes list order irrelevant.
            moldElements.sort(java.util.Comparator.comparing(element -> canonicalize(element).toString()));
            JsonArray molds = new JsonArray();
            moldElements.forEach(molds::add);
            encoded.add("molds", molds);
        }
    }

    private static JsonElement encodeSemanticIngredient(
            Ingredient ingredient,
            com.mojang.serialization.DynamicOps<JsonElement> context,
            boolean preserveTags) {
        if (preserveTags && hasDirectTag(ingredient)) {
            return Ingredient.CODEC.encodeStart(context, ingredient).getOrThrow();
        }
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

    private static boolean hasDirectTag(Ingredient ingredient) {
        if (ingredient == null || ingredient.isCustom()) return false;
        for (Ingredient.Value value : ingredient.getValues()) {
            if (value instanceof Ingredient.TagValue) return true;
        }
        return false;
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
