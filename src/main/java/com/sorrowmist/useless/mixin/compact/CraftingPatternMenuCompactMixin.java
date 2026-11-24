package com.sorrowmist.useless.mixin.compact;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactF9BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.gui.crafting.CraftingPatternMenu", remap = false)
public abstract class CraftingPatternMenuCompactMixin {
    @ModifyExpressionValue(method = "decode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            remap = false), remap = false)
    private static BlockEntity uselessMod$resolvePatternInterface(BlockEntity entity) {
        return CompactF9BlockEntity.resolveCraftingInterface(entity);
    }
}
