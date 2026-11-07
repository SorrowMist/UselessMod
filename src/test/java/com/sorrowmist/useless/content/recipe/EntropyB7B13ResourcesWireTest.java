package com.sorrowmist.useless.content.recipe;

import appeng.recipes.transform.TransformRecipe;
import appeng.recipes.transform.TransformRecipeSerializer;
import com.google.gson.*;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.mixin.ae2.TransformCategoryMergeMixin;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

public class EntropyB7B13ResourcesWireTest {
    private static final Path BASE = Path.of("src/generated/resources/data/useless_mod/recipes");
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static JsonObject recipe(String name) throws Exception {
        return JsonParser.parseString(Files.readString(BASE.resolve(name + ".json"))).getAsJsonObject();
    }
    private static List<String> names() {
        List<String> names = new ArrayList<>(List.of("advanced_alloy_furnace_block", "endless_beaf_item",
                "crafting/multiblock_alloy_furnace_core", "crafting/useful_coil", "advanced_alloy/ingot/useful_ingot"));
        for (int tier = 1; tier <= 9; tier++) {
            names.add("advanced_alloy/ingot/useless_ingot_tier_" + tier);
            names.add("crafting/useless_coil_tier_" + tier);
        }
        return names;
    }

    @Test void allTwentyThreeRoutesKeepTheirExistingIdsAndCorrectAcquisitionTypes() throws Exception {
        assertEquals(23, names().size());
        assertEquals("useless_mod:entropy_transmutation", recipe("advanced_alloy_furnace_block").get("type").getAsString());
        assertEquals("ae2:inscriber", recipe("endless_beaf_item").get("type").getAsString());
        int[] entries = {96, 129, 145, 82};
        for (int tier = 1; tier <= 9; tier++) {
            var ingot = recipe("advanced_alloy/ingot/useless_ingot_tier_" + tier);
            if (tier <= 4) {
                assertEquals("ae2:transform", ingot.get("type").getAsString());
                assertEquals(entries[tier - 1], ingot.getAsJsonArray("ingredients").size());
                assertEquals(tier >= 3, ingot.has("circumstance"));
            } else {
                assertEquals("useless_mod:advanced_alloy_furnace", ingot.get("type").getAsString());
                assertEquals(tier - 1, ingot.get("tier").getAsInt());
            }
            var coil = recipe("crafting/useless_coil_tier_" + tier);
            assertEquals("useless_mod:advanced_alloy_furnace", coil.get("type").getAsString());
            assertEquals(tier - 1, coil.get("tier").getAsInt());
        }
        for (String name : names()) {
            var json = recipe(name);
            if (json.has("id")) assertEquals("useless_mod:" + name, json.get("id").getAsString());
            assertFalse(Files.exists(Path.of("src/main/resources/data/useless_mod/recipes").resolve(name + ".json")));
        }
        assertEquals(9, recipe("crafting/multiblock_alloy_furnace_core").get("tier").getAsInt());
        assertEquals(3_276_800_000L, recipe("advanced_alloy/ingot/useful_ingot").get("energy").getAsLong());
    }

