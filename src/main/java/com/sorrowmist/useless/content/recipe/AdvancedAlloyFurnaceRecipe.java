package com.sorrowmist.useless.content.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.init.ModRecipeSerializers;
import com.sorrowmist.useless.init.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record AdvancedAlloyFurnaceRecipe(
        ResourceLocation id,
        List<CountedIngredient> inputs,
        List<FluidStack> inputFluids,
        List<ItemStack> outputs,
        List<FluidStack> outputFluids,
        int energy,
        int processTime,
        Ingredient catalyst,
        int catalystUses,
        Ingredient mold,
        AlloyFurnaceMode mode
) implements Recipe<RecipeInput> {

    // 网络同步 StreamCodec
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedAlloyFurnaceRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, r.id());
                CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.inputs());
                FluidStack.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.inputFluids());
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.outputs());
                FluidStack.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.outputFluids());
                ByteBufCodecs.VAR_INT.encode(buf, r.energy());
                ByteBufCodecs.VAR_INT.encode(buf, r.processTime());
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.catalyst());
                ByteBufCodecs.VAR_INT.encode(buf, r.catalystUses());
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.mold());
                ByteBufCodecs.STRING_UTF8.encode(buf, r.mode().getSerializedName());
            },
            buf -> {
                ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
                List<CountedIngredient> inputs = CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                List<FluidStack> inputFluids = FluidStack.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                List<ItemStack> outputs = ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                List<FluidStack> outputFluids = FluidStack.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                int energy = ByteBufCodecs.VAR_INT.decode(buf);
                int processTime = ByteBufCodecs.VAR_INT.decode(buf);
                Ingredient catalyst = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                int catalystUses = ByteBufCodecs.VAR_INT.decode(buf);
                Ingredient mold = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                String modeStr = ByteBufCodecs.STRING_UTF8.decode(buf);
                AlloyFurnaceMode mode = AlloyFurnaceMode.fromString(modeStr);

                return new AdvancedAlloyFurnaceRecipe(
                        id, inputs, inputFluids, outputs, outputFluids,
                        energy, processTime, catalyst, catalystUses, mold, mode
                );
            }
    );
    // 自定义ItemStack Codec，匹配JSON格式 {"id": "xxx", "count": 1}
    private static final Codec<ItemStack> ITEM_STACK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ItemStack::getItem),
            Codec.INT.optionalFieldOf("count", 1).forGetter(ItemStack::getCount)
    ).apply(instance, (item, count) -> new ItemStack(item, count)));

    // 主 Codec（JSON / datapack）
    static final MapCodec<AdvancedAlloyFurnaceRecipe> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(AdvancedAlloyFurnaceRecipe::id),
                    CountedIngredient.CODEC.codec().listOf().fieldOf("ingredients")
                                           .forGetter(AdvancedAlloyFurnaceRecipe::inputs),
                    FluidStack.CODEC.listOf().optionalFieldOf("input_fluids", List.of())
                                    .forGetter(AdvancedAlloyFurnaceRecipe::inputFluids),
                    ITEM_STACK_CODEC.listOf().fieldOf("outputs").forGetter(AdvancedAlloyFurnaceRecipe::outputs),
                    FluidStack.CODEC.listOf().optionalFieldOf("output_fluids", List.of())
                                    .forGetter(AdvancedAlloyFurnaceRecipe::outputFluids),
                    Codec.INT.optionalFieldOf("energy", 2000).forGetter(AdvancedAlloyFurnaceRecipe::energy),
                    Codec.INT.optionalFieldOf("process_time", 200).forGetter(AdvancedAlloyFurnaceRecipe::processTime),
                    Ingredient.CODEC.optionalFieldOf("catalyst", Ingredient.EMPTY)
                                    .forGetter(AdvancedAlloyFurnaceRecipe::catalyst),
                    Codec.INT.optionalFieldOf("catalyst_uses", 0).forGetter(AdvancedAlloyFurnaceRecipe::catalystUses),
                    Ingredient.CODEC.optionalFieldOf("mold", Ingredient.EMPTY)
                                    .forGetter(AdvancedAlloyFurnaceRecipe::mold),
                    AlloyFurnaceMode.CODEC.optionalFieldOf("mode", AlloyFurnaceMode.NORMAL)
                                          .forGetter(AdvancedAlloyFurnaceRecipe::mode)
            ).apply(instance, AdvancedAlloyFurnaceRecipe::new));

    // ────────────── 以下是 Recipe 接口的占位实现 ──────────────

    @Override public boolean matches(RecipeInput recipeInput, Level level) {
        return false;   // 机器自己匹配，不走原版系统
    }

    @Override public ItemStack assemble(RecipeInput recipeInput, HolderLookup.Provider provider) {
        return this.outputs.isEmpty() ? ItemStack.EMPTY : this.outputs.getFirst().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.assemble(null, registries);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        this.inputs.forEach(ci -> list.add(ci.ingredient()));
        return list;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ADVANCED_ALLOY_FURNACE_SERIALIZER.get(); // 替换成你实际的注册对象
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get(); // 替换成你实际的注册对象
    }
}