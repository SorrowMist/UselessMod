package com.sorrowmist.useless.content.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.init.ModRecipeSerializers;
import com.sorrowmist.useless.init.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 「熵变机械臂潜行右键 ME 箱子 → 原地替换为输出方块」的配方数据。
 *
 * <p>AE2 熵变机械臂的 {@code EntropyRecipe} 只匹配方块状态与流体状态，无法表达
 * 「ME 箱子里的磁盘装了哪些物品」，因此本机制不能落在 {@code EntropyRecipe} 上。
 * 这里用一条自定义配方承载「需要哪些物品、各多少、产出哪个方块」，
 * 由事件层在潜行右键 ME 箱子时读取并校验、消耗磁盘内容。</p>
 *
 * <p>配方本身不参与原版合成：{@link #matches} 恒为 false，机器逻辑由事件层驱动。</p>
 *
 * @param inputs 需要从 ME 箱子磁盘中扣除的物品（同一磁盘内合计即可）
 * @param output 满足条件后原地替换成的方块物品
 */
public record EntropyTransmutationRecipe(
        List<CountedIngredient> inputs,
        ItemStack output
) implements Recipe<RecipeInput> {

    public static final MapCodec<EntropyTransmutationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    CountedIngredient.CODEC.codec().listOf().fieldOf("inputs")
                            .forGetter(EntropyTransmutationRecipe::inputs),
                    ItemStack.CODEC.fieldOf("output")
                            .forGetter(EntropyTransmutationRecipe::output)
            ).apply(instance, EntropyTransmutationRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntropyTransmutationRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    EntropyTransmutationRecipe::inputs,
                    ItemStack.STREAM_CODEC,
                    EntropyTransmutationRecipe::output,
                    EntropyTransmutationRecipe::new);

    public EntropyTransmutationRecipe {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("熵变转化配方必须至少指定一项输入物品");
        }
        if (output == null || output.isEmpty()) {
            throw new IllegalArgumentException("熵变转化配方必须指定输出方块物品");
        }
        inputs = List.copyOf(inputs);
        output = output.copy();
    }

    @Override
    public boolean matches(@NotNull RecipeInput input, @NotNull Level level) {
        return false; // 事件层自行匹配，不走原版配方系统
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull RecipeInput input, @NotNull HolderLookup.Provider registries) {
        return this.output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public @NotNull ItemStack getResultItem(@NotNull HolderLookup.Provider registries) {
        return this.output.copy();
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        this.inputs.forEach(counted -> list.add(counted.ingredient()));
        return list;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ENTROPY_TRANSMUTATION_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipeTypes.ENTROPY_TRANSMUTATION_TYPE.get();
    }
}
