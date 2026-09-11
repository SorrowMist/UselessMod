package com.sorrowmist.useless.mixin.malum;

import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import com.sammy.malum.registry.common.MalumAttachmentTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.sammy.malum.core.handlers.SoulDataHandler", remap = false)
public abstract class SoulHarvestHandlerMixin {
    @Inject(method = "exposeSoul", at = @At("HEAD"), cancellable = true, remap = false)
    private static void uselessMod$disableBeefSoulExposure(
            LivingDamageEvent.Post event, CallbackInfo callback) {
        if (isDisabledBeefDamage(event.getSource())) {
            callback.cancel();
        } else if (isEnabledBeefDamage(event.getSource())) {
            event.getEntity().getData(MalumAttachmentTypes.LIVING_SOUL_INFO).setExposed();
        }
    }

    private static boolean isDisabledBeefDamage(DamageSource source) {
        ItemStack stack = beefTool(source);
        return !stack.isEmpty()
                && !stack.getOrDefault(UComponents.BeefMalumSpiritEnabledComponent.get(), false);
    }

    private static boolean isEnabledBeefDamage(DamageSource source) {
        ItemStack stack = beefTool(source);
        return !stack.isEmpty()
                && stack.getOrDefault(UComponents.BeefMalumSpiritEnabledComponent.get(), false);
    }

    private static ItemStack beefTool(DamageSource source) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player)) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof EndlessBeafItem ? stack : ItemStack.EMPTY;
    }
}
