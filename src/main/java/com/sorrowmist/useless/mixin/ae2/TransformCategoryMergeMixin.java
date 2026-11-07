package com.sorrowmist.useless.mixin.ae2;

import appeng.core.definitions.AEBlocks;
import appeng.integration.modules.jei.FluidBlockRenderer;
import appeng.integration.modules.jei.widgets.View;
import appeng.integration.modules.jei.widgets.Widget;
import appeng.integration.modules.jei.widgets.WidgetFactory;
import appeng.recipes.transform.TransformRecipe;
import com.sorrowmist.useless.compat.jei.ItemStackCountRenderer;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * AE2 15.4.11 has a ViewBasedCategory, not the 1.21 tamaized category. Wrap its cached View;
 * retain its environment widgets/tooltips while grouping only semantically equal ingredients.
 * Client-only mixin registration is intentionally left to the main migration session.
 */
@Pseudo
@Mixin(targets = "appeng.integration.modules.jei.TransformCategory", remap = false)
public abstract class TransformCategoryMergeMixin {
    @Shadow @Final private FluidBlockRenderer fluidRenderer;
    @Shadow @Final private IDrawable arrow;

    /**
     * AE2 15 writes the ingredient count as one byte, but reads it as SIGNED. Tier 2/3
     * need 129/145 entries. Keep the native wire bytes and interpret their count unsigned.
     * Register this nested mixin on BOTH physical sides, separately from the JEI mixin.
     * It has no client/JEI fields, so loading it does not resolve its enclosing client mixin.
     */
    @Pseudo
    @Mixin(targets = "appeng.recipes.transform.TransformRecipeSerializer", remap = false)
    public abstract static class NativeTransformCountWireMixin {
        @ModifyVariable(method = "fromNetwork(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/FriendlyByteBuf;)Lappeng/recipes/transform/TransformRecipe;",
                at = @At("STORE"), ordinal = 0, remap = false)
        private int useless$unsignedTransformIngredientCount(int count) {
            return count & 0xFF;
        }
    }

    @Inject(method = "getView(Lappeng/recipes/transform/TransformRecipe;)Lappeng/integration/modules/jei/widgets/View;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void useless$mergeTransformInputs(TransformRecipe recipe, CallbackInfoReturnable<View> cir) {
        List<CountedIngredient> merged = AdapterUtils.mergeIngredients(recipe.getIngredients());
        if (merged.stream().noneMatch(input -> input.count() > 1)) return;
        View original = cir.getReturnValue();
        cir.setReturnValue(new View() {
            @Override public void createWidgets(WidgetFactory factory, List<Widget> widgets) {
                int start = widgets.size();
                original.createWidgets(factory, widgets);
                // In AE2 15 the first widget is the input arrow at (25,23). Two input columns
                // need this space; draw a shorter arrow instead of covering the second column.
                if (merged.size() > 3 && widgets.size() > start) widgets.remove(start);
            }

            @Override public void buildSlots(IRecipeLayoutBuilder builder) {
                int firstY = merged.size() < 3 ? 6 + (3 - merged.size()) * 9 : 6;
                for (int i = 0; i < merged.size(); i++) {
                    CountedIngredient counted = merged.get(i);
                    List<ItemStack> stacks = new ArrayList<>();
                    for (ItemStack stack : counted.ingredient().getItems()) {
                        stacks.add(stack.copyWithCount(Math.toIntExact(counted.count())));
                    }
                    int x = (merged.size() > 3 ? 1 : 6) + (i / 3) * 18;
                    builder.addSlot(RecipeIngredientRole.INPUT, x, firstY + (i % 3) * 18)
                            .setSlotName("input" + i).setStandardSlotBackground().addItemStacks(stacks)
                            .setCustomRenderer(VanillaTypes.ITEM_STACK, ItemStackCountRenderer.INSTANCE);
                }
                if (recipe.circumstance.isFluid()) {
                    IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.CATALYST, 56, 24).setSlotName("fluid");
                    for (Fluid fluid : recipe.circumstance.getFluidsForRendering()) {
                        if (fluid.isSource(fluid.defaultFluidState())) slot.addFluidStack(fluid, 1000);
                    }
                    slot.setCustomRenderer(ForgeTypes.FLUID_STACK, fluidRenderer);
                } else if (recipe.circumstance.isExplosion()) {
                    builder.addSlot(RecipeIngredientRole.CATALYST, 56, 24).setSlotName("explosion")
                            .addItemStack(new ItemStack(Blocks.TNT)).addItemStack(AEBlocks.TINY_TNT.stack());
                }
                builder.addSlot(RecipeIngredientRole.OUTPUT, 106, 24).setSlotName("output")
                        .setStandardSlotBackground().addItemStack(recipe.getResultItem());
            }

            @Override public void draw(GuiGraphics graphics, IRecipeSlotsView slots, double mouseX, double mouseY) {
                if (merged.size() > 3) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(38, 23, 0);
                    graphics.pose().scale(0.5f, 1, 1);
                    arrow.draw(graphics, 0, 0);
                    graphics.pose().popPose();
                }
                original.draw(graphics, slots, mouseX, mouseY);
            }

            @Override public List<Component> getTooltipStrings(double mouseX, double mouseY) {
                return original.getTooltipStrings(mouseX, mouseY);
            }
        });
    }
}
