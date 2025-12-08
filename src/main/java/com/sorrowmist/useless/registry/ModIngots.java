package com.sorrowmist.useless.registry;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModIngots {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    // 定义稀有度等级
    public static final Rarity COMMON = Rarity.COMMON;
    public static final Rarity UNCOMMON = Rarity.UNCOMMON;
    public static final Rarity RARE = Rarity.RARE;
    public static final Rarity EPIC = Rarity.EPIC;

    // 自定义稀有度
    public static final Rarity MYTHIC = Rarity.create("mythic", style -> style.withColor(ChatFormatting.GOLD));
    public static final Rarity LEGENDARY = Rarity.create("legendary", style -> style.withColor(ChatFormatting.RED));

    // 注册九种不同等级的锭
    public static final RegistryObject<Item> USELESS_INGOT_TIER_1 = ITEMS.register("useless_ingot_tier_1",
            () -> new Item(new Item.Properties().rarity(COMMON)));

    public static final RegistryObject<Item> USELESS_INGOT_TIER_2 = ITEMS.register("useless_ingot_tier_2",
            () -> new Item(new Item.Properties().rarity(COMMON)));

    public static final RegistryObject<Item> USELESS_INGOT_TIER_3 = ITEMS.register("useless_ingot_tier_3",
            () -> new Item(new Item.Properties().rarity(UNCOMMON)));

    public static final RegistryObject<Item> USELESS_INGOT_TIER_4 = ITEMS.register("useless_ingot_tier_4",
            () -> new Item(new Item.Properties().rarity(UNCOMMON)));

    public static final RegistryObject<Item> USELESS_INGOT_TIER_5 = ITEMS.register("useless_ingot_tier_5",
            () -> new Item(new Item.Properties().rarity(RARE)));

    public static final RegistryObject<Item> USELESS_INGOT_TIER_6 = ITEMS.register("useless_ingot_tier_6",
            () -> new Item(new Item.Properties().rarity(RARE)));

    public static final RegistryObject<Item> USELESS_INGOT_TIER_7 = ITEMS.register("useless_ingot_tier_7",
            () -> new Item(new Item.Properties().rarity(EPIC)));

    public static final RegistryObject<Item> USELESS_INGOT_TIER_8 = ITEMS.register("useless_ingot_tier_8",
            () -> new Item(new Item.Properties().rarity(MYTHIC)));

    public static final RegistryObject<Item> USELESS_INGOT_TIER_9 = ITEMS.register("useless_ingot_tier_9",
            () -> new Item(new Item.Properties().rarity(LEGENDARY)));

    // 新增：可能有用的锭，仅作为合成中间材料
    public static final RegistryObject<Item> POSSIBLE_USEFUL_INGOT = ITEMS.register("possible_useful_ingot",
            () -> new Item(new Item.Properties().rarity(LEGENDARY)));

    // 新增：有用的锭，作为终极催化剂
    public static final RegistryObject<Item> USEFUL_INGOT = ITEMS.register("useful_ingot",
            () -> new Item(new Item.Properties().rarity(LEGENDARY)));
}