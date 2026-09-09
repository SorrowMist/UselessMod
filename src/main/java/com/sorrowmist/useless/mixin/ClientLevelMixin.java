package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.StreamSupport;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "entitiesForRendering", at = @At("RETURN"), cancellable = true)
    private void useless_mod$filterProtectedPlayersFromRenderingEntities(
            CallbackInfoReturnable<Iterable<Entity>> cir
    ) {
        if (!EventHandler.hasAnyBeefAdvancedStealthPlayers()) {
            return;
        }

        Iterable<Entity> entities = cir.getReturnValue();
        cir.setReturnValue(() -> StreamSupport.stream(entities.spliterator(), false)
                .filter(entity -> !(entity instanceof net.minecraft.world.entity.player.Player player
                        && EventHandler.hasBeefAdvancedStealthItem(player)))
                .iterator());
    }
}
