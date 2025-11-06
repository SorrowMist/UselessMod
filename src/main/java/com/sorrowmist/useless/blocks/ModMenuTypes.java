// ModMenuTypes.java
package com.sorrowmist.useless.blocks;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, UselessMod.MOD_ID);

    public static final RegistryObject<MenuType<AdvancedAlloyFurnaceMenu>> ADVANCED_ALLOY_FURNACE_MENU =
            MENUS.register("advanced_alloy_furnace_menu",
                    () -> IForgeMenuType.create((windowId, inv, data) -> {
                        // 添加更健壮的数据检查
                        if (data == null) {
                            UselessMod.LOGGER.error("Received null data when opening Advanced Alloy Furnace menu");
                            // 返回一个安全的默认菜单，而不是null
                            return new AdvancedAlloyFurnaceMenu(windowId, inv, null);
                        }

                        try {
                            BlockPos pos = data.readBlockPos();
                            if (pos == null) {
                                UselessMod.LOGGER.error("Received null position data");
                                return new AdvancedAlloyFurnaceMenu(windowId, inv, null);
                            }

                            // 检查世界和方块实体是否存在
                            if (inv.player == null || inv.player.level() == null) {
                                UselessMod.LOGGER.error("Player or level is null");
                                return new AdvancedAlloyFurnaceMenu(windowId, inv, null);
                            }

                            BlockEntity blockEntity = inv.player.level().getBlockEntity(pos);
                            if (blockEntity == null) {
                                UselessMod.LOGGER.warn("Block entity not found at position: {}", pos);
                                // 仍然创建菜单，但方块实体为null
                                return new AdvancedAlloyFurnaceMenu(windowId, inv, null);
                            }

                            return new AdvancedAlloyFurnaceMenu(windowId, inv, blockEntity);
                        } catch (Exception e) {
                            UselessMod.LOGGER.error("Error creating Advanced Alloy Furnace menu: {}", e.getMessage());
                            // 发生异常时返回安全的默认菜单
                            return new AdvancedAlloyFurnaceMenu(windowId, inv, null);
                        }
                    }));
}