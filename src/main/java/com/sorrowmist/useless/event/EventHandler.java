package com.sorrowmist.useless.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.common.FlyEffectedHolder;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = UselessMod.MODID)
public class EventHandler {
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                UselessItemUtils.onLivingDrops(event, mainHandItem, player);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.getItem() instanceof EndlessBeafItem) {
            MiningDispatcher.dispatchBreak(event, mainHandItem, player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

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

    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof EndlessBeafItem)) return;

        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;

        Level world = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) return;

        String className = be.getClass().getName();
        if (!className.contains("WirelessAccessPoint")) return;

        if (!world.isClientSide) {
            GlobalPos globalPos = GlobalPos.of(world.dimension(), pos);
            stack.set(UComponents.WIRELESS_LINK_TARGET.get(), globalPos);
            player.displayClientMessage(Component.translatable("gui.useless_mod.wireless_access_point_bound", pos.toShortString()), true);
        }
        // 取消事件，阻止方块本身的逻辑（如 AE2 的拆卸或旋转）
        event.setCanceled(true);
        // 设置结果，告知系统处理已成功，停止后续传播
        event.setCancellationResult(InteractionResult.sidedSuccess(world.isClientSide));
    }
}