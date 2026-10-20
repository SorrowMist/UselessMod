package com.sorrowmist.useless.compat.forge1201.recipe;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopLevelNbtIngredientTest {
    @BeforeAll static void bootstrap() {
        RecipeTestBootstrap.initialize();
        CraftingHelper.register(new ResourceLocation("useless_mod:top_level_nbt"),TopLevelNbtIngredient.TYPE);
    }
    private static ItemStack matrix() {
        var stack=new ItemStack(Items.DIAMOND); var inner=new CompoundTag();inner.putInt("level",3);
        stack.getOrCreateTag().put("MatrixState",inner);return stack;
    }
    @Test void matchesNativeHnnTopLevelNbtRuleNotRecursivePartialNbt() {
        var required=matrix(); var ingredient=new TopLevelNbtIngredient(required);
        var extraTop=required.copy(); extraTop.getOrCreateTag().putString("Unrelated","allowed");
        assertTrue(ingredient.test(extraTop));
        var extraNested=required.copy(); extraNested.getTag().getCompound("MatrixState").putString("Unrelated","rejected");
        assertFalse(ingredient.test(extraNested));
        assertTrue(DataComponentIngredient.of(false,required).test(extraNested),"Forge partial NBT has different nested semantics");
        var wrongItem=new ItemStack(Items.EMERALD); wrongItem.setTag(required.getTag().copy());
        assertFalse(ingredient.test(wrongItem));
    }
    @Test void nativeJsonAndNetworkRoundTripPreservePredicateWithoutFlattening() {
        var source=new TopLevelNbtIngredient(matrix());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecipeCodecs.INGREDIENT_STREAM.encode(buffer,source);
            var wire=RecipeCodecs.INGREDIENT_STREAM.decode(buffer);
            var json=Ingredient.fromJson(source.toJson());
            for(var restored:new Ingredient[]{wire,json}) {
                assertInstanceOf(TopLevelNbtIngredient.class,restored);
                assertTrue(restored.test(matrix()));
                assertFalse(restored.test(new ItemStack(Items.DIAMOND)));
                assertEquals(source,restored);
            }
            assertEquals(0,buffer.readableBytes());
        } finally {buffer.release();}
    }
    @Test void missingNbtConstraintAllowsForeignNbtAndInputIsDefensivelyCopied() {
        var required=matrix();var ingredient=new TopLevelNbtIngredient(required);
        required.getTag().getCompound("MatrixState").putInt("level",4);
        ingredient.getItems()[0].setTag(null);
        assertTrue(ingredient.test(matrix())); assertFalse(ingredient.test(required));
        assertTrue(new TopLevelNbtIngredient(new ItemStack(Items.DIAMOND)).test(matrix()));
        assertThrows(IllegalArgumentException.class,()->new TopLevelNbtIngredient(ItemStack.EMPTY));
    }
}
