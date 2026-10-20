package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SoulVialSetIngredientPortTest {
    @BeforeAll static void bootstrap() {
        RecipeTestBootstrap.initialize();
        CraftingHelper.register(new ResourceLocation("useless_mod:soul_vial_set"),SoulVialSetIngredient.TYPE);
    }
    private static ItemStack vial(String entity) {
        var stack=new ItemStack(Items.GLASS_BOTTLE); stack.getOrCreateTag().putString("Entity",entity); return stack;
    }
    @Test void nativeJsonAndWireKeepExactNbtAndAllAlternatives() {
        var zombie=vial("minecraft:zombie"); var skeleton=vial("minecraft:skeleton");
        Ingredient source=SoulVialSetIngredient.of(List.of(zombie,skeleton));
        var json=Ingredient.fromJson(source.toJson());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecipeCodecs.INGREDIENT_STREAM.encode(buffer,source);
            var wire=RecipeCodecs.INGREDIENT_STREAM.decode(buffer);
            for(var decoded:List.of(json,wire)) {
                assertInstanceOf(SoulVialSetIngredient.class,decoded);
                assertTrue(decoded.test(zombie.copyWithCount(64))); assertTrue(decoded.test(skeleton));
                assertFalse(decoded.test(vial("minecraft:creeper")));
                assertFalse(decoded.test(new ItemStack(Items.GLASS_BOTTLE)));
                var extra=zombie.copy(); extra.getOrCreateTag().putBoolean("OtherModFlag",true);
                assertFalse(decoded.test(extra));
                assertEquals(2,decoded.getItems().length);
            }
            assertEquals(0,buffer.readableBytes());
        } finally {buffer.release();}
    }
    @Test void defensiveCopiesPreventDisplayCodeFromMutatingRecipePredicates() {
        var stack=vial("minecraft:zombie");
        var ingredient=(SoulVialSetIngredient)SoulVialSetIngredient.of(List.of(stack,stack.copyWithCount(64)));
        stack.getOrCreateTag().putString("Entity","minecraft:creeper");
        assertEquals(1,ingredient.getItems().length);
        ingredient.getItems()[0].getOrCreateTag().putString("Entity","minecraft:skeleton");
        assertTrue(ingredient.test(vial("minecraft:zombie"))); assertFalse(ingredient.test(stack));
        assertThrows(IllegalArgumentException.class,()->SoulVialSetIngredient.of(List.of(ItemStack.EMPTY)));
    }
}
