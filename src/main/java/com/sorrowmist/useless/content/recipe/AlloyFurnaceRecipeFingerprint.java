package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        try {
            return createEncoded(recipe, registries, normalizeIngredientSemantics, preserveTags);
        } catch (RuntimeException ignored) {
            // Keep the historical encoded fingerprint above untouched. This path is only for a
            // runtime recipe that the historical codec cannot represent.
            try {
                return createStructuralFallback(recipe, registries, normalizeIngredientSemantics, preserveTags);
            } catch (RuntimeException structuralFailure) {
                // This is still deterministic: a runtime object address must never be part of a
                // persisted pattern identity.
                return createFinalFallback(recipe);
            }
        }
    }

    private static String createEncoded(
            AdvancedAlloyFurnaceRecipe recipe,
            HolderLookup.Provider registries,
            boolean normalizeIngredientSemantics,
            boolean preserveTags) {
        var context = registries.createSerializationContext(JsonOps.INSTANCE);
        // Do not change this successful path: old patterns depend on its exact JSON shape.
        JsonElement encoded = AdvancedAlloyFurnaceRecipe.CODEC.codec()
                .encodeStart(context, recipe)
                .getOrThrow();
        JsonObject recipeJson = encoded.getAsJsonObject();
        if (normalizeIngredientSemantics) {
            normalizeIngredients(recipe, recipeJson, context, preserveTags);
        }
        if ((!normalizeIngredientSemantics || !preserveTags)
                && !recipe.inputFluids().isEmpty()
                && recipe.inputFluids().stream().allMatch(LongSizedFluidIngredient::fitsInt)) {
            encodeLegacyFluidInputs(recipe, recipeJson, context);
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
     * Builds an identity without invoking a codec. This is deliberately a last resort for
     * malformed third-party data, so every individual field is guarded and a bad component cannot
     * discard the whole recipe.
     */
    private static String createStructuralFallback(
            AdvancedAlloyFurnaceRecipe recipe,
            HolderLookup.Provider registries,
            boolean normalizeIngredientSemantics,
            boolean preserveTags) {
        StringBuilder value = new StringBuilder("alloy_furnace_fingerprint_fallback_v1|");
        append(value, "id", recipe.id());
        append(value, "inputs", recipe.inputs(), input -> safeCountedIngredient(input, registries,
                normalizeIngredientSemantics, preserveTags));
        append(value, "input_fluids", recipe.inputFluids(), input -> safeFluidIngredient(input, registries));
        append(value, "key_inputs", recipe.keyInputs(), input -> safeGenericStack(input, registries));
        append(value, "outputs", recipe.outputs(), output -> safeItemStack(output, registries));
        append(value, "output_fluids", recipe.outputFluids(), fluid -> safeFluidStack(fluid, registries));
        append(value, "key_outputs", recipe.keyOutputs(), output -> safeGenericStack(output, registries));
        append(value, "energy", recipe.energy());
        append(value, "process_time", recipe.processTime());
        append(value, "catalyst", safeIngredient(recipe.catalyst(), registries, normalizeIngredientSemantics,
                preserveTags));
        append(value, "catalyst_uses", recipe.catalystUses());
        append(value, "molds", recipe.molds(), mold -> safeIngredient(mold, registries,
                normalizeIngredientSemantics, preserveTags));
        append(value, "mode", recipe.mode());
        append(value, "tier", recipe.tier());
        return digest(value.toString());
    }

    private static String safeCountedIngredient(
            CountedIngredient input,
            HolderLookup.Provider registries,
            boolean normalizeIngredientSemantics,
            boolean preserveTags) {
        if (input == null) return "null";
        return safeIngredient(input.ingredient(), registries, normalizeIngredientSemantics, preserveTags)
                + "#" + input.count();
    }

    private static String safeFluidIngredient(
            LongSizedFluidIngredient input,
            HolderLookup.Provider registries) {
        if (input == null) return "null";
        return safeFluidIngredientValue(input.ingredient(), registries) + "#" + input.amount();
    }

    private static String safeIngredient(
            Ingredient ingredient,
            HolderLookup.Provider registries,
            boolean normalizeIngredientSemantics,
            boolean preserveTags) {
        if (ingredient == null) return "empty";
        try {
            if (ingredient.isEmpty()) return "empty";
        } catch (RuntimeException exception) {
            return "ingredient_error:" + ingredient.getClass().getName()
                    + ":" + exception.getClass().getName();
        }
        try {
            if (normalizeIngredientSemantics && ingredient.isSimple() && !(preserveTags && hasDirectTag(ingredient))) {
                String[] itemIds = Arrays.stream(ingredient.getItems())
                        .filter(stack -> stack != null && !stack.isEmpty())
                        .map(ItemStack::getItem)
                        .map(BuiltInRegistries.ITEM::getKey)
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .distinct()
                        .sorted()
                        .toArray(String[]::new);
                return String.join(",", itemIds);
            }
        } catch (RuntimeException ignored) {
            // Continue with the codec or textual fallback.
        }
        try {
            var context = registries.createSerializationContext(JsonOps.INSTANCE);
            var encoded = Ingredient.CODEC.encodeStart(context, ingredient);
            if (encoded.result().isPresent()) return canonicalize(encoded.result().get()).toString();
        } catch (RuntimeException ignored) {
            // Continue with the representative stack fallback.
        }
        try {
            String items = Arrays.stream(ingredient.getItems())
                    .map(stack -> safeItemStack(stack, registries))
                    .sorted()
                    .reduce((left, right) -> left + "," + right)
                    .orElse(ingredient.getClass().getName());
            return ingredient.getClass().getName() + "[" + items + "]";
        } catch (RuntimeException ignored) {
            return ingredient.getClass().getName();
        }
    }

    private static String safeFluidIngredientValue(
            net.neoforged.neoforge.fluids.crafting.FluidIngredient ingredient,
            HolderLookup.Provider registries) {
        if (ingredient == null) return "empty";
        try {
            if (ingredient.isEmpty()) return "empty";
        } catch (RuntimeException exception) {
            return "fluid_ingredient_error:" + ingredient.getClass().getName()
                    + ":" + exception.getClass().getName();
        }
        try {
            var context = registries.createSerializationContext(JsonOps.INSTANCE);
            var encoded = net.neoforged.neoforge.fluids.crafting.FluidIngredient.CODEC
                    .encodeStart(context, ingredient);
            if (encoded.result().isPresent()) return canonicalize(encoded.result().get()).toString();
        } catch (RuntimeException ignored) {
            // Use representatives below.
        }
        try {
            return Arrays.stream(ingredient.getStacks())
                    .map(stack -> safeFluidStack(stack, registries))
                    .sorted()
                    .reduce((left, right) -> left + "," + right)
                    .orElse(ingredient.getClass().getName());
        } catch (RuntimeException ignored) {
            return ingredient.getClass().getName();
        }
    }

    /**
     * 把 ItemStack 编码成只由数据内容决定的稳定字符串。
     *
     * <p>编码结果交给 {@link #canonicalize(JsonElement)} 递归排序，因此组件映射、嵌套容器内容等
     * 任何深度的迭代顺序差异都会被消除。需要为物品栈生成跨进程一致的签名时（例如派生配方 id）
     * 应复用本方法，而不要直接拼接组件映射的字符串表示。
     */
    public static String safeItemStack(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null) return "null";
        String itemId;
        try {
            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            itemId = key == null
                    ? "unregistered_item:" + stack.getItem().getClass().getName()
                    : key.toString();
        } catch (RuntimeException exception) {
            itemId = "item_error:" + exception.getClass().getName();
        }
        String components;
        try {
            var context = registries.createSerializationContext(JsonOps.INSTANCE);
            var encoded = net.minecraft.core.component.DataComponentPatch.CODEC
                    .encodeStart(context, stack.getComponentsPatch());
            components = encoded.result().map(AlloyFurnaceRecipeFingerprint::canonicalize)
                    .map(JsonElement::toString)
                    .orElseGet(() -> stableComponentPatch(stack.getComponentsPatch(), registries));
        } catch (RuntimeException exception) {
            components = stableComponentPatch(stack.getComponentsPatch(), registries);
        }
        String count;
        try {
            count = String.valueOf(stack.getCount());
        } catch (RuntimeException exception) {
            count = "count_error:" + exception.getClass().getName();
        }
        return itemId + "#" + count + "#" + components;
    }

    /**
     * 把 FluidStack 编码成只由数据内容决定的稳定字符串；与 {@link #safeItemStack(ItemStack, HolderLookup.Provider)}
     * 同源，供派生配方 id 等需要跨进程一致的场景复用。
     */
    public static String safeFluidStack(FluidStack stack, HolderLookup.Provider registries) {
        if (stack == null) return "null";
        String fluidId;
        try {
            var key = BuiltInRegistries.FLUID.getKey(stack.getFluid());
            fluidId = key == null
                    ? "unregistered_fluid:" + stack.getFluid().getClass().getName()
                    : key.toString();
        } catch (RuntimeException exception) {
            fluidId = "fluid_error:" + exception.getClass().getName();
        }
        String components;
        try {
            var context = registries.createSerializationContext(JsonOps.INSTANCE);
            var encoded = FluidStack.CODEC.encodeStart(context, stack);
            components = encoded.result().map(AlloyFurnaceRecipeFingerprint::canonicalize)
                    .map(JsonElement::toString)
                    .orElseGet(() -> stableComponentPatch(stack.getComponentsPatch(), registries));
        } catch (RuntimeException exception) {
            components = stableComponentPatch(stack.getComponentsPatch(), registries);
        }
        String amount;
        try {
            amount = String.valueOf(stack.getAmount());
        } catch (RuntimeException exception) {
            amount = "amount_error:" + exception.getClass().getName();
        }
        return fluidId + "#" + amount + "#" + components;
    }

    private static String safeGenericStack(GenericStack stack, HolderLookup.Provider registries) {
        if (stack == null) return "null";
        try {
            var context = registries.createSerializationContext(JsonOps.INSTANCE);
            var encoded = GenericStack.CODEC.encodeStart(context, stack);
            if (encoded.result().isPresent()) {
                return canonicalize((JsonElement) encoded.result().get()).toString();
            }
        } catch (RuntimeException exception) {
            // Try the key codec separately so a broken amount or wrapper does not erase the key.
        }
        try {
            var context = registries.createSerializationContext(JsonOps.INSTANCE);
            var encoded = AEKey.CODEC.encodeStart(context, stack.what());
            if (encoded.result().isPresent()) {
                return "key=" + canonicalize(encoded.result().get()) + ";amount=" + stack.amount();
            }
        } catch (RuntimeException exception) {
            // Use registry identity and scalar metadata as the final stable representation.
        }
        AEKey key = stack.what();
        return "key_type=" + (key == null ? "null" : key.getClass().getName())
                + ";key_id=" + safeKeyId(key)
                + ";has_components=" + safeKeyHasComponents(key)
                + ";amount=" + safeLong(() -> stack.amount());
    }

    private static String safeKeyId(AEKey key) {
        if (key == null) return "null";
        try {
            return safeText(key.getId());
        } catch (RuntimeException exception) {
            return "id_error:" + exception.getClass().getName();
        }
    }

    private static String safeKeyHasComponents(AEKey key) {
        if (key == null) return "false";
        try {
            return Boolean.toString(key.hasComponents());
        } catch (RuntimeException exception) {
            return "error:" + exception.getClass().getName();
        }
    }

    private static String stableComponentPatch(
            DataComponentPatch patch, HolderLookup.Provider registries) {
        if (patch == null) return "null";
        try {
            var context = registries.createSerializationContext(JsonOps.INSTANCE);
            var encoded = DataComponentPatch.CODEC.encodeStart(context, patch);
            if (encoded.result().isPresent()) {
                return canonicalize(encoded.result().get()).toString();
            }
        } catch (RuntimeException ignored) {
            // Encode entries independently below so one invalid component cannot erase the stack.
        }

        List<String> entries = new ArrayList<>();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            String type = encodeComponentType(entry.getKey(), registries);
            String value = entry.getValue().isEmpty()
                    ? "removed"
                    : encodeComponentValue(entry.getKey(), entry.getValue().get(), registries);
            entries.add(type + "=" + value);
        }
        entries.sort(Comparator.naturalOrder());
        return "component_patch_v1[" + String.join(",", entries) + "]";
    }

    private static String encodeComponentType(
            DataComponentType<?> type, HolderLookup.Provider registries) {
        try {
            var context = registries.createSerializationContext(JsonOps.INSTANCE);
            var encoded = DataComponentType.CODEC.encodeStart(context, type);
            if (encoded.result().isPresent()) {
                return canonicalize(encoded.result().get()).toString();
            }
        } catch (RuntimeException ignored) {
            // Fall through to the stable implementation class name.
        }
        return "type=" + (type == null ? "null" : type.getClass().getName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String encodeComponentValue(
            DataComponentType<?> type, Object value, HolderLookup.Provider registries) {
        try {
            var context = registries.createSerializationContext(JsonOps.INSTANCE);
            Codec codec = type.codec();
            var encoded = codec.encodeStart(context, value);
            if (encoded.result().isPresent()) {
                return canonicalize((JsonElement) encoded.result().get()).toString();
            }
        } catch (RuntimeException ignored) {
            // A component without a working codec is represented by type and value class only.
        }
        return "value_type=" + (value == null ? "null" : value.getClass().getName());
    }

    private static String safeLong(java.util.function.LongSupplier supplier) {
        try {
            return Long.toString(supplier.getAsLong());
        } catch (RuntimeException exception) {
            return "long_error:" + exception.getClass().getName();
        }
    }

    private static <T> void append(StringBuilder value, String name, T field) {
        value.append(name).append('=');
        appendToken(value, safeText(field));
        value.append(';');
    }

    private static <T> void append(
            StringBuilder value, String name, List<T> fields,
            java.util.function.Function<T, String> encoder) {
        value.append(name).append('[');
        if (fields != null) {
            int index = 0;
            for (T field : fields) {
                value.append(index++).append(':');
                try {
                    appendToken(value, encoder.apply(field));
                } catch (RuntimeException exception) {
                    appendToken(value, "field_error:" + exception.getClass().getName());
                }
                value.append('|');
            }
        }
        value.append("];");
    }

    private static void appendToken(StringBuilder value, String token) {
        String safe = token == null ? "null" : token;
        value.append(safe.length()).append(':').append(safe);
    }

    private static String safeText(Object value) {
        try {
            return String.valueOf(value);
        } catch (RuntimeException exception) {
            return "text_error:" + exception.getClass().getName();
        }
    }

    private static String createFinalFallback(AdvancedAlloyFurnaceRecipe recipe) {
        return digest("alloy_furnace_fingerprint_emergency_v1"
                + "|class=" + recipe.getClass().getName()
                + "|id=" + safeText(recipe.id())
                + "|inputs=" + safeListSize(recipe.inputs())
                + "|input_fluids=" + safeListSize(recipe.inputFluids())
                + "|key_inputs=" + safeListSize(recipe.keyInputs())
                + "|outputs=" + safeListSize(recipe.outputs())
                + "|output_fluids=" + safeListSize(recipe.outputFluids())
                + "|key_outputs=" + safeListSize(recipe.keyOutputs())
                + "|energy=" + safeText(recipe.energy())
                + "|process_time=" + safeText(recipe.processTime())
                + "|catalyst_uses=" + safeText(recipe.catalystUses())
                + "|mode=" + safeText(recipe.mode())
                + "|tier=" + safeText(recipe.tier()));
    }

    private static String safeListSize(List<?> values) {
        try {
            return values == null ? "null" : Integer.toString(values.size());
        } catch (RuntimeException exception) {
            return "size_error:" + exception.getClass().getName();
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
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

    /** Restores the pre-Ingredient representation used by version 1-4 pattern fingerprints. */
    private static void encodeLegacyFluidInputs(
            AdvancedAlloyFurnaceRecipe recipe,
            JsonObject encoded,
            com.mojang.serialization.DynamicOps<JsonElement> context) {
        JsonArray fluids = new JsonArray();
        for (LongSizedFluidIngredient ingredient : recipe.inputFluids()) {
            if (ingredient == null || ingredient.ingredient() == null || ingredient.amount() <= 0) continue;
            for (FluidStack stack : ingredient.toSizedFluidIngredient().getFluids()) {
                if (stack == null || stack.isEmpty()) continue;
                fluids.add(FluidStack.CODEC.encodeStart(context, stack).getOrThrow());
            }
        }
        encoded.add("input_fluids", fluids);
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
