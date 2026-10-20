package com.sorrowmist.useless.compat.occultism;

import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.registry.OccultismRecipes;
import com.sorrowmist.useless.core.component.RitualBlueprintPentacles;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.Set;

public final class OccultismBlueprintCompat {
    public static InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack blueprint = context.getItemInHand();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof GoldenSacrificialBowlBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.has(blueprint, UComponents.RITUAL_BLUEPRINT_PENTACLE.get())) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Set<ResourceLocation> matches = new LinkedHashSet<>();
        for (var holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getAllRecipesFor(level.getRecipeManager(), OccultismRecipes.RITUAL_TYPE.get())) {
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
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(blueprint, UComponents.RITUAL_BLUEPRINT_PENTACLE.get(), pentacles);
        if (context.getPlayer() != null) {
            Component message = pentacles.pentacles().size() == 1
                    ? Component.translatable("item.useless_mod.ritual_blueprint.imprinted",
                            pentacleName(pentacles.pentacles().get(0)))
                    : Component.translatable("item.useless_mod.ritual_blueprint.imprinted_multiple",
                            pentacles.pentacles().size());
            context.getPlayer().displayClientMessage(message, true);
        }
        return InteractionResult.CONSUME;
    }

    private static Component pentacleName(ResourceLocation pentacle) {
        return Component.translatableWithFallback(
                Util.makeDescriptionId("multiblock", pentacle), pentacle.toString());
    }
}
