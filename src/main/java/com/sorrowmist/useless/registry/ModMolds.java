package com.sorrowmist.useless.registry;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMolds {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    // 注册四种金属模具
    public static final RegistryObject<Item> METAL_MOLD_PLATE = ITEMS.register("metal_mold_plate",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> METAL_MOLD_ROD = ITEMS.register("metal_mold_rod",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> METAL_MOLD_GEAR = ITEMS.register("metal_mold_gear",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> METAL_MOLD_WIRE = ITEMS.register("metal_mold_wire",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> METAL_MOLD_BLOCK = ITEMS.register("metal_mold_block",
            () -> new Item(new Item.Properties()));
}