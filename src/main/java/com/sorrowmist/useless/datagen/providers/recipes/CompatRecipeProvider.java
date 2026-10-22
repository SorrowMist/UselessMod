package com.sorrowmist.useless.datagen.providers.recipes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.UselessMod;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 可选兼容模组配方的数据生成器。
 *
 * <p>沉浸工程、神秘学与 neoecoae 在数据生成运行时不进入类路径，无法通过配方构建器引用其物品，
 * 因此这些配方改为直接生成 JSON。配方 id 与手写版本完全一致，并保留 {@code neoforge:mod_loaded}
 * 条件，使目标模组缺席时整条配方不参与加载。</p>
 *
 * <p>本类仍然由 {@code runData} 统一驱动，生成产物与其他配方一样可被陈旧文件清理机制追踪。</p>
 */
public class CompatRecipeProvider implements DataProvider {

    private static final String IMMERSIVE_ENGINEERING = "immersiveengineering";
    private static final String OCCULTISM = "occultism";
    private static final String NEOECOAE = "neoecoae";
    private static final String EXTENDEDAE = "extendedae";

    private final PackOutput.PathProvider pathProvider;

    public CompatRecipeProvider(PackOutput output) {
        this.pathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe");
    }

    @Override
    public @NotNull CompletableFuture<?> run(@NotNull CachedOutput cache) {
        List<CompletableFuture<?>> futures = new ArrayList<>();

        this.addImmersiveEngineeringMolds(cache, futures);
        this.addNeoEcoCompacts(cache, futures);
        this.addRitualBlueprint(cache, futures);
        this.addBeefUnbreakable(cache, futures);
        this.addExtendedAEMachineRecipes(cache, futures);

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * 沉浸工程机器的工作台配方：以无用锭与无用玻璃包围机器专用建材，产出对应机器本体。
     *
     * <p>中心材料与输出机器一一对应，机械臂装配线等其余机器不在本次范围内。</p>
     */
    private void addImmersiveEngineeringMolds(CachedOutput cache, List<CompletableFuture<?>> futures) {
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/alloy_smelter"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:alloy_smelter",
                        "immersiveengineering:alloybrick"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/arc_furnace"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:arc_furnace",
                        "immersiveengineering:heavy_engineering"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/blast_furnace"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:blast_furnace",
                        "immersiveengineering:blastbrick"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/bottling_machine"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:bottling_machine",
                        "immersiveengineering:conveyor_basic"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/coke_oven"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:coke_oven",
                        "immersiveengineering:cokebrick"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/crusher"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:crusher",
                        "immersiveengineering:steel_scaffolding_standard"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/fermenter"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:fermenter",
                        "immersiveengineering:light_engineering"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/metal_press"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:metal_press",
                        "immersiveengineering:component_steel"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/mixer"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:mixer",
                        "immersiveengineering:component_iron"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/refinery"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:refinery",
                        "immersiveengineering:sheetmetal_steel"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/sawmill"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:sawmill",
                        "immersiveengineering:sawblade"));
        this.save(cache, futures, UselessMod.id("immersiveengineering/mold/squeezer"),
                this.shapedImmersiveEngineeringMachine("immersiveengineering:squeezer",
                        "immersiveengineering:hemp_fiber"));
    }

    /**
     * neoecoae 紧凑方块 C9 / F9 / L9 的合金炉配方。
     *
     * <p>三种方块分别等价于整套计算、合成与存储多方块，输入为其对应系统的建材与核心部件。</p>
     */
    private void addNeoEcoCompacts(CachedOutput cache, List<CompletableFuture<?>> futures) {
        JsonObject c9 = this.advancedAlloyFurnace("useless_mod:advanced_alloy/compact_c9",
                "useless_mod:useless_compact_c9", 4000000, 200);
        JsonArray c9Inputs = c9.getAsJsonArray("ingredients");
        addCounted(c9Inputs, "neoecoae:computation_casing", 168);
        addCounted(c9Inputs, "neoecoae:computation_system_l9", 8);
        addCounted(c9Inputs, "neoecoae:computation_interface", 8);
        addCounted(c9Inputs, "neoecoae:computation_transmitter", 88);
        addCounted(c9Inputs, "neoecoae:computation_drive", 176);
        addCounted(c9Inputs, "neoecoae:computation_parallel_core_l9", 176);
        addCounted(c9Inputs, "neoecoae:computation_threading_core_l9", 88);
        addCounted(c9Inputs, "neoecoae:computation_cooling_controller_l9", 8);
        addCounted(c9Inputs, "neoecoae:eco_computation_cell_l9", 176);
        this.save(cache, futures, UselessMod.id("advanced_alloy/compact_c9"), c9);

        JsonObject f9 = this.advancedAlloyFurnace("useless_mod:advanced_alloy/compact_f9",
                "useless_mod:useless_compact_f9", 4000000, 200);
        JsonArray f9Inputs = f9.getAsJsonArray("ingredients");
        addCounted(f9Inputs, "neoecoae:crafting_casing", 160);
        addCounted(f9Inputs, "neoecoae:crafting_system_l9", 8);
        addCounted(f9Inputs, "neoecoae:output_hatch", 8);
        addCounted(f9Inputs, "neoecoae:crafting_interface", 8);
        addCounted(f9Inputs, "neoecoae:input_hatch", 8);
        addCounted(f9Inputs, "neoecoae:crafting_worker", 88);
        addCounted(f9Inputs, "neoecoae:crafting_parallel_core_l9", 176);
        addCounted(f9Inputs, "neoecoae:crafting_pattern_bus", 176);
        addCounted(f9Inputs, "neoecoae:crafting_vent", 88);
        this.save(cache, futures, UselessMod.id("advanced_alloy/compact_f9"), f9);

        JsonObject l9 = this.advancedAlloyFurnace("useless_mod:advanced_alloy/compact_l9",
                "useless_mod:useless_compact_l9", 4000000, 200);
        JsonArray l9Inputs = l9.getAsJsonArray("ingredients");
        addCounted(l9Inputs, "neoecoae:storage_casing", 22);
        addCounted(l9Inputs, "neoecoae:storage_system_l9", 1);
        addCounted(l9Inputs, "neoecoae:storage_interface", 1);
        addCounted(l9Inputs, "neoecoae:eco_drive", 33);
        addCounted(l9Inputs, "neoecoae:energy_cell_l9", 22);
        addCounted(l9Inputs, "neoecoae:storage_vent", 11);
        addCounted(l9Inputs, "neoecoae:eco_infinite_cell_component", 64);
        addCounted(l9Inputs, "neoecoae:eco_item_storage_cell_256m", 12);
        this.save(cache, futures, UselessMod.id("advanced_alloy/compact_l9"), l9);
    }

    /**
     * ExtendedAE 安装时的机器配方替代版本。
     *
     * <p>ExtendedAE 提供的扩展机器替换基础版本中的原版部件：样板供应器与分子装配室换用扩展机器，
     * 接口换用超大接口，IO 端口与输入输出总线换用扩展版本。基础版本在 {@code CraftingRecipes} 中
     * 以 {@code not(mod_loaded("extendedae"))} 条件承载，两者互斥，同一时刻只有一条参与加载。</p>
     */
    private void addExtendedAEMachineRecipes(CachedOutput cache, List<CompletableFuture<?>> futures) {
        this.save(cache, futures, UselessMod.id("crafting/extendedae/me_pattern_assembly"),
                this.shapedExtendedAE("useless_mod:me_pattern_assembly",
                        new String[]{"AEA", "BCB", "AFA"},
                        "A", "useless_mod:omniversal_furnace_casing",
                        "B", "extendedae:ex_pattern_provider",
                        "C", "extendedae:oversize_interface",
                        "E", "extendedae:ex_io_port",
                        "F", "ae2:capacity_card"));

        this.save(cache, futures, UselessMod.id("crafting/extendedae/omniversal_mold_hub"),
                this.shapedExtendedAE("useless_mod:omniversal_mold_hub",
                        new String[]{"ABA", "DCD", "AFA"},
                        "A", "useless_mod:omniversal_furnace_casing",
                        "B", "ae2:chest",
                        "C", "extendedae:ex_molecular_assembler",
                        "D", "extendedae:oversize_interface",
                        "F", "ae2:capacity_card"));

        this.save(cache, futures, UselessMod.id("crafting/extendedae/passive_crafting_hatch"),
                this.shapedExtendedAE("useless_mod:passive_crafting_hatch",
                        new String[]{"ACA", "DPE", "ARA"},
                        "A", "extendedae:ex_molecular_assembler",
                        "C", "minecraft:clock",
                        "D", "extendedae:ex_import_bus_part",
                        "E", "extendedae:ex_export_bus_part",
                        "P", "useless_mod:me_pattern_assembly",
                        "R", "ae2:singularity"));
    }

    /** 生成带 ExtendedAE 加载条件的 3×3 有序合成配方；键值对按「字符, 物品 id」成对给出。 */
    private JsonObject shapedExtendedAE(String outputId, String[] pattern, String... keyPairs) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.addProperty("category", "building");
        json.add("neoforge:conditions", modLoaded(EXTENDEDAE));

        JsonArray patternArray = new JsonArray();
        for (String row : pattern) {
            patternArray.add(row);
        }
        json.add("pattern", patternArray);

        JsonObject key = new JsonObject();
        for (int i = 0; i < keyPairs.length; i += 2) {
            key.add(keyPairs[i], item(keyPairs[i + 1]));
        }
        json.add("key", key);

        json.add("result", result(outputId, 1));
        return json;
    }

    /** 神秘学仪式蓝图：纸与异界精华的无序合成。 */
    private void addRitualBlueprint(CachedOutput cache, List<CompletableFuture<?>> futures) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        json.add("neoforge:conditions", modLoaded(OCCULTISM));

        JsonArray ingredients = new JsonArray();
        ingredients.add(item("minecraft:paper"));
        ingredients.add(item("occultism:otherworld_essence"));
        json.add("ingredients", ingredients);

        json.add("result", result("useless_mod:ritual_blueprint", 1));
        this.save(cache, futures, UselessMod.id("ritual_blueprint"), json);
    }

    /** 无尽牛肉的不破坏配方，无输入材料。 */
    private void addBeefUnbreakable(CachedOutput cache, List<CompletableFuture<?>> futures) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "useless_mod:beef_unbreakable");
        json.addProperty("category", "misc");
        this.save(cache, futures, UselessMod.id("beef_unbreakable"), json);
    }

    private JsonObject shapedImmersiveEngineeringMachine(String outputId, String centerItemId) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.addProperty("category", "building_blocks");
        json.add("neoforge:conditions", modLoaded(IMMERSIVE_ENGINEERING));

        JsonArray pattern = new JsonArray();
        pattern.add("IGI");
        pattern.add("GCG");
        pattern.add("IGI");
        json.add("pattern", pattern);

        JsonObject key = new JsonObject();
        key.add("I", item("useless_mod:useless_ingot_tier_1"));
        key.add("G", item("useless_mod:useless_glass_tier_1"));
        key.add("C", item(centerItemId));
        json.add("key", key);

        json.add("result", result(outputId, 1));
        return json;
    }

    private JsonObject advancedAlloyFurnace(String id, String outputId, int energy, int processTime) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "useless_mod:advanced_alloy_furnace");
        json.add("neoforge:conditions", modLoaded(NEOECOAE));
        json.addProperty("energy", energy);
        json.addProperty("id", id);
        json.add("ingredients", new JsonArray());

        JsonArray outputs = new JsonArray();
        JsonObject output = new JsonObject();
        output.addProperty("id", outputId);
        outputs.add(output);
        json.add("outputs", outputs);

        json.addProperty("process_time", processTime);
        return json;
    }

    private static void addCounted(JsonArray ingredients, String itemId, int count) {
        JsonObject entry = new JsonObject();
        entry.addProperty("count", count);
        entry.add("ingredient", item(itemId));
        ingredients.add(entry);
    }

    private static JsonArray modLoaded(String modId) {
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "neoforge:mod_loaded");
        condition.addProperty("modid", modId);

        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        return conditions;
    }

    private static JsonObject item(String itemId) {
        JsonObject json = new JsonObject();
        json.addProperty("item", itemId);
        return json;
    }

    private static JsonObject result(String itemId, int count) {
        JsonObject json = new JsonObject();
        json.addProperty("id", itemId);
        json.addProperty("count", count);
        return json;
    }

    private void save(CachedOutput cache, List<CompletableFuture<?>> futures, ResourceLocation id, JsonObject json) {
        Path path = this.pathProvider.json(id);
        futures.add(DataProvider.saveStable(cache, json, path));
    }

    @Override
    public @NotNull String getName() {
        return "Compat Recipes: " + UselessMod.MODID;
    }
}
