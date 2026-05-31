package com.sorrowmist.useless.inventory.slot;

import appeng.api.crafting.PatternDetailsHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class PatternSlotItemHandler extends SlotItemHandler {

    private appeng.client.gui.Icon icon;
    private boolean enabled = true;
    private float opacity = 1.0f;
    private boolean renderIconWithItem = false;
    private boolean active = true;

    public PatternSlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
    }

    public void setIcon(appeng.client.gui.Icon icon) {
        this.icon = icon;
    }

    public appeng.client.gui.Icon getIcon() {
        return this.icon;
    }

    public boolean isSlotEnabled() {
        return this.enabled;
    }

    public void setSlotEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getOpacityOfIcon() {
        return this.opacity;
    }

    public void setOpacityOfIcon(float opacity) {
        this.opacity = opacity;
    }

    public boolean renderIconWithItem() {
        return this.renderIconWithItem;
    }

    public void setRenderIconWithItem(boolean renderIconWithItem) {
        this.renderIconWithItem = renderIconWithItem;
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return PatternDetailsHelper.isEncodedPattern(stack);
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        return super.mayPickup(player);
    }
}