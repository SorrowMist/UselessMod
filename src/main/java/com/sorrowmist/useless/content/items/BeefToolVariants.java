package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.world.item.ItemStack;

public final class BeefToolVariants {
    private BeefToolVariants() {}

    public static boolean isBaseVariant(ItemStack stack) {
        return stack.getItem() == ModItems.ENDLESS_BEAF_ITEM.get()
                || stack.getItem() == ModItems.ENDLESS_BEAF_ITEM_NO_WRENCH.get();
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
