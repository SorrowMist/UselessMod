package com.sorrowmist.useless.content.items;

import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.registry.OccultismRecipes;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Imprints the validated Occultism pentacles at a ritual bowl onto an alloy-furnace mold. */
public final class RitualBlueprintItem extends Item {
    public RitualBlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack blueprint = context.getItemInHand();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof GoldenSacrificialBowlBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (blueprint.has(UComponents.RITUAL_BLUEPRINT_PENTACLE.get())) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Set<ResourceLocation> matches = new LinkedHashSet<>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(OccultismRecipes.RITUAL_TYPE.get())) {
            RitualRecipe recipe = holder.value();
            if (recipe == null || recipe.getPentacle() == null
                    || recipe.getPentacle().validate(level, context.getClickedPos()) == null) {
                continue;
            }
            matches.add(recipe.getPentacleId());
        }

        if (matches.isEmpty()) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable(
                        "item.useless_mod.ritual_blueprint.no_pentacle"), true);
            }
            return InteractionResult.FAIL;
        }

        RitualBlueprintPentacles pentacles = RitualBlueprintPentacles.of(matches);
        blueprint.set(UComponents.RITUAL_BLUEPRINT_PENTACLE.get(), pentacles);
        if (context.getPlayer() != null) {
            Component message = pentacles.pentacles().size() == 1
                    ? Component.translatable("item.useless_mod.ritual_blueprint.imprinted",
                            pentacleName(pentacles.pentacles().getFirst()))
                    : Component.translatable("item.useless_mod.ritual_blueprint.imprinted_multiple",
                            pentacles.pentacles().size());
            context.getPlayer().displayClientMessage(message, true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RitualBlueprintPentacles pentacles = stack.get(UComponents.RITUAL_BLUEPRINT_PENTACLE.get());
        if (pentacles == null || pentacles.isEmpty()) {
            tooltip.add(Component.translatable("item.useless_mod.ritual_blueprint.blank")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (pentacles.pentacles().size() == 1) {
            tooltip.add(Component.translatable("item.useless_mod.ritual_blueprint.pentacle",
                            pentacleName(pentacles.pentacles().getFirst()))
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
