package com.sorrowmist.useless.mixin.malum;

import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels Malum 1.6.7 spirit drops when the killing blow came from an Endless Beef tool whose
 * Malum-spirit module is disabled.
 */
@Pseudo
@Mixin(targets = "com.sammy.malum.core.handlers.SpiritHarvestHandler", remap = false)
public abstract class SoulHarvestDropMixin {
    @Inject(
            method = "spawnSpiritsOnDeath(Lnet/minecraftforge/event/entity/living/LivingDeathEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void uselessMod$disableBeefSoulDrops(
            LivingDeathEvent event, CallbackInfo callback) {
        if (event != null && isDisabledBeefDamage(event.getSource())) {
            callback.cancel();
        }
    }

    private static boolean isDisabledBeefDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player)) {
            return false;
        }

        var stack = player.getMainHandItem();
        return stack.getItem() instanceof EndlessBeafItem
                && !com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(
                        stack, UComponents.BeefMalumSpiritEnabledComponent.get(), false);
    }
}
