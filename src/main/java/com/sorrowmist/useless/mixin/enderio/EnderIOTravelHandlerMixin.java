package com.sorrowmist.useless.mixin.enderio;

import com.enderio.base.common.handler.TravelHandler;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native Ender IO tests the exact travel-staff item; also recognize an enabled UselessMod staff. */
@Mixin(value=TravelHandler.class,remap=false)
public abstract class EnderIOTravelHandlerMixin {
    @Inject(method="canItemTeleport(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Z",
            at=@At("HEAD"),cancellable=true)
    private static void useless$travelItem(Player player,InteractionHand hand,CallbackInfoReturnable<Boolean> cir) {
        var stack=player.getItemInHand(hand);
        if (stack.getItem() instanceof EndlessBeafItem && EndlessBeafItem.isTeleportEnabled(stack)) {
            cir.setReturnValue(true);
        }
    }
}
