package com.sorrowmist.useless.mixin.botanypots;

import com.sorrowmist.useless.core.config.ConfigManager;
import net.darkhax.botanypots.block.BlockEntityBotanyPot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Advance only an actual native growth step, preserving all harvest events and inventory rules. */
@Mixin(value=BlockEntityBotanyPot.class,remap=false)
public abstract class BotanyPotBlockEntityMixin {
    @Inject(method="tickPot",at=@At(value="FIELD",target="Lnet/darkhax/botanypots/block/BlockEntityBotanyPot;growthTime:I",opcode=org.objectweb.asm.Opcodes.GETFIELD,ordinal=0))
    private static void useless$growthMultiplier(Level level,BlockPos pos,BlockState state,BlockEntityBotanyPot pot,CallbackInfo ci){
        int multiplier=ConfigManager.getBotanyPotGrowthMultiplier();
        if(multiplier<=1) return;
        int current=pot.getGrowthTime();
        if(current<0) return;
        long remaining=Integer.MAX_VALUE-(long)current-1L;
        pot.addGrowth((int)Math.min(remaining,(long)multiplier-1L));
    }
}
