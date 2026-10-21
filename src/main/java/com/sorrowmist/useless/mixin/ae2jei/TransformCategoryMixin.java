package com.sorrowmist.useless.mixin.ae2jei;

import java.util.ArrayList;
import java.util.List;

import appeng.core.definitions.AEBlocks;
import appeng.recipes.transform.TransformRecipe;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tamaized.ae2jeiintegration.integration.modules.jei.FluidBlockRenderer;
import tamaized.ae2jeiintegration.integration.modules.jei.categories.TransformCategory;

/**
 * 合并世界交互配方在 JEI 中重复展示的材料槽位。
 *
 * <p>世界交互配方以「同一材料重复书写若干次」表示消耗数量，原类别实现把每条
 * {@link net.minecraft.world.item.crafting.Ingredient} 单独铺成一个槽位，
 * 数量较大的配方因此占据数十个槽位，难以辨认实际所需材料。</p>
 *
 * <p>本混入在原方法开头接管布局：先用 {@link AdapterUtils#mergeIngredients(List)}
 * 合并重复材料，再按合并结果重新铺设输入槽位，并把重复次数写入物品堆叠数量。
 * 无重复材料时不做接管，保持原类别行为。环境槽（流体或爆炸物）与输出槽的
 * 位置和呈现方式沿用原实现。</p>
 *
 * <p>布局坐标取自 AE2 JEI Integration 的
 * {@code tamaized.ae2jeiintegration.integration.modules.jei.categories.TransformCategory#setRecipe}。</p>
 */
@Mixin(TransformCategory.class)
public abstract class TransformCategoryMixin {
    /** 原类别持有的流体方块渲染器，用于环境槽的流体展示。 */
    @Shadow
    @Final
    private FluidBlockRenderer fluidRenderer;

    @Inject(method = "setRecipe", at = @At("HEAD"), cancellable = true)
    private void useless$mergeRepeatedIngredients(IRecipeLayoutBuilder builder,
            RecipeHolder<TransformRecipe> holder, IFocusGroup focuses, CallbackInfo ci) {
        TransformRecipe recipe = holder.value();
        List<CountedIngredient> merged = AdapterUtils.mergeIngredients(recipe.getIngredients());
        if (!hasRepeatedIngredient(merged)) {
            return;
        }

        // 原实现在输入不足三行时把整列居中，合并后条目数变化，需按新数量重新计算。
        int yOffset = 23;
        int y = 5;
        int x = 5;
        if (merged.size() < 3) {
            y += (3 - merged.size()) * 18 / 2;
        }

        for (CountedIngredient counted : merged) {
            List<ItemStack> stacks = new ArrayList<>();
            // ItemStack 的计数为 int，配方中的重复次数实际远小于该上限。
            int count = (int) Math.min(counted.count(), Integer.MAX_VALUE);
            for (ItemStack stack : counted.ingredient().getItems()) {
                stacks.add(stack.copyWithCount(count));
            }
            if (stacks.isEmpty()) {
                continue;
            }
            builder.addInputSlot(x + 1, y + 1).setStandardSlotBackground().addItemStacks(stacks);

            y += 18;
            if (y >= 54) {
                y -= 54;
                x += 18;
            }
        }

        if (recipe.circumstance.isFluid()) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.CATALYST, 56, yOffset + 1);
            for (Fluid fluid : recipe.circumstance.getFluidsForRendering()) {
                if (fluid.isSource(fluid.defaultFluidState())) {
                    slot.addFluidStack(fluid);
                }
            }
            slot.setCustomRenderer(NeoForgeTypes.FLUID_STACK, fluidRenderer);
        } else if (recipe.circumstance.isExplosion()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 56, yOffset + 1)
                    .addItemLike(Blocks.TNT)
                    .addItemLike(AEBlocks.TINY_TNT);
        }

        builder.addOutputSlot(106, yOffset + 1).setStandardSlotBackground()
                .addItemStack(recipe.getResultItem());

        ci.cancel();
    }

    /** 判断合并结果中是否存在重复材料；无重复时无需接管原布局。 */
    private static boolean hasRepeatedIngredient(List<CountedIngredient> merged) {
        for (CountedIngredient counted : merged) {
            if (counted.count() > 1) {
                return true;
            }
        }
        return false;
    }
}