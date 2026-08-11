package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.EnumColor;
import com.sorrowmist.useless.client.gui.AdvancedAlloyFurnaceScreen;
import com.sorrowmist.useless.client.gui.PagedRecoverableScreen;
import com.sorrowmist.useless.client.gui.PatternAssemblyScreen;
import com.sorrowmist.useless.client.gui.MoldHubScreen;
import com.sorrowmist.useless.client.gui.MultiblockAlloyFurnaceScreen;
import com.sorrowmist.useless.client.gui.PassiveCraftingHatchScreen;
import com.sorrowmist.useless.client.gui.OreGeneratorScreen;
import com.sorrowmist.useless.client.render.ctm.CtmModelRegistrar;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.component.UComponents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        CtmModelRegistrar.modifyBakingResult(event);
    }

    @SubscribeEvent
    public static void onItemColor(RegisterColorHandlersEvent.Item event) {
        // 注册所有颜色的物品染色器
        for (EnumColor color : EnumColor.valuesInOrder()) {
            var block = GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.get(color).get();
            event.register((stack, tintIndex) -> {
                // 只对 tintIndex 0 应用颜色染色
                return tintIndex == 0 ? color.getRgb() : 0xFFFFFFFF;
            }, block.asItem());
        }
    }

    @SubscribeEvent
    public static void onBlockColor(RegisterColorHandlersEvent.Block event) {
        // 注册所有颜色的方块染色器
        for (EnumColor color : EnumColor.valuesInOrder()) {
            var block = GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.get(color).get();
            event.register((state, world, pos, tintIndex) -> {
                // 只对 tintIndex 0 应用颜色染色
                return tintIndex == 0 ? color.getRgb() : 0xFFFFFFFF;
            }, block);
        }
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuType.ADVANCED_ALLOY_FURNACE_MENU.get(), AdvancedAlloyFurnaceScreen::new);
        event.register(ModMenuType.ME_PATTERN_ASSEMBLY_MENU.get(), PatternAssemblyScreen::new);
        event.register(ModMenuType.OMNIVERSAL_MOLD_HUB_MENU.get(), MoldHubScreen::new);
        event.register(ModMenuType.MULTIBLOCK_ALLOY_FURNACE_MENU.get(), MultiblockAlloyFurnaceScreen::new);
        event.register(ModMenuType.PASSIVE_CRAFTING_HATCH_MENU.get(), PassiveCraftingHatchScreen::new);
        event.register(ModMenuType.ORE_GENERATOR_MENU.get(), OreGeneratorScreen::new);
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        AlloyFurnaceRecipeCatalog.invalidate();
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(ModItems.OMNIVERSAL_PATTERN.get())) return;
        var data = event.getItemStack().get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (data == null) return;
        event.getToolTip().add(createRecipeTooltip(data.recipeId()));
        if (!data.requiresMold()) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.useless_mod.omniversal_pattern.mold",
                    Component.translatable("tooltip.useless_mod.omniversal_pattern.no_mold"))
                    .withStyle(ChatFormatting.GOLD));
        } else if (!data.displayMolds().isEmpty()) {
            for (var mold : data.displayMolds()) {
                event.getToolTip().add(Component.translatable(
                        "tooltip.useless_mod.omniversal_pattern.mold",
                        mold.getDisplayName()).withStyle(ChatFormatting.GOLD));
            }
        } else {
            Component mold = data.displayMold().<Component>map(key -> key.getDisplayName())
                    .orElseGet(() -> Component.translatable("tooltip.useless_mod.omniversal_pattern.unknown_mold"));
            event.getToolTip().add(Component.translatable(
                    "tooltip.useless_mod.omniversal_pattern.mold", mold).withStyle(ChatFormatting.GOLD));
        }
    }

    static Component createRecipeTooltip(ResourceLocation recipeId) {
        return Component.translatable(
                "tooltip.useless_mod.omniversal_pattern.recipe", recipeId.toString())
                .withStyle(ChatFormatting.DARK_GRAY);
    }
}
