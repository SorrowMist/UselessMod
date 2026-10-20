package com.sorrowmist.useless.compat.forge1201.fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.compat.forge1201.network.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fluids.FluidStack;
import java.util.Arrays;
import java.util.Objects;

public record SizedFluidIngredient(FluidIngredient ingredient,int amount) {
    public static final Codec<SizedFluidIngredient> CODEC=RecordCodecBuilder.create(instance -> instance.group(
            FluidIngredient.CODEC.fieldOf("ingredient").forGetter(SizedFluidIngredient::ingredient),
            Codec.intRange(1,Integer.MAX_VALUE).fieldOf("amount").forGetter(SizedFluidIngredient::amount)
    ).apply(instance,SizedFluidIngredient::new));
    public static final StreamCodec<FriendlyByteBuf,SizedFluidIngredient> STREAM_CODEC=StreamCodec.composite(
            FluidIngredient.STREAM_CODEC,SizedFluidIngredient::ingredient,
            ByteBufCodecs.VAR_INT,SizedFluidIngredient::amount,SizedFluidIngredient::new);
    public SizedFluidIngredient {
        Objects.requireNonNull(ingredient);
        if(amount<=0) throw new IllegalArgumentException("Fluid amount must be positive");
    }
    public static SizedFluidIngredient of(FluidStack stack) {
        return new SizedFluidIngredient(stack.hasTag()?FluidIngredient.nbt(true,stack):FluidIngredient.single(stack),stack.getAmount());
    }
    public boolean test(FluidStack stack) { return ingredient.test(stack) && stack.getAmount()>=amount; }
    public FluidStack[] getFluids() {
        return Arrays.stream(ingredient.getStacks()).map(s -> new FluidStack(s,amount)).toArray(FluidStack[]::new);
    }
}
