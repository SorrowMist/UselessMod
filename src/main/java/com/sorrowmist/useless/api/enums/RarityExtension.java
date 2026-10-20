package com.sorrowmist.useless.api.enums;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Rarity;

/** Forge 1.20.1 extends Rarity through IExtensibleEnum, not NeoForge EnumProxy. */
public final class RarityExtension {
    public record Entry(Rarity value) { public Rarity getValue() { return value; } }
    public static final Entry MYTHIC = new Entry(Rarity.create("USELESS_MYTHIC", ChatFormatting.GOLD));
    public static final Entry LEGENDARY = new Entry(Rarity.create("USELESS_LEGENDARY", ChatFormatting.RED));
    private RarityExtension() {}
}
