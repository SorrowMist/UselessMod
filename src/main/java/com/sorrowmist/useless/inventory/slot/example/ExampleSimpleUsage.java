package com.sorrowmist.useless.inventory.slot.example;

import com.sorrowmist.useless.inventory.slot.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 简单使用示例
 * 展示了如何创建和使用大容量槽位
 */
public class ExampleSimpleUsage {

    /**
     * 示例1：创建简单的大容量槽位
     */
    public static void example1_CreateSlot() {
        // 创建一个容量为1024的槽位
        LargeInventorySlot slot = LargeInventorySlot.create(
                1024,           // 容量
                null,           // 监听器（可以为null）
                stack -> true,  // 接受所有物品
                0, 0            // GUI坐标
        );

        // 创建一个物品堆（100个钻石）
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 100);

        // 插入物品
        ItemStack remainder = slot.insertItem(diamonds, Action.EXECUTE, AutomationType.MANUAL);
        // remainder 应该是空的，因为100 < 1024

        // 检查槽位中的数量
        System.out.println("Slot count: " + slot.getCount()); // 输出: 100
    }

    /**
     * 示例2：输入槽和输出槽
     */
    public static void example2_InputOutputSlots() {
        // 创建输入槽（容量512，不允许外部自动化提取）
        LargeInventorySlot inputSlot = LargeInventorySlot.createInput(512, null, 10, 10);

        // 创建输出槽（容量2048，不允许外部自动化插入）
        LargeInventorySlot outputSlot = LargeInventorySlot.createOutput(2048, null, 80, 10);

        // 向输入槽插入物品
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 100);
        inputSlot.insertItem(iron, Action.EXECUTE, AutomationType.MANUAL);

        // 尝试从输入槽通过外部自动化提取（会失败）
        ItemStack extracted = inputSlot.extractItem(64, Action.EXECUTE, AutomationType.EXTERNAL);
        // extracted 是空的，因为输入槽不允许外部提取

        // 手动提取是可以的
        ItemStack manualExtract = inputSlot.extractItem(64, Action.EXECUTE, AutomationType.MANUAL);
        // manualExtract 有64个铁锭
    }

    /**
     * 示例3：使用 BasicInventorySlot 的静态工厂
     */
    public static void example3_BasicSlot() {
        // 创建一个基础槽位，容量为256
        BasicInventorySlot slot = BasicInventorySlot.at(
                stack -> stack.is(Items.GOLD_INGOT),  // 只接受金锭
                null,  // 监听器
                0, 0,  // 坐标
                256    // 自定义容量
        );

        // 现在可以存放超过64个金锭（通过构造函数已设置）
        ItemStack gold = new ItemStack(Items.GOLD_INGOT, 200);
        slot.insertItem(gold, Action.EXECUTE, AutomationType.MANUAL);
    }

    /**
     * 示例4：使用 InputInventorySlot
     */
    public static void example4_InputSlot() {
        // 创建输入槽
        InputInventorySlot inputSlot = InputInventorySlot.at(
                stack -> stack.is(Items.COAL),  // 只接受煤炭
                null,  // 监听器
                0, 0   // 坐标
        );

        // 设置容量（通过覆盖 limit 字段的方式）
        // 注意：BasicInventorySlot 的 limit 是 final 的，
        // 所以这里我们使用 LargeInventorySlot 来实现大容量
    }

    /**
     * 示例5：槽位内容监听
     */
    public static void example5_WithListener() {
        // 创建一个有监听器的槽位
        LargeInventorySlot slot = LargeInventorySlot.create(1024, () -> {
            System.out.println("Slot contents changed!");
        }, 0, 0);

        // 当槽位内容变化时，会打印消息
        slot.insertItem(new ItemStack(Items.STONE, 10), Action.EXECUTE, AutomationType.MANUAL);
        // 输出: Slot contents changed!
    }

    /**
     * 示例6：模拟操作 vs 执行操作
     */
    public static void example6_ActionTypes() {
        LargeInventorySlot slot = LargeInventorySlot.create(1024, null, 0, 0);

        ItemStack items = new ItemStack(Items.DIRT, 100);

        // 模拟插入 - 不会实际改变槽位
        ItemStack simulateRemainder = slot.insertItem(items, Action.SIMULATE, AutomationType.MANUAL);
        System.out.println("After simulate: " + slot.getCount()); // 输出: 0

        // 实际执行插入
        ItemStack executeRemainder = slot.insertItem(items, Action.EXECUTE, AutomationType.MANUAL);
        System.out.println("After execute: " + slot.getCount()); // 输出: 100
    }

    /**
     * 示例7：序列化和反序列化
     */
    public static void example7_Serialization() {
        LargeInventorySlot slot = LargeInventorySlot.create(1024, null, 0, 0);
        slot.insertItem(new ItemStack(Items.EMERALD, 500), Action.EXECUTE, AutomationType.MANUAL);

        // 序列化（保存到NBT）
        // CompoundTag nbt = slot.serializeNBT(provider);

        // 反序列化（从NBT加载）
        // LargeInventorySlot newSlot = LargeInventorySlot.create(1024, null, 0, 0);
        // newSlot.deserializeNBT(provider, nbt);
        // System.out.println(newSlot.getCount()); // 输出: 500
    }
}
