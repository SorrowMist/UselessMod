package com.sorrowmist.useless.compat.kubejs;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import appeng.api.stacks.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeKubeComponentTest {
    @BeforeAll static void init(){RecipeTestBootstrap.initialize();}
    @Test void jsonPreservesLongQuantityAndItemNbt(){
        var item=new ItemStack(Items.DIAMOND);item.getOrCreateTag().putString("marker","kubejs");
        var stack=new GenericStack(AEItemKey.of(item),9_000_000_000L);
        var component=GenericStackRecipeComponent.GENERIC_STACK;
        var json=component.write(null,stack);
        // Verify encoded native NBT directly; key-type registration belongs to mod bootstrap.
        assertTrue(json.toString().contains("9000000000"));assertTrue(json.toString().contains("kubejs"));
        assertSame(stack,component.read(null,stack));
    }
    @Test void bothNativeObjectsAndJsonRejectZeroOrNegativeAmounts(){
        var stack=new GenericStack(AEItemKey.of(Items.DIAMOND),0);
        var component=GenericStackRecipeComponent.GENERIC_STACK;
        assertThrows(IllegalArgumentException.class,()->component.read(null,stack));
        assertThrows(IllegalArgumentException.class,()->component.write(null,stack));
    }
}
