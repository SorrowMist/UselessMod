package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.core.component.RitualBlueprintPentacles;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Imprints the validated Occultism pentacles at a ritual bowl onto an alloy-furnace mold. */
public final class RitualBlueprintItem extends Item {
    public RitualBlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return com.sorrowmist.useless.compat.forge1201.OptionalIntegration.call("occultism",
                "com.sorrowmist.useless.compat.occultism.OccultismBlueprintCompat","useOn",InteractionResult.class,
                InteractionResult.PASS,new Class<?>[]{UseOnContext.class},context);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        RitualBlueprintPentacles pentacles = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, UComponents.RITUAL_BLUEPRINT_PENTACLE.get());
        if (pentacles == null || pentacles.isEmpty()) {
            tooltip.add(Component.translatable("item.useless_mod.ritual_blueprint.blank")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (pentacles.pentacles().size() == 1) {
            tooltip.add(Component.translatable("item.useless_mod.ritual_blueprint.pentacle",
                            pentacleName(pentacles.pentacles().get(0)))
                    .withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        tooltip.add(Component.translatable("item.useless_mod.ritual_blueprint.pentacles",
                        pentacles.pentacles().size())
                .withStyle(ChatFormatting.DARK_PURPLE));
        for (ResourceLocation pentacle : pentacles.pentacles()) {
            tooltip.add(Component.translatable("item.useless_mod.ritual_blueprint.pentacle_entry",
                            pentacleName(pentacle))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static Component pentacleName(ResourceLocation pentacle) {
        return Component.translatableWithFallback(
                Util.makeDescriptionId("multiblock", pentacle), pentacle.toString());
    }
}
