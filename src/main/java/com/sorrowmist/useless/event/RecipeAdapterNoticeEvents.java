package com.sorrowmist.useless.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdapterGuards;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registration and catalog failures are collected before the player joins the server. */
@Mod.EventBusSubscriber(modid = UselessMod.MODID)
public final class RecipeAdapterNoticeEvents {
    private RecipeAdapterNoticeEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var names = AdapterGuards.disabledModNames();
        if (names.isEmpty()) return;
        player.sendSystemMessage(Component.translatable("message.useless_mod.adapters.disabled", String.join(", ", names))
                .withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.translatable("message.useless_mod.adapters.report").withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                        "https://github.com/SorrowMist/UselessMod/issues/new"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("message.useless_mod.adapters.report.hover")))));
    }
}
