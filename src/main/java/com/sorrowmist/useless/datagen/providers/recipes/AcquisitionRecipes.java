package com.sorrowmist.useless.datagen.providers.recipes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Forge 1.20.1 acquisition routes. Pure JSON definitions also allow offline datagen regression tests. */
public final class AcquisitionRecipes {
    private AcquisitionRecipes() {}

    public static void generate(Consumer<FinishedRecipe> output) {
        definitions().forEach((path, json) -> output.accept(new FinishedRecipe() {
            @Override public void serializeRecipeData(JsonObject target) {
                json.entrySet().forEach(entry -> {
                    if (!entry.getKey().equals("type")) target.add(entry.getKey(), entry.getValue().deepCopy());
                });
            }
            @Override public ResourceLocation getId() { return new ResourceLocation("useless_mod", path); }
            @Override public RecipeSerializer<?> getType() {
                return BuiltInRegistries.RECIPE_SERIALIZER.get(new ResourceLocation(json.get("type").getAsString()));
            }
            @Override public JsonObject serializeAdvancement() { return null; }
            @Override public ResourceLocation getAdvancementId() { return null; }
        }));
    }

    public static Map<String, JsonObject> definitions() {
        Map<String, JsonObject> recipes = new LinkedHashMap<>();
        transform(recipes, 1, false, new String[]{"minecraft:mutton", "minecraft:beef", "minecraft:porkchop",
                "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond"}, new int[]{16,16,16,16,16,16});
        transform(recipes, 2, false, new String[]{"minecraft:rotten_flesh", "minecraft:gunpowder", "minecraft:bone",
                "ae2:sky_dust", "#forge:gems/certus_quartz", ingot(1)}, new int[]{16,16,16,64,16,1});
        transform(recipes, 3, true, new String[]{"minecraft:red_nether_bricks", "minecraft:blaze_powder",
                "minecraft:glowstone_dust", "ae2:certus_quartz_dust", "ae2:matter_ball", ingot(2)},
                new int[]{16,32,32,32,32,1});
        transform(recipes, 4, true, new String[]{"minecraft:ender_eye", "minecraft:chorus_flower",
                "minecraft:phantom_membrane", "minecraft:gold_block", ingot(2), ingot(3)}, new int[]{16,16,32,16,1,1});

        alloy(recipes, "advanced_alloy/ingot/useless_ingot_tier_5", ingot(5), 3_200_000L, 1, 4,
                new String[]{"minecraft:wither_skeleton_skull", "minecraft:shulker_shell", "minecraft:sea_lantern",
                        "minecraft:gold_block", "minecraft:diamond_block", ingot(4)}, new int[]{32,32,32,32,32,1});
        alloy(recipes, "advanced_alloy/ingot/useless_ingot_tier_6", ingot(6), 12_800_000L, 1, 5,
                new String[]{"minecraft:dragon_breath", "minecraft:totem_of_undying", "minecraft:enchanted_golden_apple",
                        "ae2:fluix_dust", "minecraft:diamond_block", ingot(5)}, new int[]{16,8,8,128,64,1});
        alloy(recipes, "advanced_alloy/ingot/useless_ingot_tier_7", ingot(7), 51_200_000L, 1, 6,
                new String[]{"minecraft:nether_star", "minecraft:wither_rose", "minecraft:ghast_tear", "ae2:singularity",
                        "minecraft:netherite_ingot", ingot(6)}, new int[]{8,32,32,128,32,1});
        alloy(recipes, "advanced_alloy/ingot/useless_ingot_tier_8", ingot(8), 204_800_000L, 1, 7,
                new String[]{"minecraft:turtle_helmet", "minecraft:echo_shard", "minecraft:conduit",
                        "minecraft:netherite_ingot", ingot(7)}, new int[]{8,16,16,64,1});
        alloy(recipes, "advanced_alloy/ingot/useless_ingot_tier_9", ingot(9), 819_200_000L, 1, 8,
                new String[]{"minecraft:dragon_head", "minecraft:goat_horn", "minecraft:rabbit_foot",
                        "ae2:not_so_mysterious_cube", "minecraft:netherite_block", "minecraft:turtle_helmet",
                        "minecraft:piglin_head", ingot(8)}, new int[]{8,8,8,16,16,8,8,1});
        alloy(recipes, "advanced_alloy/ingot/useful_ingot", "useless_mod:useful_ingot", 3_276_800_000L, 100, 9,
                new String[]{"useless_mod:possible_useful_ingot", "ae2:flawless_budding_quartz", "ae2:cell_component_256k",
                        "ae2:spatial_cell_component_128", "ae2:dense_energy_cell", "ae2:ender_dust", "ae2:singularity"},
                new int[]{32,32,64,64,64,512,1024});
        alloy(recipes, "crafting/multiblock_alloy_furnace_core", "useless_mod:multiblock_alloy_furnace_core",
                500_000_000L, 100, 9, new String[]{"useless_mod:advanced_alloy_furnace_block", "ae2:controller", ingot(9),
                        "ae2:formation_core", "ae2:annihilation_core", "ae2:crafting_accelerator"},
                new int[]{16,64,2,1024,1024,1024});

        long[] coilEnergy = {10_000,50_000,100_000,500_000,1_000_000,5_000_000,10_000_000,50_000_000,100_000_000};
        for (int tier = 1; tier <= 9; tier++) {
            alloy(recipes, "crafting/useless_coil_tier_" + tier, coil(tier), coilEnergy[tier - 1], 40, tier - 1,
                    new String[]{"useless_mod:useless_glass_tier_" + tier, "useless_mod:useless_gear_tier_" + tier,
                            "useless_mod:omniversal_furnace_casing", tier == 1 ? "ae2:quartz_fiber" : coil(tier - 1),
                            tier < 5 ? "ae2:fluix_glass_cable" : tier < 8 ? "ae2:fluix_covered_cable" : "ae2:fluix_covered_dense_cable"},
                    new int[]{2,3,2,tier == 1 ? 4 : 1,8});
        }
        String[] usefulInputs = new String[15];
        int[] usefulCounts = new int[15];
        String[] base = {"useless_mod:omniversal_furnace_casing", coil(9), "ae2:dense_energy_cell", "ae2:spatial_pylon",
                "ae2:spatial_anchor", "ae2:fluix_covered_dense_cable"};
        int[] counts = {2,1,16,8,8,8};
        System.arraycopy(base, 0, usefulInputs, 0, base.length);
        System.arraycopy(counts, 0, usefulCounts, 0, counts.length);
        for (int tier = 1; tier <= 9; tier++) {
            usefulInputs[5 + tier] = "useless_mod:useless_gear_tier_" + tier;
            usefulCounts[5 + tier] = tier == 9 ? 4 : (9 - tier) * 8;
        }
        alloy(recipes, "crafting/useful_coil", "useless_mod:useful_coil", 500_000_000L, 40, 9, usefulInputs, usefulCounts);

        JsonObject furnace = recipe("useless_mod:entropy_transmutation");
        furnace.add("inputs", counted(new String[]{ingot(1), "minecraft:furnace", "minecraft:iron_block",
                "minecraft:gold_block", "minecraft:lapis_block", "minecraft:diamond_block", "minecraft:emerald_block",
                "minecraft:netherite_block"}, new int[]{8,1,1,1,1,1,1,1}));
        furnace.add("output", stack("useless_mod:advanced_alloy_furnace_block", 1));
        recipes.put("advanced_alloy_furnace_block", furnace);

        JsonObject inscriber = recipe("ae2:inscriber");
        JsonObject tools = new JsonObject();
        tools.add("bottom", enchanted("minecraft:netherite_axe", "{Enchantments:[{id:\"minecraft:looting\",lvl:3s},{id:\"minecraft:silk_touch\",lvl:1s}]}"));
        tools.add("middle", ingredient(ingot(5)));
        tools.add("top", enchanted("minecraft:netherite_pickaxe", "{Enchantments:[{id:\"minecraft:fortune\",lvl:3s}]}"));
        inscriber.add("ingredients", tools);
        inscriber.addProperty("mode", "press");
        inscriber.add("result", stack("useless_mod:endless_beaf_item", 1));
        recipes.put("endless_beaf_item", inscriber);
        return recipes;
    }

