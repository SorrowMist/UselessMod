// 文件：CatalystManager.java
package com.sorrowmist.useless.registry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class CatalystManager {
    private static final Map<String, Integer> CATALYST_PARALLEL_MAP = new LinkedHashMap<>();

    static {
        // 初始化催化剂并行数映射
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_1", 3);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_2", 9);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_3", 27);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_4", 81);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_5", 243);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_6", 729);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_7", 2187);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_8", 6561);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_9", 19683);
    }

    public static int getCatalystParallel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 1; // 没有催化剂时并行数为1
        }

        String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        return CATALYST_PARALLEL_MAP.getOrDefault(itemId, 1);
    }

    public static Map<String, Integer> getAllCatalysts() {
        return new HashMap<>(CATALYST_PARALLEL_MAP);
    }

    // 获取催化剂名称（用于显示）
    public static String getCatalystName(ItemStack stack) {
        if (stack.isEmpty()) return "";

        String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        switch (itemId) {
            case "useless_mod:useless_ingot_tier_1": return "一阶无用锭";
            case "useless_mod:useless_ingot_tier_2": return "二阶无用锭";
            case "useless_mod:useless_ingot_tier_3": return "三阶无用锭";
            case "useless_mod:useless_ingot_tier_4": return "四阶无用锭";
            case "useless_mod:useless_ingot_tier_5": return "五阶无用锭";
            case "useless_mod:useless_ingot_tier_6": return "六阶无用锭";
            case "useless_mod:useless_ingot_tier_7": return "七阶无用锭";
            case "useless_mod:useless_ingot_tier_8": return "八阶无用锭";
            case "useless_mod:useless_ingot_tier_9": return "九阶无用锭";
            default: return "";
        }
    }


    // 在 CatalystManager.java 中修改 getFormattedCatalystList 方法
    public static List<Component> getFormattedCatalystList() {
        List<Component> list = new ArrayList<>();

        // 按照阶数顺序创建催化剂列表
        ItemStack[] orderedCatalysts = new ItemStack[]{
                new ItemStack(ModIngots.USELESS_INGOT_TIER_1.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_2.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_3.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_4.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_5.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_6.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_7.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_8.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_9.get())
        };

        // 按照顺序添加催化剂信息
        for (ItemStack catalyst : orderedCatalysts) {
            int parallel = getCatalystParallel(catalyst);
            String catalystName = getCatalystName(catalyst);
            list.add(Component.literal("• " + catalystName + ": " + parallel + "倍并行").withStyle(ChatFormatting.GREEN));
        }

        return list;
    }
}