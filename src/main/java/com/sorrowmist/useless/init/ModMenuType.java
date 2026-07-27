package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.content.menus.OmniversalPatternEncoderMenu;
import com.sorrowmist.useless.content.menus.MePatternAssemblyMenu;
import com.sorrowmist.useless.content.menus.OmniversalMoldHubMenu;
import com.sorrowmist.useless.content.menus.MultiblockAlloyFurnaceMenu;
import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenuType {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, UselessMod.MODID);
    public static final Supplier<MenuType<AdvancedAlloyFurnaceMenu>> ADVANCED_ALLOY_FURNACE_MENU =
            MENU_TYPES.register("advanced_alloy_furnace_menu",
                                () -> IMenuTypeExtension.create(AdvancedAlloyFurnaceMenu::new)
            );
    public static final Supplier<MenuType<OmniversalPatternEncoderMenu>> OMNIVERSAL_PATTERN_ENCODER_MENU =
            MENU_TYPES.register("omniversal_pattern_encoder_menu",
                    () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                            new OmniversalPatternEncoderMenu(id, inventory)));
    public static final Supplier<MenuType<MePatternAssemblyMenu>> ME_PATTERN_ASSEMBLY_MENU =
            MENU_TYPES.register("me_pattern_assembly_menu",
                    () -> IMenuTypeExtension.create(MePatternAssemblyMenu::new));
    public static final Supplier<MenuType<OmniversalMoldHubMenu>> OMNIVERSAL_MOLD_HUB_MENU =
            MENU_TYPES.register("omniversal_mold_hub_menu",
                    () -> IMenuTypeExtension.create(OmniversalMoldHubMenu::new));
    public static final Supplier<MenuType<MultiblockAlloyFurnaceMenu>> MULTIBLOCK_ALLOY_FURNACE_MENU =
            MENU_TYPES.register("multiblock_alloy_furnace_menu",
                    () -> IMenuTypeExtension.create(MultiblockAlloyFurnaceMenu::new));
    public static final Supplier<MenuType<PassiveCraftingHatchMenu>> PASSIVE_CRAFTING_HATCH_MENU =
            MENU_TYPES.register("passive_crafting_hatch_menu",
                    () -> IMenuTypeExtension.create(PassiveCraftingHatchMenu::new));

    private ModMenuType() {}

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
