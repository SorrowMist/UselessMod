package com.sorrowmist.useless.compat.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.network.SelectOmniversalPatternRecipePacket;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Transfers an alloy furnace recipe from JEI into AE2's pattern encoding terminal.
 *
 * <p>Registering this for {@link AdvancedAlloyFurnaceRecipeCategory#TYPE} takes precedence over
 * ae2jeiintegration's universal encode handler, which JEI only consults when no category-specific
 * handler matches the open menu. That is what makes the omniversal pattern exclusive to this
 * category: every other JEI page still goes through the universal handler and yields a plain
 * processing pattern.
 *
 * <p>The transfer itself is the same as AE2's — fill the terminal's fake slots and let the player
 * press encode — except that the picked recipe's identity is sent to the server first. Only that
 * identity carries the mold, which no combination of terminal slots can express, and several
 * recipes may share their inputs and outputs while differing only by mold. Recording the click
 * instead of guessing from the slots is what lets the player name the exact recipe they want.
 *
 * <p>JEI looks handlers up by the menu's exact class, so subclassed terminals (ae2wtlib's wireless
 * one) need their own instance; hence the menu class and type are constructor arguments rather than
 * constants, matching how ae2jeiintegration registers its encode handler twice.
 */
public final class OmniversalPatternJeiTransferHandler<T extends PatternEncodingTermMenu>
        implements IRecipeTransferHandler<T, AlloyFurnaceRecipeCatalog.Entry> {
    private final Class<T> menuClass;
    private final MenuType<T> menuType;
    private final IRecipeTransferHandlerHelper helper;

    public OmniversalPatternJeiTransferHandler(
            Class<T> menuClass, MenuType<T> menuType, IRecipeTransferHandlerHelper helper) {
        this.menuClass = menuClass;
        this.menuType = menuType;
        this.helper = helper;
    }

    @Override
    public Class<? extends T> getContainerClass() {
        return menuClass;
    }

    @Override
    public Optional<MenuType<T>> getMenuType() {
        return Optional.of(menuType);
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<AlloyFurnaceRecipeCatalog.Entry> getRecipeType() {
        return AdvancedAlloyFurnaceRecipeCategory.TYPE;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            T menu,
            AlloyFurnaceRecipeCatalog.Entry entry,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        return transferOmniversalRecipe(menu, entry, recipeSlots, player, maxTransfer, doTransfer, this.helper);
    }

    /**
     * Transfers a selected alloy-furnace recipe through any AE2 pattern-encoding menu, including optional terminals
     * that subclass the base menu and register their own exact JEI container type.
     *
     * @param menu active AE2-compatible pattern encoding menu
     * @param recipe exact JEI recipe selection whose identity includes the mold
     * @param recipeSlots displayed JEI slots for the selected recipe
     * @param player player requesting the transfer
     * @param maxTransfer whether JEI requested a maximum transfer
     * @param doTransfer whether JEI is performing rather than simulating the transfer
     * @param helper JEI error factory for the active registration
     * @return a user-facing transfer error, or {@code null} when the transfer succeeds
     */
    public static @Nullable IRecipeTransferError transferOmniversalRecipe(
            PatternEncodingTermMenu menu,
            AlloyFurnaceRecipeCatalog.Entry entry,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer,
            IRecipeTransferHandlerHelper helper) {
        AdvancedAlloyFurnaceRecipe recipe = entry.recipe();
        List<List<GenericStack>> inputs = inputOptions(recipe);
        List<GenericStack> outputs = outputs(recipe);
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return helper.createInternalError();
        }
        // AE2 merges repeated inputs before filling the slots, so this can reject a recipe that would
        // just barely have fit. Refusing up front beats transferring a truncated recipe, which the
        // server would reject anyway and leave as a plain pattern with no explanation.
        if (inputs.size() > menu.getProcessingInputSlots().length
                || outputs.size() > menu.getProcessingOutputSlots().length) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("gui.useless_mod.omniversal_pattern.too_many_slots"));
        }
        if (!doTransfer) {
            return null;
        }

        // Sent before the slot contents so the pick is on record by the time the server sees the
        // encoded pattern, including when a helper mod encodes it automatically on the same tick.
        PacketDistributor.sendToServer(new SelectOmniversalPatternRecipePacket(
                menu.containerId,
                recipe.id(),
                AlloyFurnaceRecipeFingerprint.create(recipe, player.level().registryAccess()),
                entry.sourceId()));
        EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
        return null;
    }

    /**
     * One entry per input, each listing the interchangeable stacks that satisfy it so AE2 can encode
     * whichever the player's network actually stocks. Ordered like
     * {@link com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding#createProcessingPattern}
     * so the encoded slots line up with the recipe the server validates them against.
     */
    private static List<List<GenericStack>> inputOptions(AdvancedAlloyFurnaceRecipe recipe) {
        List<List<GenericStack>> inputs = new ArrayList<>();
        for (CountedIngredient input : recipe.inputs()) {
            if (input == null || input.count() <= 0) continue;
            List<GenericStack> options = new ArrayList<>();
            for (ItemStack option : input.ingredient().getItems()) {
                AEItemKey key = AEItemKey.of(option);
                if (key != null) options.add(new GenericStack(key, input.count()));
            }
            if (!options.isEmpty()) inputs.add(options);
        }
        recipe.inputFluids().forEach(input -> {
            List<GenericStack> options = new ArrayList<>();
            for (FluidStack option : input.getFluids()) {
                GenericStack stack = GenericStack.fromFluidStack(option);
                if (stack != null) options.add(stack);
            }
            if (!options.isEmpty()) inputs.add(options);
        });
        recipe.keyInputs().stream()
                .filter(Objects::nonNull)
                .forEach(stack -> inputs.add(List.of(stack)));
        return inputs;
    }

    private static List<GenericStack> outputs(AdvancedAlloyFurnaceRecipe recipe) {
        List<GenericStack> outputs = new ArrayList<>();
        recipe.outputs().stream().map(GenericStack::fromItemStack).forEach(outputs::add);
        recipe.outputFluids().stream().map(GenericStack::fromFluidStack).forEach(outputs::add);
        outputs.addAll(recipe.keyOutputs());
        outputs.removeIf(Objects::isNull);
        return outputs;
    }
}
