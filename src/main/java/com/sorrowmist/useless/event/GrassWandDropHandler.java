package com.sorrowmist.useless.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

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

    public static void onBlockDestroyed(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player breaker, net.minecraft.core.BlockPos pos, BlockState state) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel)
                || !(breaker instanceof ServerPlayer player)
                || COMPLETED_PLAYERS.contains(player.getUUID())) {
            return;
        }
        if (player.isCreative()
                || !isGrass(state)
                || !ConfigManager.shouldEnableGrassWandDrop()
                || level.getRandom().nextDouble() >= ConfigManager.getGrassWandDropProbability()) {
            return;
        }

        net.minecraft.advancements.Advancement advancement = getAdvancement(player);
        if (advancement == null || !player.getAdvancements().award(advancement, ADVANCEMENT_CRITERION)) {
            if (advancement != null && hasCompletedAdvancement(player)) {
                COMPLETED_PLAYERS.add(player.getUUID());
            }
            return;
        }

        COMPLETED_PLAYERS.add(player.getUUID());

        ItemStack wand = new ItemStack(ModItems.ENDLESS_BEAF_ITEM.get());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(wand, UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE);
        wand.getOrCreateTag().putInt("CustomModelData", 0);
        EndlessBeafItem.refreshEnchantments(wand, level);
        level.addFreshEntity(new ItemEntity(
                level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, wand));
    }

    private static boolean isGrass(BlockState state) {
        return state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS);
    }

    private static net.minecraft.advancements.Advancement getAdvancement(ServerPlayer player) {
        return player.getServer().getAdvancements().getAdvancement(ADVANCEMENT_ID);
    }

    private static boolean hasCompletedAdvancement(ServerPlayer player) {
        net.minecraft.advancements.Advancement advancement = getAdvancement(player);
        return advancement != null
                && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
}
