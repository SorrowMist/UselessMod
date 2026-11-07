package com.sorrowmist.useless.mixin.malum;

import com.sammy.malum.core.handlers.SoulDataHandler;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors the Endless Beef Malum-spirit mode onto Malum 1.6.7's soul exposure. When the module is
 * enabled the target is exposed explicitly because the beef tool is not in Malum's soul-hunter
 * item tag; when it is disabled vanilla exposure is cancelled.
 */
@Pseudo
@Mixin(targets = "com.sammy.malum.core.handlers.SoulDataHandler", remap = false)
public abstract class SoulHarvestHandlerMixin {
    @Inject(
            method = "exposeSoul(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void uselessMod$controlBeefSoulExposure(
            LivingHurtEvent event, CallbackInfo callback) {
        // The native handler rejects cancelled, zero/negative and NaN damage before exposure.
        if (event == null || event.isCanceled() || !(event.getAmount() > 0.0F)) {
            return;
        }
        if (isDisabledBeefDamage(event.getSource())) {
            callback.cancel();
        } else if (isEnabledBeefDamage(event.getSource())) {
            SoulDataHandler.exposeSoul(event.getEntity());
            callback.cancel();
        }
    }

    private static boolean isDisabledBeefDamage(DamageSource source) {
        ItemStack stack = beefTool(source);
        return !stack.isEmpty()
                && !com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(
                        stack, UComponents.BeefMalumSpiritEnabledComponent.get(), false);
    }

    private static boolean isEnabledBeefDamage(DamageSource source) {
        ItemStack stack = beefTool(source);
        return !stack.isEmpty()
                && com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(
                        stack, UComponents.BeefMalumSpiritEnabledComponent.get(), false);
    }

    private static ItemStack beefTool(DamageSource source) {
        if (source == null) {
            return ItemStack.EMPTY;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player)) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof EndlessBeafItem ? stack : ItemStack.EMPTY;
    }
}
