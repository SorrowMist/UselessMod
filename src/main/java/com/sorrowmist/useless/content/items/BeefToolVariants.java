package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BeefToolVariants {
    private BeefToolVariants() {}

    public static boolean isBaseVariant(ItemStack stack) {
        return stack.getItem() == ModItems.ENDLESS_BEAF_ITEM.get()
                || stack.getItem() == ModItems.ENDLESS_BEAF_ITEM_NO_WRENCH.get();
    }

    /**
     * 判断物品是否为造化杖（牛排工具）的任意变体。
     * <p>
     * 凡属于 {@link EndlessBeafItem} 的物品都算，包含基础形态、关闭扳手标记的
     * {@code endless_beaf_item_no_wrench} 以及各工具模式子物品。
     */
    public static boolean isBeafTool(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof EndlessBeafItem;
    }

    /**
     * 枚举造化杖的全部变体物品。
     * <p>
     * 以 {@link EndlessBeafItem} 类型为准直接从注册表收集，保证与
     * {@link #isBeafTool(ItemStack)} 的判定范围始终一致：新增变体（例如关闭扳手标记的
     * {@code endless_beaf_item_no_wrench}）后，需要枚举具体物品的场景（模具 Ingredient、
     * JEI 展示等）不会漏项。
     */
    public static List<Item> allVariantItems() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof EndlessBeafItem) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    public static boolean isWrenchTagEnabled(ItemStack stack) {
        return stack.getOrDefault(UComponents.WrenchTagEnabledComponent.get(), true);
    }

    public static ItemStack withWrenchTag(ItemStack source, boolean enabled) {
        ItemStack result = new ItemStack(
                enabled ? ModItems.ENDLESS_BEAF_ITEM.get() : ModItems.ENDLESS_BEAF_ITEM_NO_WRENCH.get());
        result.applyComponents(source.getComponents());
        result.set(UComponents.WrenchTagEnabledComponent.get(), enabled);
        result.set(UComponents.CurrentToolTypeComponent.get(), ToolTypeMode.NONE_MODE);
        return result;
    }

    public static ItemStack createForToolMode(ItemStack source, ToolTypeMode mode) {
        ItemStack result = switch (mode) {
            case NONE_MODE -> withWrenchTag(source, isWrenchTagEnabled(source));
            case WRENCH_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_WRENCH.get());
            case SCREWDRIVER_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_SCREWDRIVER.get());
            case MALLET_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_MALLET.get());
            case CROWBAR_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_CROWBAR.get());
            case HAMMER_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_HAMMER.get());
            case OMNITOOL_MODE -> ItemStack.EMPTY;
        };
        if (!result.isEmpty()) {
            result.applyComponents(source.getComponents());
            result.set(UComponents.CurrentToolTypeComponent.get(), mode);
        }
        return result;
    }
}
