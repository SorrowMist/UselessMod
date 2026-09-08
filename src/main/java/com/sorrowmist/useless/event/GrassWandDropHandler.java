package com.sorrowmist.useless.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = UselessMod.MODID)
public final class GrassWandDropHandler {
    private static final ResourceLocation ADVANCEMENT_ID = UselessMod.id("grass_wand_drop");
    private static final String ADVANCEMENT_CRITERION = "main";
    private static final Set<UUID> COMPLETED_PLAYERS = ConcurrentHashMap.newKeySet();

    private GrassWandDropHandler() {}

    public static void onPlayerLoggedIn(ServerPlayer player) {
        if (hasCompletedAdvancement(player)) {
            COMPLETED_PLAYERS.add(player.getUUID());
        } else {
            COMPLETED_PLAYERS.remove(player.getUUID());
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        COMPLETED_PLAYERS.remove(player.getUUID());
    }

    public static void clearCache() {
        COMPLETED_PLAYERS.clear();
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.isCanceled()
                || !(event.getBreaker() instanceof ServerPlayer player)
                || COMPLETED_PLAYERS.contains(player.getUUID())) {
            return;
        }
        if (player.isCreative()
                || !isGrass(event.getState())
                || !ConfigManager.shouldEnableGrassWandDrop()
                || event.getLevel().getRandom().nextDouble() >= ConfigManager.getGrassWandDropProbability()) {
            return;
        }

        AdvancementHolder advancement = getAdvancement(player);
        if (advancement == null || !player.getAdvancements().award(advancement, ADVANCEMENT_CRITERION)) {
            if (advancement != null && hasCompletedAdvancement(player)) {
                COMPLETED_PLAYERS.add(player.getUUID());
            }
            return;
        }

        COMPLETED_PLAYERS.add(player.getUUID());

        ItemStack wand = new ItemStack(ModItems.ENDLESS_BEAF_ITEM.get());
        wand.set(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE);
        wand.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0));
        EndlessBeafItem.refreshEnchantments(wand, event.getLevel());
        BlockPos pos = event.getPos();
        event.getDrops().add(new ItemEntity(
                event.getLevel(), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, wand));
    }

    private static boolean isGrass(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS);
    }

    private static AdvancementHolder getAdvancement(ServerPlayer player) {
        return player.getServer().getAdvancements().get(ADVANCEMENT_ID);
    }

    private static boolean hasCompletedAdvancement(ServerPlayer player) {
        AdvancementHolder advancement = getAdvancement(player);
        return advancement != null
                && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
}
