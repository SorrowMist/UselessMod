package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.GrassWandDropHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20.1 has no BlockDropsEvent. Run after real destruction, not simulated recipe/loot queries. */
@Mixin(Block.class)
public abstract class BlockGrassWandDropMixin {
    @Inject(method="playerDestroy", at=@At("TAIL"))
    private void useless$grassReward(Level level,Player player,BlockPos pos,BlockState state,
            BlockEntity entity,ItemStack tool,CallbackInfo ci) {
        GrassWandDropHandler.onBlockDestroyed(level,player,pos,state);
    }
}
