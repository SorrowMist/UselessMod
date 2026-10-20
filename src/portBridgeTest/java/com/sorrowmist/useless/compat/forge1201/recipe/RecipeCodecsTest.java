package com.sorrowmist.useless.compat.forge1201.recipe;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.network.PortCodecs;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeCodecsTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }

    @Test void itemJsonPreservesNbtAndLargeIntCount() {
        var stack=RecipeCodecs.readItem(JsonParser.parseString("""
                {"item":"minecraft:diamond","count":32768,"nbt":"{OtherModState:{value:17},Enchantments:[{id:\"minecraft:fortune\",lvl:3s}]}"}
                """.replace("id:\"minecraft", "id:\\\"minecraft").replace("fortune\",lvl", "fortune\\\",lvl")));
        var restored=RecipeCodecs.readItem(RecipeCodecs.writeItem(stack));
        assertEquals(32768,restored.getCount());
        assertEquals(stack.getTag(),restored.getTag());
        assertEquals(17,restored.getTag().getCompound("OtherModState").getInt("value"));
    }

    @Test void packetKeepsCountAboveByteRangeAndAllNbt() {
        ItemStack stack=new ItemStack(Items.DIAMOND,50000);
        stack.getOrCreateTag().putString("ForeignMod","preserved");
        CompoundTag custom=new CompoundTag(); custom.putLong("operations",Long.MAX_VALUE);
        stack.getOrCreateTag().put("useless_mod:components",custom);
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            PortCodecs.ITEM_STACK.encode(buffer,stack);
            ItemStack decoded=PortCodecs.ITEM_STACK.decode(buffer);
            assertEquals(50000,decoded.getCount());
            assertEquals(stack.getTag(),decoded.getTag());
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void unknownComponentsItemsOrFractionalCountsFailInsteadOfDroppingData() {
        for(String json:new String[]{
                "{\"item\":\"missing:thing\"}",
                "{\"item\":\"minecraft:diamond\",\"count\":1.5}",
                "{\"item\":\"minecraft:diamond\",\"count\":2147483648}",
                "{\"item\":\"minecraft:diamond\",\"components\":{\"other:state\":true}}"})
            assertTrue(RecipeCodecs.ITEM_STACK.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).error().isPresent(),json);
    }

    @Test void nativeNbtItemIngredientKeepsExactAndPartialSemantics() {
        ItemStack required=new ItemStack(Items.DIAMOND); required.getOrCreateTag().putString("name","required");
        ItemStack extra=required.copy(); extra.getOrCreateTag().putInt("extra",1);
        assertTrue(DataComponentIngredient.of(true,required).test(required.copyWithCount(64)));
        assertFalse(DataComponentIngredient.of(true,required).test(extra));
        assertTrue(DataComponentIngredient.of(false,required).test(extra));
        assertFalse(DataComponentIngredient.of(false,required).test(new ItemStack(Items.DIAMOND)));
    }

    @Test void nativeCraftingGridCopiesInputsAndPreservesDimensionsAndEmptySlots() {
        var original=new ItemStack(Items.DIAMOND,64);
        var grid=CraftingInputs.of(2,1,java.util.List.of(original,ItemStack.EMPTY));
        assertEquals(2,grid.getWidth()); assertEquals(1,grid.getHeight());
        grid.removeItem(0,1);
        assertEquals(64,original.getCount()); assertEquals(63,grid.getItem(0).getCount());
        assertTrue(grid.getItem(1).isEmpty());
        assertThrows(IllegalArgumentException.class,()->CraftingInputs.of(3,3,java.util.List.of(original)));
    }
}
