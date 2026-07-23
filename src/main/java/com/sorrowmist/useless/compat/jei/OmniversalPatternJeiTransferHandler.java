package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.content.menus.OmniversalPatternEncoderMenu;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import com.sorrowmist.useless.init.ModMenuType;
import com.sorrowmist.useless.network.EncodeJeiOmniversalPatternPacket;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class OmniversalPatternJeiTransferHandler
        implements IRecipeTransferHandler<OmniversalPatternEncoderMenu, AdvancedAlloyFurnaceRecipe> {
    private final IRecipeTransferHandlerHelper helper;

    public OmniversalPatternJeiTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends OmniversalPatternEncoderMenu> getContainerClass() {
        return OmniversalPatternEncoderMenu.class;
    }

    @Override
    public Optional<MenuType<OmniversalPatternEncoderMenu>> getMenuType() {
        return Optional.of(ModMenuType.OMNIVERSAL_PATTERN_ENCODER_MENU.get());
    }

    @Override
    public RecipeType<AdvancedAlloyFurnaceRecipe> getRecipeType() {
        return AdvancedAlloyFurnaceRecipeCategory.TYPE;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            OmniversalPatternEncoderMenu menu,
            AdvancedAlloyFurnaceRecipe recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        if (!menu.canAcceptJeiRecipe()) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("gui.useless_mod.omniversal_encoder.not_empty"));
        }
        if (!menu.hasBlankPattern(player)) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("gui.useless_mod.omniversal_encoder.missing_blank_pattern"));
        }
        if (doTransfer) {
            String fingerprint = AlloyFurnaceRecipeFingerprint.create(recipe, player.level().registryAccess());
            PacketDistributor.sendToServer(new EncodeJeiOmniversalPatternPacket(
                    menu.containerId, recipe.id(), fingerprint));
        }
        return null;
    }
}
