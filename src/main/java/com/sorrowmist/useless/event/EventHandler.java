package com.sorrowmist.useless.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.common.FlyEffectedHolder;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = UselessMod.MODID)
public class EventHandler {
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        // 检查伤害来源是否是玩家
        if (event.getSource().getEntity() instanceof Player player) {
            ItemStack mainHandItem = player.getMainHandItem();

            // 检查主手物品是否是EndlessBeafItem
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                UselessItemUtils.onLivingDrops(event, mainHandItem, player);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.getItem() instanceof EndlessBeafItem) {
            MiningDispatcher.dispatchBreak(event, mainHandItem, player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;

        if (!player.isCreative()) {
            boolean hasItemInInventory = player.getInventory().items.stream().anyMatch(
                    item -> item.getItem() instanceof EndlessBeafItem);

            if (hasItemInInventory && ConfigManager.shouldEnableFlightEffect()) {
                FlyEffectedHolder.add(player.getUUID());
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
            } else {
                if (player.getAbilities().mayfly && FlyEffectedHolder.contains(player.getUUID())) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                FlyEffectedHolder.remove(player.getUUID());
            }
        }
        
        MiningDispatcher.tickCacheUpdate(player);
    }
}