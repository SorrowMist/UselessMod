package com.sorrowmist.useless.registry;

/*
 * This file is part of Thermal More Integral Components.
 * 
 * Copyright (c) 2024 Elephant_dev
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import cofh.core.common.item.CountedItem;
import cofh.core.common.item.ItemCoFH;
import cofh.core.util.helpers.AugmentDataHelper;
import cofh.lib.util.DeferredRegisterCoFH;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.items.UpgradeAugmentItem;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ThermalMoreItems {
    public static final DeferredRegisterCoFH<Item> ITEMS = DeferredRegisterCoFH.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    public static final Rarity YELLOW = Rarity.UNCOMMON;
    public static final Rarity EPIC = Rarity.EPIC;
    public static final Rarity DARK_AQUA = Rarity.create("extra_dark_aqua", style -> style.withColor(ChatFormatting.DARK_AQUA));
    public static final Rarity DARK_PURPLE = Rarity.create("extra_dark_purple", style -> style.withColor(ChatFormatting.DARK_PURPLE));
    public static final Rarity RED = Rarity.create("extra_red", style -> style.withColor(ChatFormatting.RED));

    // 重命名所有物品
    public static final RegistryObject<ItemCoFH> USELESS_INTEGRAL_COMPONENT_TIER_1 = ITEMS.register("useless_integral_component_tier_1",
            () -> new UpgradeAugmentItem((new Item.Properties()).rarity(RED),
                    AugmentDataHelper.builder().type("Upgrade").mod("BaseMod", 10.0F).build()));

    public static final RegistryObject<ItemCoFH> USELESS_INTEGRAL_COMPONENT_TIER_2 = ITEMS.register("useless_integral_component_tier_2",
            () -> new UpgradeAugmentItem((new Item.Properties()).rarity(RED),
                    AugmentDataHelper.builder().type("Upgrade").mod("BaseMod", 25.0F).build()));

    public static final RegistryObject<ItemCoFH> USELESS_INTEGRAL_COMPONENT_TIER_3 = ITEMS.register("useless_integral_component_tier_3",
            () -> new UpgradeAugmentItem((new Item.Properties()).rarity(YELLOW),
                    AugmentDataHelper.builder().type("Upgrade").mod("BaseMod", 50.0F).build()));

    public static final RegistryObject<ItemCoFH> USELESS_INTEGRAL_COMPONENT_TIER_4 = ITEMS.register("useless_integral_component_tier_4",
            () -> new UpgradeAugmentItem((new Item.Properties()).rarity(YELLOW),
                    AugmentDataHelper.builder().type("Upgrade").mod("BaseMod", 100.0F).build()));

    public static final RegistryObject<ItemCoFH> USELESS_INTEGRAL_COMPONENT_TIER_5 = ITEMS.register("useless_integral_component_tier_5",
            () -> new UpgradeAugmentItem((new Item.Properties()).rarity(DARK_AQUA),
                    AugmentDataHelper.builder().type("Upgrade").mod("BaseMod", 250.0F).build()));

    public static final RegistryObject<ItemCoFH> USELESS_INTEGRAL_COMPONENT_TIER_6 = ITEMS.register("useless_integral_component_tier_6",
            () -> new UpgradeAugmentItem((new Item.Properties()).rarity(DARK_AQUA),
                    AugmentDataHelper.builder().type("Upgrade").mod("BaseMod", 500.0F).build()));

    public static final RegistryObject<ItemCoFH> USELESS_INTEGRAL_COMPONENT_TIER_7 = ITEMS.register("useless_integral_component_tier_7",
            () -> new UpgradeAugmentItem((new Item.Properties()).rarity(DARK_PURPLE),
                    AugmentDataHelper.builder().type("Upgrade").mod("BaseMod", 1000.0F).build()));

    public static final RegistryObject<ItemCoFH> USELESS_INTEGRAL_COMPONENT_TIER_8 = ITEMS.register("useless_integral_component_tier_8",
            () -> new UpgradeAugmentItem((new Item.Properties()).rarity(DARK_PURPLE),
                    AugmentDataHelper.builder().type("Upgrade").mod("BaseMod", 2500.0F).build()));

    public static final RegistryObject<ItemCoFH> USELESS_INTEGRAL_COMPONENT_TIER_9 = ITEMS.register("useless_integral_component_tier_9",
            () -> new UpgradeAugmentItem((new Item.Properties()).rarity(EPIC),
                    AugmentDataHelper.builder().type("Upgrade").mod("BaseMod", 5000.0F).build()));


    public static Supplier<ItemCoFH> item(Consumer<Item.Properties> consumer) {
        return item(consumer, false);
    }

    public static Supplier<ItemCoFH> item(Consumer<Item.Properties> consumer, boolean count) {
        Item.Properties props = new Item.Properties();
        consumer.accept(props);
        return count ? () -> new CountedItem(props) : () -> new ItemCoFH(props);
    }
}