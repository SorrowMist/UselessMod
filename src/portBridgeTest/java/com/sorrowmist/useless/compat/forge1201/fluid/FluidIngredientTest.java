package com.sorrowmist.useless.compat.forge1201.fluid;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FluidIngredientTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    private static FluidStack water(String value) {
        FluidStack stack=new FluidStack(Fluids.WATER,1000);
        stack.getOrCreateTag().putString("grade",value); return stack;
    }

    @Test void exactAndPartialNbtNeverCollapseToPlainFluid() {
        var expected=water("pure"); var extra=expected.copy(); extra.getOrCreateTag().putInt("extra",1);
        var exact=FluidIngredient.nbt(true,expected); var partial=FluidIngredient.nbt(false,expected);
        expected.getOrCreateTag().putString("grade","mutated");
        assertTrue(exact.test(water("pure"))); assertFalse(exact.test(water("dirty")));
        assertFalse(exact.test(extra)); assertTrue(partial.test(extra));
        var representative=exact.getStacks()[0]; representative.getOrCreateTag().putString("grade","mutated");
        assertTrue(exact.test(water("pure")));
    }

    @Test void compoundKeepsEveryAlternativeAcrossJsonAndNetwork() {
        var ingredient=FluidIngredient.any(List.of(FluidIngredient.nbt(true,water("pure")),FluidIngredient.single(Fluids.LAVA)));
        var decoded=FluidIngredient.fromJson(ingredient.toJson());
        assertEquals(ingredient,decoded);
        FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            FluidIngredient.STREAM_CODEC.encode(buf,decoded); decoded=FluidIngredient.STREAM_CODEC.decode(buf);
            assertTrue(decoded.test(water("pure"))); assertFalse(decoded.test(water("dirty")));
            assertTrue(decoded.test(new FluidStack(Fluids.LAVA,1))); assertEquals(0,buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test void fluidTagResolvesAfterBindingAndReloadWithoutSnapshot() {
        TagKey<Fluid> tag=TagKey.create(Registries.FLUID,new ResourceLocation("useless_port_test:live_fluid"));
        var ingredient=FluidIngredient.tag(tag);
        assertFalse(ingredient.isEmpty()); // Unbound tags must not get dropped by recipe normalization.
        assertEquals(tag.location().toString(),ingredient.toJson().getAsJsonObject().get("tag").getAsString());
        BuiltInRegistries.FLUID.bindTags(Map.of(tag,List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER))));
        assertTrue(ingredient.test(new FluidStack(Fluids.WATER,1)));
        assertFalse(ingredient.test(new FluidStack(Fluids.LAVA,1)));
        BuiltInRegistries.FLUID.bindTags(Map.of(tag,List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA))));
        assertFalse(ingredient.test(new FluidStack(Fluids.WATER,1)));
        assertTrue(ingredient.test(new FluidStack(Fluids.LAVA,1)));
        assertSame(Fluids.LAVA,ingredient.getStacks()[0].getFluid());
    }

    @Test void invalidOrUnknownPredicatesAreNotAcceptedAsOrdinaryFluid() {
        for(String json:List.of("{\"fluid\":\"missing:fluid\"}","{\"type\":\"unknown:predicate\",\"fluid\":\"minecraft:water\"}",
                "{\"fluid\":\"minecraft:water\",\"components\":{\"other:state\":1}}"))
            assertTrue(FluidIngredient.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).error().isPresent());
        assertThrows(IllegalArgumentException.class,()->new SizedFluidIngredient(FluidIngredient.single(Fluids.WATER),0));
    }
}
