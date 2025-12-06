package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.registry.ModIngots;
import com.sorrowmist.useless.registry.ModMolds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 模具和机器标志物检测器
 * 用于检测物品是否为模具或其他模组的机器标志物，以防止配方冲突
 */
public class MoldIdentifier {
    
    // 私有构造函数，防止实例化
    private MoldIdentifier() {}
    
    /**
     * 检查物品是否为无用锭（催化剂）
     * @param stack 要检查的物品堆叠
     * @return 如果是无用锭则返回true，否则返回false
     */
    public static boolean isUselessIngot(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 检查物品是否在无用锭列表中
        return stack.is(ModIngots.USELESS_INGOT_TIER_1.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_2.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_3.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_4.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_5.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_6.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_7.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_8.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_9.get());
    }
    
    /**
     * 检查物品是否为金属模具
     * @param stack 要检查的物品堆叠
     * @return 如果是金属模具则返回true，否则返回false
     */
    public static boolean isMetalMold(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 检查物品是否在金属模具列表中
        return stack.is(ModMolds.METAL_MOLD_PLATE.get()) ||
                stack.is(ModMolds.METAL_MOLD_ROD.get()) ||
                stack.is(ModMolds.METAL_MOLD_GEAR.get()) ||
                stack.is(ModMolds.METAL_MOLD_WIRE.get());
    }
    
    /**
     * 检查物品是否为机器标志物（来自其他模组的机器）
     * @param stack 要检查的物品堆叠
     * @return 如果是机器标志物则返回true，否则返回false
     */
    public static boolean isMachineMarker(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        // 这里可以添加其他模组机器的检测逻辑
        // 例如，检查物品的注册表名称是否包含特定模组的命名空间
        Item item = stack.getItem();
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(item);
        
        // 示例：检测Mekanism模组的机器
        if (registryName != null && registryName.getNamespace().equals("mekanism")) {
            return isMekanismMachine(registryName);
        }
        
        // 示例：检测Applied Energistics 2模组的机器
        if (registryName != null && registryName.getNamespace().equals("ae2")) {
            return isAE2Machine(registryName);
        }
        
        // 示例：检测Thermal（热力系列）模组的机器
        if (registryName != null && isThermalNamespace(registryName.getNamespace())) {
            return isThermalMachine(registryName);
        }
        
        // 可以根据需要添加更多模组的检测逻辑
        
        return false;
    }
    
    /**
     * 检查物品是否为可接受的标志物（模具或机器标志物）
     * @param stack 要检查的物品堆叠
     * @return 如果是可接受的标志物则返回true，否则返回false
     */
    public static boolean isAcceptableMarker(ItemStack stack) {
        return isMetalMold(stack) || isMachineMarker(stack);
    }
    
    /**
     * 检查物品是否为Mekanism模组的机器
     * @param registryName 物品的注册表名称
     * @return 如果是Mekanism机器则返回true，否则返回false
     */
    private static boolean isMekanismMachine(ResourceLocation registryName) {
        // 检查是否为Mekanism机器，这里检查注册表路径中的关键字
        return registryName.getPath().contains("machine") || registryName.getPath().contains("generator") || 
               registryName.getPath().contains("processor") || registryName.getPath().contains("factory") ||
               registryName.getPath().contains("metallurgic_infuser");
    }
    
    /**
     * 检查物品是否为Applied Energistics 2模组的机器
     * @param registryName 物品的注册表名称
     * @return 如果是AE2机器则返回true，否则返回false
     */
    private static boolean isAE2Machine(ResourceLocation registryName) {
        // 检查是否为AE2机器，这里检查注册表路径中的关键字
        return registryName.getPath().contains("interface") || registryName.getPath().contains("terminal") || 
               registryName.getPath().contains("storage") || registryName.getPath().contains("controller") ||
               registryName.getPath().contains("drive") || registryName.getPath().contains("crafting") ||
               registryName.getPath().contains("processor") || registryName.getPath().contains("import") ||
               registryName.getPath().contains("export") || registryName.getPath().contains("p2p") ||
               registryName.getPath().contains("inscriber");
    }
    
    /**
     * 检查物品是否为TravelAnchors模组的机器
     * @param registryName 物品的注册表名称
     * @return 如果是TravelAnchors机器则返回true，否则返回false
     */
    private static boolean isTravelAnchorsMachine(ResourceLocation registryName) {
        // 检查是否为TravelAnchors机器，这里检查注册表路径中的关键字
        return registryName.getPath().contains("anchor") || registryName.getPath().contains("teleport");
    }
    
    /**
     * 检查命名空间是否属于热力系列模组
     * @param namespace 物品的命名空间
     * @return 如果是热力系列命名空间则返回true，否则返回false
     */
    private static boolean isThermalNamespace(String namespace) {
        // 热力系列模组的命名空间列表
        return namespace.equals("thermal") ||
               namespace.equals("thermal_cultivation") ||
               namespace.equals("thermal_expansion") ||
               namespace.equals("thermal_foundation") ||
               namespace.equals("thermal_innovation") ||
               namespace.equals("thermal_locomotion");
    }
    
    /**
     * 检查物品是否为热力系列模组的机器
     * @param registryName 物品的注册表名称
     * @return 如果是热力系列机器则返回true，否则返回false
     */
    private static boolean isThermalMachine(ResourceLocation registryName) {
        // 检查是否为热力系列机器，这里检查注册表路径中的关键字
        String path = registryName.getPath();
        return path.contains("machine") ||
               path.contains("generator") ||
               path.contains("furnace") ||
               path.contains("dynamo") ||
               path.contains("engine") ||
               path.contains("factory") ||
               path.contains("processor") ||
               path.contains("centrifuge") ||
               path.contains("smelter") ||
               path.contains("crucible") ||
               path.contains("compressor") ||
               path.contains("sawmill") ||
               path.contains("pulverizer") ||
               path.contains("refinery");
    }
}