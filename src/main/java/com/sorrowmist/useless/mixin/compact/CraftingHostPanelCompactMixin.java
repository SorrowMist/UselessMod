package com.sorrowmist.useless.mixin.compact;

import cn.dancingsnow.neoecoae.api.me.network.CraftingCapabilitySnapshot;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerPanelState;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactF9BlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.ui.CompactHostPanelState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

@Mixin(targets = "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity", remap = false)
public abstract class CraftingHostPanelCompactMixin {
    @ModifyArgs(method = "createCraftingPanelConfig", at = @At(value = "INVOKE",
            target = "Lcn/dancingsnow/neoecoae/gui/crafting/CraftingHostPanelUI$Config;<init>"), require = 0)
    private void uselessMod$displayCoolantLimitedCapacity(Args args) {
        if (!((Object) this instanceof CompactF9BlockEntity host)) {
            return;
        }
        BooleanSupplier virtualMode = args.get(5);
        IntSupplier singleCoreCapacity = args.get(11);
        LongSupplier batchCapacity = args.get(36);
        BooleanSupplier coolantBlocked = () -> virtualMode.getAsBoolean() && !host.hasBufferedVirtualCraftingCoolant();
        args.set(5, (BooleanSupplier) () -> virtualMode.getAsBoolean() && host.hasBufferedVirtualCraftingCoolant());
        args.set(11, (IntSupplier) () -> {
            if (!coolantBlocked.getAsBoolean()) {
                return singleCoreCapacity.getAsInt();
            }
            CraftingCapabilitySnapshot snapshot = host.getCapabilitySnapshot();
            return (int) Math.min(Integer.MAX_VALUE, snapshot.parallelLimit() / Math.max(1, snapshot.physicalFxCount()));
        });
        args.set(36, (LongSupplier) () -> coolantBlocked.getAsBoolean()
                ? host.getCapabilitySnapshot().parallelLimit() : batchCapacity.getAsLong());
    }

    @Inject(method = "getMaintenanceStatusTooltip", at = @At("HEAD"), require = 0, cancellable = true)
    private void uselessMod$displayMissingVirtualCoolant(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof CompactF9BlockEntity host && host.getCapabilitySnapshot().virtualMode()
                && !host.hasBufferedVirtualCraftingCoolant()) {
            cir.setReturnValue(Component.translatable("gui.neoecoae.crafting.virtual_blocked.coolant")
                    .withColor(0xFFFF5555));
        }
    }

    @ModifyArg(method = "getCraftingControllerPanelState", at = @At(value = "INVOKE",
            target = "Lcn/dancingsnow/neoecoae/gui/crafting/CraftingControllerPanelState;of"), index = 7, require = 0)
    private int uselessMod$displayCoolantLimitedQueueFill(int original) {
        if ((Object) this instanceof CompactF9BlockEntity host) {
            CraftingCapabilitySnapshot snapshot = host.getCapabilitySnapshot();
            if (snapshot.virtualMode() && !host.hasBufferedVirtualCraftingCoolant()) {
                return CraftingControllerPanelState.permille(host.getDisplayedQueuedCraftCount(), snapshot.parallelLimit());
            }
        }
        return original;
    }

    @Inject(method = "getCraftingControllerPanelState", at = @At("RETURN"), require = 0, remap = false,
            cancellable = true)
    private void uselessMod$compactHardware(CallbackInfoReturnable<Object> cir) {
        if ((Object) this instanceof CompactF9BlockEntity) {
            cir.setReturnValue(CompactHostPanelState.levelNine(cir.getReturnValue(), false));
        }
    }
}