    private static String ingot(int tier) { return "useless_mod:useless_ingot_tier_" + tier; }
    private static String coil(int tier) { return "useless_mod:useless_coil_tier_" + tier; }
    private static JsonObject recipe(String type) {
        JsonObject json = new JsonObject(); json.addProperty("type", type); return json;
    }
    private static JsonObject ingredient(String id) {
        JsonObject json = new JsonObject();
        json.addProperty(id.startsWith("#") ? "tag" : "item", id.startsWith("#") ? id.substring(1) : id);
        return json;
    }
    private static JsonObject stack(String id, int count) {
        JsonObject json = ingredient(id); json.addProperty("count", count); return json;
    }
    private static JsonArray counted(String[] items, int[] counts) {
        if (items.length != counts.length) throw new IllegalArgumentException("Input/count mismatch");
        JsonArray result = new JsonArray();
        for (int i = 0; i < items.length; i++) {
            JsonObject input = new JsonObject(); input.add("ingredient", ingredient(items[i]));
            if (counts[i] != 1) input.addProperty("count", counts[i]);
            result.add(input);
        }
        return result;
    }
    private static void alloy(Map<String, JsonObject> recipes, String path, String output, long energy, int time,
                              int tier, String[] inputs, int[] counts) {
        JsonObject json = recipe("useless_mod:advanced_alloy_furnace");
        json.addProperty("id", "useless_mod:" + path);
        json.addProperty("energy", energy); json.addProperty("process_time", time); json.addProperty("tier", tier);
        json.add("ingredients", counted(inputs, counts));
        JsonArray outputs = new JsonArray(); outputs.add(ingredient(output)); json.add("outputs", outputs);
        recipes.put(path, json);
    }
    private static void transform(Map<String, JsonObject> recipes, int tier, boolean explosion, String[] inputs, int[] counts) {
        JsonObject json = recipe("ae2:transform");
        JsonArray ingredients = new JsonArray();
        for (int i = 0; i < inputs.length; i++) for (int n = 0; n < counts[i]; n++) ingredients.add(ingredient(inputs[i]));
        json.add("ingredients", ingredients);
        if (explosion) {
            JsonObject circumstance = new JsonObject(); circumstance.addProperty("type", "explosion");
            json.add("circumstance", circumstance);
        }
        json.add("result", stack(ingot(tier), 1));
        recipes.put("advanced_alloy/ingot/useless_ingot_tier_" + tier, json);
    }
    private static JsonObject enchanted(String item, String nbt) {
        JsonObject json = recipe("useless_mod:enchanted_item");
        JsonObject stack = stack(item, 1); stack.addProperty("nbt", nbt); json.add("item", stack); return json;
    }
}
