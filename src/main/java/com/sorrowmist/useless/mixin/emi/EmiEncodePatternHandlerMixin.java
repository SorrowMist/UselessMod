package com.sorrowmist.useless.mixin.emi;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.sorrowmist.useless.compat.ae.TianshuPatternEmiCompat;
import com.sorrowmist.useless.compat.jei.OmniversalEmiSelection;
import com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** AE2's native EMI handler bypasses category-specific JEI transfer handlers. */
@Pseudo
@Mixin(targets = "appeng.integration.modules.emi.EmiEncodePatternHandler", remap = false)
public abstract class EmiEncodePatternHandlerMixin {
    @Inject(method = "craft(Ldev/emi/emi/api/recipe/EmiRecipe;Ldev/emi/emi/api/recipe/handler/EmiCraftContext;)Z",
            at = @At("HEAD"), cancellable = true)
    private void useless$transferTianshuOmniversalRecipe(
            EmiRecipe recipe, EmiCraftContext<PatternEncodingTermMenu> context,
            CallbackInfoReturnable<Boolean> cir) {
        var entry = OmniversalEmiSelection.recipe(recipe);
        if (entry == null) return;
        // Enter Tianshu's dedicated page before native processing slot checks and the
        // processing-transfer hooks that reset (or restore) the terminal's current mode.
        var result = TianshuPatternEmiCompat.transferRecipe(context.getScreenHandler(), entry);
        if (result != null) {
            if (result) Minecraft.getInstance().setScreen(context.getScreen());
            cir.setReturnValue(result);
        }
    }

    @Redirect(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Lnet/minecraft/world/item/crafting/Recipe;Ldev/emi/emi/api/recipe/EmiRecipe;Z)Lappeng/integration/modules/emi/AbstractRecipeHandler$Result;",
            at = @At(value = "INVOKE", target = "Lappeng/integration/modules/jeirei/EncodingHelper;encodeProcessingRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/util/List;Ljava/util/List;)V"))
    private void useless$encodeOmniversalRecipe(
            PatternEncodingTermMenu menu, List<List<GenericStack>> inputs, List<GenericStack> outputs,
            PatternEncodingTermMenu originalMenu, Recipe<?> backingRecipe,
            EmiRecipe displayedRecipe, boolean doTransfer) {
        var entry = OmniversalEmiSelection.recipe(displayedRecipe);
        if (entry != null && Minecraft.getInstance().level != null) {
            var canonicalInputs = OmniversalPatternJeiTransferHandler.inputOptions(entry.recipe());
            var canonicalOutputs = OmniversalPatternJeiTransferHandler.outputs(entry.recipe());
            if (canonicalInputs != null && canonicalOutputs != null
                    && !canonicalInputs.isEmpty() && !canonicalOutputs.isEmpty()
                    && canonicalInputs.size() <= menu.getProcessingInputSlots().length
                    && canonicalOutputs.size() <= menu.getProcessingOutputSlots().length) {
                // EMI's generic conversion drops custom AE keys (e.g. AppMek infusion types).
                // Keep the exact keys/amounts and AE2's normal network substitution/encoding.
                inputs = canonicalInputs;
                outputs = canonicalOutputs;
                OmniversalPatternJeiTransferHandler.selectRecipe(menu, entry, Minecraft.getInstance().level);
            }
        }
        // A direct redirect avoids Mixin's synthetic Args classes, which Forge 47's module
        // classloader cannot load. All unrelated recipe pages retain their native arguments.
        EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
    }
}
