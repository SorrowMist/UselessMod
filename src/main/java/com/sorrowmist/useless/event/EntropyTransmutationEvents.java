package com.sorrowmist.useless.event;

import appeng.api.implementations.blockentities.IMEChest;
import appeng.items.tools.powered.EntropyManipulatorItem;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.ae.AeEntropyTransmutationHandler;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.network.EntropyTransmutationEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Separate Forge subscriber: does not modify the shared EventHandler or client setup. */
@Mod.EventBusSubscriber(modid = UselessMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EntropyTransmutationEvents {
    private EntropyTransmutationEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        // Let protection/claim handlers run first. A denied action must not consume a disk.
        if (event.isCanceled() || event.getUseBlock() == Event.Result.DENY || event.getUseItem() == Event.Result.DENY
                || !(event.getItemStack().getItem() instanceof EntropyManipulatorItem)
                || !event.getEntity().isShiftKeyDown()
                || !(event.getLevel() instanceof ServerLevel level)
                || !(level.getBlockEntity(event.getPos()) instanceof IMEChest)) return;
        if (AeEntropyTransmutationHandler.tryTransmute(level, event.getPos(), event.getEntity())) {
            PacketDistributor.sendToPlayersTrackingChunk(level, new net.minecraft.world.level.ChunkPos(event.getPos()),
                    new EntropyTransmutationEffectPacket(level.dimension().location(), event.getPos()));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
