package com.sorrowmist.useless.content.recipe;

import com.google.gson.JsonParser;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FurnaceRecipePortTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    @Test void nativeSerializerUsesManagerIdAndPreservesLongQuantitiesAndNbt() {
        var json=JsonParser.parseString("""
                {"id":"wrong:ignored","ingredients":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":5000000000}],
                 "input_fluids":[{"ingredient":{"tag":"forge:water"},"amount":6000000000}],
                 "outputs":[{"item":"minecraft:diamond","count":32768,"nbt":"{OtherMod:42}"}],
                 "output_fluids":[{"fluid":"minecraft:water","amount":1000,"nbt":"{grade:2}"}],
                 "energy":7000000000,"process_time":60,"tier":4,
                 "molds":[{"item":"minecraft:gold_ingot"},{"item":"minecraft:copper_ingot"}]}
                """).getAsJsonObject();
        var id=new ResourceLocation("kubejs:exact_runtime_id");
        var serializer=AdvancedAlloyFurnaceRecipeSerializer.INSTANCE;
        var recipe=serializer.fromJson(id,json);
        assertEquals(id,recipe.getId());
        assertEquals("wrong:ignored",json.get("id").getAsString());
        assertEquals(5_000_000_000L,recipe.inputs().get(0).count());
        assertEquals(6_000_000_000L,recipe.inputFluids().get(0).amount());
        assertEquals(7_000_000_000L,recipe.energy()); assertEquals(2,recipe.molds().size());
        FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buf,recipe);
            var restored=serializer.fromNetwork(id,buf);
            assertEquals(recipe.id(),restored.id());
            assertEquals(recipe.inputFluids(),restored.inputFluids());
            assertEquals(32768,restored.outputs().get(0).getCount());
            assertEquals(42,restored.outputs().get(0).getTag().getInt("OtherMod"));
            assertEquals(2,restored.outputFluids().get(0).getTag().getInt("grade"));
            assertEquals(2,restored.molds().size()); assertEquals(4,restored.tier());
            assertEquals(0,buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test void nativeFinishedRecipeRoundTripsAndMalformedMoldShapeFails() {
        var id=new ResourceLocation("useless_mod:port_builder_roundtrip");
        List<net.minecraft.data.recipes.FinishedRecipe> generated=new ArrayList<>();
        AdvancedAlloyFurnaceRecipeBuilder.create().input(Items.IRON_INGOT,12)
                .fluidInput(new FluidStack(Fluids.WATER,1000)).output(Items.DIAMOND,8192).save(generated::add,id);
        // This suite does not run Forge's mod registry events; assert identity, then serialize recipe data.
        assertSame(AdvancedAlloyFurnaceRecipeSerializer.INSTANCE,generated.get(0).getType());
        var json=new com.google.gson.JsonObject(); generated.get(0).serializeRecipeData(json);
        var restored=AdvancedAlloyFurnaceRecipeSerializer.INSTANCE.fromJson(id,json);
        assertEquals(8192,restored.outputs().get(0).getCount());
        assertEquals(1000,restored.inputFluids().get(0).amount());
        json.add("mold",JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\"}"));
        json.add("molds",JsonParser.parseString("[{\"item\":\"minecraft:gold_ingot\"}]"));
        assertThrows(RuntimeException.class,()->AdvancedAlloyFurnaceRecipeSerializer.INSTANCE.fromJson(id,json));
    }

    @Test void overlappingItemAndFluidDemandsCannotDoubleSpendSupply() {
        var ironOrGold=Ingredient.of(Items.IRON_INGOT,Items.GOLD_INGOT);
        var requirements=List.of(new CountedIngredient(ironOrGold,1),new CountedIngredient(Ingredient.of(Items.IRON_INGOT),1));
        assertFalse(ItemIngredientAllocator.matches(requirements,List.of(new ItemStack(Items.IRON_INGOT)),1));
        var allocation=ItemIngredientAllocator.allocate(requirements,List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.GOLD_INGOT)),1);
        assertNotNull(allocation); assertEquals(1,allocation.consumedFromInput(0)); assertEquals(1,allocation.consumedFromInput(1));
        var waterOrLava=FluidIngredient.any(List.of(FluidIngredient.single(Fluids.WATER),FluidIngredient.single(Fluids.LAVA)));
        var fluids=List.of(new LongSizedFluidIngredient(waterOrLava,1000),new LongSizedFluidIngredient(FluidIngredient.single(Fluids.WATER),1000));
        assertFalse(FluidIngredientAllocator.matchesLong(fluids,List.of(new FluidStack(Fluids.WATER,1000)),1));
        assertTrue(FluidIngredientAllocator.matchesLong(fluids,List.of(new FluidStack(Fluids.WATER,1000),new FluidStack(Fluids.LAVA,1000)),1));
    }

    @Test void fluidMapKeepsAboveIntAmountsAndRejectsOverflowInsteadOfFreeCrafts() {
        var fluid=FluidIngredient.single(Fluids.WATER);
        Map<FluidStack,Long> supplies=Map.of(new FluidStack(Fluids.WATER,1),6_000_000_000L);
        var requirement=List.of(new LongSizedFluidIngredient(fluid,3_000_000_000L));
        assertTrue(FluidIngredientAllocator.matchesLong(requirement,supplies,2));
        assertFalse(FluidIngredientAllocator.matchesLong(requirement,supplies,3));
        assertFalse(FluidIngredientAllocator.matchesLong(List.of(new LongSizedFluidIngredient(fluid,Long.MAX_VALUE)),
                Map.of(new FluidStack(Fluids.WATER,1),Long.MAX_VALUE),2));
        assertFalse(FluidIngredientAllocator.matchesLong(List.of(new LongSizedFluidIngredient(fluid,Long.MAX_VALUE),new LongSizedFluidIngredient(fluid,1)),
                Map.of(new FluidStack(Fluids.WATER,1),Long.MAX_VALUE),1));
        var exact=DataComponentIngredient.of(true,new ItemStack(Items.IRON_INGOT));
        assertFalse(ItemIngredientAllocator.matches(Map.of(exact,Long.MAX_VALUE),Map.of(exact,Long.MAX_VALUE,Ingredient.of(Items.GOLD_INGOT),1L)));
    }

    @Test void nativeIngredientPacketRetainsExactNbtPredicate() {
        var required=new ItemStack(Items.DIAMOND); required.getOrCreateTag().putInt("grade",3);
        var codec=com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs.INGREDIENT_STREAM;
        FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buf,DataComponentIngredient.of(true,required));
            Ingredient restored=codec.decode(buf);
            assertTrue(restored.test(required)); assertFalse(restored.test(new ItemStack(Items.DIAMOND)));
            var different=required.copy(); different.getOrCreateTag().putInt("grade",2);
            assertFalse(restored.test(different));
        } finally { buf.release(); }
    }
}
