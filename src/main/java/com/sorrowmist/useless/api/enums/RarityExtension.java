package com.sorrowmist.useless.api.enums;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

import java.util.function.UnaryOperator;

public class RarityExtension {
    public static final EnumProxy<Rarity> MYTHIC = new EnumProxy<>(
            Rarity.class,
            -1,
            UselessMod.MODID + ":mythic",
            (UnaryOperator<Style>) style -> style.withColor(ChatFormatting.GOLD)
    );

    public static final EnumProxy<Rarity> LEGENDARY = new EnumProxy<>(
            Rarity.class,
            -1,
            UselessMod.MODID + ":legendary",
            (UnaryOperator<Style>) style -> style.withColor(ChatFormatting.RED)
    );
}