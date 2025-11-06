package com.sorrowmist.useless.utils;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;

public class ThermalConfigUtils {

    // Thermal Parallel 配置默认值
    public static boolean PARALLEL_INCREASE_ITEM_CAPACITY = true;
    public static boolean PARALLEL_INCREASE_FLUID_CAPACITY = true;
    public static boolean PARALLEL_INCREASE_ENERGY_CAPACITY = false;
    public static boolean PARALLEL_INCREASE_ITEM_TRANSFER = true;
    public static boolean PARALLEL_INCREASE_FLUID_TRANSFER = true;
    public static boolean PARALLEL_INCREASE_ENERGY_TRANSFER = true;
    public static boolean PARALLEL_INCREASE_ENERGY_CONSUMPTION = false;
    public static boolean BASE_MOD_AFFECT_PARALLEL = false;
    public static boolean ADD_EXTRA_PARALLEL_AUGMENTS_TO_TAB = false;

    static {
        loadConfig();
    }

    public static void loadConfig() {
        File file = FMLPaths.CONFIGDIR.get().resolve("useless_mod-common.toml").toFile();
        CommentedFileConfig config = CommentedFileConfig.builder(file).autosave().sync().build();
        config.load();

        // 读取 Thermal Parallel 配置
        Config thermalSection = config.get("Thermal Parallel 设置");
        if (thermalSection != null) {
            PARALLEL_INCREASE_ITEM_CAPACITY = thermalSection.getOrElse("并行增加物品容量", true);
            PARALLEL_INCREASE_FLUID_CAPACITY = thermalSection.getOrElse("并行增加流体容量", true);
            PARALLEL_INCREASE_ENERGY_CAPACITY = thermalSection.getOrElse("并行增加能量容量", false);
            PARALLEL_INCREASE_ITEM_TRANSFER = thermalSection.getOrElse("并行增加物品传输", true);
            PARALLEL_INCREASE_FLUID_TRANSFER = thermalSection.getOrElse("并行增加流体传输", true);
            PARALLEL_INCREASE_ENERGY_TRANSFER = thermalSection.getOrElse("并行增加能量传输", true);
            PARALLEL_INCREASE_ENERGY_CONSUMPTION = thermalSection.getOrElse("并行增加能耗", false);
            BASE_MOD_AFFECT_PARALLEL = thermalSection.getOrElse("基础倍率影响并行", false);
            ADD_EXTRA_PARALLEL_AUGMENTS_TO_TAB = thermalSection.getOrElse("添加额外并行增强到标签", false);
        } else {
            // 如果没有找到配置节，写入默认值
            config.set("Thermal Parallel 设置.并行增加物品容量", PARALLEL_INCREASE_ITEM_CAPACITY);
            config.set("Thermal Parallel 设置.并行增加流体容量", PARALLEL_INCREASE_FLUID_CAPACITY);
            config.set("Thermal Parallel 设置.并行增加能量容量", PARALLEL_INCREASE_ENERGY_CAPACITY);
            config.set("Thermal Parallel 设置.并行增加物品传输", PARALLEL_INCREASE_ITEM_TRANSFER);
            config.set("Thermal Parallel 设置.并行增加流体传输", PARALLEL_INCREASE_FLUID_TRANSFER);
            config.set("Thermal Parallel 设置.并行增加能量传输", PARALLEL_INCREASE_ENERGY_TRANSFER);
            config.set("Thermal Parallel 设置.并行增加能耗", PARALLEL_INCREASE_ENERGY_CONSUMPTION);
            config.set("Thermal Parallel 设置.基础倍率影响并行", BASE_MOD_AFFECT_PARALLEL);
            config.set("Thermal Parallel 设置.添加额外并行增强到标签", ADD_EXTRA_PARALLEL_AUGMENTS_TO_TAB);
            config.save();
        }

        config.close();
    }
}