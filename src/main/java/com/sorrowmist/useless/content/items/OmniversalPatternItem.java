package com.sorrowmist.useless.content.items;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.EncodedPatternItem;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** AE2 15 recognizes EncodedPatternItem directly through its native pattern decoder. */
public final class OmniversalPatternItem extends EncodedPatternItem {
    public OmniversalPatternItem(){super(new Properties().stacksTo(1));}
    @Override public IPatternDetails decode(ItemStack stack,Level level,boolean tryRecovery){
        return decode(AEItemKey.of(stack),level);
    }
    @Override public IPatternDetails decode(AEItemKey key,Level level){return OmniversalPatternDetails.decode(key,level);}
}
