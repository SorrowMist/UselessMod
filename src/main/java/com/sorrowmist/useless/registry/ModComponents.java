package com.sorrowmist.useless.registry;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModComponents {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);
    
    // 无用锭对应的齿轮物品
    public static final RegistryObject<Item> USELESS_GEAR_TIER_1 = ITEMS.register("useless_gear_tier_1",
            () -> new Item(new Item.Properties().rarity(ModIngots.COMMON)));
    
    public static final RegistryObject<Item> USELESS_GEAR_TIER_2 = ITEMS.register("useless_gear_tier_2",
            () -> new Item(new Item.Properties().rarity(ModIngots.COMMON)));
    
    public static final RegistryObject<Item> USELESS_GEAR_TIER_3 = ITEMS.register("useless_gear_tier_3",
            () -> new Item(new Item.Properties().rarity(ModIngots.UNCOMMON)));
    
    public static final RegistryObject<Item> USELESS_GEAR_TIER_4 = ITEMS.register("useless_gear_tier_4",
            () -> new Item(new Item.Properties().rarity(ModIngots.UNCOMMON)));
    
    public static final RegistryObject<Item> USELESS_GEAR_TIER_5 = ITEMS.register("useless_gear_tier_5",
            () -> new Item(new Item.Properties().rarity(ModIngots.RARE)));
    
    public static final RegistryObject<Item> USELESS_GEAR_TIER_6 = ITEMS.register("useless_gear_tier_6",
            () -> new Item(new Item.Properties().rarity(ModIngots.RARE)));
    
    public static final RegistryObject<Item> USELESS_GEAR_TIER_7 = ITEMS.register("useless_gear_tier_7",
            () -> new Item(new Item.Properties().rarity(ModIngots.EPIC)));
    
    public static final RegistryObject<Item> USELESS_GEAR_TIER_8 = ITEMS.register("useless_gear_tier_8",
            () -> new Item(new Item.Properties().rarity(ModIngots.MYTHIC)));
    
    public static final RegistryObject<Item> USELESS_GEAR_TIER_9 = ITEMS.register("useless_gear_tier_9",
            () -> new Item(new Item.Properties().rarity(ModIngots.LEGENDARY)));
    
    // 无用锭对应的玻璃物品
    public static final RegistryObject<Item> USELESS_GLASS_TIER_1 = ITEMS.register("useless_glass_tier_1",
            () -> new Item(new Item.Properties().rarity(ModIngots.COMMON)));
    
    public static final RegistryObject<Item> USELESS_GLASS_TIER_2 = ITEMS.register("useless_glass_tier_2",
            () -> new Item(new Item.Properties().rarity(ModIngots.COMMON)));
    
    public static final RegistryObject<Item> USELESS_GLASS_TIER_3 = ITEMS.register("useless_glass_tier_3",
            () -> new Item(new Item.Properties().rarity(ModIngots.UNCOMMON)));
    
    public static final RegistryObject<Item> USELESS_GLASS_TIER_4 = ITEMS.register("useless_glass_tier_4",
            () -> new Item(new Item.Properties().rarity(ModIngots.UNCOMMON)));
    
    public static final RegistryObject<Item> USELESS_GLASS_TIER_5 = ITEMS.register("useless_glass_tier_5",
            () -> new Item(new Item.Properties().rarity(ModIngots.RARE)));
    
    public static final RegistryObject<Item> USELESS_GLASS_TIER_6 = ITEMS.register("useless_glass_tier_6",
            () -> new Item(new Item.Properties().rarity(ModIngots.RARE)));
    
    public static final RegistryObject<Item> USELESS_GLASS_TIER_7 = ITEMS.register("useless_glass_tier_7",
            () -> new Item(new Item.Properties().rarity(ModIngots.EPIC)));
    
    public static final RegistryObject<Item> USELESS_GLASS_TIER_8 = ITEMS.register("useless_glass_tier_8",
            () -> new Item(new Item.Properties().rarity(ModIngots.MYTHIC)));
    
    public static final RegistryObject<Item> USELESS_GLASS_TIER_9 = ITEMS.register("useless_glass_tier_9",
            () -> new Item(new Item.Properties().rarity(ModIngots.LEGENDARY)));
}