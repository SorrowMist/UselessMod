package com.sorrowmist.useless.mixin.itemobliterator;

import com.sorrowmist.useless.compat.itemobliterator.ItemObliteratorProtection;
import java.util.List;

import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
        targets = "elocindev.item_obliterator.neoforge.ItemObliterator",
        remap = false,
        priority = 11000)
public abstract class ItemObliteratorContainerMixin {
    @Redirect(
            method = "onPlayerContainer(Lnet/neoforged/neoforge/event/entity/player/PlayerContainerEvent;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;contains(Ljava/lang/Object;)Z",
                    remap = false),
            remap = false)
    private boolean uselessMod$keepProtectedItems(
            List<?> blacklist, Object itemId) {
        if (itemId instanceof String id
                && ItemObliteratorProtection.isProtectedItemId(id)) {
            return false;
        }
        return blacklist.contains(itemId);
    }
}
