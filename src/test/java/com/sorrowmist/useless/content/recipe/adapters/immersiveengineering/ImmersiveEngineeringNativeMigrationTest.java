package com.sorrowmist.useless.content.recipe.adapters.immersiveengineering;

import blusunrize.immersiveengineering.api.crafting.FluidTagInput;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImmersiveEngineeringNativeMigrationTest {
    private static TagKey<Fluid> TAG;
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        TAG = TagKey.create(Registries.FLUID, new ResourceLocation("test", "ie_coolants"));
    }

    @Test void nativeTagNbtAndAmountArePreservedOnBothServerAndSyncedClient() throws Exception {
        BuiltInRegistries.FLUID.bindTags(Map.of(TAG, List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER))));
        CompoundTag nbt = new CompoundTag(); nbt.putString("grade", "pure");
        var source = new FluidTagInput(TAG, 750, nbt);
        verifyPredicate(source, convert(source));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            source.write(buf);
            var synced = FluidTagInput.read(buf);
            verifyPredicate(synced, convert(synced));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test void plainNativeTagRemainsLiveRatherThanAFluidIdSnapshot() throws Exception {
        BuiltInRegistries.FLUID.bindTags(Map.of(TAG, List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER))));
        var source = new FluidTagInput(TAG, 1000);
        var converted = convert(source);
        assertEquals(TAG.location().toString(), converted.ingredient().toJson().getAsJsonObject().get("tag").getAsString());
        BuiltInRegistries.FLUID.bindTags(Map.of(TAG, List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA))));
        assertFalse(converted.ingredient().test(new FluidStack(Fluids.WATER, 1000)));
        assertTrue(converted.ingredient().test(new FluidStack(Fluids.LAVA, 1000)));
    }

    private static void verifyPredicate(FluidTagInput source, SizedFluidIngredient converted) {
        assertEquals(source.getAmount(), converted.amount());
        for (Fluid fluid : List.of(Fluids.WATER, Fluids.LAVA)) for (String grade : List.of("pure", "dirty")) {
            var stack = new FluidStack(fluid, 1000); stack.getOrCreateTag().putString("grade", grade);
            assertEquals(source.testIgnoringAmount(stack), converted.ingredient().test(stack));
            stack.getOrCreateTag().putInt("extra", 1);
            assertEquals(source.testIgnoringAmount(stack), converted.ingredient().test(stack));
        }
    }
    @SuppressWarnings("unchecked")
    private static SizedFluidIngredient convert(FluidTagInput source) throws Exception {
        var method = ImmersiveEngineeringRecipeAdapter.class.getDeclaredMethod("fluidInputs", FluidTagInput[].class);
        method.setAccessible(true);
        return ((List<SizedFluidIngredient>) method.invoke(null, (Object) new FluidTagInput[]{source})).get(0);
    }
}
