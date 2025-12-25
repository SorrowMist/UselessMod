// mixin/AbstractContainerScreenMixin.java
package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.menu.slot.HighStackSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    @ModifyVariable(
            method = {"renderSlot"},
            index = 5,
            at = @At(
                    value = "STORE",
                    ordinal = 0
            )
        
    )
    private ItemStack modifyRenderedStack(ItemStack original, GuiGraphics guiGraphics, Slot slot) {
        if (slot instanceof HighStackSlot highStackSlot) {
            return highStackSlot.getDisplayStack();
        } else {
            return original;
        }
    }
}