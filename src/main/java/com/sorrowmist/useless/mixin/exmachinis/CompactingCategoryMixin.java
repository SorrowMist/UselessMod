package com.sorrowmist.useless.mixin.exmachinis;

import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Ex Machinis 3.0.3 still reads a JEI internal field removed by current JEI. */
@Pseudo
@Mixin(targets = "net.permutated.exmachinis.compat.jei.category.CompactingCategory", remap = false)
public abstract class CompactingCategoryMixin {
    // A fixed Ex Machinis version may no longer read this field.
    @Redirect(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lmezz/jei/common/Constants;RECIPE_GUI_VANILLA:Lnet/minecraft/resources/ResourceLocation;"),
            require = 0)
    private ResourceLocation uselessMod$recipeBackground() {
        return new ResourceLocation("useless_mod", "textures/gui/exmachinis_recipe.png");
    }
}
