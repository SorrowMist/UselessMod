package com.sorrowmist.useless.registry;

/*
 * This file is part of Thermal Parallel.
 * 
 * Copyright (c) 2025 EtSH-C2H6S
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

import cofh.core.util.helpers.AugmentDataHelper;
import cofh.thermal.lib.common.item.AugmentItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ThermalParallelItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "useless_mod");

    public static final RegistryObject<AugmentItem> AUGMENT_PARALLEL_1 = ITEMS.register("augment_parallel_1",
            () -> new AugmentItem(new Item.Properties(), AugmentDataHelper.builder()
                    .type("Machine")
                    .mod("MachineParallel", 1.0F)
                    .build()));

    public static final RegistryObject<AugmentItem> AUGMENT_PARALLEL_2 = ITEMS.register("augment_parallel_2",
            () -> new AugmentItem(new Item.Properties(), AugmentDataHelper.builder()
                    .type("Machine")
                    .mod("MachineParallel", 16.0F)
                    .build()));

    public static final RegistryObject<AugmentItem> AUGMENT_PARALLEL_3 = ITEMS.register("augment_parallel_3",
            () -> new AugmentItem(new Item.Properties(), AugmentDataHelper.builder()
                    .type("Machine")
                    .mod("MachineParallel", 256.0F)
                    .build()));
}