    @Test void referencesExistInActual1201OrAe15OrOwnedItemModelsAndHaveNo121Components() throws Exception {
        Path aeJar = Path.of(TransformRecipe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var zip = new ZipFile(aeJar.toFile())) {
            for (String name : names()) checkReferences(recipe(name), zip);
            assertNotNull(zip.getEntry("data/forge/tags/items/gems/certus_quartz.json"));
        }
        String ninth = Files.readString(BASE.resolve("advanced_alloy/ingot/useless_ingot_tier_9.json"));
        assertTrue(ninth.contains("minecraft:turtle_helmet"));
        assertFalse(ninth.contains("minecraft:heavy_core"));
    }
    private static void checkReferences(JsonElement value, ZipFile ae) {
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(element -> checkReferences(element, ae)); return; }
        if (!value.isJsonObject()) return;
        var object = value.getAsJsonObject();
        assertFalse(object.has("components"));
        if (object.has("item") && object.get("item").isJsonPrimitive()) {
            var id = new ResourceLocation(object.get("item").getAsString());
            if (id.getNamespace().equals("minecraft")) assertTrue(BuiltInRegistries.ITEM.containsKey(id), id.toString());
            if (id.getNamespace().equals("ae2")) assertNotNull(ae.getEntry("assets/ae2/models/item/" + id.getPath() + ".json"), id.toString());
            if (id.getNamespace().equals("useless_mod")) {
                String model = "assets/useless_mod/models/item/" + id.getPath() + ".json";
                assertTrue(Files.exists(Path.of("src/main/resources", model)) || Files.exists(Path.of("src/generated/resources", model)), id.toString());
            }
        }
        object.entrySet().forEach(entry -> checkReferences(entry.getValue(), ae));
    }

    @Test void eachAcquiredItemHasOnlyOneRecipeResourceAndNoOldAlternateRoute() throws Exception {
        Map<String, Integer> occurrences = new HashMap<>();
        for (String root : List.of("src/main/resources/data", "src/generated/resources/data")) {
            try (var files = Files.walk(Path.of(root))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json") && path.toString().contains("recipes")).toList()) {
                    JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    for (String field : List.of("output", "result", "outputs")) {
                        if (json.has(field)) collectOutputs(json.get(field), occurrences);
                    }
                }
            }
        }
        List<String> items = new ArrayList<>(List.of("advanced_alloy_furnace_block", "endless_beaf_item", "multiblock_alloy_furnace_core", "useful_coil", "useful_ingot"));
        for (int tier = 1; tier <= 9; tier++) { items.add("useless_ingot_tier_" + tier); items.add("useless_coil_tier_" + tier); }
        for (String item : items) assertEquals(1, occurrences.getOrDefault("useless_mod:" + item, 0), item);
    }
    private static void collectOutputs(JsonElement value, Map<String, Integer> occurrences) {
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(element -> collectOutputs(element, occurrences)); return; }
        if (!value.isJsonObject()) return;
        JsonObject json = value.getAsJsonObject();
        JsonElement id = json.has("item") ? json.get("item") : json.get("id");
        if (id != null && id.isJsonPrimitive()) occurrences.merge(id.getAsString(), 1, Integer::sum);
    }

    @Test void realAe15WireWithTheExactUnsignedCountPatchRoundTripsAllFourWorldRecipes() throws Exception {
        String name = "appeng.recipes.transform.TransformRecipeSerializer";
        byte[] bytes;
        try (var stream = TransformRecipeSerializer.class.getResourceAsStream("TransformRecipeSerializer.class")) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int patched = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("fromNetwork") || !method.desc.endsWith("Lappeng/recipes/transform/TransformRecipe;")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                // The isolated class loader is a different runtime package. Bridge the unchanged
                // package-private native circumstance reader solely for this bytecode test.
                if (instruction instanceof MethodInsnNode call && call.name.equals("fromNetwork")
                        && call.owner.equals("appeng/recipes/transform/TransformCircumstance")) {
                    call.owner = "com/sorrowmist/useless/content/recipe/EntropyB7B13ResourcesWireTest";
                    call.name = "readCircumstance";
                }
                if (instruction instanceof MethodInsnNode call && call.name.equals("readByte") && call.desc.equals("()B")) {
                    assertInstanceOf(VarInsnNode.class, instruction.getNext());
                    assertEquals(Opcodes.ISTORE, instruction.getNext().getOpcode());
                    assertEquals(4, ((VarInsnNode) instruction.getNext()).var);
                    InsnList unsigned = new InsnList();
                    unsigned.add(new IntInsnNode(Opcodes.SIPUSH, 255));
                    unsigned.add(new InsnNode(Opcodes.IAND));
                    method.instructions.insert(instruction, unsigned);
                    patched++;
                }
            }
        }
        assertEquals(1, patched, "The mixin's first int STORE must still be AE2 15's signed ingredient count");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        class IsolatedSerializerLoader extends ClassLoader {
            IsolatedSerializerLoader() { super(TransformRecipeSerializer.class.getClassLoader()); }
            Class<?> loadPatched(byte[] code) { return defineClass(name, code, 0, code.length); }
        }
        Class<?> patchedType = new IsolatedSerializerLoader().loadPatched(writer.toByteArray());
        Object serializer = patchedType.getField("INSTANCE").get(null);
        var fromNetwork = patchedType.getMethod("fromNetwork", ResourceLocation.class, FriendlyByteBuf.class);
        var hook = TransformCategoryMergeMixin.NativeTransformCountWireMixin.class
                .getDeclaredMethod("useless$unsignedTransformIngredientCount", int.class);
        hook.setAccessible(true);
        var instance = new TransformCategoryMergeMixin.NativeTransformCountWireMixin() {};
        for (int count : new int[] {0, 96, 127, -128, -127, -111, -1})
            assertEquals(count & 255, hook.invoke(instance, count));
        for (int tier = 1; tier <= 4; tier++) {
            JsonObject json = recipe("advanced_alloy/ingot/useless_ingot_tier_" + tier);
            substituteUnregisteredModItems(json);
            var id = new ResourceLocation("test", "entropy_b7b13_tier_" + tier);
            var recipe = TransformRecipeSerializer.INSTANCE.fromJson(id, json);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                TransformRecipeSerializer.INSTANCE.toNetwork(buffer, recipe);
                TransformRecipe decoded = (TransformRecipe) fromNetwork.invoke(serializer, id, buffer);
                assertEquals(recipe.getIngredients().size(), decoded.getIngredients().size());
                assertEquals(recipe.circumstance, decoded.circumstance);
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
    public static appeng.recipes.transform.TransformCircumstance readCircumstance(FriendlyByteBuf buffer) throws Exception {
        var method = appeng.recipes.transform.TransformCircumstance.class.getDeclaredMethod("fromNetwork", FriendlyByteBuf.class);
        method.setAccessible(true);
        return (appeng.recipes.transform.TransformCircumstance) method.invoke(null, buffer);
    }

    private static void substituteUnregisteredModItems(JsonElement value) {
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(EntropyB7B13ResourcesWireTest::substituteUnregisteredModItems); return; }
        if (!value.isJsonObject()) return;
        var json = value.getAsJsonObject();
        if (json.has("item") && json.get("item").isJsonPrimitive()
                && !json.get("item").getAsString().startsWith("minecraft:")) json.addProperty("item", "minecraft:iron_ingot");
        if (json.has("tag")) { json.remove("tag"); json.addProperty("item", "minecraft:quartz"); }
        json.entrySet().forEach(entry -> substituteUnregisteredModItems(entry.getValue()));
    }
}
